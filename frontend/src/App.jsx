import { useEffect, useState } from 'react'
import { api } from './api'
import BoardPage from './pages/BoardPage'
import DashboardPage from './pages/DashboardPage'
import JobsPage from './pages/JobsPage'
import GeneratePage from './pages/GeneratePage'
import ProfilePage from './pages/ProfilePage'
import HistoryPage from './pages/HistoryPage'
import UsagePage from './pages/UsagePage'
import SettingsPage from './pages/SettingsPage'
import './App.css'

const TABS = [
  { id: 'board', label: 'Board' },
  { id: 'dashboard', label: 'Dashboard' },
  { id: 'jobs', label: 'Jobs' },
  { id: 'generate', label: 'Generate' },
  { id: 'profile', label: 'Profile' },
  { id: 'history', label: 'History' },
  { id: 'usage', label: 'Usage' },
  { id: 'settings', label: 'Settings' },
]

function tabFromHash() {
  const hash = window.location.hash.replace('#', '')
  return TABS.some((t) => t.id === hash) ? hash : 'board'
}

export default function App() {
  // The URL hash is the source of truth for the active tab, so the browser's
  // back/forward buttons navigate between tabs instead of leaving the app.
  const [tab, setTabState] = useState(tabFromHash)
  const [health, setHealth] = useState(null)
  const [offline, setOffline] = useState(false)
  // Set from the board's "Generate documents" button; consumed by GeneratePage.
  const [generateContext, setGenerateContext] = useState(null)

  function setTab(id) {
    if (id === tab) return
    window.location.hash = id // pushes a history entry; hashchange updates state
  }

  useEffect(() => {
    const onHashChange = () => setTabState(tabFromHash())
    window.addEventListener('hashchange', onHashChange)
    return () => window.removeEventListener('hashchange', onHashChange)
  }, [])

  function openGenerate(context) {
    setGenerateContext({ ...context, requestedAt: Date.now() })
    setTab('generate')
  }

  useEffect(() => {
    let cancelled = false
    const check = () =>
      api
        .health()
        .then((h) => {
          if (!cancelled) {
            setHealth(h)
            setOffline(false)
          }
        })
        .catch(() => {
          if (!cancelled) setOffline(true)
        })
    check()
    const timer = setInterval(check, 15000)
    return () => {
      cancelled = true
      clearInterval(timer)
    }
  }, [])

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          <span className="brand-mark">JM</span>
          <span className="brand-name">JobMaster</span>
        </div>
        <nav className="tabs">
          {TABS.map((t) => (
            <button
              key={t.id}
              className={`tab ${tab === t.id ? 'active' : ''}`}
              onClick={() => setTab(t.id)}
            >
              {t.label}
            </button>
          ))}
        </nav>
        <div className="health">
          {offline ? (
            <span className="health-chip offline" title="Backend not reachable on port 8080">
              ● backend offline
            </span>
          ) : health ? (
            <span
              className="health-chip online"
              title={health.profileConfigured ? 'Profile configured' : 'No profile configured yet'}
            >
              ● {health.aiProvider} / {health.aiModel}
            </span>
          ) : (
            <span className="health-chip">…</span>
          )}
        </div>
      </header>

      {offline && (
        <div className="banner error">
          Backend is not reachable. Start it with{' '}
          <code>cd Backend &amp;&amp; ./gradlew bootRun</code> and this page will reconnect.
        </div>
      )}
      {health && !health.profileConfigured && !offline && (
        <div className="banner warn">
          No profile is stored yet — set it up in the <b>Profile</b> tab before generating.
        </div>
      )}

      <main className="content">
        {tab === 'board' && <BoardPage onGenerate={openGenerate} />}
        {tab === 'dashboard' && <DashboardPage />}
        {tab === 'jobs' && <JobsPage onGenerate={openGenerate} />}
        {tab === 'generate' && <GeneratePage context={generateContext} />}
        {tab === 'profile' && <ProfilePage />}
        {tab === 'history' && <HistoryPage />}
        {tab === 'usage' && <UsagePage />}
        {tab === 'settings' && <SettingsPage />}
      </main>
    </div>
  )
}
