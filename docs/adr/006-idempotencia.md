# ADR 006 — Idempotência do `POST /requests`

## Contexto

O README exige que `POST /requests` com o mesmo `Idempotency-Key`, do mesmo usuário e com o mesmo payload, devolva **a mesma resposta** sem criar outra solicitação, com validade mínima de 24 h; a mesma chave com payload diferente responde `409`. O caso real é o retry do cliente: timeout, queda de rede depois de o servidor ter criado, ou duplo clique.

## Opções consideradas

1. **Cache em memória** (mapa ou Caffeine): some no restart e não funciona com mais de uma instância (contraria o ADR 007).
2. **Redis** com TTL: mais uma peça de infraestrutura e, principalmente, fora da transação do Postgres: a chave e a solicitação poderiam divergir.
3. **Tabela no Postgres** gravada na mesma transação da criação, com `UNIQUE (user_id, key)`.

E, para o que salvar: **só sucesso** ou também as respostas de erro.

## Decisão

- **Tabela `idempotency_key`** (`user_id`, `key`, `request_hash`, `response_status`, `response_body jsonb`, `created_at`) com **`UNIQUE (user_id, key)`**: a chave é por usuário; dois usuários podem usar a mesma.
- **Mesma transação**: o `IdempotencyService` abre a transação (`TransactionTemplate`), cria a solicitação e grava a chave com a resposta; se a criação falhar, nada fica salvo.
- **Só respostas de sucesso ficam salvas.** Um `422` desfaz a transação, então o cliente pode corrigir o corpo e reenviar com a **mesma chave** sem levar `409`. Um `400` nem chega ao serviço.
- **Hash normalizado** (SHA-256 sobre `CreatePurchaseRequestRequest.fingerprint()`): só os campos que definem a solicitação, numa ordem fixa, com o preço normalizado por `Money` (`"10.5"` e `"10.50"` geram o mesmo hash). O `total` enviado pelo cliente não entra, porque é ignorado.
- **Replay**: mesma chave + mesmo hash → a resposta salva (mesmo status `201` e mesmo corpo), com o header `Idempotent-Replayed: true`. Mesma chave + hash diferente → `409 IDEMPOTENCY_KEY_REUSED`.
- **Validade de 24 h**: no início da transação, um `DELETE` condicional apaga a chave daquele usuário se tiver mais de 24 h, e a criação é refeita como nova.
- **Corrida** (duas requisições simultâneas com a mesma chave): as duas passam pela leitura sem achar nada; a `UNIQUE` barra a segunda no `saveAndFlush`, e a transação dela é desfeita (inclusive a solicitação que ela criou). O serviço captura a `DataIntegrityViolationException`, relê a chave numa transação nova e devolve a resposta do vencedor (ou `409`, se o payload for outro).
- **Header opcional**: sem `Idempotency-Key`, a criação é feita normalmente. Quando presente, é validado (`\p{Graph}{1,255}`) e um valor inválido dá `400` com o campo `Idempotency-Key`.
- **Front (E29):** a chave é um UUID gerado por **instância do formulário**: retry depois de `5xx` ou de erro de rede e duplo clique reutilizam a chave; abrir `/requests/new` de novo gera outra.

## Consequências

- Exatamente uma solicitação por (usuário, chave) dentro da validade, garantido pelo banco e não por lock na aplicação (coberto por teste com chamadas simultâneas).
- O replay devolve a snapshot do momento da criação, não o estado atual da solicitação.
- Qualquer `DataIntegrityViolationException` na transação leva à releitura da chave, sem checar o nome da constraint; se a chave não existir, a exceção original é relançada.
- Um `costCenterId` omitido e um explícito igual ao do usuário geram hashes **diferentes** (`409` se a mesma chave for usada nas duas formas).
- **Risco conhecido:** a validação do corpo roda antes da idempotência; um replay feito depois que o `neededBy` deixou de ser futuro recebe `400`, e não a resposta salva.
- As chaves expiradas só são apagadas quando a mesma chave é reutilizada; não há job de limpeza (o índice em `created_at` permite fazer isso depois).
- As transições (`submit`, `approve` etc.) não têm `Idempotency-Key` (diferencial X7, não feito); um retry delas recebe `409` pelo `lockVersion` já incrementado, o que evita a duplicação mas não devolve a resposta original.
