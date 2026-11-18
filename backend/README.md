# Back-end — API de solicitações de compra



API REST em **Java 21 + Spring Boot 4.1**, com PostgreSQL 17, Flyway e autenticação JWT. Visão geral, decisões e usuários do seed estão no [`SOLUTION.md`](../SOLUTION.md); os ADRs, em [`docs/adr/`](../docs/adr/).

## Pré-requisitos

- **Java 21** (o Maven vem pelo wrapper `./mvnw`; não precisa instalar)
- **Docker**: para o Postgres do compose e para os testes de integração (Testcontainers)

## Variáveis de ambiente

Todas têm default de **desenvolvimento** em [`application.properties`](src/main/resources/application.properties); o [`.env.example`](../.env.example) da raiz lista as mesmas variáveis.

| Variável | Default | Descrição |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5433/purchase` | JDBC do Postgres (o `db` do compose fica em `localhost:5433`) |
| `DB_USER` | `purchase` | Usuário do banco |
| `DB_PASSWORD` | `purchase` | Senha do banco |
| `SERVER_PORT` | `8080` | Porta HTTP |
| `JWT_SECRET` | segredo de desenvolvimento | Chave HS256, **mínimo de 32 bytes**. Fora do ambiente local, gere uma (`openssl rand -base64 48`) |
| `JWT_TTL_MINUTES` | `15` | Validade do access token |
| `SEED_ENABLED` | `false` | Popula o banco vazio com dados fictícios na subida (o `docker compose` liga: `true`). Não faz nada se já houver usuários |

## Comandos

Todos os comandos da tabela rodam na pasta **`backend/`**. A tabela usa a forma do bash (`./mvnw`); no cmd, troque por `mvnw.cmd`, e no PowerShell, por `.\mvnw.cmd`.

| Comando | O que faz |
|---|---|
| `./mvnw verify` | Build completo: Spotless (check), testes unitários, de integração e de concorrência (Testcontainers, exige Docker), ArchUnit e relatório JaCoCo em `target/site/jacoco/index.html`. É o comando do CI |
| `./mvnw test -Dtest=ApprovalConcurrencyIntegrationTest` | Roda uma classe de teste específica |
| `./mvnw spotless:apply` | Formata o código (palantir-java-format) |
| `./mvnw -DskipTests package` | Gera o jar em `target/` (usado no Dockerfile; os testes rodam no CI) |
| `./mvnw spring-boot:run` | Sobe a API usando o Postgres de `DB_URL` |
| `./mvnw spring-boot:test-run` | Sobe a API com um Postgres efêmero via Testcontainers (ver abaixo) |

### Rodar com o Postgres do compose

1. Suba o banco. Na **raiz do repositório**:

```bash
docker compose up -d db
```

2. Suba a API. Na pasta **`backend/`**, use o comando do seu terminal.

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

No cmd e no PowerShell a variável vale até fechar o terminal. Para desligar o seed: `set "SEED_ENABLED="` (cmd) ou `Remove-Item Env:SEED_ENABLED` (PowerShell).

As migrations (`src/main/resources/db/migration`) rodam na subida; o schema nunca é gerado pelo Hibernate (`ddl-auto=validate`).

### Rodar com Testcontainers (`TestPurchaseApplication`)

[`TestPurchaseApplication`](src/test/java/com/kingspan/purchase/TestPurchaseApplication.java) sobe a aplicação real junto com a [`TestcontainersConfiguration`](src/test/java/com/kingspan/purchase/TestcontainersConfiguration.java), que cria um container `postgres:17` e liga o datasource a ele via `@ServiceConnection`. Não precisa do `db` do compose nem de `DB_URL`, só do Docker. Na pasta **`backend/`**, use o comando do seu terminal.

bash (Linux, macOS, Git Bash):

```bash
SEED_ENABLED=true ./mvnw spring-boot:test-run
```

cmd:

```bat
set "SEED_ENABLED=true" && mvnw.cmd spring-boot:test-run
```

PowerShell:

```powershell
$env:SEED_ENABLED="true"; .\mvnw.cmd spring-boot:test-run
```

Também dá para rodar o `main` da classe direto pela IDE. O banco é descartado ao parar a aplicação, então cada subida começa do zero (e o seed roda de novo). A API usa a porta `8080`: pare o `backend` do compose antes, se ele estiver rodando.

## Endereços

| URL | Descrição |
|---|---|
| http://localhost:8080/swagger-ui | Swagger UI |
| http://localhost:8080/v3/api-docs | OpenAPI (JSON) |
| http://localhost:8080/health | Health check, com o banco |

Os endpoints **não têm prefixo `/api`**: esse prefixo existe só no front, e o proxy (nginx ou `ng serve`) o remove.

## Exemplos de requisição

Os exemplos usam **bash** (Linux, macOS ou Git Bash), porque dependem de variáveis (`$TOKEN`), de `$(...)` e do `sed`; não rodam no cmd nem no PowerShell. Eles podem ser executados **em qualquer pasta**, porque só chamam a API em `localhost:8080`. Rode os blocos em ordem, no mesmo terminal: os seguintes usam as variáveis definidas nos anteriores. Os logins são os do seed (senha `Senha@123`), e o token é extraído com `sed` para não depender do `jq`.

Login (a resposta traz `accessToken`, `tokenType`, `expiresAt` e o usuário) e consulta do usuário logado:

```bash
BASE=http://localhost:8080

TOKEN=$(curl -s -X POST "$BASE/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"solicitante.ti@example.com","password":"Senha@123"}' \
  | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')

curl -s "$BASE/auth/me" -H "Authorization: Bearer $TOKEN"
```

Criar uma solicitação em `DRAFT`. O `Idempotency-Key` é opcional; repetir a mesma chave com o mesmo corpo devolve a mesma resposta com `Idempotent-Replayed: true`, e com corpo diferente responde `409`. Sem `costCenterId`, vale o centro de custo do usuário. O total é calculado pelo back-end, e `unitPrice` vai como string com até 2 casas:

```bash
curl -i -X POST "$BASE/requests" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 3f6c2a9e-1b7d-4c55-9a0e-2d8f4b1c7e01' \
  -d '{
        "title": "Teclados para o suporte",
        "description": "Reposição dos teclados com defeito",
        "category": "EQUIPMENT",
        "neededBy": "2030-12-31",
        "items": [{ "description": "Teclado ABNT2", "quantity": 5, "unitPrice": "150.00" }]
      }'
```

Detalhe (versão atual, etapas, `allowedActions` e `lockVersion`) e submissão. Toda transição recebe o `lockVersion` atual no corpo; valor divergente responde `409`. Troque o `1` de `ID` pelo `id` devolvido na criação, e o `0` do `lockVersion` pelo valor que o detalhe mostrar:

```bash
ID=1
curl -s "$BASE/requests/$ID" -H "Authorization: Bearer $TOKEN"

curl -s -X POST "$BASE/requests/$ID/submit" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"lockVersion": 0}'
```

Listagem paginada, com filtros e ordenação (campos permitidos em `sort`: `createdAt`, `updatedAt`, `status`, `total`, `title`):

```bash
curl -s "$BASE/requests?status=PENDING_APPROVAL&category=EQUIPMENT&sort=total,desc&page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"
```

Como aprovador: caixa de aprovação, aprovação e devolução (o comentário é obrigatório em `reject` e `request-changes`). Use o `lockVersion` atual do detalhe no lugar do `1`:

```bash
APPROVER=$(curl -s -X POST "$BASE/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"aprovador1.ti@example.com","password":"Senha@123"}' \
  | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')

curl -s "$BASE/approvals/inbox" -H "Authorization: Bearer $APPROVER"

curl -s -X POST "$BASE/requests/$ID/approve" \
  -H "Authorization: Bearer $APPROVER" -H 'Content-Type: application/json' \
  -d '{"lockVersion": 1, "comment": "De acordo."}'

curl -s -X POST "$BASE/requests/$ID/request-changes" \
  -H "Authorization: Bearer $APPROVER" -H 'Content-Type: application/json' \
  -d '{"lockVersion": 1, "comment": "Inclua o orçamento de dois fornecedores."}'
```

Histórico, versões e dashboard:

```bash
curl -s "$BASE/requests/$ID/history"    -H "Authorization: Bearer $TOKEN"
curl -s "$BASE/requests/$ID/versions"   -H "Authorization: Bearer $TOKEN"
curl -s "$BASE/requests/$ID/versions/1" -H "Authorization: Bearer $TOKEN"
curl -s "$BASE/dashboard/summary"       -H "Authorization: Bearer $TOKEN"
```

Os erros saem em `application/problem+json` com `code` e `requestId`, por exemplo um `lockVersion` desatualizado:

```json
{
  "type": "urn:problem-type:concurrent-modification",
  "title": "Conflito de concorrência",
  "status": 409,
  "detail": "O recurso foi alterado por outra operação. Recarregue os dados e tente novamente.",
  "code": "CONCURRENT_MODIFICATION",
  "requestId": "426cb93b-710e-49e9-b39f-c3de652f9628"
}
```

Envie `X-Request-Id` para correlacionar a chamada com os logs (JSON no formato ECS) e o histórico; sem o header, o back-end gera um UUID e o devolve na resposta.

### Collection do Insomnia

Importe [`docs/api/insomnia-collection.json`](../docs/api/insomnia-collection.json) no Insomnia (**Create → Import → File**); ela traz todos os endpoints e os logins do seed.
