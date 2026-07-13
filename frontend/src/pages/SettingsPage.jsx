import { useEffect, useState } from 'react'
import { api } from '../api'

const EMPTY_FORM = {
  provider: 'OPENAI',
  model: '',
  apiKey: '',
  temperature: '0.4',
  topP: '',
  maxTokens: '',
}

const PARAM_INFO = [
  {
    name: 'Provider & model',
    text: 'Which AI vendor and model generates your CVs and cover letters. Smarter models write better documents but cost more per generation (see the Usage tab for prices actually paid). Model names come from the vendor, e.g. "gpt-4o-mini", "gpt-5.4", "claude-sonnet-4-5".',
  },
  {
    name: 'API key',
    text: 'Your key for that vendor. Stored in your local database and never shown again in full (only the last 4 characters). When adding another model for a vendor you already use, leave the key empty to reuse the stored one.',
  },
  {
    name: 'Temperature',
    text: 'Controls randomness/creativity. 0 = focused and deterministic, higher = more varied wording. OpenAI allows 0-2, Anthropic 0-1. For CVs and cover letters 0.2-0.7 is a good range: low enough to stay factual, high enough to avoid robotic phrasing. Empty = provider default.',
  },
  {
    name: 'Top-p (nucleus sampling)',
    text: 'An alternative way to limit randomness: the model only picks from the smallest set of words covering this probability mass (0-1). Usually you tune either temperature or top-p, not both. Leave empty unless you know you want it.',
  },
  {
    name: 'Max output tokens',
    text: 'Hard cap on the length of the generated response. Empty = provider default (recommended; for Anthropic the app then uses 8192). Set it only to protect against runaway costs; too low will truncate CVs mid-sentence.',
  },
]

export default function SettingsPage() {
  const [models, setModels] = useState(null)
  const [form, setForm] = useState(EMPTY_FORM)
  const [editingId, setEditingId] = useState(null)
  const [showInfo, setShowInfo] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)
  const [notice, setNotice] = useState(null)

  function load() {
    api.aiModels().then(setModels).catch((e) => setError(e.message))
  }
  useEffect(load, [])

  const set = (field) => (e) => setForm({ ...form, [field]: e.target.value })

  function startEdit(m) {
    setEditingId(m.id)
    setForm({
      provider: m.provider,
      model: m.model,
      apiKey: '',
      temperature: m.temperature ?? '',
      topP: m.topP ?? '',
      maxTokens: m.maxTokens ?? '',
    })
    setNotice(null)
    setError(null)
  }

  function cancelEdit() {
    setEditingId(null)
    setForm(EMPTY_FORM)
  }

  async function save(e) {
    e.preventDefault()
    setSaving(true)
    setError(null)
    const payload = {
      provider: form.provider,
      model: form.model,
      apiKey: form.apiKey,
      temperature: form.temperature === '' ? null : Number(form.temperature),
      topP: form.topP === '' ? null : Number(form.topP),
      maxTokens: form.maxTokens === '' ? null : Number(form.maxTokens),
    }
    try {
      if (editingId) {
        await api.updateAiModel(editingId, payload)
        setNotice('Model updated.')
      } else {
        await api.addAiModel(payload)
        setNotice(`${form.model} added — click Activate to start using it.`)
      }
      cancelEdit()
      load()
    } catch (err) {
      setError(err.message)
    } finally {
      setSaving(false)
    }
  }

  async function activate(m) {
    setError(null)
    try {
      await api.activateAiModel(m.id)
      setNotice(`${m.model} is now active — all generations use it from now on.`)
      load()
    } catch (err) {
      setError(err.message)
    }
  }

  async function remove(m) {
    if (!window.confirm(`Delete ${m.model}?`)) return
    setError(null)
    try {
      await api.deleteAiModel(m.id)
      load()
    } catch (err) {
      setError(err.message)
    }
  }

  if (!models) return <div className="placeholder">Loading AI settings…</div>

  return (
    <div className="settings-layout">
      <div className="result-head">
        <h2>AI models</h2>
        <button
          className="btn"
          onClick={() => setShowInfo(!showInfo)}
          title="What do these parameters do?"
        >
          ⓘ {showInfo ? 'Hide' : 'What do these settings mean?'}
        </button>
      </div>
      <p className="muted">
        The <b>active</b> model generates every CV and cover letter. Switching takes effect
        immediately, no restart needed; each document records which model made it (see Usage).
      </p>

      {showInfo && (
        <div className="panel info-panel">
          {PARAM_INFO.map((p) => (
            <p key={p.name}>
              <b>{p.name}.</b> {p.text}
            </p>
          ))}
        </div>
      )}

      {error && <div className="banner error">{error}</div>}
      {notice && <div className="banner warn">{notice}</div>}

      <table className="history-table">
        <thead>
          <tr>
            <th>Provider</th>
            <th>Model</th>
            <th>API key</th>
            <th>Temp</th>
            <th>Top-p</th>
            <th>Max tokens</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {models.map((m) => (
            <tr key={m.id} className={m.active ? 'selected' : ''}>
              <td>{m.provider.toLowerCase()}</td>
              <td>
                <b>{m.model}</b>
                {m.active && <span className="chip matched active-chip">active</span>}
              </td>
              <td className="muted">{m.apiKeyMasked}</td>
              <td className="muted">{m.temperature ?? '—'}</td>
              <td className="muted">{m.topP ?? '—'}</td>
              <td className="muted">{m.maxTokens ?? '—'}</td>
              <td>
                <div className="btn-group">
                  {!m.active && (
                    <button className="btn small primary" onClick={() => activate(m)}>
                      Activate
                    </button>
                  )}
                  <button className="btn small" onClick={() => startEdit(m)}>
                    Edit
                  </button>
                  {!m.active && (
                    <button className="btn small danger" onClick={() => remove(m)}>
                      Delete
                    </button>
                  )}
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      <div className="panel">
        <h2>{editingId ? 'Edit model' : 'Add a model'}</h2>
        <form className="form-grid" onSubmit={save}>
          <div className="row2">
            <label>
              Provider
              <select value={form.provider} onChange={set('provider')}>
                <option value="OPENAI">OpenAI</option>
                <option value="ANTHROPIC">Anthropic</option>
              </select>
            </label>
            <label>
              Model name *
              <input
                value={form.model}
                onChange={set('model')}
                placeholder={form.provider === 'OPENAI' ? 'e.g. gpt-5.4, gpt-4o-mini' : 'e.g. claude-sonnet-4-5'}
                required
              />
            </label>
          </div>
          <label>
            API key {editingId ? '(leave empty to keep the current one)' : '(leave empty to reuse this provider’s stored key)'}
            <input
              type="password"
              value={form.apiKey}
              onChange={set('apiKey')}
              placeholder="sk-…"
              autoComplete="off"
            />
          </label>
          <div className="row2">
            <label>
              Temperature (0–2, empty = default)
              <input type="number" min="0" max="2" step="0.1" value={form.temperature} onChange={set('temperature')} />
            </label>
            <label>
              Top-p (0–1, empty = default)
              <input type="number" min="0" max="1" step="0.05" value={form.topP} onChange={set('topP')} />
            </label>
          </div>
          <label>
            Max output tokens (empty = provider default)
            <input type="number" min="0" step="256" value={form.maxTokens} onChange={set('maxTokens')} />
          </label>
          <div className="btn-group end">
            {editingId && (
              <button type="button" className="btn" onClick={cancelEdit} disabled={saving}>
                Cancel
              </button>
            )}
            <button type="submit" className="btn primary" disabled={saving}>
              {saving ? 'Saving…' : editingId ? 'Save changes' : 'Add model'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
