# JobMaster Backend

Spring Boot backend that tailors your CV to a job description and writes cover
letters, using your stored master profile as the single source of truth. Both
render as one-page PDFs in the same visual format as your original Word CV.

## How it stays vendor-neutral

All AI calls go through Spring AI's `ChatClient` ([AiConfig.java](src/main/java/com/example/jobmaster/config/AiConfig.java)).
No application code imports an OpenAI or Anthropic SDK. The active vendor is
picked by configuration only:

| Env var | Meaning | Default |
|---|---|---|
| `AI_PROVIDER` | `openai` or `anthropic` | `openai` |
| `OPENAI_API_KEY` / `OPENAI_MODEL` | OpenAI credentials/model | model: `gpt-4o-mini` |
| `ANTHROPIC_API_KEY` / `ANTHROPIC_MODEL` | Anthropic credentials/model | model: `claude-sonnet-4-5` |

To add another vendor later (Google, Mistral, Ollama, ...): add its
`spring-ai-starter-model-*` dependency to `build.gradle`, set its properties in
`application.properties`, and switch `AI_PROVIDER`. Nothing else changes.

## Run it

```bash
# Real AI (uses your OpenAI credits)
OPENAI_API_KEY=sk-... ./gradlew bootRun

# Switch vendor — same code, different config
AI_PROVIDER=anthropic ANTHROPIC_API_KEY=sk-ant-... ./gradlew bootRun

# Mock AI — full API works (echoes your profile back), no key, no credits spent
./gradlew bootRun --args='--spring.profiles.active=mock'
```

Requires JDK 21 (Gradle toolchain). Server: `http://localhost:8080`.

**Storage: PostgreSQL** (default) — connection via `DB_HOST`/`DB_PORT`/
`DB_NAME`/`DB_USER`/`DB_PASSWORD` env vars (defaults: localhost:5432/jobmaster,
postgres/password). In the Docker stack these are wired automatically (see
[../compose.yaml](../compose.yaml)). Every generated CV and cover letter is
saved with its job title, company, and job description, so you can always look
up which CV you used for which application. If Postgres isn't running, fall
back to an embedded file DB with `--spring.profiles.active=h2`.

**Docker:** the [Dockerfile](Dockerfile) builds a slim JRE-21 image; run the
whole stack from the project root with `docker compose up -d --build`
(configuration reference in the [root README](../README.md)).

## Workflow / API

1. **Store your profile once** — `PUT /api/profile`. [requests.http](requests.http)
   contains an example profile — replace it with your own data (or use the Profile tab).
   The AI is instructed to never invent anything beyond it.
2. **Per job application:**
   - `POST /api/cv` `{jobDescription, jobTitle?, company?, extraInstructions?}`
     → CV tailored to the job (structured JSON + Markdown), with
     `keywordsMatched`/`keywordsAdded` and `tailoringNotes`. Prompt-budgeted
     to fit one page.
   - `POST /api/cover-letter` `{jobDescription, ..., tone?, cvGenerationId?}`
     → one-page cover letter; pass the `id` from `/api/cv` as
     `cvGenerationId` so the letter builds on the tailored CV.
   - `GET /api/generations/{id}/pdf` → **download as PDF**, ready to upload
     to job portals. CVs use your Word template's layout (Calibri-metric
     Carlito font, blue section rules, underlined keywords); cover letters get
     a matching letterhead.
3. **History** — `GET /api/generations` (newest first), `GET /api/generations/{id}`.

Other endpoints: `GET /api/profile`, `GET /api/health`.

## Layout

```
src/main/java/com/example/jobmaster/
├── api/        REST controllers + error handling
├── config/     ChatClient bean, mock AI provider, CORS
├── domain/     JPA entities (profile document, generation history)
├── dto/        Request/response records incl. TailoredCv (the AI's output contract)
├── repository/ Spring Data repositories
└── service/    CV tailoring, cover letter, prompts, Markdown + PDF rendering
src/main/resources/fonts/   Carlito (metric-compatible Calibri, SIL OFL license)
```

`sample_output_cv.pdf` / `sample_output_cover_letter.pdf` show the rendered
output (generated with the mock provider echoing the profile).
