import { useEffect, useState } from 'react'
import { api } from '../api'
import CvPreview from '../components/CvPreview'

const EMPTY_PROFILE = {
  fullName: '',
  email: '',
  phone: '',
  location: '',
  links: {},
  headline: '',
  summary: '',
  skills: {},
  experiences: [],
  education: [],
  projects: [],
  certifications: [],
  languages: [],
}

export default function ProfilePage() {
  const [profile, setProfile] = useState(null)
  const [missing, setMissing] = useState(false)
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)
  const [loaded, setLoaded] = useState(false)

  useEffect(() => {
    api
      .getProfile()
      .then((p) => setProfile(p))
      .catch((err) => {
        if (err.status === 404) setMissing(true)
        else setError(err.message)
      })
      .finally(() => setLoaded(true))
  }, [])

  function startEditing() {
    setDraft(JSON.stringify(profile ?? EMPTY_PROFILE, null, 2))
    setEditing(true)
    setError(null)
  }

  async function save() {
    let parsed
    try {
      parsed = JSON.parse(draft)
    } catch {
      setError('Not valid JSON — fix the highlighted syntax and try again.')
      return
    }
    setSaving(true)
    setError(null)
    try {
      const saved = await api.saveProfile(parsed)
      setProfile(saved)
      setMissing(false)
      setEditing(false)
    } catch (err) {
      setError(err.message)
    } finally {
      setSaving(false)
    }
  }

  if (!loaded) return <div className="placeholder">Loading profile…</div>

  return (
    <div className="profile-layout">
      <div className="result-head">
        <h2>Master profile</h2>
        {!editing ? (
          <button className="btn" onClick={startEditing}>
            {missing ? 'Create profile' : 'Edit as JSON'}
          </button>
        ) : (
          <div className="btn-group">
            <button className="btn" onClick={() => setEditing(false)} disabled={saving}>
              Cancel
            </button>
            <button className="btn primary" onClick={save} disabled={saving}>
              {saving ? 'Saving…' : 'Save profile'}
            </button>
          </div>
        )}
      </div>

      <p className="muted">
        This is the single source of truth the AI tailors from — it will never invent
        anything that isn&apos;t in here. Keep it complete and current; the AI trims it
        per job, so more detail is better.
      </p>

      {error && <div className="banner error">{error}</div>}

      {editing ? (
        <textarea
          className="json-editor"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          spellCheck={false}
          rows={30}
        />
      ) : missing ? (
        <div className="placeholder">
          No profile stored yet. Click <b>Create profile</b> and fill in your details —
          or PUT the profile from <code>Backend/requests.http</code>.
        </div>
      ) : (
        <div className="panel">
          <CvPreview data={profile} contact={profile} />
        </div>
      )}
    </div>
  )
}
