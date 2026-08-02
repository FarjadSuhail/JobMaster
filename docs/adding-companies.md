# How to add a company to the Job Watcher (beginner guide)

The watcher checks each company's career portal once a day at 11:00 (and on
startup if it missed a day) and shows new postings in the **Jobs** tab.
Adding a company takes about two minutes — the only "technical" part is
finding out which job platform (ATS) the company uses, because that decides
what you put in the form.

---

## Step 1 — Find out which platform the company uses

Companies almost never build their own job site. They rent one of a handful
of platforms (called an ATS: applicant tracking system). JobMaster supports
four of them:

| Platform | You'll recognize it by |
|---|---|
| **SmartRecruiters** | job links contain `jobs.smartrecruiters.com/CompanyName/...` |
| **Greenhouse** | job links contain `boards.greenhouse.io/companyname` or `job-boards.greenhouse.io` |
| **Personio** | careers page lives at `companyname.jobs.personio.de` (or `.com`) |
| **SuccessFactors** | job list URLs look like `jobs.company.com/go/Some-Team/12345/`, footer often says "SAP SuccessFactors" |
| **Workday** | job links contain `company.wd5.myworkdayjobs.com` (the number after `wd` varies) |
| **Ashby** | job links contain `jobs.ashbyhq.com/companyname/...` |
| **BeeSite** | rare; Deutsche Bank's `careers.db.com` runs on it (job id after a `#` in the URL) |
| **Arbeitsagentur** | not a company at all — the federal job board; one watch entry = a keyword search across all of Germany |

> **Shortcut 1:** for SmartRecruiters, Greenhouse, Personio, Workday, Ashby, and
> Deutsche Bank you don't need any of this — paste a job link into the
> **Generate** tab's URL field and the company is added to the watch list
> automatically when it isn't there yet.
>
> **Shortcut 2:** Jobs → Watched companies → **Discover companies** probes ~100
> German tech employers against the public board APIs and lets you add every
> verified one with checkboxes.

How to check, in order of ease:

1. **Open the company's careers page and click any job posting.** Look at
   the address bar. If the address jumps to one of the domains above, done.
2. **Still on the company's own domain?** Right-click the careers page →
   *View Page Source* → press Cmd+F and search for: `greenhouse`,
   `smartrecruiters`, `personio`, `successfactors`. A hit tells you the
   platform hiding behind their pretty frontend.
3. **Direct test URLs** (paste in your browser, replace the identifier):
   - SmartRecruiters: `https://api.smartrecruiters.com/v1/companies/IDENTIFIER/postings`
     → correct if you see JSON with `"totalFound": <some number>` greater than 0
   - Greenhouse: `https://boards-api.greenhouse.io/v1/boards/IDENTIFIER/jobs`
     → correct if you see JSON with a `"jobs"` list
   - Personio: `https://IDENTIFIER.jobs.personio.de/xml`
     → correct if you see an XML feed with `<position>` entries

If none of these match, see [When nothing matches](#when-nothing-matches--custom-career-sites) below.

## Step 2 — Find the identifier

The identifier is the company's name *as the platform knows it*, which is
often not the everyday name:

- **SmartRecruiters**: the name in the job URL right after the domain:
  `jobs.smartrecruiters.com/BoschGroup/1234...` → identifier is `BoschGroup`.
- **Greenhouse**: the token after `boards.greenhouse.io/`:
  `boards.greenhouse.io/n26` → identifier is `n26` (usually all lowercase).
- **Personio**: the subdomain: `egym.jobs.personio.de` → identifier is the
  full host `egym.jobs.personio.de`.
- **SuccessFactors**: you need two things from the job *search results* page:
  the site root (e.g. `https://jobs.sap.com`) and the path of the list page
  (e.g. `/go/SAP-Jobs-in-Germany/539599/`).

Confirm with the test URL from Step 1.3 before continuing — a wrong
identifier is the #1 reason a new company shows 0 postings.

## Step 3 — Add it in the app

1. Open the **Jobs** tab → click **+ Watch company**.
2. **Company name**: anything you like (display only), e.g. `Revolut`.
3. **Platform**: pick what you found in Step 1.
4. **Adapter config**: the JSON in the box already shows the right shape,
   just replace the identifier:
   - SmartRecruiters: `{"companyId": "BoschGroup"}`
   - Greenhouse: `{"boardToken": "n26"}`
   - Personio: `{"host": "egym.jobs.personio.de"}`
   - SuccessFactors: `{"baseUrl": "https://jobs.sap.com", "listPath": "/go/SAP-Jobs-in-Germany/539599/", "maxPages": 10}`
5. **Include keywords**: pipe-separated words; a job title must contain at
   least one to show up. Working example used by the other companies:
   `            "software|developer|engineer|backend|full stack|fullstack|full-stack|angular|react|vue|node|node.js|python|nest|nest.js|cloud|aws|gcp|azure|docker|kubernetes|devops"`
   Leave empty to get every posting.
6. **Exclude keywords**: titles containing any of these are dropped, e.g.
   `intern|working student|thesis`.
7. **Locations**: pipe-separated, matches the job's location text, e.g.
   `germany|munich|berlin|remote`. Leave empty for worldwide.
8. Click **Add & watch**.

## Step 4 — First check and verification

Click **Check** on the new company's row.

- The **first check is a silent import**: everything currently online is
  saved as *seen*, not *new* — so you don't get 50 fake "new job!" alerts
  for postings that are months old. Only jobs published **after** this
  first check will ever show as NEW.
- Verify it worked: the row shows `✓` with a timestamp, and switching the
  postings filter to **all** shows the imported jobs.

### Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `✓` but 0 postings found | Identifier wrong (redo Step 2) or filters too strict — clear the include keywords, save, Check again, then re-add filters. |
| Red `✗` on the row | Hover over it to read the error. Usually a typo in the config JSON or the platform blocking briefly — try Check again. |
| Postings appear but all irrelevant | Tighten include keywords; check the locations filter. |

---

## When nothing matches — custom career sites

Some companies (usually big tech/fintech) build their own hiring platform.
No supported adapter can read those without new code.

**Worked example — Revolut (checked 2026-07-08).** A LinkedIn posting
redirects to `revolut.com/careers/position/...`:

1. Clicking postings never leaves `revolut.com` → no platform domain visible.
2. Page source: no `greenhouse` / `smartrecruiters` / `personio` /
   `successfactors` fingerprints.
3. Test URLs: Greenhouse 404; SmartRecruiters responds but with
   `totalFound: 0` (an empty shell, not their real data).
4. Conclusion: Revolut runs its own in-house ATS ("Revolut People" — they
   even sell it as a product), rendered fully in the browser with no public
   data feed.

What you can do in that case:

- **Ask Claude to build a custom adapter** for that company — sometimes the
  site has a hidden internal API that a new adapter can use; sometimes it
  genuinely needs browser automation and isn't worth it. It's a code change,
  so it's a "ask, don't click" situation.
- **Meanwhile**: set a LinkedIn/company email alert for the company and add
  interesting jobs to the Board manually (Board → + button), pasting the job
  description into Generate as usual.

---

## Quick reference

| Platform | Config JSON | Browser test URL |
|---|---|---|
| SmartRecruiters | `{"companyId": "BoschGroup"}` | `api.smartrecruiters.com/v1/companies/BoschGroup/postings` |
| Greenhouse | `{"boardToken": "n26"}` | `boards-api.greenhouse.io/v1/boards/n26/jobs` |
| Personio | `{"host": "egym.jobs.personio.de"}` | `egym.jobs.personio.de/xml` |
| SuccessFactors | `{"baseUrl": "https://jobs.sap.com", "listPath": "/go/SAP-Jobs-in-Germany/539599/", "maxPages": 10}` | the listPath page itself |
| Workday | `{"host": "cerence.wd5.myworkdayjobs.com", "tenant": "cerence", "site": "Cerence"}` | the careers page itself (host = its domain; tenant = first part of the host; site = path segment after the domain) |
| Ashby | `{"jobBoardName": "galvany"}` | `api.ashbyhq.com/posting-api/job-board/galvany` |
| BeeSite | `{"apiHost": "api-deutschebank.beesite.de", "jobUrlTemplate": "https://careers.db.com/professionals/search-roles/#/professional/job/{id}"}` | `api-deutschebank.beesite.de/jobhtml/71753.json` |

| Arbeitsagentur | `{"was": "java backend software engineer", "wo": "Deutschland", "maxPages": 5}` | rest.arbeitsagentur.de is key-protected; just add the entry and click Check |

BeeSite note: its API reports German country names ("Deutschland", "Italien"),
so use city names in the locations filter for BeeSite companies.

Arbeitsagentur note: `was` is the search text (like typing into arbeitsagentur.de),
`wo` the region. The employer name is folded into each posting's title since the
"company" here is the job board itself. Leave include-keywords empty — the
search already filters server-side.
