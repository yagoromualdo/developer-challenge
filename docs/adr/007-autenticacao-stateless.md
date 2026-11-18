# ADR 007 — Autenticação stateless com JWT

## Contexto

A API precisa de login, de identificar o ator em toda requisição (papel, centro de custo e nível de aprovador alimentam a visibilidade e a `ActionPolicy`) e de rodar com mais de uma instância atrás de um balanceador sem afinidade de sessão. O front é uma SPA servida na mesma origem da API (nginx com `location /api/` no compose; `proxy.conf.json` em desenvolvimento).

## Opções consideradas

**Sessão**

1. **Sessão HTTP no servidor** (`JSESSIONID`): exige sessão compartilhada (Redis/JDBC) ou afinidade para escalar, e traz CSRF.
2. **Token opaco** guardado no banco: revogação fácil, mas uma consulta por requisição.
3. **JWT assinado**, validado sem estado no servidor.

**Implementação do JWT**

1. Biblioteca avulsa (jjwt) e um filtro próprio.
2. **Spring Security OAuth2 Resource Server** com Nimbus (`NimbusJwtEncoder`/`NimbusJwtDecoder`).

**Assinatura**: HS256 (segredo compartilhado) ou RS256 (par de chaves).

**Claims**: confiar nelas ou reler o usuário no banco a cada requisição.

**Token no front**: `localStorage`, `sessionStorage`, memória ou cookie `httpOnly`.

## Decisão

- **API stateless**: `SessionCreationPolicy.STATELESS`, sem `httpBasic`, `formLogin`, `logout` nem request cache; CSRF desligado porque não há cookie de sessão (o token vai no header `Authorization: Bearer`). Todo estado de negócio fica no Postgres.
- **OAuth2 Resource Server + Nimbus, HS256.** `POST /auth/login` emite o token (`JwtService`); o Resource Server o valida. O decoder tem o algoritmo fixo em HS256, então tokens com outro `alg` (inclusive `none`) são rejeitados. HS256 basta porque o mesmo serviço emite e valida; RS256 só se pagaria com outro serviço validando os tokens.
- **Segredo por variável de ambiente** (`JWT_SECRET`), validado na subida: pelo menos 32 bytes (256 bits, RFC 7518 §3.2). O compose e o `.env.example` trazem um valor claramente de desenvolvimento.
- **TTL de 15 min**, configurável (`JWT_TTL_MINUTES`). Sem refresh token.
- **Claims:** `sub` (id do usuário), `role`, `costCenterId`, `approverLevel`. Um `JwtAuthenticationConverter` mapeia `role` para `ROLE_*` (usado nas rotas exclusivas de `ADMIN`), e o `ActorArgumentResolver` monta o `record Actor` a partir das claims para os controllers. Claims com assinatura válida mas incoerentes → `401`, não `500`.
- **Confiar nas claims sem consultar o banco.** É o que mantém a validação sem estado e sem consulta extra por requisição. O preço é uma **janela de até 15 min**: um usuário rebaixado ou movido de centro de custo continua agindo com o perfil antigo até o token expirar; o perfil novo vale a partir do próximo login.
- **Senhas** com `PasswordEncoderFactories.createDelegatingPasswordEncoder()` (bcrypt, prefixo `{bcrypt}`), o que permite trocar o algoritmo no futuro sem migrar as senhas de uma vez. O login devolve a mesma mensagem para e-mail inexistente e senha errada (`401 INVALID_CREDENTIALS`).
- **401/403 em Problem Details**: o `ProblemDetailsSecurityHandler` (entry point e access denied, inclusive os do Resource Server) repassa ao `GlobalExceptionHandler`, então "sem token" e "token inválido/expirado" saem no mesmo formato, com `WWW-Authenticate: Bearer`.
- **`/auth/register`** cria apenas `SOLICITANTE`; os outros papéis só pelo `ADMIN` (`POST /users`).
- **Front:** o token e o `expiresAt` ficam em **`sessionStorage`** (os dados do usuário só em memória). Um interceptor põe o `Authorization`; outro trata o `401` encerrando a sessão e voltando ao login com `returnUrl`.

## Consequências

- Qualquer instância valida qualquer token: escalar horizontalmente é só subir mais réplicas com o mesmo `JWT_SECRET`.
- **Não há revogação** antes do TTL (logout no front só apaga o token local). Para revogar, a evolução seria uma denylist de `jti` ou um `token_version` no usuário, que reintroduz uma consulta por requisição.
- Mudanças de perfil feitas pelo `PATCH /users/:id` só valem no próximo login. Pelo mesmo `PATCH` não dá para tornar alguém `ADMIN` global (sem centro de custo), e nada impede o `ADMIN` de rebaixar a si mesmo ou o último `ADMIN` (limitações conhecidas da E12).
- `sessionStorage` sobrevive ao recarregar a página, mas não é compartilhado entre abas e é legível por JavaScript: um XSS rouba o token. O cookie `httpOnly` + `SameSite` (com CSRF de volta) fica registrado como evolução.
- Sem refresh token, o usuário faz login de novo a cada 15 min de uso; foi aceito pela simplicidade.
- Trocar o `JWT_SECRET` invalida todos os tokens emitidos (todos fazem login de novo).
- Rate limit no login não foi feito (diferencial X5).
