# Job Watcher — Architecture & Implementation Plan

Goal: every day at 11:00 (Europe/Berlin), check watched companies' career
portals, detect newly posted jobs, and surface them so an application can be
started immediately.

## Architecture decision: module inside the existing backend, NOT a microservice

The watcher becomes a new `watcher` package in the existing Spring Boot app,
with its own tables in the same Postgres. Reasons:

1. **The value is in the integration.** A new posting should become a board
   card in one click, get AI match-scored against the stored profile, and have
   its cost land in the Usage tab. All of that lives in this backend. A separate
   service would spend most of its code calling back into this one.
2. **No scaling or isolation problem exists.** Single user, 3 HTTP fetches per
   day. Microservices pay off for independent scaling, independent deployment,
   or team boundaries — none apply here. The cost (second image, service-to-
   service API, shared-DB coupling or data duplication) buys nothing.
3. **Fault isolation is achievable in-process.** The scheduled run executes on
   a separate thread; each company's fetch is wrapped in its own try/catch with
   HTTP timeouts. A broken scraper marks that company's run as failed — it
   cannot take down CV generation.
4. **Clean module boundary anyway.** The watcher talks to the rest of the app
   only through `ApplicationService` (convert posting → board card) and
   `ChatClient` (match scoring). If it ever needs to run 24/7 on a VPS while
   the laptop sleeps, it can be extracted — but the better first answer to
   "laptop was asleep at 11:00" is a startup catch-up run (below), and the
   better long-term answer is hosting the whole stack on a small VM.

**Revisit trigger:** only if the watcher must run on different infrastructure
than the app (always-on cloud vs. laptop). Until then, a module wins.

## Recon results (2026-07-04)

| Company | ATS | Endpoint | Reliability |
|---|---|---|---|
| Delivery Hero | SmartRecruiters | `GET api.smartrecruiters.com/v1/companies/DeliveryHero/postings` (official, paginated JSON, 1115 postings) | High — documented public API |
| EGYM | Personio | `GET egym.jobs.personio.de/xml` (official XML feed: id, office, department, descriptions) | High — official Personio feature |
| SAP | SuccessFactors (career site builder) | HTML list pages under `jobs.sap.com/go/SAP-Jobs-in-Germany/850601/`, ~50 postings/page, anchors with class `jobTitle-link`, numeric job id in URL, offset-in-path pagination | Medium — stable for years but unofficial; breaks if SAP redesigns |

Key insight: **adapters map to ATS platforms, not companies.** Three adapters
(SmartRecruiters, Personio, SuccessFactors) cover these three companies — and
every other company using those platforms comes free.

Update 2026-07-07: a fourth adapter, **Greenhouse** (official public API,
`boards-api.greenhouse.io/v1/boards/{token}/jobs`), was added along with ten
international-hiring companies: Bosch, Sixt, Scalable Capital (SmartRecruiters)
and Celonis, N26, HelloFresh, GetYourGuide, Wolt, SumUp, Adyen (Greenhouse).
Ops note: adding a value to `AdapterType` requires widening the Postgres check
constraint `watched_company_adapter_type_check` by hand — Hibernate's
`ddl-auto=update` creates enum check constraints but never widens them.

## Data model (new tables, same Postgres)

- `watched_company` — id, name, adapterType (`SMARTRECRUITERS` | `PERSONIO` |
  `SUCCESSFACTORS`), configJson (adapter-specific, see below), filter fields
  (includeKeywords, excludeKeywords, locations), enabled, lastRunAt,
  lastRunStatus, lastError.
- `job_posting` — id, watchedCompany FK, externalId, title, location,
  department, url, postedAt (source), firstSeenAt, lastSeenAt,
  status (`NEW` | `SEEN` | `DISMISSED` | `CONVERTED`), matchScore?,
  matchNotes?, applicationId? (set on convert).
  Unique constraint on (watched_company_id, external_id) — this is the diff.
- `watch_run` — per-company run log: startedAt, jobsFound, jobsNew, status,
  error. Powers "last checked ✓/✗" in the UI and debugging.

Adapter configs:
```json
SmartRecruiters: { "companyId": "DeliveryHero" }
Personio:        { "host": "egym.jobs.personio.de" }
SuccessFactors:  { "baseUrl": "https://jobs.sap.com", "listPath": "/go/SAP-Jobs-in-Germany/850601/", "maxPages": 10 }
```

## Adapter SPI

```java
public interface JobPortalAdapter {
    AdapterType type();
    List<FetchedJob> fetch(WatchedCompany company);   // throws on failure
}
public record FetchedJob(String externalId, String title, String location,
                         String department, String url, Instant postedAt) {}
```

- `SmartRecruitersAdapter` — official JSON API, paginate `offset/limit`.
- `PersonioAdapter` — official XML feed, parse `<position>` elements.
- `SuccessFactorsAdapter` — jsoup HTML parsing of `a.jobTitle-link` tiles,
  offset pagination, 1 req/sec politeness, `maxPages` cap.
  (New Gradle dependency: `org.jsoup:jsoup`.)

Filters run after fetch, per company: keep postings whose title matches
includeKeywords (e.g. `java|backend|software engineer`) and whose location
matches locations (e.g. `Munich|Berlin|Germany|Remote`); drop excludeKeywords.
Without filters Delivery Hero alone would produce 1,115 rows.

## Scheduling & diffing

- `@Scheduled(cron = "0 0 11 * * *", zone = "Europe/Berlin")` — explicit zone
  because containers run UTC.
- **Startup catch-up:** on boot, if the last successful run is older than ~20h,
  run immediately. Covers "laptop was asleep at 11:00".
- **Baseline on first run:** a company's first-ever fetch marks everything
  `SEEN`, not `NEW` — no thousand-job flood on day one.
- Per-company isolation: one failure never blocks the others.
- Jobs that disappear from the source get flagged (posting closed) — useful
  signal on already-converted applications.
- Manual triggers: `POST /api/watch/run` and per-company "Check now".

Update 2026-07-07 (Phase 2, part 1 shipped): every adapter now implements
`fetchDescription` — Greenhouse and SmartRecruiters via their posting-detail
APIs, Personio from the feed's `<jobDescriptions>` with a job-page-parse
fallback for companies that leave it empty (EGYM does), SuccessFactors by
parsing the job page (`[itemprop=description]`). The Jobs tab's **Apply**
button converts a posting with the JD attached and jumps straight into the
Generate tab prefilled. JD fetching is best-effort: on failure the application
is still created, with a note to paste the JD manually.

## AI match scoring (Phase 2)

For each NEW posting after filtering: one cheap ChatClient call scoring
title/location/(JD when fetchable) against the stored profile → score 0–100 +
one-line reason, stored on the posting. Because it reuses the existing
provider-agnostic ChatClient, vendor swap and Usage-tab cost tracking come for
free. Configurable off. JD fetch per ATS: SmartRecruiters posting-detail API,
Personio feed already includes descriptions, SAP job-detail page parse.

## UI & API

New **Jobs** tab:
- Feed of NEW postings grouped by company, match-score badges, "last checked"
  status per company, "Check now" button.
- Actions per posting: **Convert to application** (creates a SAVED board card
  with title/company/location/URL/JD prefilled → existing generate flow takes
  over) and **Dismiss**.
- Manage watched companies: add/edit/enable/disable + filters + test run.

Endpoints:
```
GET/POST        /api/watch/companies          PATCH/DELETE /api/watch/companies/{id}
POST            /api/watch/run                POST /api/watch/companies/{id}/run
GET             /api/jobs?status=NEW
POST            /api/jobs/{id}/dismiss        POST /api/jobs/{id}/convert
GET             /api/watch/runs
```

Phase 3 — push notification via Telegram bot (token + chat id in `.env`,
~30 lines): "3 new jobs at SAP, best match 82%: Senior Java Developer, Munich".
Email/SMTP as alternative.

## Onboarding a new company portal — runbook

**Step 1 — identify the ATS (≈5 minutes).** Open the careers page with browser
DevTools → Network tab (XHR/fetch) and reload; look for these hosts (also grep
page source):

| You see | ATS | Effort |
|---|---|---|
| `api.smartrecruiters.com` | SmartRecruiters | zero code — config row only |
| `*.jobs.personio.de` / `personio.com` | Personio | zero code — config row only |
| `jobs.<company>.com` with `/go/...` paths and `jobTitle-link` markup | SuccessFactors | zero code — reuse SAP adapter with different baseUrl |
| `boards.greenhouse.io` / `boards-api.greenhouse.io` | Greenhouse | **implemented 2026-07-07** — zero code, config `{"boardToken": "..."}` |
| `api.lever.co` | Lever | same — official JSON |
| `*.myworkdayjobs.com` | Workday | new adapter, medium effort (unofficial JSON POST) |
| `*.recruitee.com`, `*.ashbyhq.com`, `*.teamtailor.com` | Recruitee/Ashby/Teamtailor | small adapters, official JSON |

**Step 2 — adapter exists:** add the company in the UI (name, adapter type,
config, filters). No code, no deployment.

**Step 3 — no adapter yet:** either write one against the SPI (most are
50–150 lines), or use the fallback **LLM-extractor adapter**: fetch the page
HTML and let the AI extract `[{title, url, location}]`. Works on almost any
site, costs a fraction of a cent per run, but is the least deterministic —
fine as a stopgap, flagged in the UI as such.

**Step 4 — verify:** "Check now" dry run shows fetched count + sample postings
before enabling the daily schedule; first enabled run baselines silently.

**Politeness/ToS:** one fetch per company per day with a real User-Agent;
official APIs/feeds preferred (2 of your 3 companies have them); HTML parsing
only where there's no API (SAP), at trivial volume. Failures surface in the
run log instead of failing silently.

## Phasing

1. **Phase 1 (core):** tables, SPI, 3 adapters, scheduler + catch-up + baseline,
   Jobs tab, convert-to-board, company management. → daily new-job feed working.
2. **Phase 2:** AI match scoring + JD prefetch.
3. **Phase 3:** Telegram notifications, closed-posting detection.

No infrastructure changes: same containers, same compose file, one new Gradle
dependency (jsoup).
