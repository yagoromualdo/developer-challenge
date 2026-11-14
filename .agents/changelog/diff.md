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
