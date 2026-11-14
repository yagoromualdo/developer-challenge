# Plano de execução

Plano incremental do teste técnico (solicitações de compra). Cada etapa tem objetivo, arquivos, critério de pronto e sugestão de commit. **O agente para ao fim de cada etapa; o usuário revisa e commita manualmente** (ver `backend/CLAUDE.md` e `frontend/CLAUDE.md`, seção 2).

Legenda: 🧑 = feito pelo usuário, sem agente · 🤖 = executado com o Claude Code e revisado pelo usuário.

---

## Decisões registradas

| # | Tema | Escolha | ADR |
|---|---|---|---|
| — | Stateful × stateless | Stateless (JWT); todo estado no Postgres | 007 |
| — | Formato de erro | Problem Details (RFC 9457) com `code` | — |
| D1 | Versões | Java 25, Spring Boot 4.1.2, Maven, `.properties` | — |
| D2 | Estilo | Hexagonal pragmático (núcleo de domínio puro) | 004 |
| D3 | Pacotes | Por feature, com `domain/application/infra/web` | 004 |
| D4 | Máquina de estados | Tabela de transições (enums + mapa) + guards | 004 |
| D5 | Cadeia e elegibilidade | Strategy (`ChainRule`) + Policy (`ApprovalRule`); mesma avaliação para 422 e `allowedActions` | 004 |
| D6 | Validação | Bean Validation (400) + `BusinessRuleException` (422) + `@RestControllerAdvice` | — |
| D7 | DTO × entidade | `records` + mapeamento manual | — |
| D8 | Síncrono × assíncrono | Síncrono; histórico na mesma transação; outbox só como diferencial | — |
| D9 | Cache | Sem cache (justificado) | — |
| D10 | Concorrência da solicitação | Otimista: `@Version` = `lockVersion` → 409 | **001** |
| D11 | Orçamento | UPDATE condicional atômico + `CHECK` | **001** |
| D12 | Versões e histórico | Tabelas de snapshot por versão + `request_event` append-only | **002** |
| D13 | Dinheiro | `BigDecimal`/`NUMERIC(19,2)`, string no JSON, rejeitar > 2 casas | 005 |
| D14 | Datas | `Instant`/`timestamptz` UTC; competência gravada no submit; `Clock` | 005 |
| D15 | Transações | `@Transactional` por caso de uso, READ COMMITTED | 001 |
| D16 | Idempotência | Tabela `idempotency_key` com UNIQUE(user, key) + hash | 006 |
| D17 | Filtros | `Specification` + whitelist de ordenação; visibilidade como Specification | — |
| D18 | Seed | `ApplicationRunner` via services, `SEED_ENABLED`, mês dinâmico | — |
| D19 | JWT | OAuth2 Resource Server + Nimbus, HS256 | 007 |
| D20 | Claims | Confiar nas claims (janela de 15 min documentada) | 007 |
| D21 | Autorização | Grossa no Spring Security; fina no service seguindo a ordem 4.3 | 003 |
| D22 | Visibilidade | **404** para recurso fora da visibilidade | **003** |
| D23 | IDs | `bigint` identity | — |
| D24 | Senha | Delegating encoder (bcrypt) | — |
| D25 | Extras de auth | Nenhum agora; rate limit como diferencial | — |
| D26 | Angular | 22, standalone + zoneless | — |
| D27 | Estado | Services + signals; URL como fonte da verdade | — |
| D28 | Formulários | Reactive Forms + `FormArray` | — |
| D29 | Pastas | `core/`, `shared/`, `features/` | — |
| D30 | UI | Angular Material | — |
| D31 | Token | `sessionStorage` (cookie httpOnly como evolução) | 007 |
| D32 | Erros no front | Interceptor transversal; 400/409/422 nos componentes | — |
| D33 | Comunicação | Mesma origem via proxy (nginx / `proxy.conf.json`) | — |
| D34 | Testes front | Vitest + TestBed | — |
| D35 | Testes back | JUnit/AssertJ; integração RANDOM_PORT + Testcontainers; latch | — |
| D36 | Logs | Structured logging nativo (ECS) + MDC; `/health` | — |
| D37 | Docker | Multi-stage; defaults de dev no compose | — |
| D38 | CI | Jobs paralelos back/front + smoke test do compose | — |
| D39 | Deploy | VM (EC2/Azure) com o mesmo compose + Caddy — se der tempo | — |
| D40 | Escalabilidade | Diagrama: stateless + garantias no banco; evolução | — |
| D41 | E2E | Não agora; Playwright como diferencial | — |

**ADRs** (`docs/adr/`): **001** Concorrência e orçamento · **002** Versões e histórico · **003** 403 × 404 e ordem de verificação · 004 Arquitetura do domínio · 005 Dinheiro e datas · 006 Idempotência · 007 Autenticação stateless. Os três primeiros são obrigatórios.

---

## Fase 0 — Preparação

### E0 🧑 Configurar o fork
- Fork **desmarcando** "Copy the main branch only"; clone; `git switch teste-pleno`; `./setup.sh` (Git Bash no Windows); habilitar o Actions na aba *Actions*.
- **Pronto quando:** o `./setup.sh` terminou sem erro e a aba *Actions* está habilitada. Não altere nenhuma configuração de hooks depois disso.
- **Commit:** nenhum.

### E1 🧑 Adicionar os arquivos de planejamento
- Arquivos: `docs/plano-execucao.md`, `backend/CLAUDE.md`, `frontend/CLAUDE.md` (gerados no Claude.ai). Não toque no `CATALOGO.md` que já veio no repositório.
- Registrar o uso de IA (nome do agente conforme o `AGENTS.md`; resumo em inglês): `bash .agents/hooks/ai-log.sh snapshot Other-ClaudeAI` imediatamente antes de colar e `bash .agents/hooks/ai-log.sh record Other-ClaudeAI "Execution plan and Claude Code instructions generated in Claude.ai chat"` logo depois.
- **Commit:** `docs: adiciona plano de execução e instruções para o Claude Code`

---

## Fase 1 — Fundação do back-end

### E2 🤖 Inicializar o projeto Spring Boot
- Projeto do start.spring.io (Maven, Java 25, Boot 4.1.2, Web, Validation, Data JPA, PostgreSQL, Flyway, Actuator, Testcontainers) descompactado em `backend/`; adicionar springdoc 3.x, ArchUnit, JaCoCo e Spotless no `pom.xml`; `backend/.gitignore`; `application.properties` com placeholders de env; `ddl-auto=validate`.
- **Pronto quando:** `./mvnw verify` verde (o teste de contexto sobe com Testcontainers).
- **Commit:** `chore(backend): inicializa projeto Spring Boot 4.1`

### E3 🤖 Compose com Postgres e `.env.example`
- `docker-compose.yml` (raiz) só com o serviço `db` (Postgres 17, healthcheck `pg_isready`, porta 5433 no host); `.env.example`.
- **Pronto quando:** `docker compose up db` sobe e o back conecta via `./mvnw spring-boot:run`.
- **Commit:** `chore: adiciona docker compose com PostgreSQL e .env.example`

### E4 🤖 Infraestrutura transversal
- `shared/`: `RequestIdFilter` + MDC + header na resposta; logs estruturados ECS; `BusinessRuleException` + enum de códigos; `@RestControllerAdvice` com `ProblemDetail` (400 com `errors[]`, 409, 422, 500); `/health` exposto na raiz; configuração do OpenAPI; bean `Clock`.
- **Pronto quando:** teste de integração confirma `X-Request-Id` devolvido (gerado e propagado) e `/health` UP com banco; `/swagger-ui` abre.
- **Commit:** `feat(backend): adiciona request id, logs estruturados, problem details e health`

---

## Fase 2 — Domínio puro (sem Spring, sem banco)

### E5 🤖 Value objects e enums
- `Money`, `RequestStatus`, `RequestAction`, `Category`, `Role`, `ApproverLevel`, `StepLevel`, `Actor`.
- **Pronto quando:** testes de `Money` (soma, multiplicação por quantidade, rejeição de mais de 2 casas).
- **Commit:** `feat(domain): adiciona value objects e enums do domínio`

### E6 🤖 Máquina de estados
- Tabela de transições espelhando a 4.2 do README; estados finais bloqueados.
- **Pronto quando:** `@ParameterizedTest` cobre **todas** as combinações (status × ação), válidas e inválidas (`INVALID_TRANSITION` com mensagem explicativa).
- **Commit:** `feat(domain): implementa máquina de estados das solicitações`

### E7 🤖 Montagem da cadeia de aprovação
- `ChainRule`, `ThresholdRule`, `SoftwareRequiresAdminRule`, `ApprovalChainBuilder`.
- **Pronto quando:** testes com os limites 1000.00 / 1000.01 / 10000.00 / 10000.01, `SOFTWARE` abaixo e acima de 10.000 (sem `ADMIN` duplicado).
- **Commit:** `feat(domain): implementa montagem da cadeia de aprovação`

### E8 🤖 Políticas de aprovação e `allowedActions`
- `ApprovalRule`s (mesmo centro de custo, nível, não é o dono, não aprovou outra etapa), `ActionPolicy` (validação + lista de ações permitidas), regra de comentário obrigatório (≥ 10 caracteres); teste ArchUnit de isolamento do domínio.
- **Pronto quando:** testes cobrem cada violação (`SELF_APPROVAL_FORBIDDEN`, `APPROVER_NOT_ELIGIBLE`, `DUPLICATE_APPROVER`, `COMMENT_REQUIRED`), `NIVEL_2` aprovando `NIVEL_1`, `ADMIN` aprovando qualquer etapa, etapa `ADMIN` só por `ADMIN`; ArchUnit verde.
- **Commit:** `feat(domain): adiciona políticas de aprovação e allowedActions`

---

## Fase 3 — Persistência

### E9 🤖 Schema inicial (Flyway)
- `V1__schema.sql`: `cost_center`, `app_user`, `budget` (com `CHECK`), `purchase_request` (`lock_version`), `request_version`, `request_item`, `approval_step`, `request_event`, `idempotency_key`; constraints, FKs e índices dos filtros.
- **Pronto quando:** a aplicação sobe com `ddl-auto=validate` e o teste de integração aplica a migration.
- **Commit:** `feat(db): cria schema inicial com Flyway`

### E10 🤖 Entidades JPA e repositórios
- Entidades em `infra/`, repositórios, mapeamento entidade ↔ domínio, query nativa do UPDATE condicional de orçamento.
- **Pronto quando:** teste de integração do repositório de orçamento (consome quando cabe; 0 linhas quando não cabe ou não existe).
- **Commit:** `feat(backend): mapeia entidades JPA e repositórios`

---

## Fase 4 — Segurança e cadastros

### E11 🤖 Autenticação JWT
- Spring Security + OAuth2 Resource Server; `JwtService` (Nimbus HS256, TTL configurável); `POST /auth/login`, `POST /auth/register` (só `SOLICITANTE`), `GET /auth/me`; entry point e access denied em Problem Details; resolver de `Actor`.
- **Pronto quando:** testes: login válido devolve JWT com as claims; credencial inválida → 401 com mensagem única; sem token → 401 `problem+json`; register cria `SOLICITANTE`; nenhuma resposta contém senha/hash.
- **Commit:** `feat(auth): adiciona autenticação JWT com login, registro e me`

### E12 🤖 Usuários, centros de custo e orçamentos
- `GET/POST /users`, `PATCH /users/:id`, `GET/POST /cost-centers`, `GET/PUT /cost-centers/:id/budgets/:yyyy-mm` (`BUDGET_BELOW_CONSUMED`).
- **Pronto quando:** testes: não-admin → 403; PUT abaixo do consumido → 422; mês sem orçamento → saldo zero.
- **Commit:** `feat(admin): adiciona endpoints de usuários, centros de custo e orçamentos`

---

## Fase 5 — Solicitações e aprovações

### E13 🤖 Rascunho: criar, editar e detalhar
- `POST /requests`, `PUT /requests/:id`, `GET /requests/:id` (versão atual, itens, etapas, `allowedActions`, `lockVersion`); Specification de visibilidade (404); validações 400 (quantidade, preço, `neededBy` futura); total calculado no back; eventos de histórico.
- **Pronto quando:** testes: total ignora o valor do cliente; DRAFT de outro usuário → 404; edição fora de DRAFT/CHANGES_REQUESTED → 422; edição por não-dono segue a ordem 4.3 (404 se não visível; caso visível, o código escolhido fica registrado no ADR 003).
- **Commit:** `feat(request): cria, edita e detalha solicitações em rascunho`

### E14 🤖 Idempotência do `POST /requests`
- Tabela `idempotency_key`, hash do payload, replay da resposta, 409 com payload diferente, corrida pela UNIQUE.
- **Pronto quando:** testes de integração: mesma chave e payload → mesma resposta e uma só solicitação; payload diferente → 409; duas chamadas simultâneas → uma solicitação.
- **Commit:** `feat(request): adiciona idempotência na criação`

### E15 🤖 Listagem com filtros e paginação
- Specifications de filtro + visibilidade, whitelist de ordenação, DTO de página no formato do README.
- **Pronto quando:** testes de visibilidade por papel (incluindo DRAFT só para o dono), filtros, ordenação inválida → 400.
- **Commit:** `feat(request): adiciona listagem com filtros, ordenação e paginação`

### E16 🤖 Submissão e cancelamento
- `POST /requests/:id/submit` (monta a cadeia na versão, competência pelo `Clock` em São Paulo), `POST /requests/:id/cancel` (dono ou ADMIN); checagem de `lockVersion` → 409.
- **Pronto quando:** testes: cadeia correta por valor/categoria; `lockVersion` divergente → 409; cancelar estado final → 422.
- **Commit:** `feat(request): implementa submissão e cancelamento`

### E17 🤖 Aprovar, rejeitar e devolver
- `approve`, `reject`, `request-changes` com a ordem 404 → 403 → 409 → 422; avanço de etapa; última etapa consome orçamento na mesma transação (`BUDGET_EXCEEDED` com valores na mensagem); eventos.
- **Pronto quando:** testes de integração: fluxo de 3 etapas até `APPROVED` com orçamento consumido; segregação (próprio pedido; mesmo aprovador em duas etapas); `BUDGET_EXCEEDED` mantém pendente; comentário obrigatório.
- **Commit:** `feat(approval): implementa aprovação, rejeição e devolução para ajuste`

### E18 🤖 Versionamento no reenvio
- Versão de trabalho na edição em `CHANGES_REQUESTED`; `submit` congela e recalcula a cadeia; `GET /requests/:id/versions` e `/versions/:n`.
- **Pronto quando:** teste de integração: devolução → edição → reenvio gera v2; v1 preservada e consultável; aprovações da v1 não contam.
- **Commit:** `feat(request): implementa versionamento no reenvio`

### E19 🤖 Testes de concorrência
- Dois testes com `ExecutorService` + `CountDownLatch` e `@RepeatedTest`.
- **Pronto quando:** mesma etapa → exatamente uma 200 e uma 409; solicitações diferentes no mesmo saldo → `consumed <= amount` e as excedentes com 422.
- **Commit:** `test(approval): adiciona testes de concorrência de aprovação e orçamento`

### E20 🤖 Histórico e caixa de aprovação
- `GET /requests/:id/history`, `GET /approvals/inbox` (filtrada pela mesma `ActionPolicy`).
- **Pronto quando:** testes: histórico com todos os campos exigidos (incluindo `requestId`); inbox respeita centro de custo, nível e segregação.
- **Commit:** `feat(approval): adiciona histórico e caixa de aprovação`

### E21 🤖 Dashboard
- `GET /dashboard/summary`: totais por status no escopo; orçamento do mês corrente (orçado, consumido, disponível).
- **Pronto quando:** testes de escopo por papel.
- **Commit:** `feat(dashboard): adiciona resumo por status e orçamento do mês`

### E22 🤖 Seed automático
- `ApplicationRunner` com `SEED_ENABLED`, idempotente, via services; orçamento quase esgotado no mês corrente; estados variados.
- **Pronto quando:** subir duas vezes não duplica dados; com o seed é possível reproduzir `BUDGET_EXCEEDED` em qualquer data.
- **Commit:** `feat(seed): adiciona seed automático com dados fictícios`

### E23 🤖 Docker do back-end
- `backend/Dockerfile` multi-stage (usuário não-root, `-DskipTests`); serviço `backend` no compose com `depends_on: service_healthy` e healthcheck.
- **Pronto quando:** `docker compose up --build db backend` sobe com migrations e seed; login via `curl` funciona.
- **Commit:** `build(backend): adiciona Dockerfile e serviço no compose`

### E24 🤖 CI do back-end
- `.github/workflows/ci.yml` com o job `backend` (`setup-java` 25, cache, `./mvnw -B verify`).
- **Pronto quando:** pipeline verde no GitHub.
- **Commit:** `ci: adiciona pipeline do back-end`

---

## Fase 6 — Front-end

### E25 🤖 Inicializar o projeto Angular
- `ng new frontend --skip-git` (Angular 22, roteamento, SCSS), Angular Material, angular-eslint, Prettier (scripts), Vitest, `proxy.conf.json`, `frontend/.gitignore`.
- **Pronto quando:** `npm run lint`, `npm test` e `npm run build` verdes.
- **Commit:** `chore(frontend): inicializa projeto Angular 22`

### E26 🤖 Login, sessão e interceptors
- `AuthService` (`sessionStorage`), interceptors (bearer, request id, erro/401), `ApiProblem`, guards, tela de login com `returnUrl`, layout com menu por papel.
- **Pronto quando:** login funciona contra o back; token expirado leva ao login e volta à rota de origem.
- **Commit:** `feat(frontend): adiciona login, sessão e interceptors`

### E27 🤖 Dashboard
- **Pronto quando:** totais por status e orçamento do mês exibidos com estados de carregando/vazio/erro.
- **Commit:** `feat(frontend): adiciona dashboard`

### E28 🤖 Listagem
- Filtros, ordenação e paginação no servidor, refletidos na URL.
- **Pronto quando:** copiar a URL e abrir em outra aba reproduz a mesma busca.
- **Commit:** `feat(frontend): adiciona listagem com filtros refletidos na URL`

### E29 🤖 Formulário de criação e edição
- `FormArray` de itens, total em tempo real, `Idempotency-Key` por instância do formulário, erros 400 por campo.
- **Pronto quando:** teste do formulário (itens e total; erro do servidor no campo certo).
- **Commit:** `feat(frontend): adiciona formulário de solicitação com itens dinâmicos`

### E30 🤖 Detalhe e ações
- Dados, versões, cadeia, linha do tempo; ações via `allowedActions`; modal de comentário; tratamento de 409 e 422.
- **Pronto quando:** testes de `allowedActions` e de 409/422.
- **Commit:** `feat(frontend): adiciona detalhe com ações guiadas pela API`

### E31 🤖 Caixa de aprovação
- **Pronto quando:** aprovador vê só o que pode aprovar agora; ação a partir da caixa atualiza a lista.
- **Commit:** `feat(frontend): adiciona caixa de aprovação`

### E32 🤖 Docker do front e compose completo
- `frontend/Dockerfile` (build Node → nginx), `nginx.conf` (`try_files` + `location /api/` com `proxy_pass http://backend:8080/`), serviço `frontend` no compose.
- **Pronto quando:** `docker compose up --build` num **clone limpo, sem `.env`**, sobe tudo e o fluxo completo funciona no navegador.
- **Commit:** `build(frontend): adiciona Dockerfile, nginx e serviço no compose`

### E33 🤖 CI completo
- Jobs `frontend` (`npm ci`, lint, `format:check`, test, build) e `compose-smoke` (sobe o compose, espera `/health`, faz login via `curl`).
- **Pronto quando:** os três jobs verdes.
- **Commit:** `ci: adiciona jobs do front-end e smoke test do compose`

---

## Fase 7 — Documentação e entrega

### E34 🤖 + 🧑 ADRs
- `docs/adr/001` a `007` (formato: contexto, opções consideradas, decisão, consequências). O agente gera o rascunho; **o usuário revisa e reescreve com as próprias palavras** o que for defender no vídeo.
- **Commit:** `docs(adr): adiciona ADRs de concorrência, versões, visibilidade e arquitetura`

### E35 🤖 + 🧑 SOLUTION, READMEs e diagrama
- `SOLUTION.md` (visão geral, como rodar com compose e local, usuários do seed, decisões e trade-offs, o que ficou de fora, o que faria com mais tempo); `backend/README.md` e `frontend/README.md` (pré-requisitos, variáveis, comandos, exemplos `curl`); `docs/architecture.md` (diagrama Mermaid atual + evolução).
- **Commit:** `docs: adiciona SOLUTION, READMEs e diagrama de arquitetura`

### E36 🧑 AI_USAGE.md
- Escrito **pelo usuário, sem IA**: ferramentas, ≥ 2 erros concretos da IA e como foram corrigidos, partes escritas sem IA, como validou.
- **Commit:** `docs: adiciona relato de uso de IA`

### E37 🧑 Verificação final
- Clone limpo → `docker compose up --build` → seguir o `SOLUTION.md` do zero; CI verde no último commit; nenhum arquivo protegido alterado no histórico (`git log --stat -- README.md .gitignore .gitattributes AGENTS.md CLAUDE.md .githooks setup.sh .claude/settings.json .agents`).
- Roteiro e gravação do vídeo (15–25 min), seguindo a seção 8.1 do README.
- **Commit:** só se houver correção.

---

## Fase 8 — Diferenciais (só se der tempo, nesta ordem)

| Etapa | Diferencial | Custo | Commit sugerido |
|---|---|---|---|
| X1 | Trigger que bloqueia UPDATE/DELETE em `request_event` | ~30 min | `feat(db): garante histórico append-only por trigger` |
| X2 | Filtros `minAmount`, `maxAmount`, `q` (back + front) | ~1h | `feat(request): adiciona filtros por valor e busca no título` |
| X3 | Tempo médio de aprovação (30 dias) no dashboard | ~1h | `feat(dashboard): adiciona tempo médio de aprovação` |
| X4 | Métricas Micrometer/Prometheus de negócio | ~30 min | `feat(observability): expõe métricas de aprovação e orçamento` |
| X5 | Rate limit no login (Bucket4j) | ~1h | `feat(auth): adiciona rate limit no login` |
| X6 | Deploy em VM (EC2/Azure) com Caddy | 2–4h | `docs: documenta deploy em cloud` |
| X7 | Idempotência nas transições | ~1h | `feat(approval): adiciona idempotência nas transições` |
| X8 | E2E com Playwright no job `compose-smoke` | ~2h | `test(e2e): adiciona fluxo de aprovação com Playwright` |

Se um diferencial ficar de fora, registre no `SOLUTION.md` em "o que eu faria com mais tempo".
