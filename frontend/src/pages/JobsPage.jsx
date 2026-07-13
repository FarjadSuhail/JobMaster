import { useEffect, useMemo, useState } from 'react'
import { api } from '../api'

const PAGE_SIZE = 25

// Postings table columns; every one is sortable by clicking its header.
const JOB_COLUMNS = [
  { key: 'companyName', label: 'Company' },
  { key: 'title', label: 'Title' },
  { key: 'location', label: 'Location' },
  { key: 'firstSeenAt', label: 'Seen' },
  { key: 'status', label: 'Status' },
]

// Bare-city locations ("Berlin", "Ulm") carry no country, so recognizing
// Germany needs a city list. Lowercase; covers every German city seen in the
// data plus the usual tech hubs.
const GERMAN_CITIES = new Set([
  'berlin', 'münchen', 'munich', 'hamburg', 'frankfurt', 'köln', 'cologne',
  'stuttgart', 'düsseldorf', 'dusseldorf', 'ulm', 'nürnberg', 'nuremberg',
  'karlsruhe', 'heidelberg', 'walldorf', 'leipzig', 'dresden', 'hannover',
  'hanover', 'bremen', 'essen', 'dortmund', 'bonn', 'aachen', 'mannheim',
  'münster', 'potsdam', 'eschborn', 'ingolstadt', 'augsburg', 'darmstadt',
  'freiburg', 'kiel', 'magdeburg', 'saarbrücken', 'barleben', 'wiesbaden',
  'mainz', 'erlangen', 'regensburg', 'jena', 'kaiserslautern', 'paderborn',
  'bielefeld', 'braunschweig', 'wolfsburg', 'bochum', 'duisburg', 'wuppertal',
  'chemnitz', 'rostock', 'lübeck', 'garching', 'unterföhring', 'ottobrunn',
  'holzkirchen', 'tübingen', 'göttingen', 'würzburg', 'kassel', 'osnabrück',
])

// Words that say nothing about the country ("Remote", "2 Locations", "Hybrid")
const NON_LOCATION_WORDS = new Set(['remote', 'hybrid', 'locations', 'location', 'or', 'and'])

/**
 * True when the posting is (or could be) in Germany. Handles the formats the
 * portals actually use: "Berlin, Germany", "Frankfurt, Deutschland" (BeeSite),
 * "Berlin, DE", bare cities ("Ulm"), and multi-location strings. Postings with
 * no location info at all ("", "Remote", "2 Locations") are kept visible —
 * hiding them would silently drop jobs that might be German.
 */
function isGermanLocation(location) {
  if (!location || !location.trim()) return true
  const lower = location.toLowerCase()
  if (lower.includes('germany') || lower.includes('deutschland')) return true
  if (/[,\s]de\s*$/.test(lower)) return true // "Berlin, DE"
  const tokens = lower.split(/[^a-zäöüß]+/).filter(Boolean)
  if (tokens.some((t) => GERMAN_CITIES.has(t))) return true
  // nothing left after generic words = location effectively unknown → show
  return tokens.every((t) => NON_LOCATION_WORDS.has(t))
}

function compareJobs(a, b, key) {
  if (key === 'firstSeenAt') {
    return new Date(a.firstSeenAt) - new Date(b.firstSeenAt)
  }
  return String(a[key] ?? '').localeCompare(String(b[key] ?? ''), undefined, {
    sensitivity: 'base',
  })
}

const ADAPTER_HINTS = {
  SMARTRECRUITERS: '{"companyId": "DeliveryHero"}',
  GREENHOUSE: '{"boardToken": "n26"}',
  PERSONIO: '{"host": "acme.jobs.personio.de"}',
  SUCCESSFACTORS:
    '{"baseUrl": "https://jobs.example.com", "listPath": "/go/Some-List/12345/", "maxPages": 10}',
  WORKDAY:
    '{"host": "cerence.wd5.myworkdayjobs.com", "tenant": "cerence", "site": "Cerence"}',
  ASHBY: '{"jobBoardName": "galvany"}',
  BEESITE:
    '{"apiHost": "api-deutschebank.beesite.de", "jobUrlTemplate": "https://careers.db.com/professionals/search-roles/#/professional/job/{id}"}',
  ARBEITSAGENTUR: '{"was": "java backend software engineer", "wo": "Deutschland", "maxPages": 5}',
}

const STATUS_FILTERS = ['NEW', 'ALL', 'SEEN', 'DISMISSED', 'CONVERTED']

export default function JobsPage({ onGenerate }) {
  const [companies, setCompanies] = useState(null)
  const [jobs, setJobs] = useState(null)
  // postings are the daily driver; company management lives in its own sub-tab
  const [view, setView] = useState('postings')
  const [statusFilter, setStatusFilter] = useState('NEW')
  // 'ALL' or a company name — narrows the postings table to that company
  const [companyFilter, setCompanyFilter] = useState('ALL')
  // 'DE' (default) shows German + unknown-location postings; 'ALL' shows everything
  const [locationFilter, setLocationFilter] = useState('DE')
  // newest postings first by default; click a column header to re-sort
  const [sort, setSort] = useState({ key: 'firstSeenAt', dir: 'desc' })
  const [page, setPage] = useState(0)
  const [checking, setChecking] = useState(false)
  const [error, setError] = useState(null)
  const [notice, setNotice] = useState(null)
  const [editing, setEditing] = useState(null) // null | 'new' | company object
  const [discovering, setDiscovering] = useState(false)

  function loadCompanies() {
    api.watchCompanies().then(setCompanies).catch((e) => setError(e.message))
  }
  function loadJobs(filter = statusFilter) {
    api.jobs(filter).then(setJobs).catch((e) => setError(e.message))
  }
  useEffect(() => {
    loadCompanies()
    loadJobs()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function checkAll() {
    setChecking(true)
    setError(null)
    setNotice(null)
    try {
      const runs = await api.watchRunAll()
      const failed = runs.filter((r) => r.status !== 'OK')
      const totalNew = runs.reduce((sum, r) => sum + r.jobsNew, 0)
      setNotice(
        `Checked ${runs.length} compan${runs.length === 1 ? 'y' : 'ies'}: ` +
          `${totalNew} new posting${totalNew === 1 ? '' : 's'}` +
          (failed.length ? ` — ${failed.map((f) => f.companyName).join(', ')} failed` : ''),
      )
      loadCompanies()
      loadJobs()
    } catch (e) {
      setError(e.message)
    } finally {
      setChecking(false)
    }
  }

  async function checkOne(company) {
    setError(null)
    setNotice(null)
    try {
      const run = await api.watchRunCompany(company.id)
      setNotice(
        run.status === 'OK'
          ? `${run.companyName}: ${run.jobsFound} postings after filters, ${run.jobsNew} new`
          : `${run.companyName} failed: ${run.error}`,
      )
      loadCompanies()
      loadJobs()
    } catch (e) {
      setError(e.message)
    }
  }

  async function toggleEnabled(company) {
    try {
      await api.updateWatchCompany(company.id, { enabled: !company.enabled })
      loadCompanies()
    } catch (e) {
      setError(e.message)
    }
  }

  async function removeCompany(company) {
    if (!window.confirm(`Stop watching ${company.name}? Its discovered postings are deleted too.`)) {
      return
    }
    try {
      await api.deleteWatchCompany(company.id)
      loadCompanies()
      loadJobs()
    } catch (e) {
      setError(e.message)
    }
  }

  async function dismiss(job) {
    try {
      await api.dismissJob(job.id)
      loadJobs()
      loadCompanies()
    } catch (e) {
      setError(e.message)
    }
  }

  // bulk save: selected posting ids → board applications (status SAVED)
  const [selectedIds, setSelectedIds] = useState(new Set())
  const [savingSelected, setSavingSelected] = useState(false)

  function toggleSelected(id) {
    const next = new Set(selectedIds)
    if (next.has(id)) next.delete(id)
    else next.add(id)
    setSelectedIds(next)
  }

  async function saveSelected() {
    setSavingSelected(true)
    setError(null)
    setNotice(null)
    const ids = [...selectedIds]
    const results = await Promise.allSettled(ids.map((id) => api.convertJob(id)))
    const saved = results.filter((r) => r.status === 'fulfilled').length
    const failed = results.length - saved
    setNotice(
      `${saved} job${saved === 1 ? '' : 's'} saved to the board (Saved column)` +
        (failed ? ` — ${failed} failed, try them individually` : '') +
        '. Open the Board tab to apply one by one.',
    )
    setSelectedIds(new Set())
    setSavingSelected(false)
    loadJobs()
    loadCompanies()
  }

  // Apply: fetch the JD, create the board application, jump to Generate prefilled
  const [applyingId, setApplyingId] = useState(null)
  async function apply(job) {
    setError(null)
    setApplyingId(job.id)
    // open the original posting right away (must happen synchronously in the
    // click handler, or the popup blocker eats it)
    if (job.url) {
      window.open(job.url, '_blank', 'noopener')
    }
    try {
      const app = await api.convertJob(job.id)
      onGenerate({
        applicationId: app.id,
        jobTitle: app.jobTitle,
        company: app.company,
        location: app.location,
        jobDescription: app.jobDescription ?? '',
      })
    } catch (e) {
      setError(e.message)
    } finally {
      setApplyingId(null)
    }
  }

  async function markSeen() {
    try {
      await api.markJobsSeen()
      loadJobs()
      loadCompanies()
    } catch (e) {
      setError(e.message)
    }
  }

  function changeFilter(value) {
    setStatusFilter(value)
    setJobs(null)
    setPage(0)
    setSelectedIds(new Set())
    loadJobs(value)
  }

  function changeSort(key) {
    setSort((s) =>
      s.key === key
        ? { key, dir: s.dir === 'asc' ? 'desc' : 'asc' }
        : { key, dir: key === 'firstSeenAt' ? 'desc' : 'asc' },
    )
    setPage(0)
  }

  const sortedJobs = useMemo(() => {
    if (!jobs) return null
    let filtered =
      companyFilter === 'ALL' ? jobs : jobs.filter((j) => j.companyName === companyFilter)
    if (locationFilter === 'DE') {
      filtered = filtered.filter((j) => isGermanLocation(j.location))
    }
    const sorted = [...filtered].sort((a, b) => compareJobs(a, b, sort.key))
    return sort.dir === 'desc' ? sorted.reverse() : sorted
  }, [jobs, sort, companyFilter, locationFilter])

  const pageCount = sortedJobs ? Math.max(1, Math.ceil(sortedJobs.length / PAGE_SIZE)) : 1
  const currentPage = Math.min(page, pageCount - 1)
  const pageJobs = sortedJobs
    ? sortedJobs.slice(currentPage * PAGE_SIZE, (currentPage + 1) * PAGE_SIZE)
    : null

  // page-level select-all (converted postings are already on the board)
  const pageSelectable = pageJobs ? pageJobs.filter((j) => j.status !== 'CONVERTED') : []
  const allPageSelected =
    pageSelectable.length > 0 && pageSelectable.every((j) => selectedIds.has(j.id))
  function togglePageSelection() {
    const next = new Set(selectedIds)
    if (allPageSelected) pageSelectable.forEach((j) => next.delete(j.id))
    else pageSelectable.forEach((j) => next.add(j.id))
    setSelectedIds(next)
  }

  if (!companies) return <div className="placeholder">Loading job watcher…</div>

  const totalNew = companies.reduce((sum, c) => sum + (c.newPostings ?? 0), 0)

  return (
    <div className="jobs-layout">
      <div className="result-head">
        <h2>Job watcher</h2>
        <div className="btn-group">
          {view === 'companies' && (
            <>
              <button className="btn" onClick={() => setDiscovering(true)}>
                Discover companies
              </button>
              <button className="btn" onClick={() => setEditing('new')}>
                + Watch company
              </button>
            </>
          )}
          <button className="btn primary" onClick={checkAll} disabled={checking}>
            {checking ? 'Checking…' : 'Check all now'}
          </button>
        </div>
      </div>

      <div className="subtabs">
        <button
          className={`subtab ${view === 'postings' ? 'active' : ''}`}
          onClick={() => setView('postings')}
        >
          Postings
          {totalNew > 0 && <span className="chip matched">{totalNew} new</span>}
        </button>
        <button
          className={`subtab ${view === 'companies' ? 'active' : ''}`}
          onClick={() => setView('companies')}
        >
          Watched companies ({companies.length})
        </button>
      </div>

      {error && <div className="banner error">{error}</div>}
      {notice && <div className="banner warn">{notice}</div>}

      {view === 'companies' && (
        <>
      <p className="muted">
        Portals are checked daily at 11:00 (Europe/Berlin), with a catch-up run when the app
        starts after being off. A company&apos;s first check imports existing postings silently;
        only jobs posted after that show up as new. Tip: pasting a job link in the Generate
        tab adds its company here automatically.
      </p>

      <table className="history-table">
        <thead>
          <tr>
            <th>Company</th>
            <th>Platform</th>
            <th>Filters</th>
            <th>Last check</th>
            <th>New</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {companies.map((c) => (
            <tr key={c.id}>
              <td>
                <b>{c.name}</b>
                {!c.enabled && <span className="muted"> (paused)</span>}
              </td>
              <td className="muted">{c.adapterType.toLowerCase()}</td>
              <td className="muted filters-cell" title={`include: ${c.includeKeywords || '—'}\nexclude: ${c.excludeKeywords || '—'}\nlocations: ${c.locations || '—'}`}>
                {c.includeKeywords ? c.includeKeywords.split('|').slice(0, 3).join(', ') + '…' : '—'}
              </td>
              <td>
                {c.lastRunAt ? (
                  <span
                    className={c.lastRunStatus === 'OK' ? 'run-ok' : 'run-error'}
                    title={c.lastError || ''}
                  >
                    {c.lastRunStatus === 'OK' ? '✓' : '✗'}{' '}
                    <span className="muted">{new Date(c.lastRunAt).toLocaleString()}</span>
                  </span>
                ) : (
                  <span className="muted">never</span>
                )}
              </td>
              <td>{c.newPostings > 0 ? <span className="chip matched">{c.newPostings}</span> : <span className="muted">0</span>}</td>
              <td>
                <div className="btn-group">
                  <button className="btn small" onClick={() => checkOne(c)}>
                    Check
                  </button>
                  <button className="btn small" onClick={() => setEditing(c)}>
                    Edit
                  </button>
                  <button className="btn small" onClick={() => toggleEnabled(c)}>
                    {c.enabled ? 'Pause' : 'Resume'}
                  </button>
                  <button className="btn small danger" onClick={() => removeCompany(c)}>
                    Delete
                  </button>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
        </>
      )}

      {view === 'postings' && (
        <>
      <div className="result-head">
        <div className="btn-group">
          <select
            value={locationFilter}
            onChange={(e) => {
              setLocationFilter(e.target.value)
              setPage(0)
            }}
            title="Germany also keeps postings whose location is unknown"
          >
            <option value="DE">Germany only</option>
            <option value="ALL">all locations</option>
          </select>
          <select
            value={companyFilter}
            onChange={(e) => {
              setCompanyFilter(e.target.value)
              setPage(0)
            }}
          >
            <option value="ALL">all companies</option>
            {[...companies]
              .sort((a, b) => a.name.localeCompare(b.name))
              .map((c) => (
                <option key={c.id} value={c.name}>
                  {c.name}
                </option>
              ))}
          </select>
          <select value={statusFilter} onChange={(e) => changeFilter(e.target.value)}>
            {STATUS_FILTERS.map((s) => (
              <option key={s} value={s}>
                {s.toLowerCase()}
              </option>
            ))}
          </select>
          {statusFilter === 'NEW' && jobs?.length > 0 && (
            <button className="btn" onClick={markSeen}>
              Mark all seen
            </button>
          )}
          {selectedIds.size > 0 && (
            <button className="btn primary" onClick={saveSelected} disabled={savingSelected}>
              {savingSelected
                ? 'Saving…'
                : `Save ${selectedIds.size} to board`}
            </button>
          )}
        </div>
      </div>

      {!pageJobs ? (
        <div className="placeholder">Loading postings…</div>
      ) : sortedJobs.length === 0 ? (
        <div className="placeholder">
          {companyFilter !== 'ALL' || locationFilter !== 'ALL'
            ? `Nothing matches the current filters (status "${statusFilter.toLowerCase()}"${
                companyFilter !== 'ALL' ? `, ${companyFilter}` : ''
              }${locationFilter === 'DE' ? ', Germany only' : ''}) — try widening them.`
            : statusFilter === 'NEW'
              ? 'No new postings. New jobs appear here after the daily 11:00 check (or "Check all now").'
              : 'Nothing here.'}
        </div>
      ) : (
        <>
        <table className="history-table">
          <thead>
            <tr>
              <th className="check-col">
                <input
                  type="checkbox"
                  checked={allPageSelected}
                  onChange={togglePageSelection}
                  title="Select all on this page"
                />
              </th>
              {JOB_COLUMNS.map((col) => (
                <th
                  key={col.key}
                  className="th-sort"
                  onClick={() => changeSort(col.key)}
                  title={`Sort by ${col.label.toLowerCase()}`}
                >
                  {col.label}
                  <span className="sort-arrow">
                    {sort.key === col.key ? (sort.dir === 'asc' ? ' ▲' : ' ▼') : ''}
                  </span>
                </th>
              ))}
              <th></th>
            </tr>
          </thead>
          <tbody>
            {pageJobs.map((j) => (
              <tr key={j.id} className={j.closed ? 'job-closed' : ''}>
                <td className="check-col">
                  <input
                    type="checkbox"
                    disabled={j.status === 'CONVERTED'}
                    checked={selectedIds.has(j.id)}
                    onChange={() => toggleSelected(j.id)}
                  />
                </td>
                <td>{j.companyName}</td>
                <td>
                  <a href={j.url} target="_blank" rel="noreferrer">
                    {j.title}
                  </a>
                  {j.closed && <span className="muted"> (no longer listed)</span>}
                </td>
                <td className="muted">{j.location || '—'}</td>
                <td className="muted">{new Date(j.firstSeenAt).toLocaleDateString()}</td>
                <td>
                  <span
                    className={`chip ${j.status === 'NEW' ? 'matched' : j.status === 'CONVERTED' ? 'added' : 'plain'}`}
                  >
                    {j.status.toLowerCase()}
                  </span>
                </td>
                <td>
                  <div className="btn-group">
                    {j.status === 'CONVERTED' ? (
                      <span className="muted">app #{j.applicationId}</span>
                    ) : (
                      <button
                        className="btn small primary"
                        onClick={() => apply(j)}
                        disabled={applyingId === j.id}
                        title="Fetches the job description, adds it to the board, and opens Generate"
                      >
                        {applyingId === j.id ? 'Fetching…' : 'Apply'}
                      </button>
                    )}
                    {(j.status === 'NEW' || j.status === 'SEEN') && (
                      <button className="btn small" onClick={() => dismiss(j)}>
                        Dismiss
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {pageCount > 1 && (
          <div className="pager">
            <span className="muted">
              {sortedJobs.length} postings · page {currentPage + 1} of {pageCount}
            </span>
            <button
              className="btn small"
              onClick={() => setPage(currentPage - 1)}
              disabled={currentPage === 0}
            >
              ‹ Prev
            </button>
            <button
              className="btn small"
              onClick={() => setPage(currentPage + 1)}
              disabled={currentPage >= pageCount - 1}
            >
              Next ›
            </button>
          </div>
        )}
        </>
      )}
        </>
      )}

      {editing && (
        <CompanyModal
          company={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null)
            loadCompanies()
          }}
        />
      )}

      {discovering && (
        <DiscoveryModal
          onClose={() => setDiscovering(false)}
          onAdded={(count) => {
            setDiscovering(false)
            setNotice(
              `${count} compan${count === 1 ? 'y' : 'ies'} added — their postings are being imported in the background (first import is silent).`,
            )
            loadCompanies()
            loadJobs()
          }}
        />
      )}
    </div>
  )
}

function DiscoveryModal({ onClose, onAdded }) {
  const [customNames, setCustomNames] = useState('')
  const [probing, setProbing] = useState(false)
  const [results, setResults] = useState(null)
  const [selected, setSelected] = useState(new Set())
  const [adding, setAdding] = useState(false)
  const [error, setError] = useState(null)

  const key = (r) => `${r.adapterType}|${r.identifier}`
  const addable = results ? results.filter((r) => !r.alreadyWatched) : []

  async function probe() {
    setProbing(true)
    setError(null)
    setResults(null)
    setSelected(new Set())
    try {
      const names = customNames.trim()
        ? customNames.split('\n').map((n) => n.trim()).filter(Boolean)
        : null
      setResults(await api.discoverCompanies(names))
    } catch (e) {
      setError(e.message)
    } finally {
      setProbing(false)
    }
  }

  function toggle(r) {
    const next = new Set(selected)
    const k = key(r)
    if (next.has(k)) next.delete(k)
    else next.add(k)
    setSelected(next)
  }

  function toggleAll() {
    setSelected(selected.size === addable.length ? new Set() : new Set(addable.map(key)))
  }

  async function addSelected() {
    setAdding(true)
    setError(null)
    try {
      const items = results.filter((r) => selected.has(key(r)))
      await api.addDiscovered(items)
      onAdded(items.length)
    } catch (e) {
      setError(e.message)
      setAdding(false)
    }
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal wide" onClick={(e) => e.stopPropagation()}>
        <h2>Discover companies</h2>
        <p className="muted">
          Probes ~100 German tech employers (or your own list below) against the public
          Greenhouse, SmartRecruiters, and Ashby APIs and shows every board it can verify.
          Companies on custom platforms won&apos;t appear — paste one of their job links in
          Generate instead.
        </p>
        <label>
          Extra company names (optional, one per line)
          <textarea
            rows={3}
            value={customNames}
            onChange={(e) => setCustomNames(e.target.value)}
            placeholder={'Company One\nCompany Two'}
          />
        </label>
        <div className="btn-group end">
          <button className="btn primary" onClick={probe} disabled={probing}>
            {probing ? 'Probing… (takes ~30s)' : results ? 'Probe again' : 'Start probing'}
          </button>
        </div>

        {error && <div className="banner error">{error}</div>}

        {results && results.length === 0 && (
          <div className="placeholder">No verifiable boards found for these names.</div>
        )}
        {results && results.length > 0 && (
          <>
            <div className="discover-results">
              <table className="history-table">
                <thead>
                  <tr>
                    <th>
                      <input
                        type="checkbox"
                        checked={addable.length > 0 && selected.size === addable.length}
                        onChange={toggleAll}
                        title="Select all new"
                      />
                    </th>
                    <th>Company</th>
                    <th>Platform</th>
                    <th>Jobs</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {results.map((r) => (
                    <tr key={key(r)}>
                      <td>
                        <input
                          type="checkbox"
                          disabled={r.alreadyWatched}
                          checked={selected.has(key(r))}
                          onChange={() => toggle(r)}
                        />
                      </td>
                      <td>
                        <b>{r.name}</b>
                      </td>
                      <td className="muted">{r.adapterType.toLowerCase()}</td>
                      <td>{r.jobCount}</td>
                      <td className="muted">
                        {r.alreadyWatched ? `already watched as ${r.watchedAs}` : ''}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="btn-group end">
              <button className="btn" onClick={onClose} disabled={adding}>
                Cancel
              </button>
              <button
                className="btn primary"
                onClick={addSelected}
                disabled={adding || selected.size === 0}
              >
                {adding ? 'Adding…' : `Add ${selected.size} selected`}
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  )
}

function CompanyModal({ company, onClose, onSaved }) {
  const [form, setForm] = useState({
    name: company?.name ?? '',
    adapterType: company?.adapterType ?? 'SMARTRECRUITERS',
    configJson: company?.configJson ?? ADAPTER_HINTS.SMARTRECRUITERS,
    includeKeywords: company?.includeKeywords ?? '',
    excludeKeywords: company?.excludeKeywords ?? '',
    locations: company?.locations ?? '',
    enabled: company?.enabled ?? true,
  })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)
  const set = (field) => (e) => setForm({ ...form, [field]: e.target.value })

  function setAdapter(e) {
    const adapterType = e.target.value
    setForm({
      ...form,
      adapterType,
      // when creating, swap in the matching config example
      configJson: company ? form.configJson : ADAPTER_HINTS[adapterType],
    })
  }

  async function save(e) {
    e.preventDefault()
    setSaving(true)
    setError(null)
    try {
      if (company) {
        await api.updateWatchCompany(company.id, form)
      } else {
        await api.createWatchCompany(form)
      }
      onSaved()
    } catch (err) {
      setError(err.message)
      setSaving(false)
    }
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h2>{company ? `Edit ${company.name}` : 'Watch a company'}</h2>
        <form className="form-grid" onSubmit={save}>
          <div className="row2">
            <label>
              Company name *
              <input value={form.name} onChange={set('name')} required />
            </label>
            <label>
              Platform (ATS)
              <select value={form.adapterType} onChange={setAdapter}>
                <option value="SMARTRECRUITERS">SmartRecruiters</option>
                <option value="GREENHOUSE">Greenhouse</option>
                <option value="PERSONIO">Personio</option>
                <option value="SUCCESSFACTORS">SuccessFactors</option>
                <option value="WORKDAY">Workday</option>
                <option value="ASHBY">Ashby</option>
                <option value="BEESITE">BeeSite (e.g. Deutsche Bank)</option>
                <option value="ARBEITSAGENTUR">Arbeitsagentur (all-Germany search)</option>
              </select>
            </label>
          </div>
          <label>
            Adapter config (JSON)
            <textarea
              rows={3}
              value={form.configJson}
              onChange={set('configJson')}
              spellCheck={false}
            />
          </label>
          <label>
            Include keywords (title must match one; pipe-separated; empty = all)
            <input
              value={form.includeKeywords}
              onChange={set('includeKeywords')}
              placeholder="software|developer|engineer|backend|java"
            />
          </label>
          <div className="row2">
            <label>
              Exclude keywords
              <input value={form.excludeKeywords} onChange={set('excludeKeywords')} placeholder="intern|working student" />
            </label>
            <label>
              Locations (empty = anywhere)
              <input value={form.locations} onChange={set('locations')} placeholder="germany|munich|berlin|remote" />
            </label>
          </div>
          {error && <div className="banner error">{error}</div>}
          <div className="btn-group end">
            <button type="button" className="btn" onClick={onClose} disabled={saving}>
              Cancel
            </button>
            <button type="submit" className="btn primary" disabled={saving}>
              {saving ? 'Saving…' : company ? 'Save' : 'Add & watch'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
