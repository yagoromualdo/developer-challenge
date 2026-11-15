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

---
### Turn 2026-11-14 20:19:06 -0300 · ClaudeCode
- **Source**: command
- **Session**: 740603e4-670d-4397-b4e8-b405a5605466
- **Prompt**: Execute a etapa E6 do docs/plano-execucao.md. Use @ParameterizedTest cobrindo todas as combinações status × ação da tabela 4.2 do README, válidas e inválidas, com mensagens de erro em pt-BR que expliquem o motivo.
- **Summary**: E6: table-driven request state machine with parameterized tests covering all 36 status x action combinations

| File | Change | Additions | Removals |
|---|---|---|---|
| `backend/src/main/java/com/kingspan/purchase/request/domain/RequestStateMachine.java` | created | +132 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/domain/StepPosition.java` | created | +10 | -0 |
| `backend/src/test/java/com/kingspan/purchase/request/domain/RequestStateMachineTest.java` | created | +147 | -0 |

---
### Turn 2026-11-14 20:26:11 -0300 · ClaudeCode
- **Source**: command
- **Session**: 740603e4-670d-4397-b4e8-b405a5605466
- **Prompt**: <pasted_content id="2c7d"> Revise criticamente a implementação da etapa E6 do docs/plano-execucao.md (máquina de estados), comparando com a tabela 4.2 e a seção 4.3 do README.md. Verifique e corrija o que faltar: 1. Todas as transições válidas da tabela 4.2 existem (incluindo approve intermediário → PENDING_APPROVAL e approve da última etapa → APPROVED, e submit a partir de CHANGES_REQUESTED). 2. Cancel é permitido a partir de DRAFT, PENDING_APPROVAL e CHANGES_REQUESTED, e bloquea
- **Summary**: E6 review: fix pt-BR phrasing of INVALID_TRANSITION messages and add isFinal/table consistency test

| File | Change | Additions | Removals |
|---|---|---|---|
| `backend/src/main/java/com/kingspan/purchase/request/domain/RequestStateMachine.java` | modified | +8 | -8 |
| `backend/src/test/java/com/kingspan/purchase/request/domain/RequestStateMachineTest.java` | modified | +17 | -10 |

---
### Turn 2026-11-14 20:36:30 -0300 · ClaudeCode
- **Source**: command
- **Session**: 879061e4-dfe1-485c-9931-19fd82f6beb7
- **Prompt**: Execute a etapa E7 do docs/plano-execucao.md. Inclua testes nos limites exatos (1000.00, 1000.01, 10000.00, 10000.01) e para SOFTWARE abaixo e acima de 10.000, garantindo que a etapa ADMIN nunca se repete.
- **Summary**: E7: approval chain builder with pluggable ChainRule (ThresholdRule, SoftwareRequiresAdminRule) and boundary/SOFTWARE tests

| File | Change | Additions | Removals |
|---|---|---|---|
| `backend/src/main/java/com/kingspan/purchase/request/domain/ApprovalChainBuilder.java` | created | +52 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/domain/ChainRule.java` | created | +15 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/domain/SoftwareRequiresAdminRule.java` | created | +19 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/domain/StepLevel.java` | modified | +3 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/domain/ThresholdRule.java` | created | +31 | -0 |
| `backend/src/test/java/com/kingspan/purchase/request/domain/ApprovalChainBuilderTest.java` | created | +142 | -0 |

---
### Turn 2026-11-14 20:57:52 -0300 · ClaudeCode
- **Source**: command
- **Session**: 5830eaad-50b7-487e-b9cb-ad49dca61720
- **Prompt**: Execute a etapa E8 do docs/plano-execucao.md. A mesma ActionPolicy precisa servir tanto para validar a ação (422) quanto para montar o allowedActions. Inclua o teste ArchUnit de isolamento do domínio.
- **Summary**: E8: add ApprovalRules, ActionPolicy (role check, 422 validation and allowedActions from one evaluation), comment rule and ArchUnit domain isolation test

| File | Change | Additions | Removals |
|---|---|---|---|
| `backend/src/main/java/com/kingspan/purchase/request/domain/ActionPolicy.java` | created | +172 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/domain/ApprovalRule.java` | created | +15 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/domain/ApprovalStep.java` | created | +38 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/domain/NoOtherStepApprovedRule.java` | created | +20 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/domain/NotOwnerRule.java` | created | +19 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/domain/RequestAction.java` | modified | +27 | -6 |
| `backend/src/main/java/com/kingspan/purchase/request/domain/RequestSnapshot.java` | created | +36 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/domain/RequestStateMachine.java` | modified | +7 | -14 |
| `backend/src/main/java/com/kingspan/purchase/request/domain/RuleViolation.java` | created | +18 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/domain/SameCostCenterRule.java` | created | +24 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/domain/StepStatus.java` | created | +9 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/domain/SufficientLevelRule.java` | created | +57 | -0 |
| `backend/src/main/java/com/kingspan/purchase/shared/error/ErrorCode.java` | modified | +1 | -0 |
| `backend/src/test/java/com/kingspan/purchase/DomainIsolationArchTest.java` | created | +78 | -0 |
| `backend/src/test/java/com/kingspan/purchase/request/domain/ActionPolicyTest.java` | created | +503 | -0 |
| `backend/src/test/java/com/kingspan/purchase/request/domain/RequestSnapshotTest.java` | created | +63 | -0 |

---
### Turn 2026-11-14 21:22:30 -0300 · ClaudeCode
- **Source**: command
- **Session**: 5830eaad-50b7-487e-b9cb-ad49dca61720
- **Prompt**: <pasted_content id="d546"> Os comentários do código da E8 estão excessivos. Ajuste seguindo esta regra: - Remova comentários que só repetem o que o código já diz (nome de método, parâmetro, "valida X", "retorna Y") e banners de seção. - Mantenha apenas comentários curtos (1–2 linhas) que expliquem o PORQUÊ de algo não óbvio, por exemplo: uma regra de negócio sutil, uma decisão de design ou um caso de borda. - Javadoc só em tipos/métodos públicos cuja intenção não fique c
- **Summary**: Trim redundant comments in E8 code and tests (no behavior change); add concise-comment rule to backend and frontend CLAUDE.md

| File | Change | Additions | Removals |
|---|---|---|---|
| `backend/CLAUDE.md` | modified | +6 | -1 |
| `backend/src/main/java/com/kingspan/purchase/request/domain/ActionPolicy.java` | modified | +7 | -43 |
| `backend/src/main/java/com/kingspan/purchase/request/domain/ApprovalRule.java` | modified | +1 | -6 |
| `backend/src/main/java/com/kingspan/purchase/request/domain/ApprovalStep.java` | modified | +0 | -5 |
| `backend/src/main/java/com/kingspan/purchase/request/domain/NoOtherStepApprovedRule.java` | modified | +0 | -4 |
| `backend/src/main/java/com/kingspan/purchase/request/domain/NotOwnerRule.java` | modified | +1 | -1 |
| `backend/src/main/java/com/kingspan/purchase/request/domain/RequestAction.java` | modified | +1 | -3 |
| `backend/src/main/java/com/kingspan/purchase/request/domain/RequestSnapshot.java` | modified | +1 | -9 |
| `backend/src/main/java/com/kingspan/purchase/request/domain/RequestStateMachine.java` | modified | +0 | -1 |
| `backend/src/main/java/com/kingspan/purchase/request/domain/RuleViolation.java` | modified | +1 | -6 |
| `backend/src/main/java/com/kingspan/purchase/request/domain/SameCostCenterRule.java` | modified | +0 | -4 |
| `backend/src/main/java/com/kingspan/purchase/request/domain/StepStatus.java` | modified | +0 | -1 |
| `backend/src/main/java/com/kingspan/purchase/request/domain/SufficientLevelRule.java` | modified | +0 | -9 |
| `backend/src/test/java/com/kingspan/purchase/DomainIsolationArchTest.java` | modified | +1 | -6 |
| `backend/src/test/java/com/kingspan/purchase/request/domain/ActionPolicyTest.java` | modified | +1 | -23 |
| `frontend/CLAUDE.md` | modified | +5 | -0 |

---
### Turn 2026-11-14 22:08:10 -0300 · ClaudeCode
- **Source**: command
- **Session**: 71550e04-6858-452c-ae3c-f54baa509c14
- **Prompt**: Execute a etapa E9 do docs/plano-execucao.md. Inclua o CHECK (consumed >= 0 AND consumed <= amount) na tabela budget e a UNIQUE (user_id, key) em idempotency_key. Os status e níveis gravados em approval_step devem corresponder aos enums do domínio criados na E8 (StepStatus, StepLevel).
- **Summary**: E9: add Flyway V1 schema (9 tables, enum CHECKs, budget CHECK, idempotency UNIQUE, append-only event trigger) and migration integration test

| File | Change | Additions | Removals |
|---|---|---|---|
| `backend/src/main/resources/db/migration/V1__schema.sql` | created | +181 | -0 |
| `backend/src/test/java/com/kingspan/purchase/SchemaMigrationIntegrationTest.java` | created | +287 | -0 |

---
### Turn 2026-11-14 22:26:28 -0300 · ClaudeCode
- **Source**: command
- **Session**: 71550e04-6858-452c-ae3c-f54baa509c14
- **Prompt**: <pasted_content id="0ac0"> Ajustes na E9 antes do commit: 1. app_user: garanta que a regra do nível vale nos dois sentidos (role = 'APROVADOR' exige approver_level NOT NULL; outros papéis exigem approver_level NULL). 2. request_item: adicione UNIQUE (version_id, line_number) DEFERRABLE INITIALLY DEFERRED, para manter a integridade sem conflitar com a ordem de flush do Hibernate. Ajuste o nome das colunas se forem diferentes. 3. Confirme que existe teste de integração provando que UPDATE e DE
- **Summary**: E9 review: deferrable UNIQUE on request_item line numbers, role/level rule tests for all roles, stronger append-only trigger and TRUNCATE cleanup tests

| File | Change | Additions | Removals |
|---|---|---|---|
| `backend/src/main/resources/db/migration/V1__schema.sql` | modified | +4 | -3 |
| `backend/src/test/java/com/kingspan/purchase/SchemaMigrationIntegrationTest.java` | modified | +105 | -12 |

---
### Turn 2026-11-14 22:46:04 -0300 · ClaudeCode
- **Source**: command
- **Session**: f77951f0-443d-4636-bf81-c39fd997fc1c
- **Prompt**: Execute a etapa E10 do docs/plano-execucao.md. O mapeamento entidade → domínio deve produzir o RequestSnapshot e os ApprovalStep da E8 (só as etapas da versão atual), sem duplicar regras na camada infra. O consumo de orçamento deve ser um UPDATE condicional atômico (sem SELECT FOR UPDATE), com teste de integração cobrindo: consome quando cabe, 0 linhas quando não cabe e 0 linhas quando o mês não tem orçamento.
- **Summary**: E10: JPA entities, repositories, entity-to-domain snapshot mapper and atomic conditional budget UPDATE with integration tests

| File | Change | Additions | Removals |
|---|---|---|---|
| `backend/src/main/java/com/kingspan/purchase/budget/infra/BudgetEntity.java` | created | +67 | -0 |
| `backend/src/main/java/com/kingspan/purchase/budget/infra/BudgetRepository.java` | created | +48 | -0 |
| `backend/src/main/java/com/kingspan/purchase/costcenter/infra/CostCenterEntity.java` | created | +52 | -0 |
| `backend/src/main/java/com/kingspan/purchase/costcenter/infra/CostCenterRepository.java` | created | +8 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/domain/HistoryAction.java` | created | +12 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/infra/ApprovalStepEntity.java` | created | +100 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/infra/IdempotencyKeyEntity.java` | created | +91 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/infra/IdempotencyKeyRepository.java` | created | +9 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/infra/PurchaseRequestEntity.java` | created | +139 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/infra/PurchaseRequestRepository.java` | created | +5 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/infra/RequestEventEntity.java` | created | +141 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/infra/RequestEventRepository.java` | created | +10 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/infra/RequestItemEntity.java` | created | +75 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/infra/RequestSnapshotMapper.java` | created | +26 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/infra/RequestVersionEntity.java` | created | +189 | -0 |
| `backend/src/main/java/com/kingspan/purchase/user/infra/UserEntity.java` | created | +106 | -0 |
| `backend/src/main/java/com/kingspan/purchase/user/infra/UserRepository.java` | created | +12 | -0 |
| `backend/src/test/java/com/kingspan/purchase/budget/infra/BudgetRepositoryIntegrationTest.java` | created | +143 | -0 |
| `backend/src/test/java/com/kingspan/purchase/request/infra/PurchaseRequestPersistenceIntegrationTest.java` | created | +255 | -0 |
| `backend/src/test/java/com/kingspan/purchase/support/DatabaseCleaner.java` | created | +25 | -0 |

---
### Turn 2026-11-14 23:13:47 -0300 · ClaudeCode
- **Source**: command
- **Session**: a11c083e-7ab8-405b-a174-598f8712bc3b
- **Prompt**: Execute a etapa E11 do docs/plano-execucao.md. Adicione as dependências de Spring Security e OAuth2 Resource Server compatíveis com o Spring Boot 4.1.2. 401 e 403 também devem sair em application/problem+json, e nenhuma resposta pode conter senha ou hash.
- **Summary**: E11: add Spring Security + OAuth2 Resource Server, HS256 JwtService, /auth login/register/me, 401/403 as problem+json, Actor resolver and tests

| File | Change | Additions | Removals |
|---|---|---|---|
| `.env.example` | modified | +3 | -0 |
| `backend/pom.xml` | modified | +18 | -0 |
| `backend/src/main/java/com/kingspan/purchase/costcenter/application/CostCenterService.java` | created | +21 | -0 |
| `backend/src/main/java/com/kingspan/purchase/shared/config/OpenApiConfig.java` | modified | +21 | -1 |
| `backend/src/main/java/com/kingspan/purchase/shared/config/WebConfig.java` | modified | +16 | -1 |
| `backend/src/main/java/com/kingspan/purchase/shared/error/ErrorCode.java` | modified | +8 | -0 |
| `backend/src/main/java/com/kingspan/purchase/shared/error/GlobalExceptionHandler.java` | modified | +42 | -0 |
| `backend/src/main/java/com/kingspan/purchase/shared/security/ActorArgumentResolver.java` | created | +43 | -0 |
| `backend/src/main/java/com/kingspan/purchase/shared/security/IssuedToken.java` | created | +5 | -0 |
| `backend/src/main/java/com/kingspan/purchase/shared/security/JwtProperties.java` | created | +29 | -0 |
| `backend/src/main/java/com/kingspan/purchase/shared/security/JwtService.java` | created | +73 | -0 |
| `backend/src/main/java/com/kingspan/purchase/shared/security/ProblemDetailsSecurityHandler.java` | created | +42 | -0 |
| `backend/src/main/java/com/kingspan/purchase/shared/security/SecurityConfig.java` | created | +113 | -0 |
| `backend/src/main/java/com/kingspan/purchase/user/application/AuthService.java` | created | +110 | -0 |
| `backend/src/main/java/com/kingspan/purchase/user/application/AuthenticatedSession.java` | created | +5 | -0 |
| `backend/src/main/java/com/kingspan/purchase/user/application/UserAccount.java` | created | +25 | -0 |
| `backend/src/main/java/com/kingspan/purchase/user/web/AuthController.java` | created | +40 | -0 |
| `backend/src/main/java/com/kingspan/purchase/user/web/LoginRequest.java` | created | +7 | -0 |
| `backend/src/main/java/com/kingspan/purchase/user/web/LoginResponse.java` | created | +12 | -0 |
| `backend/src/main/java/com/kingspan/purchase/user/web/MaxUtf8Bytes.java` | created | +45 | -0 |
| `backend/src/main/java/com/kingspan/purchase/user/web/RegisterRequest.java` | created | +26 | -0 |
| `backend/src/main/java/com/kingspan/purchase/user/web/UserResponse.java` | created | +18 | -0 |
| `backend/src/main/resources/application.properties` | modified | +5 | -0 |
| `backend/src/test/java/com/kingspan/purchase/shared/SharedInfrastructureIntegrationTest.java` | modified | +13 | -1 |
| `backend/src/test/java/com/kingspan/purchase/shared/security/JwtServiceTest.java` | created | +84 | -0 |
| `backend/src/test/java/com/kingspan/purchase/user/web/AuthIntegrationTest.java` | created | +336 | -0 |

---
### Turn 2026-11-15 12:24:06 -0300 · ClaudeCode
- **Source**: command
- **Session**: a11c083e-7ab8-405b-a174-598f8712bc3b
- **Prompt**: Pode me enviar todos esses endepoints de teste manual em um arquivo único, para o Insomnia. Além disso, se tiver outros além desses, me mande também
- **Summary**: Add Insomnia collection with manual tests for auth, Problem Details and role-based 403 (E11)

| File | Change | Additions | Removals |
|---|---|---|---|
| `docs/insomnia/purchase-api.json` | created | +312 | -0 |

---
### Turn 2026-11-15 12:34:48 -0300 · ClaudeCode
- **Source**: command
- **Session**: a11c083e-7ab8-405b-a174-598f8712bc3b
- **Prompt**: Acabei de fazer o login, porém:
- **Summary**: Insomnia collection: capture login tokens with after-response scripts instead of ID-based response tags

| File | Change | Additions | Removals |
|---|---|---|---|
| `docs/insomnia/purchase-api.json` | modified | +10 | -7 |

---
### Turn 2026-11-15 12:49:05 -0300 · ClaudeCode
- **Source**: command
- **Session**: a11c083e-7ab8-405b-a174-598f8712bc3b
- **Prompt**: <pasted_content id="a7b0"> Antes de commitar, confira quatro coisas: 1. Local permitido. Coloque em `backend/` (por exemplo, `backend/insomnia/kingspan-purchase.json`) ou em `docs/api/`. Na raiz não pode, porque fica fora dos caminhos permitidos pelo `AGENTS.md`. 2. Nenhum token salvo. Abra o arquivo e procure por `eyJ`, que é o começo de todo JWT. O token deve vir de uma variável de ambiente preenchida pela resposta do login (o Insomnia faz isso com response chaining), e não colado fixo. A
- **Summary**: Move Insomnia collection to docs/api, remove hardcoded JWTs, add Local and Compose environments

| File | Change | Additions | Removals |
|---|---|---|---|
| `docs/api/insomnia-collection.json` | created | +316 | -0 |
| `docs/insomnia/purchase-api.json` | deleted | +0 | -315 |

---
### Turn 2026-11-15 13:12:27 -0300 · ClaudeCode
- **Source**: command
- **Session**: b503fa03-4344-40c8-ac3d-d260e62785bf
- **Prompt**: Execute a etapa E12 do docs/plano-execucao.md. O PUT de orçamento deve usar UPDATE condicional para o BUDGET_BELOW_CONSUMED, sem condição de corrida.
- **Summary**: E12: admin endpoints for users, cost centers and monthly budgets (race-free conditional upsert for BUDGET_BELOW_CONSUMED), tests and Insomnia collection

| File | Change | Additions | Removals |
|---|---|---|---|
| `backend/src/main/java/com/kingspan/purchase/budget/application/BudgetService.java` | created | +83 | -0 |
| `backend/src/main/java/com/kingspan/purchase/budget/application/BudgetView.java` | created | +12 | -0 |
| `backend/src/main/java/com/kingspan/purchase/budget/infra/BudgetRepository.java` | modified | +29 | -0 |
| `backend/src/main/java/com/kingspan/purchase/budget/web/BudgetController.java` | created | +36 | -0 |
| `backend/src/main/java/com/kingspan/purchase/budget/web/BudgetResponse.java` | created | +16 | -0 |
| `backend/src/main/java/com/kingspan/purchase/budget/web/DefineBudgetRequest.java` | created | +16 | -0 |
| `backend/src/main/java/com/kingspan/purchase/costcenter/application/CostCenterService.java` | modified | +40 | -1 |
| `backend/src/main/java/com/kingspan/purchase/costcenter/application/CostCenterView.java` | created | +11 | -0 |
| `backend/src/main/java/com/kingspan/purchase/costcenter/web/CostCenterController.java` | created | +35 | -0 |
| `backend/src/main/java/com/kingspan/purchase/costcenter/web/CostCenterResponse.java` | created | +11 | -0 |
| `backend/src/main/java/com/kingspan/purchase/costcenter/web/CreateCostCenterRequest.java` | created | +14 | -0 |
| `backend/src/main/java/com/kingspan/purchase/shared/error/ErrorCode.java` | modified | +4 | -0 |
| `backend/src/main/java/com/kingspan/purchase/shared/error/GlobalExceptionHandler.java` | modified | +20 | -0 |
| `backend/src/main/java/com/kingspan/purchase/shared/error/ResourceNotFoundException.java` | created | +9 | -0 |
| `backend/src/main/java/com/kingspan/purchase/user/application/AuthService.java` | modified | +6 | -48 |
| `backend/src/main/java/com/kingspan/purchase/user/application/UserService.java` | created | +97 | -0 |
| `backend/src/main/java/com/kingspan/purchase/user/domain/UserProfile.java` | created | +47 | -0 |
| `backend/src/main/java/com/kingspan/purchase/user/infra/UserEntity.java` | modified | +7 | -0 |
| `backend/src/main/java/com/kingspan/purchase/user/web/ChangeUserProfileRequest.java` | created | +11 | -0 |
| `backend/src/main/java/com/kingspan/purchase/user/web/CreateUserRequest.java` | created | +29 | -0 |
| `backend/src/main/java/com/kingspan/purchase/user/web/UserController.java` | created | +45 | -0 |
| `backend/src/test/java/com/kingspan/purchase/budget/web/BudgetIntegrationTest.java` | created | +274 | -0 |
| `backend/src/test/java/com/kingspan/purchase/costcenter/web/CostCenterIntegrationTest.java` | created | +129 | -0 |
| `backend/src/test/java/com/kingspan/purchase/support/ApiClient.java` | created | +83 | -0 |
| `backend/src/test/java/com/kingspan/purchase/user/domain/UserProfileTest.java` | created | +81 | -0 |
| `backend/src/test/java/com/kingspan/purchase/user/web/AuthIntegrationTest.java` | modified | +4 | -2 |
| `backend/src/test/java/com/kingspan/purchase/user/web/UserAdminIntegrationTest.java` | created | +270 | -0 |
| `docs/api/insomnia-collection.json` | modified | +146 | -8 |

---
### Turn 2026-11-15 13:51:27 -0300 · ClaudeCode
- **Source**: command
- **Session**: b503fa03-4344-40c8-ac3d-d260e62785bf
- **Prompt**: Adicione ao claude.md do back: - A collection do Insomnia em `\docs\api` só deve ser alterada quando o usuário pedir explicitamente. Não a atualize como parte das etapas.
- **Summary**: Add rule to backend/CLAUDE.md: Insomnia collection changes only on explicit user request

| File | Change | Additions | Removals |
|---|---|---|---|
| `backend/CLAUDE.md` | modified | +1 | -0 |

---
### Turn 2026-11-15 14:13:12 -0300 · ClaudeCode
- **Source**: command
- **Session**: a32998bd-d583-4d66-83ea-2c191df9f110
- **Prompt**: Execute a etapa E13 do docs/plano-execucao.md. A visibilidade deve ser uma Specification reutilizável (DRAFT só para o dono; fora da visibilidade = 404). O total é sempre calculado no back. A edição deve passar pela ActionPolicy da E8: não-dono que enxerga a solicitação recebe 422 NOT_REQUEST_OWNER. O allowedActions do detalhe vem de ActionPolicy.allowedActions. O Bean Validation dos DTOs deve usar os mesmos limites das colunas da E9 (título 200, descrição e comentário 2000, item 500
- **Summary**: E13: create/edit/detail draft requests with visibility Specification, ActionPolicy-based edit checks, backend-computed total, history events and V2 CHECK on request_event.action

| File | Change | Additions | Removals |
|---|---|---|---|
| `backend/src/main/java/com/kingspan/purchase/request/application/PurchaseRequestService.java` | created | +197 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/application/RequestDetailView.java` | created | +84 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/application/RequestDraft.java` | created | +24 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/application/RequestPolicyConfig.java` | created | +15 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/domain/HistoryAction.java` | modified | +16 | -2 |
| `backend/src/main/java/com/kingspan/purchase/request/domain/RequestItem.java` | created | +28 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/domain/RequestLimits.java` | created | +20 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/infra/PurchaseRequestRepository.java` | modified | +3 | -1 |
| `backend/src/main/java/com/kingspan/purchase/request/infra/RequestVisibility.java` | created | +49 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/web/CreatePurchaseRequestRequest.java` | created | +45 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/web/PurchaseRequestController.java` | created | +45 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/web/PurchaseRequestResponse.java` | created | +78 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/web/RequestItemPayload.java` | created | +40 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/web/UpdatePurchaseRequestRequest.java` | created | +50 | -0 |
| `backend/src/main/java/com/kingspan/purchase/shared/config/ValidationConfig.java` | created | +20 | -0 |
| `backend/src/main/java/com/kingspan/purchase/shared/domain/BusinessTime.java` | created | +11 | -0 |
| `backend/src/main/java/com/kingspan/purchase/shared/error/ErrorCode.java` | modified | +1 | -0 |
| `backend/src/main/resources/db/migration/V2__request_event_action_check.sql` | created | +4 | -0 |
| `backend/src/test/java/com/kingspan/purchase/SchemaMigrationIntegrationTest.java` | modified | +50 | -2 |
| `backend/src/test/java/com/kingspan/purchase/request/domain/HistoryActionTest.java` | created | +25 | -0 |
| `backend/src/test/java/com/kingspan/purchase/request/domain/RequestItemTest.java` | created | +41 | -0 |
| `backend/src/test/java/com/kingspan/purchase/request/web/PurchaseRequestIntegrationTest.java` | created | +438 | -0 |
| `backend/src/test/java/com/kingspan/purchase/shared/config/ValidationConfigTest.java` | created | +41 | -0 |
| `docs/adr/003-visibilidade-404-e-ordem-de-verificacao.md` | created | +27 | -0 |

---
### Turn 2026-11-15 14:42:31 -0300 · ClaudeCode
- **Source**: command
- **Session**: 93e8387e-957d-41a0-a0db-88e44c1afe65
- **Prompt**: Execute a etapa E14 do docs/plano-execucao.md. Inclua o teste de duas requisições simultâneas com a mesma Idempotency-Key resultando em uma única solicitação.
- **Summary**: E14: Idempotency-Key on POST /requests with payload hash, 24h replay, 409 on reuse and UNIQUE race handling, plus integration and concurrency tests

| File | Change | Additions | Removals |
|---|---|---|---|
| `backend/src/main/java/com/kingspan/purchase/request/application/IdempotencyService.java` | created | +105 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/application/IdempotentResponse.java` | created | +15 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/infra/IdempotencyKeyRepository.java` | modified | +11 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/web/CreatePurchaseRequestRequest.java` | modified | +30 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/web/PurchaseRequestController.java` | modified | +43 | -5 |
| `backend/src/main/java/com/kingspan/purchase/shared/error/ConflictException.java` | created | +16 | -0 |
| `backend/src/main/java/com/kingspan/purchase/shared/error/ErrorCode.java` | modified | +1 | -0 |
| `backend/src/main/java/com/kingspan/purchase/shared/error/GlobalExceptionHandler.java` | modified | +18 | -1 |
| `backend/src/test/java/com/kingspan/purchase/request/application/IdempotencyServiceIntegrationTest.java` | created | +133 | -0 |
| `backend/src/test/java/com/kingspan/purchase/request/web/PurchaseRequestIdempotencyIntegrationTest.java` | created | +273 | -0 |
| `backend/src/test/java/com/kingspan/purchase/support/ApiClient.java` | modified | +10 | -5 |

---
### Turn 2026-11-15 15:12:01 -0300 · ClaudeCode
- **Source**: command
- **Session**: 2d48c403-bc06-443d-8efc-dd80f9f9da88
- **Prompt**: Execute a etapa E15 do docs/plano-execucao.md. Ordenação por campo fora da whitelist deve retornar 400, e a resposta deve seguir exatamente o formato de paginação do README.
- **Summary**: E15: paginated GET /requests with visibility, filters, sort whitelist (400) and README page format, plus integration tests

| File | Change | Additions | Removals |
|---|---|---|---|
| `backend/src/main/java/com/kingspan/purchase/request/application/PurchaseRequestService.java` | modified | +52 | -1 |
| `backend/src/main/java/com/kingspan/purchase/request/application/RequestFilter.java` | created | +33 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/application/RequestSort.java` | created | +46 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/application/RequestSummaryView.java` | created | +41 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/infra/PurchaseRequestRepository.java` | modified | +12 | -1 |
| `backend/src/main/java/com/kingspan/purchase/request/infra/RequestSortField.java` | created | +29 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/infra/RequestSpecifications.java` | created | +92 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/web/PurchaseRequestController.java` | modified | +30 | -0 |
| `backend/src/main/java/com/kingspan/purchase/request/web/PurchaseRequestSummaryResponse.java` | created | +39 | -0 |
| `backend/src/main/java/com/kingspan/purchase/shared/error/GlobalExceptionHandler.java` | modified | +7 | -0 |
| `backend/src/main/java/com/kingspan/purchase/shared/error/InvalidInputException.java` | created | +16 | -0 |
| `backend/src/main/java/com/kingspan/purchase/shared/web/PageResponse.java` | created | +22 | -0 |
| `backend/src/test/java/com/kingspan/purchase/request/web/PurchaseRequestListIntegrationTest.java` | created | +392 | -0 |
