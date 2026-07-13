// Thin client for the JobMaster backend. All calls go through the Vite dev
// proxy (see vite.config.js), so paths are same-origin.

async function request(path, options = {}) {
  let res
  try {
    res = await fetch(path, {
      headers: { 'Content-Type': 'application/json' },
      ...options,
    })
  } catch {
    const err = new Error('Cannot reach the backend. Is it running on port 8080?')
    err.offline = true
    throw err
  }
  if (!res.ok) {
    let message = `Request failed (HTTP ${res.status})`
    try {
      const body = await res.json()
      if (body.message) message = body.message
    } catch {
      // keep default message
    }
    const err = new Error(message)
    err.status = res.status
    throw err
  }
  if (res.status === 204) return null
  return res.json()
}

export const api = {
  health: () => request('/api/health'),
  getProfile: () => request('/api/profile'),
  saveProfile: (profile) =>
    request('/api/profile', { method: 'PUT', body: JSON.stringify(profile) }),
  tailorCv: (payload) =>
    request('/api/cv', { method: 'POST', body: JSON.stringify(payload) }),
  coverLetter: (payload) =>
    request('/api/cover-letter', { method: 'POST', body: JSON.stringify(payload) }),
  generations: () => request('/api/generations'),
  generation: (id) => request(`/api/generations/${id}`),
  pdfUrl: (id) => `/api/generations/${id}/pdf`,
  usage: () => request('/api/usage'),
  dashboard: () => request('/api/dashboard'),
  fetchJd: (url) => request('/api/jd-fetch', { method: 'POST', body: JSON.stringify({ url }) }),
  dashboardSummary: () => request('/api/dashboard/summary', { method: 'POST' }),
  applications: () => request('/api/applications'),
  application: (id) => request(`/api/applications/${id}`),
  createApplication: (payload) =>
    request('/api/applications', { method: 'POST', body: JSON.stringify(payload) }),
  updateApplication: (id, payload) =>
    request(`/api/applications/${id}`, { method: 'PATCH', body: JSON.stringify(payload) }),
  deleteApplication: (id) => request(`/api/applications/${id}`, { method: 'DELETE' }),
  aiModels: () => request('/api/ai/models'),
  addAiModel: (payload) =>
    request('/api/ai/models', { method: 'POST', body: JSON.stringify(payload) }),
  updateAiModel: (id, payload) =>
    request(`/api/ai/models/${id}`, { method: 'PATCH', body: JSON.stringify(payload) }),
  activateAiModel: (id) => request(`/api/ai/models/${id}/activate`, { method: 'POST' }),
  deleteAiModel: (id) => request(`/api/ai/models/${id}`, { method: 'DELETE' }),
  watchCompanies: () => request('/api/watch/companies'),
  createWatchCompany: (payload) =>
    request('/api/watch/companies', { method: 'POST', body: JSON.stringify(payload) }),
  updateWatchCompany: (id, payload) =>
    request(`/api/watch/companies/${id}`, { method: 'PATCH', body: JSON.stringify(payload) }),
  deleteWatchCompany: (id) => request(`/api/watch/companies/${id}`, { method: 'DELETE' }),
  discoverCompanies: (names) =>
    request('/api/watch/discover', { method: 'POST', body: JSON.stringify({ names }) }),
  addDiscovered: (items) =>
    request('/api/watch/discover/add', { method: 'POST', body: JSON.stringify({ items }) }),
  watchRunAll: () => request('/api/watch/run', { method: 'POST' }),
  watchRunCompany: (id) => request(`/api/watch/companies/${id}/run`, { method: 'POST' }),
  jobs: (status) => request(`/api/jobs?status=${status}`),
  dismissJob: (id) => request(`/api/jobs/${id}/dismiss`, { method: 'POST' }),
  convertJob: (id) => request(`/api/jobs/${id}/convert`, { method: 'POST' }),
  markJobsSeen: () => request('/api/jobs/mark-seen', { method: 'POST' }),
}
