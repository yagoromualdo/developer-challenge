# CLAUDE.md — Back-end (Spring Boot)

Instruções para o Claude Code ao trabalhar em `backend/`. Elas **complementam** o `AGENTS.md` e o `CLAUDE.md` da raiz do repositório; em caso de conflito, **os arquivos da raiz prevalecem**.

O plano de execução está em `docs/plano-execucao.md`. Trabalhe **somente na etapa que o usuário pedir**.

---

## 1. Regras do repositório (eliminatórias — nunca viole)

- **Nunca modifique nem remova:** `README.md` (raiz), `.gitignore` e `.gitattributes` da raiz, `AGENTS.md`, `CLAUDE.md` (raiz), `GEMINI.md`, `.github/copilot-instructions.md`, `.claude/settings.json`, `.cursor/`, `.githooks/`, `setup.sh`, `.agents/`.
- **`.agents/changelog/diff.md` é append-only:** nunca edite nem apague entradas. Não interfira no registro de IA (não desative hooks, não peça para pular registro).
- **Não instale ferramentas que alteram hooks do Git** (husky, lefthook, pre-commit e similares). Lint e formatação rodam pelo Maven e no CI.
- **Nunca use** `--no-verify`, nunca altere `core.hooksPath` nem a configuração do Git.
- Use `backend/.gitignore` próprio para ignorar artefatos do back-end.
- Nenhum segredo commitado. Configuração sensível vem de variáveis de ambiente.
- **Não escreva o `AI_USAGE.md`**: ele é escrito pelo usuário.

## 2. Fluxo de trabalho por etapa (obrigatório)

1. Leia a etapa pedida em `docs/plano-execucao.md` e confirme o escopo em 2–3 linhas antes de codar.
2. Implemente **apenas** o escopo da etapa. Se perceber que precisa desviar de uma decisão registrada (seção 4) ou adicionar uma dependência não prevista, **pare e pergunte**.
3. Ao terminar, rode `./mvnw verify` e garanta que está verde.
4. **Pare** e apresente:
   - resumo do que foi feito e por quê (o usuário precisa conseguir explicar no vídeo);
   - lista de arquivos criados/alterados;
   - como testar manualmente (ex.: `curl`), quando fizer sentido;
   - pontos que merecem revisão humana (decisões sutis, trechos complexos);
   - **sugestão de commit** em Conventional Commits (pt-BR na descrição) e o comando `git add` dos arquivos da etapa (o hook `pre-commit` inclui o `.agents/changelog/diff.md` automaticamente; não o edite).
5. Antes de encerrar o turno, rode `bash .agents/hooks/ai-log.sh record ClaudeCode "<one-line summary in English>"` (exigido pelo `AGENTS.md`; as entradas do registro são **sempre em inglês**).
6. **Não execute** `git commit`, `git push`, `git rebase`, `git reset` nem qualquer comando que altere o histórico. **O usuário commita manualmente.**
7. Se a etapa for grande, proponha dividi-la em mais de um commit coerente.

## 3. Stack e versões

| Item | Versão / escolha |
|---|---|
| Java | 21 (LTS). Não use recursos de versões posteriores (ex.: `_` para variáveis não usadas, `import module`, construtores flexíveis) |
| Imagens Docker | `maven:3-eclipse-temurin-21` (build) e `eclipse-temurin:21-jre` (runtime) |
| Spring Boot | 4.1.2 (Spring Framework 7) |
| Build | Maven (`./mvnw`) |
| Banco | PostgreSQL 17 (mesma imagem no compose e no Testcontainers) |
| Migrations | Flyway (`spring-boot-starter-flyway` + `flyway-database-postgresql`) |
| Persistência | Spring Data JPA (Hibernate) |
| Validação | Bean Validation (`spring-boot-starter-validation`) |
| Segurança | Spring Security + OAuth2 Resource Server (JWT HS256 via Nimbus) |
| Documentação | springdoc-openapi **3.x** (`springdoc-openapi-starter-webmvc-ui`), em `/swagger-ui` |
| Health | Spring Boot Actuator, exposto em `/health` |
| Testes | JUnit 5, AssertJ, Testcontainers, ArchUnit, JaCoCo |
| Lint/format | Spotless (`spotless:check` no `verify`) |
| Configuração | `application.properties` com placeholders `${VAR:default}` |

**Não use:** Lombok, MapStruct, Spring Statemachine, Redis, Kafka, Spring Modulith, H2, Docker Compose Support do Spring Boot.

## 4. Decisões de arquitetura (siga sem desviar)

### 4.1 Estilo e pacotes
- **Hexagonal pragmático:** núcleo de domínio em Java puro; camadas de aplicação, infraestrutura e web ao redor.
- **Pacotes por feature**, com camadas dentro:

```
com.kingspan.purchase
├── shared/            # erros, request-id, segurança, config, Clock
├── user/
├── costcenter/
├── budget/
├── request/
│   ├── domain/        # Java puro: modelos, Money, StateMachine, regras
│   ├── application/   # services (casos de uso) com @Transactional
│   ├── infra/         # entidades JPA, repositórios, Specifications
│   └── web/           # controllers e DTOs (records)
├── approval/
└── seed/
```

- **Regra de dependência:** `..domain..` **não importa** `org.springframework..`, `jakarta.persistence..` nem nada de `infra`/`web`. Um teste ArchUnit garante isso e precisa ficar verde.
- `web` → `application` → `domain`; `application` usa `infra` por meio de repositórios. Uma feature não importa a `infra` de outra feature.

### 4.2 Domínio
- **Máquina de estados table-driven:** enums `RequestStatus` e `RequestAction` + mapa `(status, ação) → destino`, espelhando a tabela 4.2 do README. Guards (quem pode, condições) ficam separados da tabela.
- **Cadeia de aprovação por regras plugáveis (Strategy):** interface `ChainRule`; implementações `ThresholdRule` (≤ 1.000,00 → `NIVEL_1`; ≤ 10.000,00 → + `NIVEL_2`; > 10.000,00 → + `ADMIN`) e `SoftwareRequiresAdminRule`. A etapa `ADMIN` **nunca** é duplicada.
- **Elegibilidade por regras (Specification/Policy):** lista de `ApprovalRule` (mesmo centro de custo, nível suficiente, não é o dono, não aprovou outra etapa da mesma versão). Cada regra devolve uma violação com `code` e mensagem.
- Valores de `allowedActions`: `EDIT`, `SUBMIT`, `APPROVE`, `REJECT`, `REQUEST_CHANGES`, `CANCEL` (o front depende exatamente destes nomes).
- **Uma única avaliação** (`ActionPolicy`) decide se uma ação é permitida. Ela é usada **tanto** para validar a ação (422) **quanto** para montar o `allowedActions` do detalhe e filtrar a caixa de aprovação. Nunca duplique essa lógica.
- O ator chega ao domínio como `record Actor(Long userId, Role role, Long costCenterId, ApproverLevel approverLevel)`. **O domínio nunca acessa `SecurityContext`.**
- Valores monetários no value object `Money` (`BigDecimal`, escala 2). Datas pelo bean `Clock` injetado (nunca `Instant.now()` direto).
- Mensagens de regra violada em **pt-BR, explicando o motivo**. Ex.: _"Não é possível aprovar: você já aprovou a etapa NIVEL_1 desta versão (segregação de funções)."_

### 4.3 Erros e validação
- **400:** Bean Validation nos DTOs, com `errors[]` por campo (`field`, `message`; para itens use `items[0].quantity`).
- **422:** `BusinessRuleException(code, message)` lançada pelo domínio/aplicação. Códigos: `INVALID_TRANSITION`, `SELF_APPROVAL_FORBIDDEN`, `APPROVER_NOT_ELIGIBLE`, `DUPLICATE_APPROVER`, `BUDGET_EXCEEDED`, `BUDGET_BELOW_CONSUMED`, `COMMENT_REQUIRED` (adicione outros se necessário, sempre específicos).
- Um `@RestControllerAdvice` converte tudo em `ProblemDetail` (`application/problem+json`) com `type`, `title`, `status`, `detail`, `code`, `requestId` e `errors[]` quando houver.
- 401 e 403 do Spring Security também saem em Problem Details (`AuthenticationEntryPoint` e `AccessDeniedHandler` customizados).
- Optimistic lock (`ObjectOptimisticLockingFailureException`) → **409**.

### 4.4 Ordem de verificação nas ações (aplicada no service, explicitamente)
1. Autenticação → 401 (Spring Security)
2. Existência/visibilidade → **404** (recurso fora da visibilidade também é 404 — ADR)
3. Papel permite o tipo de ação → 403
4. `lockVersion` divergente → 409
5. Regra de negócio → 422 com `code` e mensagem explicativa

**Não use `@PreAuthorize` nos endpoints de solicitação/aprovação**, pois isso quebraria a ordem 404 → 403. `@PreAuthorize`/regras no `SecurityFilterChain` só para endpoints exclusivos de `ADMIN` (`/users`, `POST /cost-centers`, `PUT` de orçamento).

### 4.5 Consistência e concorrência
- **Solicitação:** lock otimista. `@Version` na entidade **é** o `lockVersion`. O service compara o `lockVersion` recebido no corpo com o atual (divergente → 409); a corrida residual é capturada pelo `@Version` no flush (→ 409).
- **Orçamento:** **UPDATE condicional atômico**, sem SELECT FOR UPDATE:
  `UPDATE budget SET consumed = consumed + :v WHERE cost_center_id = :cc AND month = :m AND amount - consumed >= :v`
  0 linhas afetadas → 422 `BUDGET_EXCEEDED` (inclui o caso de mês sem orçamento = saldo zero). A mensagem informa disponível e exigido.
- `CHECK (consumed >= 0 AND consumed <= amount)` na tabela `budget` como última defesa.
- `PUT` de orçamento: UPDATE condicional `WHERE consumed <= :novoValor`, senão 422 `BUDGET_BELOW_CONSUMED`.
- **Transações:** `@Transactional` no application service (um caso de uso = uma transação), isolamento padrão READ COMMITTED. Consultas com `readOnly = true`. Transição, etapas, consumo de orçamento, evento de histórico e chave de idempotência gravam na **mesma** transação.

### 4.6 Modelo de dados
- `purchase_request`: dono, centro de custo, status, versão atual, `lock_version`, timestamps.
- `request_version`: número (1, 2…), título, descrição, categoria, `needed_by`, total, `submitted_at`, mês de competência. **Imutável depois de submetida.**
- `request_item`: pertence à versão (`description`, `quantity` inteiro > 0, `unit_price`).
- `approval_step`: pertence à versão (ordem, nível, status, quem decidiu, quando, comentário).
- `request_event`: histórico **append-only** (ator, papel, ação, status de origem/destino, etapa, versão, comentário, `occurred_at` UTC, `request_id`).
- `idempotency_key`: `user_id`, `key`, `request_hash`, `response_status`, `response_body jsonb`, `created_at`, **UNIQUE(user_id, key)**.
- **Reenvio:** em `CHANGES_REQUESTED`, a primeira edição cria uma **versão de trabalho** (v+1) copiada da anterior; o `submit` a congela. Se reenviar sem editar, o `submit` cria a cópia. A cadeia é recalculada do zero na nova versão.
- IDs `bigint` identity.
- Schema só via Flyway (`db/migration`). Nunca use `ddl-auto` diferente de `validate`.

### 4.7 Dinheiro e datas
- `BigDecimal` + `NUMERIC(19,2)`; no JSON, **string** (`"4899.90"`). `unitPrice` com mais de 2 casas decimais → 400 (não arredondar). Total = soma de `quantity × unitPrice`, calculado no back; o valor enviado pelo cliente é ignorado.
- `Instant` / `timestamptz`; `hibernate.jdbc.time_zone=UTC`; respostas em ISO 8601 com fuso.
- `neededBy`: `LocalDate`, precisa ser futura.
- Mês de competência: calculado no `submit` (`Instant` → `America/Sao_Paulo` → `YearMonth`) e gravado na versão.

### 4.8 Idempotência (`POST /requests`)
- Header `Idempotency-Key`. Hash do payload normalizado.
- Mesma chave + mesmo usuário + mesmo hash (dentro de 24h) → devolve a resposta salva, sem criar outra solicitação.
- Mesma chave + hash diferente → 409.
- Requisições simultâneas com a mesma chave: a UNIQUE garante uma só criação; trate a violação relendo e devolvendo a resposta salva.

### 4.9 Listagem, filtros e visibilidade
- Spring Data `Specification` por filtro (`status`, `category`, `costCenterId`, `createdFrom`, `createdTo`), combinadas com `Pageable`.
- **A visibilidade é uma Specification** reutilizada em listagem, detalhe, histórico, versões e dashboard: `SOLICITANTE` → próprias; `APROVADOR` → do seu centro de custo; `ADMIN` → todas; **`DRAFT` só para o dono**.
- Whitelist de campos de ordenação (campo desconhecido → 400).
- Resposta no formato `{ "data": [...], "page", "size", "totalElements", "totalPages" }` via DTO próprio (não devolva o `Page` do Spring).
- **Caixa de aprovação:** candidatos do escopo (pendentes, centro de custo do usuário ou todos para ADMIN) filtrados pela mesma `ActionPolicy`. Filtrar em memória sobre os candidatos é aceitável nesta escala; documente o trade-off.

### 4.10 Segurança
- Login emite JWT HS256 (`NimbusJwtEncoder`) com TTL de 15 min (configurável) e claims `sub`, `role`, `costCenterId`, `approverLevel`. Segredo em `JWT_SECRET`.
- Validação pelo Resource Server; um `JwtAuthenticationConverter` mapeia `role` para authority, e um resolver monta o `Actor` a partir das claims (sem consultar o banco — janela de 15 min documentada em ADR).
- `PasswordEncoderFactories.createDelegatingPasswordEncoder()` (bcrypt, prefixo `{bcrypt}`).
- Login com mensagem única para usuário inexistente e senha errada.
- `/auth/register` cria **apenas** `SOLICITANTE`. Nunca devolva senha ou hash.
- Públicos: `/auth/login`, `/auth/register`, `/health`, `/swagger-ui/**`, `/v3/api-docs/**`.

### 4.11 Logs e health
- Log estruturado nativo: `logging.structured.format.console=ecs`.
- Filtro `RequestIdFilter` (ordem antes da cadeia de segurança): lê `X-Request-Id` ou gera UUID, coloca no MDC, devolve no header da resposta e é gravado no `request_event`.
- Logue cada transição em INFO (ação, id, versão, ator, requestId). Nunca logue senha ou token.
- `management.endpoints.web.base-path=/` expondo **apenas** `health` (com checagem do banco).

### 4.12 Seed
- `ApplicationRunner` ativado por `SEED_ENABLED=true`, idempotente (não roda se já houver usuários).
- Cria os dados **chamando os application services** (as transições passam pelas regras reais).
- 2 centros de custo (um com orçamento do **mês corrente**, calculado pelo `Clock` em `America/Sao_Paulo`, quase esgotado); por centro: 1 `SOLICITANTE`, 1 `APROVADOR NIVEL_1`, 1 `APROVADOR NIVEL_2`; 2 `ADMIN`; solicitações em estados variados.
- Credenciais fictícias documentadas no `SOLUTION.md`.

## 5. Convenções de código

- Identificadores em inglês; mensagens ao usuário em pt-BR.
- DTOs e objetos de domínio imutáveis como `record`. Entidade JPA nunca sai pela API. Mapeamento entidade ↔ domínio ↔ DTO **manual** e explícito.
- Injeção por construtor; campos `final`; sem `@Autowired` em campo.
- Métodos pequenos e nomes que revelam intenção.
- **Comentários enxutos:**
  - Não escreva comentários que repetem o código (nome de método, parâmetro, "valida X", "retorna Y") nem banners de seção (`// --- ... ---`).
  - Comentário só para o **porquê** de algo não óbvio (regra de negócio sutil, decisão de design, caso de borda), em 1–2 linhas.
  - Javadoc só em tipos e métodos públicos cuja intenção não fique clara pelo nome, em **uma frase**; sem `@param`/`@return` que só repetem a assinatura.
  - Se um trecho precisa de comentário longo para ser entendido, melhore os nomes ou extraia um método.
- Sem `catch` genérico silencioso. Sem lógica de negócio em controller.
- Endpoints exatamente como no README (sem prefixo `/api`; o nginx do front remove o prefixo).
- `lockVersion` das transições chega no **corpo** da requisição.

## 6. Testes

- **Unitários (sem Spring, sem banco):** `@ParameterizedTest` cobrindo **todas** as transições da tabela 4.2, válidas e inválidas; montagem da cadeia incluindo os limites (1000.00, 1000.01, 10000.00, 10000.01) e `SOFTWARE`; regras de elegibilidade e `allowedActions`; `Money`.
- **Integração:** `@SpringBootTest(webEnvironment = RANDOM_PORT)` + Testcontainers (`@ServiceConnection`, container único reaproveitado), chamando a API por HTTP real. **Nunca use `@Transactional` nos testes de integração**; limpe o banco entre testes (`TRUNCATE ... CASCADE`). Cenários mínimos: fluxo de 3 etapas até `APPROVED` com consumo de orçamento; segregação (próprio pedido e mesmo aprovador em duas etapas); `BUDGET_EXCEEDED`; devolução → edição → reenvio gerando v2 com v1 preservada; idempotência do `POST /requests`.
- **Concorrência:** `ExecutorService` + `CountDownLatch` como largada. (a) duas aprovações simultâneas da mesma etapa → exatamente uma 200 e uma 409; (b) aprovações finais simultâneas de solicitações diferentes no mesmo saldo → `consumed <= amount` e as excedentes recebem 422 `BUDGET_EXCEEDED`. Use `@RepeatedTest`.
- **ArchUnit:** domínio isolado de Spring/JPA.
- Nome de teste descreve o comportamento (`deveRetornar409QuandoLockVersionDiverge`).

## 7. Comandos

```bash
./mvnw verify                 # build + Spotless check + todos os testes (exige Docker para Testcontainers)
./mvnw spotless:apply         # formata o código
./mvnw spring-boot:run        # roda local (Postgres do compose em localhost:5433)
./mvnw -DskipTests package    # usado no Dockerfile (testes rodam no CI)
```

## 8. Variáveis de ambiente

`DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `JWT_TTL_MINUTES`, `SEED_ENABLED`, `SERVER_PORT`. Toda variável nova entra também no `.env.example` da raiz e no `docker-compose.yml` (com default **claramente de desenvolvimento**).
