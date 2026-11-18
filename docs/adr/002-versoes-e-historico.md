# ADR 002 — Versões da solicitação e histórico de eventos

## Contexto

O README pede que:

- ao reenviar uma solicitação devolvida para ajuste (`CHANGES_REQUESTED` → `PENDING_APPROVAL`), seja criada uma **nova versão**; a anterior fica preservada e consultável, com as aprovações que recebeu, e essas aprovações **não contam** para a nova versão (a cadeia recomeça);
- exista um **histórico append-only** com ator, papel, ação, status de origem e destino, etapa, versão, comentário, data em UTC e o `requestId` da requisição;
- a modelagem seja justificada em ADR.

## Opções consideradas

**Versões**

1. **Tabelas por versão** (`request_version`, com `request_item` e `approval_step` pendurados na versão).
2. **Snapshot em JSONB** de cada versão numa coluna.
3. **Event sourcing**: o estado é reconstruído a partir dos eventos.
4. **Auditoria genérica** (Hibernate Envers ou trigger de auditoria).

**Histórico append-only**

1. Só convenção no código (nenhum `UPDATE`/`DELETE` escrito).
2. `@Immutable` na entidade.
3. Trigger no banco que barra `UPDATE` e `DELETE`.
4. Privilégios do banco (`REVOKE UPDATE, DELETE`) para o usuário da aplicação.

## Decisão

### Versões: tabelas por versão

- `purchase_request` guarda o que vale para a solicitação inteira (dono, centro de custo, status, `current_version`, `lock_version`). `request_version` guarda o conteúdo de cada versão (título, descrição, categoria, `needed_by`, total, `submitted_at`, `competence_month`); itens e etapas pertencem à **versão**, não à solicitação. Foi escolhida em vez do JSONB porque itens e etapas continuam consultáveis e protegidos por constraints, e em vez de event sourcing porque o custo não se paga neste escopo.
- **Versão de trabalho:** em `CHANGES_REQUESTED`, a primeira edição cria a v+1 com o conteúdo editado; as edições seguintes, antes do reenvio, alteram essa mesma versão. Se o solicitante reenviar sem editar, o `submit` cria a v+1 copiando a anterior (`copyForNextVersion`). O `submit` congela a versão (`submitted_at` + mês de competência) e monta a cadeia **do zero** a partir do total e da categoria dela.
- **Imutabilidade depois de submetida:** `RequestVersionEntity` lança `IllegalStateException` em `updateContent`/`markSubmitted` numa versão já submetida e em `defineChain` numa versão que já tem cadeia (só acontece com bug, e vira `500`). No banco, `ck_request_version_submission` garante que `submitted_at` e `competence_month` nascem juntos.
- **Aprovações da v1 não contam:** as etapas são da versão (`approval_step.version_id`), e as constraints de segregação também são por versão (`uq_approval_step_level`; o índice único parcial `uq_approval_step_approver (version_id, decided_by) WHERE status = 'APPROVED'` é a última defesa contra o mesmo aprovador em duas etapas).
- **Endpoints (E18):** `GET /requests/:id/versions` devolve um resumo por versão (número, `current`, `submittedAt`), sem itens nem etapas; `GET /requests/:id/versions/:n` devolve o conteúdo completo daquela versão, sem `allowedActions`. A versão de trabalho aparece na lista com `submittedAt` nulo e é visível a quem vê a solicitação. Versão inexistente de uma solicitação visível → `404`.

### `UNIQUE DEFERRABLE` em `request_item`

- `uq_request_item_line_number UNIQUE (version_id, line_number) DEFERRABLE INITIALLY DEFERRED` (V1). É adiável porque, ao trocar a coleção de itens de uma versão de trabalho, o Hibernate **insere os itens novos antes de apagar os antigos**; uma `UNIQUE` imediata falharia no meio do flush. A checagem fica para o commit, quando a renumeração já terminou.
- O `RequestVersionEntity.updateContent` renumera os itens a partir de 0 a cada troca, então o fluxo normal não viola a constraint (`deveRenumerarItensAoSubstituirAColecaoNaMesmaTransacao`).

### Histórico: `request_event` append-only em três camadas

- O evento é gravado pelo `RequestHistory` na **mesma transação** do caso de uso, com o status já alterado como destino. `http_request_id` guarda o `X-Request-Id` da requisição (no JSON do histórico ele sai como `requestId`, o mesmo nome usado no Problem Details); o seed grava `"seed"`.
- `RequestEventEntity` é `@Immutable`: o Hibernate nunca gera `UPDATE` para ela.
- **Trigger no banco** (V1): `trg_request_event_append_only`, `BEFORE UPDATE OR DELETE ... FOR EACH ROW`, chama `forbid_request_event_change()`, que lança exceção com `ERRCODE = 'restrict_violation'`. Isso vale para qualquer cliente do banco, não só para a aplicação. Foi adiantado da Fase 8 (diferencial X1) para a E9 (commit `d0e870c`).
- **Migration V2 com o `CHECK` de `action`** (`V2__request_event_action_check.sql`, commit `d51f5e3`, E13): `ck_request_event_action CHECK (action IN ('CREATE', 'EDIT', 'SUBMIT', 'APPROVE', 'REJECT', 'REQUEST_CHANGES', 'CANCEL'))`, espelhando o enum `HistoryAction` (as ações de `RequestAction` mais `CREATE`, que não é transição). Veio numa **migration nova** porque a V1 já estava aplicada e commitada: alterá-la mudaria o checksum do Flyway. Pela mesma regra, mudar o enum exige uma nova migration.
- Testes: `SchemaMigrationIntegrationTest` cobre `UPDATE`/`DELETE` barrados, o `TRUNCATE` permitido, toda `HistoryAction` aceita pelo `CHECK` e valor fora do domínio rejeitado; `HistoryActionTest` garante o mapeamento um a um com `RequestAction`.

## Consequências

- Consultar uma versão antiga é uma leitura simples por `(request_id, version_number)`, com itens e etapas daquela versão.
- Cada reenvio duplica o conteúdo (itens) na nova versão; o volume é pequeno e aceitável neste domínio.
- O trigger é de linha: **`TRUNCATE` não o dispara**. É assim que os testes limpam o banco, mas também quer dizer que um usuário com privilégio de `TRUNCATE` apaga o histórico. Em produção, o passo seguinte seria tirar esse privilégio (e `UPDATE`/`DELETE`) do usuário da aplicação.
- Uma violação da `UNIQUE` adiável só aparece no **commit**, como `TransactionSystemException` (causa raiz `PSQLException` 23505), e não como `DataIntegrityViolationException` no flush. O `GlobalExceptionHandler` não trata esse caso, então ela viraria `500`. Hoje isso só aconteceria com bug na renumeração; o teste `deveRejeitarNumeroDeLinhaRepetidoNoCommitDaTransacao` documenta o comportamento. **Risco conhecido.**
- Os `CHECK`s de enum (V1 e V2) acoplam o schema aos enums do domínio: é intencional (o banco rejeita valores que o código não conhece), mas toda mudança de enum exige migration.
- O mês de competência é recalculado em cada `submit` (README: "um reenvio em outro mês muda a competência"). **Lacuna de teste:** nenhum teste de integração cobre o reenvio na virada do mês. O design já permite isso, porque o `Clock` é injetado: basta um `@TestConfiguration` com um `Clock` ajustável (`@Primary`). Fica como próximo passo.
