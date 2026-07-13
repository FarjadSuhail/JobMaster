import { useEffect, useState } from 'react'
import { api } from '../api'
import { formatCost } from './UsagePage'

const COLUMNS = [
  { id: 'SAVED', label: 'Saved' },
  { id: 'APPLIED', label: 'Applied' },
  { id: 'INTERVIEWING', label: 'Interviewing' },
  { id: 'OFFER', label: 'Offer' },
  { id: 'REJECTED', label: 'Rejected' },
]

// When a card was last moved into its column. Falls back to updatedAt/createdAt
// for older applications saved before statusChangedAt was tracked.
function movedAt(a) {
  return new Date(a.statusChangedAt ?? a.updatedAt ?? a.createdAt).getTime()
}

export default function BoardPage({ onGenerate }) {
  const [apps, setApps] = useState(null)
  const [error, setError] = useState(null)
  const [selectedId, setSelectedId] = useState(null)
  const [adding, setAdding] = useState(false)
  // per-column quick filter: column id → search text (matches title + company)
  const [colSearch, setColSearch] = useState({})

  function load() {
    api
      .applications()
      .then(setApps)
      .catch((err) => setError(err.message))
  }
  useEffect(load, [])

  async function moveTo(id, status) {
    // stamp the move locally so the card jumps to the top of its new column
    // right away; load() then confirms with the server timestamp
    const now = new Date().toISOString()
    setApps((prev) =>
      prev.map((a) => (a.id === id ? { ...a, status, statusChangedAt: now } : a)),
    )
    try {
      await api.updateApplication(id, { status })
      load()
    } catch (err) {
      setError(err.message)
      load()
    }
  }

  if (error && !apps) return <div className="banner error">{error}</div>
  if (!apps) return <div className="placeholder">Loading board…</div>

  const selected = apps.find((a) => a.id === selectedId)

  return (
    <div>
      <div className="result-head">
        <h2>Applications</h2>
        <button className="btn primary" onClick={() => setAdding(true)}>
          + Add job
        </button>
      </div>
      {error && <div className="banner error">{error}</div>}

      <div className="board">
        {COLUMNS.map((col) => {
          const inColumn = apps
            .filter((a) => a.status === col.id)
            .sort((a, b) => movedAt(b) - movedAt(a))
          const query = (colSearch[col.id] ?? '').trim().toLowerCase()
          const cards = query
            ? inColumn.filter((a) =>
                `${a.jobTitle} ${a.company ?? ''}`.toLowerCase().includes(query),
              )
            : inColumn
          return (
            <div
              key={col.id}
              className={`board-col col-${col.id.toLowerCase()}`}
              onDragOver={(e) => e.preventDefault()}
              onDrop={(e) => {
                const id = Number(e.dataTransfer.getData('text/plain'))
                if (id) {
                  // clear this column's filter so the dropped card stays visible
                  setColSearch((s) => ({ ...s, [col.id]: '' }))
                  moveTo(id, col.id)
                }
              }}
            >
              <div className="board-col-head">
                {col.label}{' '}
                <span className="count">
                  {query ? `${cards.length}/${inColumn.length}` : inColumn.length}
                </span>
              </div>
              {inColumn.length > 1 && (
                <input
                  className="col-search"
                  value={colSearch[col.id] ?? ''}
                  onChange={(e) => setColSearch({ ...colSearch, [col.id]: e.target.value })}
                  placeholder="filter by title or company…"
                />
              )}
              {cards.map((a) => (
                <div
                  key={a.id}
                  className="card"
                  draggable
                  onDragStart={(e) => e.dataTransfer.setData('text/plain', String(a.id))}
                  onClick={() => setSelectedId(a.id)}
                >
                  <div className="card-title">{a.jobTitle}</div>
                  <div className="card-sub">
                    {[a.company, a.location].filter(Boolean).join(' · ') || '—'}
                  </div>
                  <div className="card-chips">
                    {a.generations.some((g) => g.kind === 'CV') && (
                      <span className="chip matched">CV</span>
                    )}
                    {a.generations.some((g) => g.kind === 'COVER_LETTER') && (
                      <span className="chip added">Letter</span>
                    )}
                    {a.salary && <span className="chip plain">{a.salary}</span>}
                  </div>
                  <div className="card-date">
                    {a.appliedAt
                      ? `applied ${a.appliedAt}`
                      : `added ${new Date(a.createdAt).toLocaleDateString()}`}
                  </div>
                </div>
              ))}
              {cards.length === 0 &&
                (inColumn.length > 0 ? (
                  <div className="col-empty">no card matches “{colSearch[col.id]}”</div>
                ) : (
                  <div className="col-empty">drop here</div>
                ))}
            </div>
          )
        })}
      </div>

      {adding && (
        <AddJobModal
          onClose={() => setAdding(false)}
          onCreated={() => {
            setAdding(false)
            load()
          }}
        />
      )}

      {selected && (
        <ApplicationDrawer
          app={selected}
          onClose={() => setSelectedId(null)}
          onChanged={load}
          onDeleted={() => {
            setSelectedId(null)
            load()
          }}
          onGenerate={onGenerate}
        />
      )}
    </div>
  )
}

function AddJobModal({ onClose, onCreated }) {
  const [form, setForm] = useState({
    jobTitle: '',
    company: '',
    location: '',
    jobUrl: '',
    salary: '',
    contactPerson: '',
    jobDescription: '',
    notes: '',
  })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)
  const set = (field) => (e) => setForm({ ...form, [field]: e.target.value })

  async function save(e) {
    e.preventDefault()
    setSaving(true)
    setError(null)
    try {
      await api.createApplication(form)
      onCreated()
    } catch (err) {
      setError(err.message)
      setSaving(false)
    }
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h2>Add job</h2>
        <form className="form-grid" onSubmit={save}>
          <div className="row2">
            <label>
              Job title *
              <input value={form.jobTitle} onChange={set('jobTitle')} required />
            </label>
            <label>
              Company
              <input value={form.company} onChange={set('company')} />
            </label>
          </div>
          <div className="row2">
            <label>
              Location (shown on documents)
              <input value={form.location} onChange={set('location')} placeholder="defaults to profile" />
            </label>
            <label>
              Salary
              <input value={form.salary} onChange={set('salary')} placeholder="e.g. 70-80k €" />
            </label>
          </div>
          <div className="row2">
            <label>
              Job posting URL
              <input value={form.jobUrl} onChange={set('jobUrl')} />
            </label>
            <label>
              Contact person
              <input value={form.contactPerson} onChange={set('contactPerson')} />
            </label>
          </div>
          <label>
            Job description
            <textarea rows={7} value={form.jobDescription} onChange={set('jobDescription')} />
          </label>
          <label>
            Notes
            <textarea rows={3} value={form.notes} onChange={set('notes')} />
          </label>
          {error && <div className="banner error">{error}</div>}
          <div className="btn-group end">
            <button type="button" className="btn" onClick={onClose} disabled={saving}>
              Cancel
            </button>
            <button type="submit" className="btn primary" disabled={saving}>
              {saving ? 'Saving…' : 'Add to board'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

function ApplicationDrawer({ app, onClose, onChanged, onDeleted, onGenerate }) {
  const [form, setForm] = useState(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    setForm({
      status: app.status,
      jobTitle: app.jobTitle ?? '',
      company: app.company ?? '',
      location: app.location ?? '',
      jobUrl: app.jobUrl ?? '',
      salary: app.salary ?? '',
      contactPerson: app.contactPerson ?? '',
      jobDescription: app.jobDescription ?? '',
      notes: app.notes ?? '',
      appliedAt: app.appliedAt ?? '',
    })
    setError(null)
  }, [app])

  if (!form) return null
  const set = (field) => (e) => setForm({ ...form, [field]: e.target.value })

  async function save() {
    setSaving(true)
    setError(null)
    try {
      await api.updateApplication(app.id, { ...form, appliedAt: form.appliedAt || null })
      onChanged()
    } catch (err) {
      setError(err.message)
    } finally {
      setSaving(false)
    }
  }

  async function remove() {
    if (!window.confirm(`Delete "${app.jobTitle}" and its ${app.generations.length} document(s)?`)) {
      return
    }
    try {
      await api.deleteApplication(app.id)
      onDeleted()
    } catch (err) {
      setError(err.message)
    }
  }

  const generateContext = {
    applicationId: app.id,
    jobTitle: form.jobTitle,
    company: form.company,
    location: form.location,
    jobDescription: form.jobDescription,
  }

  return (
    <div className="drawer-backdrop" onClick={onClose}>
      <aside className="drawer" onClick={(e) => e.stopPropagation()}>
        <div className="result-head">
          <h2>
            {app.jobTitle} <span className="muted">#{app.id}</span>
          </h2>
          <button className="btn" onClick={onClose}>
            Close
          </button>
        </div>

        <div className="form-grid">
          <div className="row2">
            <label>
              Status
              <select value={form.status} onChange={set('status')}>
                {COLUMNS.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.label}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Applied on
              <input type="date" value={form.appliedAt} onChange={set('appliedAt')} />
            </label>
          </div>
          <div className="row2">
            <label>
              Job title
              <input value={form.jobTitle} onChange={set('jobTitle')} />
            </label>
            <label>
              Company
              <input value={form.company} onChange={set('company')} />
            </label>
          </div>
          <div className="row2">
            <label>
              Location (shown on documents)
              <input value={form.location} onChange={set('location')} placeholder="defaults to profile" />
            </label>
            <label>
              Salary
              <input value={form.salary} onChange={set('salary')} />
            </label>
          </div>
          <div className="row2">
            <label>
              Job posting URL
              <input value={form.jobUrl} onChange={set('jobUrl')} />
            </label>
            <label>
              Contact person
              <input value={form.contactPerson} onChange={set('contactPerson')} />
            </label>
          </div>
          <label>
            Job description
            <textarea rows={6} value={form.jobDescription} onChange={set('jobDescription')} />
          </label>
          <label>
            Notes
            <textarea rows={4} value={form.notes} onChange={set('notes')} placeholder="interview dates, impressions, follow-ups…" />
          </label>
        </div>

        {form.jobUrl && (
          <a href={form.jobUrl} target="_blank" rel="noreferrer" className="drawer-link">
            Open job posting ↗
          </a>
        )}

        <h3 className="drawer-section">Documents</h3>
        {app.generations.length === 0 ? (
          <p className="muted">Nothing generated yet for this job.</p>
        ) : (
          <ul className="doc-list">
            {app.generations.map((g) => (
              <li key={g.id}>
                <span className={`chip ${g.kind === 'CV' ? 'matched' : 'added'}`}>
                  {g.kind === 'CV' ? 'CV' : 'Letter'}
                </span>
                <span className="muted">
                  #{g.id} · {new Date(g.createdAt).toLocaleString()} · {g.provider}/{g.model}
                  {g.costUsd != null && <> · {formatCost(g.costUsd)}</>}
                </span>
                <a className="btn small" href={api.pdfUrl(g.id)}>
                  PDF
                </a>
              </li>
            ))}
          </ul>
        )}

        {error && <div className="banner error">{error}</div>}

        <div className="btn-group drawer-actions">
          <button className="btn danger" onClick={remove}>
            Delete
          </button>
          <span className="spacer" />
          <button className="btn" onClick={() => onGenerate(generateContext)}>
            Generate documents
          </button>
          <button className="btn primary" onClick={save} disabled={saving}>
            {saving ? 'Saving…' : 'Save'}
          </button>
        </div>
      </aside>
    </div>
  )
}
