# ADR 004 — Arquitetura do domínio: hexagonal pragmático, máquina de estados e políticas

## Contexto

O núcleo do sistema são regras que mudam juntas e precisam ser testadas exaustivamente: a tabela de transições (README 4.2), a montagem da cadeia por valor e categoria, a elegibilidade dos aprovadores e a segregação de funções. As mesmas regras alimentam três saídas: o `422` de uma ação, o `allowedActions` do detalhe (que o front usa para mostrar os botões) e a caixa de aprovação. Se cada saída tiver sua própria lógica, elas divergem.

## Opções consideradas

**Estilo**

1. Camadas clássicas (controller → service → repository) com as regras nos services e nas entidades JPA.
2. **Hexagonal pragmático**: domínio em Java puro, com aplicação, infraestrutura e web ao redor, sem portas/adaptadores para tudo.
3. Hexagonal "completo", com interfaces de porta para cada repositório.

**Organização de pacotes**

1. Por camada (`controllers/`, `services/`, `repositories/`).
2. **Por feature**, com `domain/application/infra/web` dentro de cada uma.

**Máquina de estados**

1. `if`/`switch` espalhados pelos services.
2. Spring Statemachine.
3. **Tabela de transições** (enums + mapa) com os guards fora dela.

**Cadeia e elegibilidade**

1. Um método grande com os `if`s de limite e categoria.
2. **Strategy** para a cadeia (`ChainRule`) e **Policy** para a elegibilidade (`ApprovalRule`).

## Decisão

- **Hexagonal pragmático, pacotes por feature** (`request/`, `budget/`, `costcenter/`, `user/`, `dashboard/`, `seed/`, `shared/`). O `..domain..` não importa Spring, JPA, nem `application`/`infra`/`web`; isso é garantido por teste ArchUnit (`DomainIsolationArchTest`), com exceções explícitas para os tipos de `shared` de que o domínio precisa (ex.: `Actor`, `BusinessRuleException`). Não há interfaces de porta: os services de aplicação usam os repositórios Spring Data diretamente e fazem o mapeamento entidade ↔ domínio (`RequestSnapshotMapper`).
- **Máquina de estados table-driven** (`RequestStateMachine`): um `EnumMap` `(status, ação) → destino` espelha a 4.2; estados finais não têm entradas. A tabela responde só "é possível e para onde leva"; o destino do `APPROVE` depende de a etapa ser a última (`Destination.byStep`). `EDIT` entra na tabela (não muda o estado) porque a 4.1 só permite editar em `DRAFT` e `CHANGES_REQUESTED`.
- **Cadeia por Strategy**: `ApprovalChainBuilder` aplica as `ChainRule`s (`ThresholdRule`: até 1.000,00 → `NIVEL_1`; até 10.000,00 → + `NIVEL_2`; acima → + `ADMIN`; `SoftwareRequiresAdminRule`: `SOFTWARE` exige `ADMIN`). A etapa `ADMIN` nunca é duplicada (também garantido por `uq_approval_step_level` no banco).
- **Elegibilidade por Policy**: `ApprovalRule`s (`NotOwnerRule`, `SameCostCenterRule`, `SufficientLevelRule`, `NoOtherStepApprovedRule`), cada uma devolvendo uma violação com `code` e motivo em pt-BR. A ordem da lista define o motivo exibido quando mais de uma falha.
- **Uma única avaliação**: a `ActionPolicy` (uma instância, `RequestPolicyConfig`) é usada para o `422` (`check`), para o `allowedActions` (`allowedActions`) e para a caixa de aprovação (`isAllowed`). O papel é checado à parte (`roleAllows`) só para produzir o `403` na posição certa (ADR 003).
- **O ator chega ao domínio como `record Actor`**, montado das claims do JWT (ADR 007); o domínio nunca acessa o `SecurityContext`.
- **Aprovações na feature `request`**: `ApprovalService`, `ApprovalInboxService` e o `RequestActionGuard` ficam em `request.application`, porque precisam da infraestrutura de `request` (entidades, Specification de visibilidade) e uma feature não importa a `infra` de outra. O `dashboard/` só compõe services de `request`, `budget` e `costcenter`.

## Consequências

- As regras são testadas sem Spring e sem banco: `@ParameterizedTest` cobre todas as combinações status × ação, os limites 1000.00/1000.01/10000.00/10000.01 e `SOFTWARE`, e cada violação da política.
- Uma regra nova de cadeia ou de elegibilidade é uma classe nova na lista, sem mexer nas outras.
- Front e back não divergem sobre o que é permitido: o front só mostra o que está no `allowedActions`.
- O orçamento fica fora da `ActionPolicy` (é decidido no `UPDATE` condicional, ADR 001), então o `allowedActions` não antecipa um `BUDGET_EXCEEDED`.
- Sem portas, trocar o Spring Data exigiria mexer nos services de aplicação; foi um custo aceito para não criar abstrações sem uso.
- A caixa de aprovação filtra em memória os candidatos pela `ActionPolicy` (sem paginação); é aceitável nesta escala e evita duplicar a regra em SQL.
