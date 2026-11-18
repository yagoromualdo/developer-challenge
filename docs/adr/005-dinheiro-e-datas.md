# ADR 005 — Representação de dinheiro e de datas

## Contexto

O README deixa a representação de valores monetários a critério do candidato. Os valores entram em somas (total = Σ quantidade × preço unitário), em comparações com limites exatos (1.000,00 e 10.000,00 definem a cadeia) e no saldo do orçamento. As datas aparecem em três papéis: instantes (criação, submissão, decisões, histórico em UTC), a data `neededBy` (que precisa ser futura) e o **mês de competência** que o orçamento usa. O sistema é de uma empresa no Brasil, mas o servidor roda em UTC.

## Opções consideradas

**Dinheiro**

1. `double`: erro de ponto flutuante (0,1 + 0,2 ≠ 0,3) em somas e nos limites.
2. Inteiro em centavos (`long`): exato, mas o JSON e o banco passam a ter outra unidade.
3. **`BigDecimal` com escala 2 + `NUMERIC(19,2)`**.

E no JSON: número ou **string**.

**Entrada com mais de 2 casas**: arredondar ou **rejeitar**.

**Datas**: `LocalDateTime` no fuso do servidor ou **`Instant`/`timestamptz` em UTC**, com o fuso de negócio aplicado só nas regras de calendário.

**Mês de competência**: calcular na aprovação final ou **gravar no `submit`**.

## Decisão

- **`Money`** (value object do domínio, `record` sobre `BigDecimal`): escala sempre 2, nunca negativo. Valores com mais de 2 casas decimais significativas são **rejeitados**, não arredondados (arredondar mudaria em silêncio o total que o solicitante informou); zeros à direita (`10.500`) não contam. Na borda, `@Digits(fraction = 2)` devolve `400` com o campo (`items[0].unitPrice`).
- **Banco em `NUMERIC(19,2)`**; **JSON como string** (`"4899.90"`), para que nenhum cliente JavaScript converta para `number` e perca precisão. O total é calculado no back; o valor enviado pelo cliente é ignorado.
- **Limites para o total caber em `NUMERIC(19,2)`** (`RequestLimits`): até 100 itens, quantidade ≤ 1.000.000 e `unitPrice` com até 9 dígitos inteiros.
- **Instantes em `Instant`/`timestamptz`**, com `hibernate.jdbc.time_zone=UTC`; respostas em ISO 8601 com fuso.
- **Um único `Clock`** (bean `Clock.systemUTC()`), injetado onde há "agora"; nada de `Instant.now()` direto.
- **Fuso de negócio `America/Sao_Paulo`** (`BusinessTime.ZONE`), usado só nas regras de calendário:
  - `neededBy` (`LocalDate`) precisa ser futura **em São Paulo**: o `@Future` usa o mesmo `Clock` com esse fuso (`ValidationConfig`), porque às 22h em São Paulo já é amanhã em UTC;
  - o **mês de competência** é calculado no `submit` (`BusinessTime.competenceOf`: 01/12 à 01h UTC ainda é novembro em São Paulo) e gravado na versão (`competence_month`, sempre o dia 1). A aprovação final consome o orçamento **desse** mês.
  - os filtros `createdFrom`/`createdTo` da listagem são dias de São Paulo convertidos para instantes.
- **Front:** o total exibido em tempo real no formulário é calculado em centavos com `BigInt` (100 itens × 1.000.000 × 999.999.999,99 passa de 2^53).

## Consequências

- Somas e comparações com os limites são exatas; `1000.00` e `1000.01` caem em cadeias diferentes, como o README exige (coberto por teste).
- O cliente precisa enviar e ler valores como string decimal; o front formata para exibição em pt-BR.
- Uma solicitação submetida no último dia do mês e aprovada no seguinte consome o orçamento do mês da submissão. Reenviar depois de uma devolução recalcula a competência (nova versão, novo `submit`).
- Se o orçamento do mês de competência não existir, o saldo é zero e a aprovação final recebe `422 BUDGET_EXCEEDED` (ADR 001).
- As regras de fuso têm testes unitários (`BusinessTimeTest`, `ValidationConfigTest`), mas os testes de integração ainda usam o relógio real e nenhum atravessa a virada do mês. Não é limitação do design: o `Clock` foi injetado justamente para isso, e um `@TestConfiguration` com um `Clock` ajustável permite testar o reenvio em outro mês (a v2 com a nova competência e o consumo no orçamento desse mês).
