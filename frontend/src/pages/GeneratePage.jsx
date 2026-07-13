import { useEffect, useState } from 'react'
import { api } from '../api'
import CvPreview from '../components/CvPreview'
import { formatCost } from './UsagePage'

function usageLine(result) {
  if (result.promptTokens == null && result.costUsd == null) return null
  const tokens =
    result.promptTokens == null ? null : result.promptTokens + result.completionTokens
  return (
    <>
      {tokens != null && <> · {tokens.toLocaleString()} tokens</>}
      {' · '}
      {formatCost(result.costUsd)}
    </>
  )
}

const TONES = ['professional', 'enthusiastic', 'concise', 'formal']

const EMPTY_FORM = {
  jobTitle: '',
  company: '',
  location: '',
  jobDescription: '',
  extraInstructions: '',
}

export default function GeneratePage({ context }) {
  const [form, setForm] = useState(EMPTY_FORM)
  const [applicationId, setApplicationId] = useState(null)
  const [tone, setTone] = useState('professional')
  const [cv, setCv] = useState(null)
  const [letter, setLetter] = useState(null)
  const [loadingCv, setLoadingCv] = useState(false)
  const [loadingLetter, setLoadingLetter] = useState(false)
  const [error, setError] = useState(null)
  // paste-any-job-link fetch
  const [jdUrl, setJdUrl] = useState('')
  const [fetchingJd, setFetchingJd] = useState(false)
  const [jdSource, setJdSource] = useState(null)
  const [watchMessage, setWatchMessage] = useState(null)
  // fetch failures show at the top with the failing domain; dismissible
  const [jdError, setJdError] = useState(null)

  // Prefill when arriving from the board's "Generate documents" button
  useEffect(() => {
    if (!context) return
    setForm({
      jobTitle: context.jobTitle ?? '',
      company: context.company ?? '',
      location: context.location ?? '',
      jobDescription: context.jobDescription ?? '',
      extraInstructions: '',
    })
    setApplicationId(context.applicationId ?? null)
    setCv(null)
    setLetter(null)
    setError(null)
  }, [context])

  const set = (field) => (e) => setForm({ ...form, [field]: e.target.value })
  const canSubmit = form.jobDescription.trim().length > 0

  async function fetchJd() {
    if (!jdUrl.trim()) return
    setFetchingJd(true)
    setJdError(null)
    setJdSource(null)
    setWatchMessage(null)
    try {
      const jd = await api.fetchJd(jdUrl.trim())
      // fill the description; only fill title/company/location if still empty
      setForm((f) => ({
        ...f,
        jobDescription: jd.jobDescription,
        jobTitle: f.jobTitle || jd.jobTitle || '',
        company: f.company || jd.company || '',
        location: f.location || jd.location || '',
      }))
      setJdSource(jd.source)
      setWatchMessage(jd.watchMessage)
    } catch (err) {
      let domain = jdUrl.trim()
      try {
        domain = new URL(jdUrl.trim()).hostname
      } catch {
        // keep the raw input as the label
      }
      setJdError({ domain, message: err.message })
    } finally {
      setFetchingJd(false)
    }
  }

  async function tailorCv(e) {
    e.preventDefault()
    setError(null)
    setLoadingCv(true)
    setLetter(null)
    try {
      const result = await api.tailorCv({ ...form, applicationId })
      setCv(result)
      setApplicationId(result.applicationId)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoadingCv(false)
    }
  }

  async function generateLetter() {
    setError(null)
    setLoadingLetter(true)
    try {
      const result = await api.coverLetter({
        ...form,
        tone,
        applicationId,
        cvGenerationId: cv ? cv.id : null,
      })
      setLetter(result)
      setApplicationId(result.applicationId)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoadingLetter(false)
    }
  }

  return (
    <div className="generate-layout">
      <section className="panel form-panel">
        <h2>Job posting</h2>
        {jdError && (
          <div className="banner error dismissible">
            <span>
              <b>{jdError.domain}</b> — {jdError.message}
            </span>
            <button
              type="button"
              className="banner-close"
              onClick={() => setJdError(null)}
              aria-label="Dismiss error"
              title="Dismiss"
            >
              ×
            </button>
          </div>
        )}
        {applicationId && (
          <div className="notes">
            Tracked on the board as application <b>#{applicationId}</b> — documents attach to it.
          </div>
        )}
        <label className="jd-url-label">
          Job posting link (optional) — fetches the description for you
          <div className="jd-url-row">
            <input
              value={jdUrl}
              onChange={(e) => setJdUrl(e.target.value)}
              placeholder="https://… paste any job posting URL"
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  e.preventDefault()
                  fetchJd()
                }
              }}
            />
            <button
              type="button"
              className="btn"
              onClick={fetchJd}
              disabled={fetchingJd || !jdUrl.trim()}
            >
              {fetchingJd ? 'Fetching…' : 'Fetch'}
            </button>
          </div>
        </label>
        {jdSource && (
          <div className="notes">
            Description fetched from {jdSource}. Check the text below, then generate.
            {watchMessage && (
              <>
                <br />
                {watchMessage}
              </>
            )}
          </div>
        )}
        <form onSubmit={tailorCv}>
          <div className="row2">
            <label>
              Job title
              <input value={form.jobTitle} onChange={set('jobTitle')} placeholder="Backend Engineer" />
            </label>
            <label>
              Company
              <input value={form.company} onChange={set('company')} placeholder="TechCo GmbH" />
            </label>
          </div>
          <label>
            Location shown on CV &amp; letter (optional)
            <input
              value={form.location}
              onChange={set('location')}
              placeholder="defaults to profile location, e.g. Berlin, Germany"
            />
          </label>
          <label>
            Job description *
            <textarea
              rows={14}
              value={form.jobDescription}
              onChange={set('jobDescription')}
              placeholder="Paste the full job description here…"
            />
          </label>
          <label>
            Extra instructions (optional)
            <input
              value={form.extraInstructions}
              onChange={set('extraInstructions')}
              placeholder="e.g. emphasize the dataspace work"
            />
          </label>
          <button className="btn primary" type="submit" disabled={!canSubmit || loadingCv}>
            {loadingCv ? 'Tailoring CV…' : 'Tailor CV'}
          </button>
        </form>

        <div className="letter-controls">
          <h2>Cover letter</h2>
          <label>
            Tone
            <select value={tone} onChange={(e) => setTone(e.target.value)}>
              {TONES.map((t) => (
                <option key={t}>{t}</option>
              ))}
            </select>
          </label>
          <button
            className="btn"
            onClick={generateLetter}
            disabled={!canSubmit || loadingLetter}
            title={cv ? `Based on tailored CV #${cv.id}` : 'Generated from your profile'}
          >
            {loadingLetter
              ? 'Writing letter…'
              : cv
                ? `Generate cover letter (based on CV #${cv.id})`
                : 'Generate cover letter'}
          </button>
        </div>

        {error && <div className="banner error">{error}</div>}
      </section>

      <section className="results">
        {!cv && !letter && !loadingCv && !loadingLetter && (
          <div className="placeholder">
            Paste a job description and hit <b>Tailor CV</b> — the tailored CV and cover
            letter will appear here, ready to download as one-page PDFs.
          </div>
        )}

        {cv && (
          <div className="panel">
            <div className="result-head">
              <h2>
                Tailored CV <span className="muted">#{cv.id}</span>
              </h2>
              <a className="btn primary" href={api.pdfUrl(cv.id)}>
                Download PDF
              </a>
            </div>
            {(cv.cv.atsScore != null ||
              cv.cv.keywordsMatched?.length > 0 ||
              cv.cv.keywordsAdded?.length > 0 ||
              cv.cv.keywordsMissing?.length > 0) && (
              <div className="keywords">
                {cv.cv.atsScore != null && (
                  <span
                    className={`ats-badge ${cv.cv.atsScore >= 85 ? 'good' : cv.cv.atsScore >= 70 ? 'ok' : 'low'}`}
                    title="Model's estimate of the ATS keyword-match score for this CV against this job description"
                  >
                    ATS {cv.cv.atsScore}
                  </span>
                )}
                {cv.cv.keywordsMatched?.map((k) => (
                  <span key={`m-${k}`} className="chip matched" title="Matched from the job description">
                    {k}
                  </span>
                ))}
                {cv.cv.keywordsAdded?.map((k) => (
                  <span key={`a-${k}`} className="chip added" title="Added (implied by your profile)">
                    +{k}
                  </span>
                ))}
                {cv.cv.keywordsMissing?.map((k) => (
                  <span
                    key={`x-${k}`}
                    className="chip missing"
                    title="Required by the job description but not honestly supported by your profile — worth learning or addressing in the letter/interview"
                  >
                    ✕ {k}
                  </span>
                ))}
              </div>
            )}
            {cv.cv.tailoringNotes && <div className="notes">{cv.cv.tailoringNotes}</div>}
            <CvPreview data={cv.cv} />
            <div className="meta">
              generated by {cv.provider}/{cv.model}
              {usageLine(cv)}
            </div>
          </div>
        )}

        {letter && (
          <div className="panel">
            <div className="result-head">
              <h2>
                Cover letter <span className="muted">#{letter.id}</span>
              </h2>
              <a className="btn primary" href={api.pdfUrl(letter.id)}>
                Download PDF
              </a>
            </div>
            <pre className="letter-text">{letter.coverLetter}</pre>
            <div className="meta">
              generated by {letter.provider}/{letter.model}
              {usageLine(letter)}
            </div>
          </div>
        )}
      </section>
    </div>
  )
}
