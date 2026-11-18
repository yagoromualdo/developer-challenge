# SOLUTION — Sistema de solicitações de compra

## 1. Visão geral

Sistema de solicitações de compra com orçamento mensal por centro de custo, cadeia de aprovação sequencial em várias etapas, segregação de funções, versionamento das solicitações devolvidas para ajuste, controle de concorrência e idempotência na criação.

| Camada | Tecnologia |
|---|---|
| Back-end | Java 21, Spring Boot 4.1 (Spring Framework 7), Spring Data JPA, Spring Security (OAuth2 Resource Server, JWT HS256), Flyway, springdoc-openapi |
| Banco | PostgreSQL 17 |
| Front-end | Angular 22 (standalone, zoneless, signals), Angular Material |
| Testes | JUnit 5, AssertJ, Testcontainers, ArchUnit, JaCoCo (back); Vitest + TestBed (front) |
| Execução | Docker Compose (db + backend + nginx com o build do front) |
| CI | GitHub Actions: `backend`, `frontend` e `compose-smoke` ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)) |

A solução **continua correta sob concorrência e retry**:

- duas aprovações simultâneas da mesma etapa → exatamente uma passa e a outra recebe `409`;
- aprovações finais simultâneas disputando o mesmo saldo → o orçamento nunca fica negativo, e as que não cabem recebem `422 BUDGET_EXCEEDED`;
- o mesmo `POST /requests` reenviado com o mesmo `Idempotency-Key` → uma única solicitação criada e a mesma resposta devolvida.

A arquitetura (diagramas e evolução de escalabilidade) está em [`docs/architecture.md`](docs/architecture.md).

## 2. Como rodar

### 2.1 Com Docker Compose (recomendado)

Pré-requisito: Docker com Compose v2.

Na **raiz do repositório**:

```bash
docker compose up --build
```

Não há passo manual: as migrations do Flyway rodam na subida do back-end e o seed popula o banco vazio (`SEED_ENABLED=true` no compose). O `.env` é opcional; para mudar portas ou credenciais, copie o [`.env.example`](.env.example) para `.env`.

| Serviço | URL no host | Observação |
|---|---|---|
| Front-end (nginx) | http://localhost:4200 | nginx escuta na porta 80 do container; `/api/*` é encaminhado ao back-end sem o prefixo |
| Back-end | http://localhost:8080 | endpoints sem prefixo (`/auth/login`, `/requests`…) |
| Swagger UI | http://localhost:8080/swagger-ui | OpenAPI em `/v3/api-docs` |
| Health | http://localhost:8080/health | inclui a checagem do banco |
| PostgreSQL | `localhost:5433` | `5432` dentro da rede do compose; banco, usuário e senha `purchase` (só desenvolvimento) |

As portas do host mudam com `FRONTEND_HOST_PORT`, `BACKEND_HOST_PORT` e `DB_HOST_PORT`.

O seed só roda com o banco **sem usuários**. Para recomeçar do zero, na **raiz do repositório**:

```bash
docker compose down --volumes
```

### 2.2 Local (sem containers para o back e o front)

Pré-requisitos: Java 21, Node.js 26 (mesma major do Dockerfile e do CI) e Docker para o Postgres.

1. Suba só o banco. Na **raiz do repositório**:

   ```bash
   docker compose up -d db
   ```

2. Suba o back-end. O seed é **desligado por padrão** fora do compose, então ligue-o pela variável. Na pasta **`backend/`**, use o comando do seu terminal:

   bash (Linux, macOS, Git Bash):

   ```bash
   SEED_ENABLED=true ./mvnw spring-boot:run
   ```

   cmd (as aspas evitam que o espaço antes do `&&` entre no valor):

   ```bat
   set "SEED_ENABLED=true" && mvnw.cmd spring-boot:run
   ```

   PowerShell:

   ```powershell
   $env:SEED_ENABLED="true"; .\mvnw.cmd spring-boot:run
   ```

3. Suba o front-end. Na pasta **`frontend/`**:

   ```bash
   npm ci
   npm start
   ```

   Abra http://localhost:4200. O `ng serve` usa o [`proxy.conf.json`](frontend/proxy.conf.json) para encaminhar `/api` a `http://localhost:8080`.

Alternativa sem o Postgres do compose: o goal `spring-boot:test-run` sobe o back-end com um PostgreSQL efêmero via Testcontainers (comandos para bash, cmd e PowerShell em [`backend/README.md`](backend/README.md#rodar-com-testcontainers-testpurchaseapplication)).

### 2.3 Testes

| Lado | Comando | O que roda |
|---|---|---|
| Back-end | Na pasta `backend/`: `./mvnw verify` (bash), `mvnw.cmd verify` (cmd) ou `.\mvnw.cmd verify` (PowerShell) | Spotless, unitários, integração e concorrência com Testcontainers (exige Docker), ArchUnit e relatório JaCoCo |
| Front-end | Na pasta `frontend/`: `npm test` | Vitest, não interativo |

Detalhes em [`backend/README.md`](backend/README.md) e [`frontend/README.md`](frontend/README.md).

## 3. Usuários do seed

Credenciais **fictícias, exclusivamente de desenvolvimento**. Todos os usuários usam a senha **`Senha@123`**.

| E-mail | Papel | Nível | Centro de custo |
|---|---|---|---|
| `admin@example.com` | `ADMIN` | — | — (global) |
| `admin2@example.com` | `ADMIN` | — | — (global) |
| `solicitante.ti@example.com` | `SOLICITANTE` | — | `CC-TI-01` |
| `aprovador1.ti@example.com` | `APROVADOR` | `NIVEL_1` | `CC-TI-01` |
| `aprovador2.ti@example.com` | `APROVADOR` | `NIVEL_2` | `CC-TI-01` |
| `solicitante.mnt@example.com` | `SOLICITANTE` | — | `CC-MNT-01` |
| `aprovador1.mnt@example.com` | `APROVADOR` | `NIVEL_1` | `CC-MNT-01` |
| `aprovador2.mnt@example.com` | `APROVADOR` | `NIVEL_2` | `CC-MNT-01` |

### 3.1 Cenário criado

Os orçamentos são definidos para o **mês corrente** (competência em `America/Sao_Paulo`). Todas as solicitações são criadas chamando os application services, então cada transição passa pelas regras reais (cadeia, segregação, orçamento) e gera histórico como uma chamada à API (com `requestId = "seed"`).

| Centro de custo | Orçamento | Consumido | Disponível |
|---|---|---|---|
| `CC-TI-01` — Tecnologia da Informação | 50.000,00 | 23.200,00 | 26.800,00 |
| `CC-MNT-01` — Manutenção Industrial | 8.000,00 | 7.200,00 | **800,00** (quase esgotado) |

| Solicitação | Centro | Estado |
|---|---|---|
| Monitores para a sala de reuniões | TI | `DRAFT` |
| Licenças de IDE para o time (`SOFTWARE`, 899,00) | TI | `PENDING_APPROVAL`, aguardando `NIVEL_1` (cadeia `NIVEL_1 → ADMIN`) |
| Notebooks para novos desenvolvedores (14.699,70) | TI | `PENDING_APPROVAL`, aguardando `NIVEL_2` (cadeia de 3 etapas) |
| Cabos e conectores de rede (500,00) | TI | `APPROVED` (só `NIVEL_1`) |
| Servidor de backup (18.500,00) | TI | `APPROVED` (3 etapas) |
| Antivírus corporativo (pedido do `admin@`) | TI | `APPROVED`; a etapa `ADMIN` foi decidida pelo `admin2@` (segregação de funções) |
| Viagem para feira de tecnologia | TI | `REJECTED` |
| Treinamento em segurança da informação | TI | `CHANGES_REQUESTED` |
| Ferramenta de monitoramento (plano anual) | TI | `PENDING_APPROVAL` na **v2** (v1 devolvida e preservada) |
| Café para o escritório | TI | `CANCELLED` |
| Rolamentos e correias da linha 2 (7.200,00) | MNT | `APPROVED` |
| Kit de ferramentas para a oficina (1.500,00) | MNT | `PENDING_APPROVAL`, aguardando `NIVEL_2` |
| EPIs para a equipe de manutenção (1.350,00) | MNT | `PENDING_APPROVAL`, aguardando `NIVEL_1` |

Roteiros rápidos para a demonstração:

- **`BUDGET_EXCEEDED`:** entre como `aprovador2.mnt@example.com` e aprove "Kit de ferramentas para a oficina" (1.500,00 contra 800,00 disponíveis).
- **Versionamento:** abra "Ferramenta de monitoramento (plano anual)" e consulte a v1 na lista de versões.
- **Fluxo completo de 3 etapas:** "Notebooks para novos desenvolvedores" com `aprovador2.ti@` e depois `admin@`.
- **Visibilidade:** `solicitante.mnt@` não vê nenhuma solicitação de TI (detalhe responde `404`).

## 4. Principais decisões e trade-offs

| Tema | Decisão | Trade-off | ADR |
|---|---|---|---|
| Concorrência | Lock **otimista** na solicitação (`@Version` = `lockVersion`, divergente → `409`) e **UPDATE condicional atômico** no orçamento (`amount - consumed >= :v`), sem `SELECT FOR UPDATE`; `CHECK (consumed <= amount)` como última defesa. Transição, etapas, consumo, evento e chave de idempotência na mesma transação | Em disputa, o cliente perdedor precisa recarregar e tentar de novo; a linha de orçamento do mês serializa as aprovações finais daquele centro | [001](docs/adr/001-concorrencia-e-orcamento.md) |
| Versões e histórico | Tabelas por versão (`request_version`, `request_item`, `approval_step`), imutáveis depois do submit; a primeira edição em `CHANGES_REQUESTED` abre uma versão de trabalho; `request_event` append-only também **no banco** (trigger) | Mais linhas e joins do que guardar um JSON por versão, em troca de consultas e constraints reais | [002](docs/adr/002-versoes-e-historico.md) |
| 403 × 404 | Recurso fora da visibilidade responde **`404`** (não revela existência); ordem de verificação explícita no service (401 → 404 → 403 → 409 → 422); `403` só para papel | Sem `@PreAuthorize` nos endpoints de solicitação, a ordem fica no código e precisa de testes | [003](docs/adr/003-visibilidade-404-e-ordem-de-verificacao.md) |
| Domínio | Hexagonal pragmático por feature; máquina de estados table-driven; cadeia por regras plugáveis (`ChainRule`); **uma só** `ActionPolicy` decide a ação (422), o `allowedActions` e a caixa de aprovação | Sem interfaces de porta: os services usam os repositórios Spring Data diretamente | [004](docs/adr/004-arquitetura-do-dominio.md) |
| Dinheiro e datas | `BigDecimal`/`NUMERIC(19,2)`, string no JSON, mais de 2 casas → `400` (nunca arredonda); total calculado no back; `Instant` em UTC; competência do mês em `America/Sao_Paulo` gravada no submit | O cliente precisa tratar valores como string | [005](docs/adr/005-dinheiro-e-datas.md) |
| Idempotência | `Idempotency-Key` por usuário, hash do payload normalizado, só respostas de sucesso são salvas, validade de 24 h, corrida resolvida pela `UNIQUE(user_id, key)` | Erros não ficam gravados: o retry de uma requisição que falhou é reprocessado | [006](docs/adr/006-idempotencia.md) |
| Autenticação | JWT HS256 stateless, TTL de 15 min, claims `role`/`costCenterId`/`approverLevel` lidas sem consultar o banco | Mudança de perfil ou desativação só vale no próximo login (janela de até 15 min); sem revogação | [007](docs/adr/007-autenticacao-stateless.md) |

Outras escolhas que valem citar:

- **Caixa de aprovação filtrada em memória** pela mesma `ActionPolicy`, sobre os candidatos do escopo (pendentes do centro de custo, ou todos para `ADMIN`), sem paginação. Evita duplicar a regra em SQL; aceitável nesta escala, mas não para milhares de pendências.
- **Leitura coerente em `READ COMMITTED`:** solicitação, versão atual e etapas carregadas num único `SELECT` (correção de um `500` intermitente encontrado no teste de concorrência).
- **Dashboard:** `ADMIN` vê o orçamento de todos os centros; os demais, só o próprio. O mês exibido é a competência de agora.
- **Front não reimplementa regras:** botões vêm só de `allowedActions`; `409` recarrega o detalhe e descarta a edição; `422` mostra o `detail` da API.
- **Erros** em Problem Details (RFC 9457) com `code` e `requestId`; logs ECS em JSON com o `X-Request-Id` no MDC e no histórico.

## 5. Além do obrigatório

Diferenciais da seção 6 do enunciado:

- **Histórico append-only garantido pelo banco:** trigger `trg_request_event_append_only` bloqueia `UPDATE`/`DELETE` em `request_event` ([`V1__schema.sql`](backend/src/main/resources/db/migration/V1__schema.sql)).
- **Acessibilidade básica:** `mat-label` nos campos, `aria-label` em ações e indicadores de carregamento, navegação por teclado e foco devolvido ao elemento de origem ao fechar o modal (comportamento padrão do `MatDialog`).

Extras de engenharia:

- Teste **ArchUnit** garantindo que o domínio não importa Spring nem JPA.
- Job **`compose-smoke`** no CI: sobe a stack com `docker compose up --build --wait`, confere o `/health`, o proxy `/api` do nginx e um login com usuário do seed.
- Collection do Insomnia com todos os endpoints e os logins do seed ([`docs/api/insomnia-collection.json`](docs/api/insomnia-collection.json)).
- Imagens Docker multi-stage, com usuário não root no back-end e health checks em todos os serviços.

## 6. O que ficou de fora e limitações conhecidas

- **Diferenciais não implementados:** tempo médio de aprovação, filtros `minAmount`/`maxAmount`/`q`, telas de administração no front, refresh token, outbox, job de SLA, idempotência nas transições, testes E2E, métricas/tracing, rate limit no login e deploy público.
- `PATCH /users/:id` não transforma um usuário em `ADMIN` global (remover o centro de custo), porque campo ausente significa "manter"; e nada impede um `ADMIN` de rebaixar a si mesmo ou o último `ADMIN`.
- `GET` de orçamento é liberado para qualquer usuário autenticado, sem filtro de centro de custo.
- A `UNIQUE DEFERRABLE` de `request_item` é checada só no commit; uma violação (que o fluxo normal evita renumerando os itens) cairia em `500`, não em `409`.
- Duas instâncias do back-end subindo ao mesmo tempo num banco vazio: a segunda falha no seed pela `UNIQUE` de e-mail (não há advisory lock).
- Caixa de aprovação sem paginação (ver seção 4).

## 7. O que eu faria com mais tempo

1. **Idempotência nas transições** (`approve`, `reject`…), reaproveitando a tabela `idempotency_key`.
2. **Tempo médio de aprovação** no dashboard e **filtros** `minAmount`/`maxAmount`/`q`.
3. **Métricas de negócio** com Micrometer/Prometheus (aprovações, `BUDGET_EXCEEDED`, conflitos `409`) e tracing com OpenTelemetry.
4. **Rate limit no login** e **refresh token** com rotação, reduzindo o TTL do access token.
5. **Testes E2E** com Playwright no job `compose-smoke` (fluxo de aprovação de ponta a ponta).
6. **Transactional outbox** para notificar mudanças de estado e um job de **SLA** para pendências antigas.
7. Caixa de aprovação paginada, com o filtro da `ActionPolicy` traduzido para SQL.
8. Telas de administração (usuários, centros de custo, orçamentos) e **deploy** em uma VM com HTTPS.

A evolução de arquitetura para escalar está em [`docs/architecture.md`](docs/architecture.md#evolução-de-escalabilidade).
