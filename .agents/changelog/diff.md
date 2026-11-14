# AI Agent Changelog

> This file is written **automatically** by `.agents/hooks/ai-log.sh` (repository hooks for
> Claude Code and Cursor; explicit commands for every other agent — see `AGENTS.md`).
>
> Purpose: let the evaluators distinguish the candidate's own work from work delegated to AI.
>
> - **Append-only.** Never edit, reorder or delete existing entries. This is audited from the git history.
> - **All entries must be written in English.**
> - Never write secrets or personal data here.

---

## Entry format

One block per agent turn, one row per changed file:

```
---
### Turn YYYY-MM-DD HH:MM:SS ±HHMM · <Agent>
- **Source**: hook | command | manual
- **Session**: <agent session id or n/a>
- **Prompt**: <user prompt, truncated, secrets masked>
- **Summary**: <one-line summary written by the agent>

| File | Change | Additions | Removals |
|---|---|---|---|
| `relative/path/to/file` | created / modified / deleted | +N | -N |
```

`Source: manual` is only for entries appended by hand when the logger cannot run.

---

## Change log

<!-- AI agent entries will be inserted below this line -->

---
### Turn 2026-11-14 12:09:05 -0300 · Other-ClaudeAI
- **Source**: command
- **Session**: n/a
- **Prompt**: n/a
- **Summary**: Execution plan and Claude Code instructions generated in Claude.ai chat

| File | Change | Additions | Removals |
|---|---|---|---|
| `backend/CLAUDE.md` | created | +192 | -0 |
| `frontend/CLAUDE.md` | created | +137 | -0 |
| `plano-execucao.md` | created | +291 | -0 |

---
### Turn 2026-11-14 16:36:51 -0300 · Other-ClaudeAI
- **Source**: command
- **Session**: n/a
- **Prompt**: n/a
- **Summary**: Switch Java version from 25 to 21 in plan and backend instructions

| File | Change | Additions | Removals |
|---|---|---|---|
| `backend/CLAUDE.md` | modified | +2 | -1 |
| `docs/plano-execucao.md` | modified | +3 | -3 |

---
### Turn 2026-11-14 16:57:07 -0300 · ClaudeCode
- **Source**: command
- **Session**: n/a
- **Prompt**: n/a
- **Summary**: Diagnosed local env: IntelliJ Maven sync (JDK 25 -> 21, bundled Maven) and Docker/WSL not running for Testcontainers; no code changes

| File | Change | Additions | Removals |
|---|---|---|---|
| `backend/.gitattributes` | created | +2 | -0 |
| `backend/.gitignore` | created | +33 | -0 |
| `backend/.mvn/wrapper/maven-wrapper.properties` | created | +3 | -0 |
| `backend/mvnw` | created | +295 | -0 |
| `backend/mvnw.cmd` | created | +189 | -0 |
| `backend/pom.xml` | created | +114 | -0 |
| `backend/src/main/java/com/kingspan/purchase/PurchaseApplication.java` | created | +13 | -0 |
| `backend/src/main/resources/application.properties` | created | +1 | -0 |
| `backend/src/test/java/com/kingspan/purchase/PurchaseApplicationTests.java` | created | +15 | -0 |
| `backend/src/test/java/com/kingspan/purchase/TestPurchaseApplication.java` | created | +11 | -0 |
| `backend/src/test/java/com/kingspan/purchase/TestcontainersConfiguration.java` | created | +18 | -0 |

---
### Turn 2026-11-14 18:56:45 -0300 · ClaudeCode
- **Source**: command
- **Session**: b474da71-26ef-4746-ba12-a885a982828e
- **Prompt**: Execute a etapa E2 do docs/plano-execucao.md. O esqueleto do Initializr já está commitado (Java 21, Spring Boot 4.1.2, com as 7 dependências). Faça só o restante: springdoc-openapi 3.x, ArchUnit, JaCoCo, Spotless e o application.properties com variáveis de ambiente.
- **Summary**: E2: add springdoc 3.2, ArchUnit, JaCoCo and Spotless to backend pom; env-based application.properties; Testcontainers on postgres:17; apply formatting

| File | Change | Additions | Removals |
|---|---|---|---|
| `backend/pom.xml` | modified | +61 | -0 |
| `backend/src/main/java/com/kingspan/purchase/PurchaseApplication.java` | modified | +3 | -4 |
| `backend/src/main/resources/application.properties` | modified | +15 | -0 |
| `backend/src/test/java/com/kingspan/purchase/PurchaseApplicationTests.java` | modified | +2 | -4 |
| `backend/src/test/java/com/kingspan/purchase/TestPurchaseApplication.java` | modified | +5 | -4 |
| `backend/src/test/java/com/kingspan/purchase/TestcontainersConfiguration.java` | modified | +5 | -6 |

---
### Turn 2026-11-14 19:17:44 -0300 · ClaudeCode
- **Source**: command
- **Session**: d965f471-5870-4413-a055-8d929a6f2019
- **Prompt**: Execute a etapa E3 do docs/plano-execucao.md. Crie o docker-compose.yml na raiz só com o serviço db (Postgres 17, healthcheck pg_isready, porta 5433 no host) e o .env.example. Ainda não adicione os serviços backend nem frontend. Garanta que o application.properties tenha defaults que conectem nesse banco ao rodar localmente.
- **Summary**: Add docker-compose.yml with Postgres 17 db service (healthcheck, host port 5433) and .env.example (E3)

| File | Change | Additions | Removals |
|---|---|---|---|
| `.env.example` | created | +16 | -0 |
| `docker-compose.yml` | created | +26 | -0 |

---
### Turn 2026-11-14 19:40:14 -0300 · ClaudeCode
- **Source**: command
- **Session**: f4a4b604-9e6c-4991-96a0-0e29276a13db
- **Prompt**: Execute a etapa E4 do docs/plano-execucao.md. Atenção: o health precisa responder exatamente em /health (base-path do Actuator na raiz, expondo apenas health) e o RequestIdFilter deve rodar antes de qualquer outro filtro.
- **Summary**: E4: request id filter (first in chain) with MDC, ECS logs, Problem Details exception handler, BusinessRuleException and error codes, /health at root, OpenAPI config, Clock bean, integration tests

| File | Change | Additions | Removals |
|---|---|---|---|
| `backend/src/main/java/com/kingspan/purchase/shared/config/ClockConfig.java` | created | +15 | -0 |
| `backend/src/main/java/com/kingspan/purchase/shared/config/OpenApiConfig.java` | created | +20 | -0 |
| `backend/src/main/java/com/kingspan/purchase/shared/config/WebConfig.java` | created | +25 | -0 |
| `backend/src/main/java/com/kingspan/purchase/shared/error/BusinessRuleException.java` | created | +16 | -0 |
| `backend/src/main/java/com/kingspan/purchase/shared/error/ErrorCode.java` | created | +41 | -0 |
| `backend/src/main/java/com/kingspan/purchase/shared/error/FieldViolation.java` | created | +4 | -0 |
| `backend/src/main/java/com/kingspan/purchase/shared/error/GlobalExceptionHandler.java` | created | +163 | -0 |
| `backend/src/main/java/com/kingspan/purchase/shared/web/RequestId.java` | created | +17 | -0 |
| `backend/src/main/java/com/kingspan/purchase/shared/web/RequestIdFilter.java` | created | +61 | -0 |
| `backend/src/main/resources/application.properties` | modified | +13 | -0 |
| `backend/src/test/java/com/kingspan/purchase/TestcontainersConfiguration.java` | modified | +1 | -1 |
| `backend/src/test/java/com/kingspan/purchase/shared/SharedInfrastructureIntegrationTest.java` | created | +225 | -0 |

---
### Turn 2026-11-14 20:01:35 -0300 · ClaudeCode
- **Source**: command
- **Session**: b5f295ac-a6b5-434a-b75e-102139dbdf49
- **Prompt**: Execute a etapa E5 do docs/plano-execucao.md. Lembre: o pacote domain não pode importar nada de Spring nem de jakarta.persistence.
- **Summary**: E5: add pure domain value objects and enums (Money, RequestStatus, RequestAction, Category, StepLevel, Role, ApproverLevel, Actor) with unit tests

| File | Change | Additions | Removals |
|---|---|---|---|
| `backend/src/main/java/com/kingspan/purchase/request/domain/Category.java` | created | +11 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/domain/Money.java` | created | +73 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/domain/RequestAction.java` | created | +14 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/domain/RequestStatus.java` | created | +16 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/domain/StepLevel.java` | created | +12 | -0 |
| `backend/src/main/java/com/kingspan/purchase/shared/domain/Actor.java` | created | +29 | -0 |
| `backend/src/main/java/com/kingspan/purchase/shared/domain/ApproverLevel.java` | created | +7 | -0 |
| `backend/src/main/java/com/kingspan/purchase/shared/domain/Role.java` | created | +8 | -0 |
| `backend/src/test/java/com/kingspan/purchase/request/domain/MoneyTest.java` | created | +94 | -0 |
| `backend/src/test/java/com/kingspan/purchase/request/domain/RequestStatusTest.java` | created | +15 | -0 |
| `backend/src/test/java/com/kingspan/purchase/shared/domain/ActorTest.java` | created | +30 | -0 |
