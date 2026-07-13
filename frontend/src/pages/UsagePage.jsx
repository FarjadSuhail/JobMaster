import { useEffect, useState } from 'react'
import { api } from '../api'

export function formatCost(cost) {
  if (cost == null) return '—'
  return `$${Number(cost).toFixed(4)}`
}

function formatTokens(n) {
  return n.toLocaleString()
}

export default function UsagePage() {
  const [report, setReport] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    api
      .usage()
      .then(setReport)
      .catch((err) => setError(err.message))
  }, [])

  if (error) return <div className="banner error">{error}</div>
  if (!report) return <div className="placeholder">Loading usage…</div>

  const currentMonth = new Date().toISOString().slice(0, 7)
  const thisMonth = report.months.find((m) => m.month === currentMonth)

  return (
    <div className="usage-layout">
      <h2>AI usage &amp; cost</h2>
      <p className="muted">
        Token counts come from the provider; prices are configured per model in{' '}
        <code>application.properties</code> (jobmaster.pricing.*) and snapshotted per document,
        so history stays correct when prices change.
      </p>

      <div className="stats">
        <div className="stat">
          <div className="stat-value">{formatCost(thisMonth?.costUsd ?? 0)}</div>
          <div className="stat-label">spent this month</div>
        </div>
        <div className="stat">
          <div className="stat-value">{formatCost(report.totalCostUsd)}</div>
          <div className="stat-label">spent total</div>
        </div>
        <div className="stat">
          <div className="stat-value">{report.totalGenerations}</div>
          <div className="stat-label">documents generated</div>
        </div>
        <div className="stat">
          <div className="stat-value">
            {formatTokens(report.totalPromptTokens + report.totalCompletionTokens)}
          </div>
          <div className="stat-label">tokens total</div>
        </div>
      </div>

      {report.months.length === 0 ? (
        <div className="placeholder">No generations yet — costs will show up here.</div>
      ) : (
        <table className="history-table">
          <thead>
            <tr>
              <th>Month</th>
              <th>Model</th>
              <th>Documents</th>
              <th>Prompt tokens</th>
              <th>Output tokens</th>
              <th>Cost</th>
            </tr>
          </thead>
          <tbody>
            {report.months.flatMap((m) => [
              <tr key={m.month} className="month-row">
                <td>
                  <b>{m.month}</b>
                </td>
                <td className="muted">all models</td>
                <td>{m.generations}</td>
                <td>{formatTokens(m.promptTokens)}</td>
                <td>{formatTokens(m.completionTokens)}</td>
                <td>
                  <b>{formatCost(m.costUsd)}</b>
                </td>
              </tr>,
              ...m.byModel.map((mm) => (
                <tr key={`${m.month}-${mm.provider}-${mm.model}`} className="model-row">
                  <td></td>
                  <td className="muted">
                    {mm.provider}/{mm.model}
                  </td>
                  <td className="muted">{mm.generations}</td>
                  <td className="muted">{formatTokens(mm.promptTokens)}</td>
                  <td className="muted">{formatTokens(mm.completionTokens)}</td>
                  <td className="muted">{formatCost(mm.costUsd)}</td>
                </tr>
              )),
            ])}
          </tbody>
        </table>
      )}
    </div>
  )
}
