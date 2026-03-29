const BASE_URL = '/api'

// Set to false to silence API logs in production builds
const LOG = import.meta.env.DEV

async function request(path, options = {}) {
  const method = options.method ?? 'GET'
  const token  = localStorage.getItem('access_token')

  const headers = {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...options.headers,
  }

  const label = `${method} ${BASE_URL}${path}`
  if (LOG) {
    console.groupCollapsed(`%c⬆ ${label}`, 'color:#6366f1;font-weight:600')
    if (options.body) console.log('body →', JSON.parse(options.body))
    console.groupEnd()
  }

  let res
  try {
    res = await fetch(`${BASE_URL}${path}`, { ...options, headers })
  } catch (err) {
    if (LOG) console.error(`%c✖ ${label} — network error`, 'color:#ef4444', err.message)
    return null
  }

  // Clone before consuming so we can log and still return the body
  const clone = res.clone()
  const body  = res.status !== 204
    ? await clone.json().catch(() => null)
    : null

  if (LOG) {
    const ok    = res.ok
    const color = ok ? '#22c55e' : '#ef4444'
    const icon  = ok ? '⬇' : '✖'
    console.groupCollapsed(`%c${icon} ${label} — ${res.status}`, `color:${color};font-weight:600`)
    if (body !== null) console.log('response →', body)
    console.groupEnd()
  }

  if (res.status === 401) return null
  if (res.status === 204) return null

  if (!res.ok) {
    throw new Error(body?.message || `HTTP ${res.status}`)
  }

  return body
}

export const api = {
  get: (path, params) => {
    const url = params
      ? `${path}?${new URLSearchParams(
          Object.fromEntries(Object.entries(params).filter(([, v]) => v != null && v !== ''))
        )}`
      : path
    return request(url)
  },
  post:   (path, body) => request(path, { method: 'POST',   body: JSON.stringify(body) }),
  put:    (path, body) => request(path, { method: 'PUT',    body: JSON.stringify(body) }),
  delete: (path)       => request(path, { method: 'DELETE' }),
}
