# JobMaster

Personal job-hunt assistant: stores your master profile, tailors your CV to any
job description, writes cover letters, and hands you one-page PDFs in your own
CV format — with every generated document tracked per job in PostgreSQL.

---

## Quick start (Docker)

```bash
cp .env.example .env             # all config lives here; defaults = mock mode
docker compose up -d --build     # first time only (builds the images)
```

Open **http://localhost:5173**. After the first run, the whole stack shows up
in **Docker Desktop as the "jobmaster" stack** — start/stop it with one click.

Out of the box it runs in **mock mode** (no API key needed, canned AI answers,
zero credits spent) so you can click around immediately.

### Going live with real AI

1. Edit [.env](.env): paste your key and clear the mock profile:
   ```properties
   OPENAI_API_KEY=sk-...
   SPRING_PROFILES_ACTIVE=
   ```
2. Apply it: `docker compose up -d`
   *(plain Docker Desktop restart is NOT enough after editing `.env` — compose
   must recreate the container for new variables to take effect)*

The header chip in the UI always shows which provider/model is actually active.

---

## Configuration reference — [.env](.env)

Every knob lives in `.env` at the project root. After any change, run
`docker compose up -d`.

| Variable | Default | Meaning |
|---|---|---|
| `AI_PROVIDER` | `openai` | Which AI vendor to use: `openai` or `anthropic`. |
| `OPENAI_API_KEY` | *(empty)* | Your OpenAI key (needed when `AI_PROVIDER=openai` and not in mock mode). |
| `OPENAI_MODEL` | `gpt-4o-mini` | Any OpenAI chat model, e.g. `gpt-4o`, `gpt-4.1`. |
| `ANTHROPIC_API_KEY` | *(empty)* | Your Anthropic key (for `AI_PROVIDER=anthropic`). |
| `ANTHROPIC_MODEL` | `claude-sonnet-4-5` | Any Anthropic model. |
| `SPRING_PROFILES_ACTIVE` | `mock` | `mock` = fake AI, no key, no cost. Empty = real AI. |
| `DB_NAME` / `DB_USER` / `DB_PASSWORD` | `jobmaster` / `postgres` / `password` | Postgres credentials (used by both the DB and backend containers). |
| `FRONTEND_PORT` | `5173` | Host port for the web UI. |
| `BACKEND_PORT` | `8080` | Host port for the REST API. |
| `POSTGRES_HOST_PORT` | `5433` | Host port for Postgres (5433 so it never clashes with a local Postgres on 5432). |

### Switching AI vendor

```properties
# .env — that's the whole change:
AI_PROVIDER=anthropic
ANTHROPIC_API_KEY=sk-ant-...
```

No code changes — the backend talks to an abstract `ChatClient` (Spring AI).
To add a *new* vendor (Google, Mistral, Ollama, …): add its
`spring-ai-starter-model-*` dependency in [Backend/build.gradle](Backend/build.gradle),
add its `spring.ai.<vendor>...` properties in
[application.properties](Backend/src/main/resources/application.properties),
then set `AI_PROVIDER=<vendor>`.

---

## Your data

- **Master profile** — edit it in the UI (**Profile** tab, "Edit as JSON") or
  re-PUT it from [Backend/requests.http](Backend/requests.http). It is the single
  source of truth; the AI never invents anything beyond it.
- **Generation history** — every CV/cover letter is stored with the job title,
  company, and full job description it was generated for. Browse it in the
  **History** tab; download any of them as PDF again at any time.
- **Where it lives** — the Docker named volume `jobmaster_pgdata`. It survives
  restarts and rebuilds. Full reset: `docker compose down -v` (deletes everything).
- **Direct DB access** — `psql -h localhost -p 5433 -U postgres jobmaster`
  (password from `.env`).

The old manually-started `postgres-dev` container is **not** used by the Docker
stack (it has its own Postgres); you only need it for non-Docker dev mode below.

---

## Changing how documents look / read

| What | Where |
|---|---|
| CV PDF layout (fonts, colors, sections — currently your Word template) | [Backend/.../PdfService.java](Backend/src/main/java/com/example/jobmaster/service/PdfService.java) |
| CV tailoring rules & one-page budget (the AI prompt) | [Backend/.../CvTailoringService.java](Backend/src/main/java/com/example/jobmaster/service/CvTailoringService.java) |
| Cover letter rules, length, tone handling | [Backend/.../CoverLetterService.java](Backend/src/main/java/com/example/jobmaster/service/CoverLetterService.java) |
| Fonts embedded in the PDFs | `Backend/src/main/resources/fonts/` (Carlito = metric-compatible Calibri) |
| Web UI styling | `frontend/src/App.css`, `frontend/src/index.css` |

After backend changes: `docker compose up -d --build backend`.
After frontend changes: `docker compose up -d --build frontend`.

---

## API (backend, port 8080 — also proxied at `<frontend>/api`)

| Endpoint | Purpose |
|---|---|
| `GET /api/health` | Status + active provider/model + profile configured? |
| `GET` / `PUT /api/profile` | Read / replace the master profile |
| `POST /api/cv` | Tailor CV `{jobDescription, jobTitle?, company?, location?, applicationId?, extraInstructions?}` — `location` overrides the profile location on this document; without `applicationId` a board application is auto-created. Response includes ATS results: `atsScore` (0-100 keyword-match estimate vs. this JD, prompt targets 85+ via verbatim JD wording + acronym/spelled-out pairs) and `keywordsMissing` (JD must-haves the profile honestly can't cover — never faked away) |
| `POST /api/cover-letter` | Write letter `{jobDescription, ..., tone?, location?, cvGenerationId?, applicationId?}` — inherits the CV's application via `cvGenerationId` |
| `GET /api/generations` | Document history (newest first) |
| `GET /api/generations/{id}` | One generation with full content |
| `GET /api/generations/{id}/pdf` | Download as one-page PDF (keeps the location used at generation time) |
| `POST` / `GET /api/applications` | Add job to the board / list all with documents |
| `GET` / `PATCH` / `DELETE /api/applications/{id}` | Read / partial-update (status, notes, salary, jobUrl, contactPerson, appliedAt, …) / delete incl. documents |
| `GET /api/usage` | Token & cost report: totals + per-month + per-model breakdown |
| `GET/POST /api/watch/companies`, `PATCH/DELETE /api/watch/companies/{id}` | Manage watched career portals |
| `POST /api/watch/run`, `POST /api/watch/companies/{id}/run`, `GET /api/watch/runs` | Trigger checks now / run log |
| `GET /api/jobs?status=NEW\|ALL\|…`, `POST /api/jobs/{id}/dismiss`, `POST /api/jobs/{id}/convert`, `POST /api/jobs/mark-seen` | Discovered postings feed & actions |

**Board:** the UI's landing tab is a Kanban board (Saved → Applied → Interviewing
→ Offer → Rejected). Drag cards between columns, click a card to edit details
and download its documents, or hit "Generate documents" to tailor a CV/letter
attached to that job. Moving a card to *Applied* stamps the applied date.

**Job watcher (Jobs tab):** watched company portals are checked daily at 11:00
Europe/Berlin (override via `jobmaster.watcher.cron/zone`; disable with
`WATCHER_ENABLED=false`), plus a catch-up run on startup when the last
successful check is older than ~20h. A company's first check silently imports
existing postings as a baseline; afterwards only genuinely new jobs appear as
NEW, ready to dismiss or convert into a board application in one click.
Watched portals (13): SAP (SuccessFactors), Delivery Hero, Bosch, Sixt,
Scalable Capital (SmartRecruiters), EGYM (Personio), and Celonis, N26,
HelloFresh, GetYourGuide, Wolt, SumUp, Adyen (Greenhouse). Adding a company =
"+ Watch company" in the UI — pick the platform, paste its config (examples
inline), set title/location filters. **Beginner step-by-step guide (how to
identify a company's ATS, find its identifier, verify, troubleshoot):
[docs/adding-companies.md](docs/adding-companies.md).** Architecture and the
onboarding runbook for new portals:
[docs/job-watcher-plan.md](docs/job-watcher-plan.md).

**Usage & cost:** every generation stores the provider-reported token counts and
a USD cost computed from per-model prices in
[application.properties](Backend/src/main/resources/application.properties)
(`jobmaster.pricing.models.*`, USD per 1M tokens — update there when vendors
change prices; cost is snapshotted per document so history stays accurate).
The **Usage** tab shows spend this month, total, and a per-month/per-model
breakdown; individual costs appear in History and on each board card's documents.

---

## Dev mode (without Docker)

```bash
# Postgres: either the compose one (already on :5433 → set DB_PORT=5433)
# or any local Postgres with a "jobmaster" database.

# Backend (hot reload via Spring devtools)
cd Backend
OPENAI_API_KEY=sk-... ./gradlew bootRun                                   # real AI
./gradlew bootRun --args='--spring.profiles.active=mock'                  # mock AI
./gradlew bootRun --args='--spring.profiles.active=h2'                    # no Postgres needed

# Frontend (hot reload, proxies /api to :8080)
cd frontend && npm run dev
```

Backend details (architecture, entities, mock provider): [Backend/README.md](Backend/README.md).
