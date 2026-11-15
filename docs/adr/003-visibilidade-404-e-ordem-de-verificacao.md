# ADR 003 — 403 × 404 para recurso fora da visibilidade e ordem de verificação

> **Rascunho gerado na E13.**

## Contexto

O README (3.2 e 4.3) pede para decidir entre `403` e `404` quando o usuário pede uma solicitação que existe mas está fora da visibilidade dele. As regras de visibilidade (2.2) são: o `SOLICITANTE` vê as próprias; o `APROVADOR`, também as do seu centro de custo; o `ADMIN`, todas; e um `DRAFT` só é visível para o dono.

## Opções consideradas

1. **`403 Forbidden`**: diz que o recurso existe, mas o acesso é negado.
2. **`404 Not Found`**: trata o recurso fora da visibilidade como inexistente.

## Decisão

- Recurso fora da visibilidade responde **`404`**, com a mesma mensagem de um id inexistente. Um `403` confirmaria que o id existe (por exemplo, o rascunho de um colega) e permitiria enumerar solicitações.
- A visibilidade é **uma única `Specification`** (`RequestVisibility.visibleTo(actor)`), combinada com o filtro de cada caso de uso (`withId` no detalhe e na edição; os filtros da listagem, do histórico, das versões e do dashboard nas próximas etapas). Assim não há duas implementações da mesma regra.
- A ordem de verificação é aplicada explicitamente no service: autenticação (`401`) → visibilidade (`404`) → papel permite o tipo de ação (`403`) → `lockVersion` (`409`) → regra de negócio (`422`). Por isso não há `@PreAuthorize` nos endpoints de solicitação.
- **Não-dono que enxerga a solicitação** e tenta editar, submeter ou cancelar (sem ser `ADMIN`) recebe **`422 NOT_REQUEST_OWNER`**, e não `403`. O `403` fica reservado para "o papel não permite este tipo de ação" (`ActionPolicy.roleAllows`, ex.: `SOLICITANTE` aprovando). Ser ou não o dono é regra de negócio, como a segregação de funções, e sai da mesma `ActionPolicy` que monta o `allowedActions`.
  - Exemplo: um `ADMIN` abre uma solicitação em `CHANGES_REQUESTED` de outro usuário. Ele a vê (`GET` → `200`, `allowedActions: ["CANCEL"]`), mas o `PUT` devolve `422 NOT_REQUEST_OWNER`.
  - Em `DRAFT`, o não-dono nem chega nessa regra: o rascunho está fora da visibilidade e a resposta é `404`.

## Consequências

- Um usuário não distingue "não existe" de "não posso ver"; o suporte usa o `requestId` dos logs para investigar.
- Como a checagem de estado vem antes da de dono na `ActionPolicy`, um não-dono que tenta editar uma solicitação `PENDING_APPROVAL` recebe `422 INVALID_TRANSITION`, e não `NOT_REQUEST_OWNER`.
- Pela ordem 4.3, um `lockVersion` desatualizado responde `409` antes de qualquer `422`, inclusive para o não-dono.
