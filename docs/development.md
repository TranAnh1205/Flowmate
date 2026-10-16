# Flowmate developer guide

Run these commands from the repository root. Start with the [repository README](../README.md) for the product overview, architecture, and Docker quick start.

## Modules

| Path | Responsibility |
| --- | --- |
| `frontend/` | React/TypeScript UI, Vite development server, production Nginx image |
| `gateway-service/` | API routing, CORS, JWT validation, Redis-backed rate limits |
| `services/auth-service/` | Accounts, sessions, administrator actions, audit, JWKS |
| `services/task-service/` | Personal tasks, team projects, boards, cards, and reminder outbox |
| `services/ai-service/` | AI endpoints, quotas, and local fallback |
| `services/notification-service/` | Reminder scheduling and in-app notifications |
| `scripts/` | Local verification, API E2E, and UI smoke checks |

The Maven reactor is defined in `pom.xml`. Each backend service has its own `pom.xml`, Dockerfile, `src/main`, and `src/test`; services that own data have Flyway migrations under `src/main/resources/db/migration`.

## Run locally with Docker Compose

```bash
cp .env.example .env
# Edit .env: replace POSTGRES_PASSWORD and INTERNAL_SERVICE_KEY placeholders.
docker compose up --build -d
docker compose ps
```

On Windows PowerShell, `Copy-Item .env.example .env` is equivalent to the first command. Keep `.env` out of Git. The application opens at <http://localhost:3000> and the API Gateway at <http://localhost:8090>.

Set `AI_ENABLED=false` to use the local parser without an external API key. To bootstrap an administrator, temporarily enable `BOOTSTRAP_ADMIN_ENABLED` and set a strong `BOOTSTRAP_ADMIN_PASSWORD`; disable bootstrap after account creation.

`docker compose down` stops the stack and retains volumes. `docker compose down -v` deletes local database and key volumes and should only be used for an intentional reset.

## Build and test

From the repository root:

```bash
mvn clean test
cd frontend
npm ci
npm run build
```

The frontend development server uses `npm run dev` in `frontend/` and expects the Gateway at <http://localhost:8090>. Running backend services outside Docker requires PostgreSQL, Redis for the Gateway, and matching environment variables; Compose is the supported full-stack local setup.

For a Windows verification run:

```powershell
.\scripts\verify.ps1
```

The script runs Maven tests, builds the frontend, runs `npm audit`, and validates Compose configuration. `-WithDocker` additionally builds and starts containers and runs `scripts/e2e.mjs`; it requires a configured `.env` and changes local test data.

With an already-running Compose stack, run API checks from the repository root:

```bash
node scripts/e2e.mjs
node scripts/board-e2e.mjs
```

Admin-specific E2E checks are enabled by setting `ADMIN_EMAIL` and `ADMIN_PASSWORD` in the process environment. Do not put those credentials in scripts or shell history. UI smoke scripts (`ui-smoke.mjs`, `team-ui-smoke.mjs`, `board-ui-smoke.mjs`) require Microsoft Edge and a separate Playwright installation exposed through `CODEX_NODE_MODULES`. The mock team UI smoke test does not verify the real backend.

See [testing documentation](testing/testing.md) for test scope and [deployment notes](deployment/production.md) for deployment guidance.
