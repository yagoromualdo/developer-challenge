# ADR 003 — 403 × 404 para recurso fora da visibilidade e ordem de verificação

## Contexto

O README (3.2 e 4.3) pede para decidir entre `403` e `404` quando o usuário pede uma solicitação que existe mas está fora da visibilidade dele, e fixa a ordem de verificação das ações: autenticação (`401`) → existência/visibilidade (`404`, "ou `403`, conforme seu ADR") → papel permite o tipo de ação (`403`) → `lockVersion` (`409`) → regra de negócio (`422`).

As regras de visibilidade (2.2) são: o `SOLICITANTE` vê as próprias; o `APROVADOR`, também as do seu centro de custo; o `ADMIN`, todas; e um `DRAFT` só é visível para o dono.

Sobra uma segunda fronteira que o README não resolve: um usuário que **enxerga** a solicitação, tem um papel que permite o tipo de ação, mas não é o dono (por exemplo, um `ADMIN` tentando editar a solicitação de outra pessoa). Isso é `403` ou `422`?

## Opções consideradas

**Recurso fora da visibilidade**

1. **`403 Forbidden`**: diz que o recurso existe, mas o acesso é negado.
2. **`404 Not Found`**: trata o recurso fora da visibilidade como inexistente.

**Não-dono que enxerga a solicitação (editar, submeter, cancelar)**

1. **`403`**: "você não tem permissão".
2. **`404`**: esconder também de quem já vê a solicitação (incoerente: o `GET` devolve `200`).
3. **`422 NOT_REQUEST_OWNER`**: tratar a posse como regra de negócio.

## Decisão

### 404 para fora da visibilidade

- Recurso fora da visibilidade responde **`404`**, com a mesma mensagem de um id inexistente. Um `403` confirmaria que o id existe (por exemplo, o rascunho de um colega) e permitiria enumerar solicitações.
- A visibilidade é **uma única `Specification`** (`RequestVisibility.visibleTo(actor)`), combinada com o filtro de cada caso de uso: `withId` no detalhe, nas ações, no histórico e nas versões; os filtros na listagem; a contagem por status no dashboard; os candidatos da caixa de aprovação. Não há duas implementações da mesma regra.
- Histórico (`/history`) e versões (`/versions`, `/versions/:n`) seguem a visibilidade do detalhe: `DRAFT` alheio → `404`. Versão inexistente de uma solicitação visível também é `404`.
- Listagem filtrada por um centro de custo que o usuário não vê devolve página vazia, e não `403`.

### Ordem de verificação explícita no service

- O `RequestActionGuard` (extraído na E17 e usado por `PurchaseRequestService` e `ApprovalService`) aplica a ordem em código: visibilidade (`404`) → `ActionPolicy.roleAllows` (`403`) → `lockVersion` (`409`) → `ActionPolicy.check` (`422`). Por isso não há `@PreAuthorize` nos endpoints de solicitação e aprovação: ele rodaria antes da visibilidade e devolveria `403` para um id que deveria ser `404`.
- `@PreAuthorize`/regras no `SecurityFilterChain` só onde não há visibilidade a esconder: rotas exclusivas de `ADMIN` (`/users/**`, `POST /cost-centers`, `PUT /cost-centers/:id/budgets/:mes`).

### Fronteira 403 × 422: 403 só para papel

- **`403` fica reservado para "o papel não permite este tipo de ação"**, e sai de um único ponto: `ActionPolicy.roleAllows(role, action)`. Hoje isso significa: decisões de etapa (`APPROVE`, `REJECT`, `REQUEST_CHANGES`) só para `APROVADOR` e `ADMIN`. Exemplo: `SOLICITANTE` aprovando → `403`.
- **Todo o resto é `422` com `code` específico**, avaliado pela mesma `ActionPolicy` que monta o `allowedActions`: estado (`INVALID_TRANSITION`), posse (`NOT_REQUEST_OWNER`), centro de custo e nível (`APPROVER_NOT_ELIGIBLE`), segregação (`SELF_APPROVAL_FORBIDDEN`, `DUPLICATE_APPROVER`), comentário (`COMMENT_REQUIRED`), e o orçamento (`BUDGET_EXCEEDED`, no `UPDATE` condicional, ADR 001). Pelo mesmo critério, criar uma solicitação em outro centro de custo sem ser `ADMIN` é `422 COST_CENTER_NOT_ALLOWED`.

### `NOT_REQUEST_OWNER` (decisão da E8)

- Decidido na E8 (2026-11-14, commit `3876e93`), ao escrever a `ActionPolicy`: quem **vê** a solicitação mas não é o dono e tenta `EDIT`/`SUBMIT` (ou `CANCEL` sem ser `ADMIN`) recebe **`422 NOT_REQUEST_OWNER`**, e não `403`.
- Motivo: ser ou não o dono é uma regra sobre **aquela** solicitação, como a segregação de funções ("não aprove o próprio pedido"), e não sobre o papel. Se a posse desse `403`, a mesma `ActionPolicy` teria de devolver dois status diferentes, e o `403` deixaria de significar uma coisa só.
  - Exemplo: um `ADMIN` abre uma solicitação em `CHANGES_REQUESTED` de outro usuário. Ele a vê (`GET` → `200`, `allowedActions: ["CANCEL"]`), mas o `PUT` devolve `422 NOT_REQUEST_OWNER`.
  - Em `DRAFT`, o não-dono nem chega nessa regra: o rascunho está fora da visibilidade e a resposta é `404`.

### Caixa de aprovação

- `GET /approvals/inbox` para quem não pode aprovar (ex.: `SOLICITANTE`) devolve `200` com lista vazia, e não `403`: a caixa é a lista do que o usuário pode aprovar agora, filtrada pela mesma `ActionPolicy.isAllowed(..., APPROVE)`.

## Consequências

- Um usuário não distingue "não existe" de "não posso ver"; o suporte usa o `requestId` dos logs para investigar.
- O front pode tratar `403` de forma genérica ("seu papel não permite") e mostrar o `detail` do `422` como explicação; na prática, ele quase nunca vê um `403`, porque só exibe ações que estão no `allowedActions`.
- Como a checagem de estado vem antes da de dono na `ActionPolicy`, um não-dono que tenta editar uma solicitação `PENDING_APPROVAL` recebe `422 INVALID_TRANSITION`, e não `NOT_REQUEST_OWNER`.
- Pela ordem 4.3, um `lockVersion` desatualizado responde `409` antes de qualquer `422`, inclusive para o não-dono.
- A ordem existe em um só lugar (`RequestActionGuard`); um endpoint novo de ação que não passe por ele quebra o contrato.
- Limitação conhecida: o `GET /cost-centers/:id/budgets/:mes` é liberado para qualquer autenticado, sem filtro de centro de custo.
