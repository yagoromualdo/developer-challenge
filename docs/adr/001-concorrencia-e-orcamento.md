# ADR 001 — Concorrência das solicitações e do orçamento

## Contexto

O README pede controle de concorrência "otimista ou pessimista, justificado em ADR" e dá três garantias:

1. duas aprovações simultâneas da **mesma etapa**: exatamente uma vence; a outra recebe `409`;
2. aprovações finais simultâneas de **solicitações diferentes** que disputam o mesmo saldo nunca estouram o orçamento do mês (as excedentes recebem `422 BUDGET_EXCEEDED`);
3. toda transição recebe o `lockVersion` que o cliente viu; se estiver desatualizado, `409`.

A transição, as etapas, o consumo do orçamento, o evento de histórico e a chave de idempotência precisam ser gravados juntos: ou tudo, ou nada.

## Opções consideradas

**Solicitação**

1. **Pessimista** (`SELECT ... FOR UPDATE` na `purchase_request`): serializa as ações sobre a mesma solicitação, mas segura o lock durante todo o caso de uso e não atende o contrato do `lockVersion` sozinho (ainda é preciso comparar a versão que o cliente viu).
2. **Otimista** (`@Version`): sem lock durante a leitura; o conflito aparece na escrita. O próprio `@Version` pode ser o `lockVersion` exposto na API.

**Orçamento**

1. `SELECT ... FOR UPDATE` no `budget` + conta no Java: correto, mas são dois comandos e um lock explícito a mais.
2. `@Version` no `budget` com retry: numa disputa pelo mesmo saldo, as perdedoras precisariam repetir a transação inteira.
3. Isolamento `SERIALIZABLE`/`REPEATABLE READ`: o Postgres aborta as perdedoras com `40001`, que viraria retry ou `500`.
4. **`UPDATE` condicional atômico**: a regra "cabe no saldo" fica no `WHERE` do próprio `UPDATE`.

## Decisão

- **Solicitação: otimista.** O `@Version` da `PurchaseRequestEntity` **é** o `lockVersion` (coluna `lock_version`). O `RequestActionGuard` compara o `lockVersion` do corpo com o atual antes das regras de negócio (ordem 404 → 403 → **409** → 422, ADR 003). A corrida residual (os dois leram a mesma versão) é pega pelo `@Version` no flush; o `GlobalExceptionHandler` converte `OptimisticLockingFailureException` em `409 CONCURRENT_MODIFICATION`.
- **Orçamento: `UPDATE` condicional, sem `SELECT FOR UPDATE`** (`BudgetRepository.consumeIfAvailable`):
  ```sql
  UPDATE budget SET consumed = consumed + :value
   WHERE cost_center_id = :costCenterId AND month = :month AND amount - consumed >= :value
  ```
  0 linhas afetadas → `422 BUDGET_EXCEEDED`, inclusive quando o mês não tem orçamento (saldo zero). Em READ COMMITTED, um `UPDATE` que espera o lock da linha **reavalia o `WHERE`** com o `consumed` já commitado pelo concorrente, então duas aprovações nunca somam além do orçado. O saldo exibido na mensagem ("R$ 800,00 disponíveis…") é lido depois, só para informar: quem decidiu foi o `UPDATE`.
- **`CHECK (consumed >= 0 AND consumed <= amount)`** na tabela `budget` como última defesa, para nenhum caminho futuro contornar a regra.
- **`PUT` de orçamento** pelo mesmo princípio: `INSERT ... ON CONFLICT (cost_center_id, month) DO UPDATE SET amount = EXCLUDED.amount WHERE budget.consumed <= EXCLUDED.amount` (`BudgetRepository.upsertAmountIfNotBelowConsumed`). 0 linhas → `422 BUDGET_BELOW_CONSUMED`. Um consumo concorrente nunca deixa o orçado abaixo do consumido (nem cai no `CHECK` como `500`), e dois `PUT`s num mês sem orçamento não violam a `UNIQUE`.
- **Transações:** `@Transactional` por caso de uso, isolamento padrão **READ COMMITTED**. Na aprovação (`ApprovalService.decide`), a etapa é decidida e o status muda, e então há um `flush()` **antes** de `BudgetService.consume`: na corrida pela mesma etapa, o perdedor recebe `409` pelo `@Version` sem chegar a tocar no saldo.
- **Competência:** o orçamento consumido é o do mês de competência gravado na versão no `submit` (ADR 005), e não o mês da aprovação.

### Achado da E19: snapshot incoerente em READ COMMITTED

O `ApprovalConcurrencyIntegrationTest` (duas aprovações da mesma etapa com `ExecutorService` + `CountDownLatch`) devolveu, de forma intermitente, um **`500`** em vez de `409` (cerca de 1 em 10 execuções do `verify` completo; nenhuma em 100 execuções isoladas).

- **Causa:** o guard carregava `purchase_request`, a versão atual e as etapas em **três `SELECT`s separados** (coleções lazy). Em READ COMMITTED, cada `SELECT` enxerga os commits feitos até ele. O perdedor lia a solicitação ainda em `PENDING_APPROVAL` e, logo depois, as etapas já decididas pelo vencedor. Resultado: uma solicitação pendente sem etapa pendente, e a `ActionPolicy` lançava `IllegalStateException` (500).
- **Correção** (commit `9bf7291`): `PurchaseRequestRepository.findCurrentVersionWithSteps` traz solicitação + versão atual + etapas **num único `SELECT`** (`join fetch`), e o `RequestActionGuard.findVisible` o chama primeiro. O `findOne` seguinte (com a `Specification` de visibilidade) devolve a mesma instância gerenciada pelo identity map, sem reler o estado. Assim a snapshot é sempre de um único momento; quem perde a corrida cai no `409` do `lockVersion` ou do `@Version`.
- **Contrato:** essa leitura precisa ser a **primeira leitura da solicitação na transação**. Se a entidade já estiver no contexto de persistência, o Hibernate devolve a instância em cache e a snapshot volta a poder misturar commits (documentado no Javadoc de `findVisible`).
- **Alternativas descartadas:**
  - converter a snapshot incoerente em `409`: remenda o sintoma na ação, mas o `GET /requests/:id` continuaria podendo dar `500` ao montar o `allowedActions`;
  - subir para `REPEATABLE READ`: a snapshot ficaria coerente, mas o `UPDATE` condicional do orçamento deixaria de reavaliar o saldo e passaria a falhar com `40001` (serialization failure) sempre que dois consumos disputassem a mesma linha.
- **Como foi provado:** o teste pega a corrida só de vez em quando. A prova veio do stack trace do `500` e de um stress temporário com `@RepeatedTest(50)`. O teste versionado roda com `@RepeatedTest(10)`.
- **Reuso na E20:** a caixa de aprovação monta várias snapshots com o mesmo cuidado: primeiro busca só os ids (`RequestIdQuery.findIds`, sem carregar entidades no contexto) e depois `findCurrentVersionsWithSteps` (solicitação + versão atual + etapas num `SELECT`).

## Consequências

- Nenhum lock é segurado durante a leitura; o conflito custa um `409` e o cliente precisa recarregar. No front (E29/E30), o `409` recarrega o detalhe e descarta o que o usuário digitou na edição.
- Não há retry automático no servidor: quem perde recebe `409` (mesma etapa) ou `422 BUDGET_EXCEEDED` (saldo), que é o contrato pedido.
- A ordem dos locks é sempre a mesma (linha da `purchase_request` no flush, depois a linha do `budget`), o que evita ciclos entre aprovações de solicitações diferentes no mesmo saldo. *(Raciocínio, não provado por teste específico de deadlock.)*
- A regra do saldo existe em um só lugar (o `WHERE`), não na `ActionPolicy`: por isso o `allowedActions` pode mostrar `APPROVE` para uma solicitação que vai receber `422 BUDGET_EXCEEDED`.
- Toda nova leitura de solicitação para decidir ações precisa respeitar o contrato da "primeira leitura coerente"; um `findById` antes do guard reabre o bug.
- A mensagem do `BUDGET_EXCEEDED` usa o formato "R$ 8.200,00", e a do `BUDGET_BELOW_CONSUMED` ainda usa "8200.00" (inconsistência conhecida).
