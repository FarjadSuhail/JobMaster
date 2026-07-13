import { useEffect, useState } from 'react'
import { api } from '../api'

// Tiny renderer for the AI summary: handles "## " headings, "- " bullets,
// and **bold** — enough for the coach output without a markdown dependency.
function Markdownish({ text }) {
  const blocks = []
  let list = null
  const flush = () => {
    if (list) {
      blocks.push({ type: 'ul', items: list })
      list = null
    }
  }
  text.split('\n').forEach((raw) => {
    const line = raw.trim()
    if (line.startsWith('- ') || line.startsWith('* ')) {
      list = list ?? []
      list.push(line.slice(2))
    } else {
      flush()
      if (line.startsWith('## ')) blocks.push({ type: 'h', text: line.slice(3) })
      else if (line.startsWith('# ')) blocks.push({ type: 'h', text: line.slice(2) })
      else if (line) blocks.push({ type: 'p', text: line })
    }
  })
  flush()

  const inline = (str) =>
    str.split(/(\*\*[^*]+\*\*)/g).map((part, i) =>
      part.startsWith('**') && part.endsWith('**') ? <b key={i}>{part.slice(2, -2)}</b> : part,
    )

  return (
    <div className="ai-summary-text">
      {blocks.map((b, i) =>
        b.type === 'h' ? (
          <h3 key={i}>{inline(b.text)}</h3>
        ) : b.type === 'ul' ? (
          <ul key={i}>
            {b.items.map((item, j) => (
              <li key={j}>{inline(item)}</li>
            ))}
          </ul>
        ) : (
          <p key={i}>{inline(b.text)}</p>
        ),
      )}
    </div>
  )
}

function formatCost(value) {
  if (value == null) return ''
  const n = Number(value)
  return n < 0.01 && n > 0 ? `$${n.toFixed(4)}` : `$${n.toFixed(2)}`
}

export default function DashboardPage() {
  const [report, setReport] = useState(null)
  const [error, setError] = useState(null)
  const [generating, setGenerating] = useState(false)

  useEffect(() => {
    api.dashboard().then(setReport).catch((e) => setError(e.message))
  }, [])

  // Explicitly manual: one paid AI call, only from this click.
  async function runAnalysis() {
    setGenerating(true)
    setError(null)
    try {
      const summary = await api.dashboardSummary()
      setReport((r) => ({ ...r, lastSummary: summary }))
    } catch (e) {
      setError(e.message)
    } finally {
      setGenerating(false)
    }
  }

  if (error && !report) return <div className="banner error">{error}</div>
  if (!report) return <div className="placeholder">Loading dashboard…</div>

  const { stats, topProfileSkills, missingSkills, lastSummary } = report
  const nothingYet = stats.tracked === 0

  return (
    <div className="dash-layout">
      <div className="result-head">
        <h2>Dashboard</h2>
        <span className="muted">tracking since {report.since}</span>
      </div>

      {error && <div className="banner error">{error}</div>}

      {nothingYet && (
        <div className="banner warn">
          Tracking started {report.since} — applications created before that are not counted.
          Apply to a job (or add one on the Board) and the numbers appear here.
        </div>
      )}

      <div className="stats">
        <div className="stat">
          <div className="stat-value">{stats.applied}</div>
          <div className="stat-label">jobs applied</div>
        </div>
        <div className="stat">
          <div className="stat-value">{stats.interviewing}</div>
          <div className="stat-label">interviewing now</div>
        </div>
        <div className="stat">
          <div className="stat-value">{stats.offers}</div>
          <div className="stat-label">offers received</div>
        </div>
        <div className="stat">
          <div className="stat-value">{stats.rejectedNoInterview}</div>
          <div className="stat-label">rejected without interview</div>
        </div>
        <div className="stat">
          <div className="stat-value">{stats.rejectedAfterInterview}</div>
          <div className="stat-label">rejected after interview</div>
        </div>
        <div className="stat">
          <div className="stat-value">{stats.tracked}</div>
          <div className="stat-label">tracked in total</div>
        </div>
      </div>

      <div className="dash-panels">
        <div className="panel">
          <h2>Strongest skills in your profile</h2>
          <p className="muted">By prominence: how often each listed skill appears across your headline, summary, experience, and projects.</p>
          {topProfileSkills.length === 0 ? (
            <div className="placeholder">No profile stored yet.</div>
          ) : (
            <div className="skill-list">
              {topProfileSkills.map((s) => (
                <span key={s.name} className="chip matched">
                  {s.name} <b>×{s.count}</b>
                </span>
              ))}
            </div>
          )}
        </div>
        <div className="panel">
          <h2>Skills the jobs want that you lack</h2>
          <p className="muted">Keywords most often reported missing across CVs tailored since {report.since}.</p>
          {missingSkills.length === 0 ? (
            <div className="placeholder">
              Nothing yet — this fills up as you generate CVs for jobs you apply to.
            </div>
          ) : (
            <div className="skill-list">
              {missingSkills.map((s) => (
                <span key={s.name} className="chip missing">
                  {s.name} <b>×{s.count}</b>
                </span>
              ))}
            </div>
          )}
        </div>
      </div>

      <div className="panel">
        <div className="result-head">
          <h2>AI coach</h2>
          <button
            className="btn primary"
            onClick={runAnalysis}
            disabled={generating || nothingYet}
            title={
              nothingYet
                ? 'Apply to some jobs first — there is nothing to analyze yet'
                : 'Runs one paid AI call with the active model'
            }
          >
            {generating ? 'Analyzing…' : lastSummary ? 'Run new analysis' : 'Generate AI analysis'}
          </button>
        </div>
        <p className="muted">
          Analyzes the type of jobs you apply to and how to improve your odds. Runs a single
          AI call with the active model, only when you click the button (the cost lands in
          the Usage tab).
        </p>
        {lastSummary ? (
          <>
            <Markdownish text={lastSummary.text} />
            <p className="muted ai-summary-meta">
              Generated {new Date(lastSummary.createdAt).toLocaleString()} by {lastSummary.model}
              {lastSummary.costUsd != null && <> · {formatCost(lastSummary.costUsd)}</>}
            </p>
          </>
        ) : (
          <div className="placeholder">No analysis yet — click the button when you have a few applications tracked.</div>
        )}
      </div>
    </div>
  )
}
