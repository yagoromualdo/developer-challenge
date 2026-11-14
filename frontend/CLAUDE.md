# CLAUDE.md — Front-end (Angular)

Instruções para o Claude Code ao trabalhar em `frontend/`. Elas **complementam** o `AGENTS.md` e o `CLAUDE.md` da raiz do repositório; em caso de conflito, **os arquivos da raiz prevalecem**.

O plano de execução está em `docs/plano-execucao.md`. Trabalhe **somente na etapa que o usuário pedir**.

---

## 1. Regras do repositório (eliminatórias — nunca viole)

- **Nunca modifique nem remova:** `README.md` (raiz), `.gitignore` e `.gitattributes` da raiz, `AGENTS.md`, `CLAUDE.md` (raiz), `GEMINI.md`, `.github/copilot-instructions.md`, `.claude/settings.json`, `.cursor/`, `.githooks/`, `setup.sh`, `.agents/`.
- **`.agents/changelog/diff.md` é append-only:** nunca edite nem apague entradas. Não interfira no registro de IA.
- **Não instale husky, lefthook, lint-staged com hooks, simple-git-hooks nem nada que altere hooks do Git.** Lint e formatação rodam como script do npm e no CI.
- **Nunca use** `--no-verify`, nunca altere `core.hooksPath` nem a configuração do Git.
- Ao gerar o projeto, use `ng new ... --skip-git`. Use `frontend/.gitignore` próprio.
- **Não escreva o `AI_USAGE.md`**: ele é escrito pelo usuário.

## 2. Fluxo de trabalho por etapa (obrigatório)

1. Leia a etapa pedida em `docs/plano-execucao.md` e confirme o escopo em 2–3 linhas antes de codar.
2. Implemente **apenas** o escopo da etapa. Para desviar de uma decisão registrada (seção 4) ou adicionar uma dependência não prevista, **pare e pergunte**.
3. Ao terminar, rode `npm run lint`, `npm test` e `npm run build` e garanta que estão verdes.
4. **Pare** e apresente:
   - resumo do que foi feito e por quê;
   - lista de arquivos criados/alterados;
   - como testar manualmente na tela;
   - pontos que merecem revisão humana;
   - **sugestão de commit** em Conventional Commits (pt-BR na descrição) e o comando `git add` dos arquivos da etapa (o hook `pre-commit` inclui o `.agents/changelog/diff.md` automaticamente; não o edite).
5. Antes de encerrar o turno, rode `bash .agents/hooks/ai-log.sh record ClaudeCode "<one-line summary in English>"` (exigido pelo `AGENTS.md`; as entradas do registro são **sempre em inglês**).
6. **Não execute** `git commit`, `git push`, `git rebase`, `git reset` nem nada que altere o histórico. **O usuário commita manualmente.**

## 3. Stack e versões

| Item | Versão / escolha |
|---|---|
| Angular | 22 (standalone, zoneless, padrões do `ng new`) |
| UI | Angular Material (mesma major do Angular) |
| Formulários | Reactive Forms (`FormArray` para itens) |
| Estado | services por feature + signals; RxJS apenas no `HttpClient` |
| Testes | Vitest (padrão do `ng new`) + TestBed |
| Lint | angular-eslint; Prettier via script |
| Node | LTS compatível com o Angular 22 (no Docker: imagem `node` LTS) |
| Servidor (Docker) | nginx alpine |

**Não use:** NgModules, NgRx, Signal Forms, Karma/Jasmine, Jest, PrimeNG, Bootstrap, bibliotecas de estado externas.

## 4. Decisões de arquitetura (siga sem desviar)

### 4.1 Estrutura

```
src/app/
├── core/
│   ├── auth/           # AuthService (token em sessionStorage), modelos de sessão
│   ├── http/           # interceptors, parser de Problem Details (ApiProblem)
│   └── guards/         # authGuard, roleGuard
├── shared/
│   └── components/     # comment-dialog, problem-alert, status-badge, money pipe...
└── features/
    ├── auth/           # login
    ├── dashboard/
    ├── requests/       # list, form (create/edit), detail
    └── approvals/      # inbox
```

- Rotas das features com lazy loading (`loadComponent`/`loadChildren`).
- Componentes standalone, `inject()`, control flow `@if`/`@for`, `ChangeDetectionStrategy.OnPush`.

### 4.2 Princípio central: o front não reimplementa regras
- Botões de ação são renderizados **exclusivamente** a partir de `allowedActions` da resposta de detalhe (`SUBMIT`, `APPROVE`, `REJECT`, `REQUEST_CHANGES`, `CANCEL`, `EDIT`). Nunca derive permissão de papel/status no front.
- O total exibido no formulário é **só exibição**; quem calcula é o back.
- Mensagens de 422 exibem o `detail` vindo da API. Não reescreva a regra no front (no máximo, um título amigável por `code`).
- Guards (`authGuard`, `roleGuard` via `route.data.roles`) são **apenas UX de navegação**. Quem decide é o back.

### 4.3 Estado e dados
- Signals para estado de tela (`loading`, `error`, `data`).
- **A URL é a fonte da verdade da listagem:** filtros, ordenação e paginação ficam em query params; o componente lê os params e busca no servidor. Mudar filtro = navegar com novos params (link compartilhável).
- **Depois de qualquer ação** (submit, approve, reject, request-changes, cancel, edit), recarregue o detalhe: `lockVersion`, `allowedActions` e etapas mudam. Sem cache no cliente.
- Toda ação de transição envia o `lockVersion` atual no corpo.

### 4.4 HTTP, sessão e erros
- **Comunicação:** URL relativa `/api/...`. No dev, `proxy.conf.json` encaminha `/api` → `http://localhost:8080` removendo o prefixo; no Docker, o nginx faz o mesmo. Sem CORS.
- **Token:** `sessionStorage`. Dados do usuário via `GET /auth/me` após o login.
- **Interceptors (funcionais):**
  1. `authInterceptor`: adiciona `Authorization: Bearer`.
  2. `requestIdInterceptor`: envia `X-Request-Id` (UUID) em cada requisição.
  3. `errorInterceptor`: converte respostas `application/problem+json` em `ApiProblem` tipado; **401** → limpa a sessão e navega para `/login?returnUrl=<rota atual>`; rede/5xx → mensagem genérica com o `requestId`.
- **Tratamento contextual (nos componentes):**
  - **400:** mapear `errors[]` para os controles (`items[0].quantity` → controle do `FormArray`) via `setErrors({ server: mensagem })`.
  - **409:** "Esta solicitação foi alterada por outra pessoa. Os dados foram atualizados." e recarregar o detalhe. No `POST` com `Idempotency-Key` reutilizada com payload diferente, mensagem própria.
  - **422:** exibir o `detail` da API num alerta próximo da ação.
- Após o login, redirecionar para `returnUrl` (validar que é rota interna).

### 4.5 Telas obrigatórias
- **Login.**
- **Dashboard:** totais por status no escopo do papel; orçamento do mês corrente por centro de custo (orçado, consumido, disponível).
- **Listagem:** filtros (`status`, `category`, `costCenterId`, `createdFrom`, `createdTo`), ordenação e paginação no servidor, refletidos na URL.
- **Caixa de aprovação** (`/approvals/inbox`).
- **Formulário de criação/edição:** itens dinâmicos (`FormArray`), total em tempo real, validação local básica + erros do servidor por campo. Gerar **um `Idempotency-Key` (UUID) por instância do formulário de criação** e reutilizá-lo em reenvios do mesmo formulário (duplo clique, retry); botão desabilitado enquanto envia.
- **Detalhe:** dados da versão atual, lista de versões (consultar versões anteriores), cadeia de aprovação (etapa, quem aprovou e quando), linha do tempo do histórico, ações via `allowedActions`.
- **Modal de comentário** obrigatório para rejeitar e para devolver (mínimo de 10 caracteres na validação local; o back valida também).
- Estados de carregando, vazio e erro em todas as telas.
- Telas de administração **não** fazem parte do escopo obrigatório.

### 4.6 Valores e datas
- Valores monetários chegam como **string**; exiba formatados em BRL (pipe próprio ou `CurrencyPipe` com `pt-BR`). Envie `unitPrice` como string com no máximo 2 casas decimais.
- Datas em ISO 8601; exiba no fuso local com `DatePipe` (`pt-BR`).

### 4.7 Acessibilidade (baixo custo)
- `mat-label` em todos os campos, botões com texto ou `aria-label`, foco devolvido após fechar modal, navegação por teclado funcionando.

## 5. Convenções de código

- Identificadores em inglês; textos da interface em pt-BR.
- Tipos explícitos para DTOs da API em `features/*/models` ou `core/http/models`.
- Sem `any`. Sem lógica de negócio em template.
- Componentes pequenos; lógica de chamada HTTP em services.

## 6. Testes (mínimo obrigatório: 3 significativos)

1. **Formulário:** adicionar/remover itens recalcula o total exibido; erros do servidor (400) aparecem no campo certo.
2. **Ações a partir de `allowedActions`:** dado um detalhe com `["APPROVE","REJECT"]`, apenas esses botões aparecem; com `[]`, nenhum.
3. **Tratamento de 409/422:** 409 mostra a mensagem de conflito e recarrega; 422 exibe o `detail` da API.

Teste comportamento observável (o que o usuário vê), não detalhes de implementação. Mocke o `HttpClient` com `provideHttpClientTesting`.

## 7. Comandos

```bash
npm ci               # instala dependências
npm start            # ng serve com proxy.conf.json
npm test             # Vitest, modo não interativo no CI
npm run lint         # angular-eslint
npm run format       # Prettier (escreve)
npm run format:check # Prettier (só verifica; usado no CI)
npm run build        # build de produção
```
