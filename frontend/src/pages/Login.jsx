import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { loginUser } from '../api/auth'
import { useAuth } from '../context/AuthContext'

const EMAIL_RE    = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/
const PASSWORD_RE = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{12,}$/

const PASSWORD_RULES = [
  { test: (p) => p.length >= 12,          label: 'At least 12 characters' },
  { test: (p) => /[a-z]/.test(p),         label: 'One lowercase letter (a–z)' },
  { test: (p) => /[A-Z]/.test(p),         label: 'One uppercase letter (A–Z)' },
  { test: (p) => /\d/.test(p),            label: 'One digit (0–9)' },
  { test: (p) => /[@$!%*?&]/.test(p),     label: 'One special character (@$!%*?&)' },
]

function emailError(value) {
  if (!value) return 'Email is required.'
  if (!EMAIL_RE.test(value)) return 'Enter a valid email address (e.g. name@domain.com).'
  return ''
}

function passwordMissingRules(value) {
  return PASSWORD_RULES.filter((r) => !r.test(value)).map((r) => r.label)
}

function EyeIcon({ open }) {
  return open ? (
    <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
      <circle cx="12" cy="12" r="3"/>
    </svg>
  ) : (
    <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M17.94 17.94A10.07 10.07 0 0112 20c-7 0-11-8-11-8a18.45 18.45 0 015.06-5.94"/>
      <path d="M9.9 4.24A9.12 9.12 0 0112 4c7 0 11 8 11 8a18.5 18.5 0 01-2.16 3.19"/>
      <line x1="1" y1="1" x2="23" y2="23"/>
    </svg>
  )
}

export default function Login() {
  const navigate  = useNavigate()
  const { login } = useAuth()

  const [form, setForm]         = useState({ email: '', password: '' })
  const [touched, setTouched]   = useState({ email: false, password: false })
  const [showPwd, setShowPwd]   = useState(false)
  const [serverError, setServerError] = useState('')
  const [loading, setLoading]   = useState(false)

  function handleChange(e) {
    setForm((f) => ({ ...f, [e.target.name]: e.target.value }))
  }

  function handleBlur(e) {
    setTouched((t) => ({ ...t, [e.target.name]: true }))
  }

  const emailErr   = touched.email    ? emailError(form.email) : ''
  const missingPwd = touched.password ? passwordMissingRules(form.password) : []

  async function handleSubmit(e) {
    e.preventDefault()
    setTouched({ email: true, password: true })
    setServerError('')

    if (emailError(form.email)) return
    if (passwordMissingRules(form.password).length > 0) return

    setLoading(true)
    try {
      const data = await loginUser(form)
      if (!data) throw new Error('Invalid email or password.')
      login(data)
      navigate('/')
    } catch (err) {
      setServerError(err.message || 'Login failed.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen flex flex-col bg-gray-50">
      <header className="bg-white shadow-sm">
        <div className="max-w-6xl mx-auto px-4 h-16 flex items-center">
          <Link to="/" className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-full bg-blue-600 flex items-center justify-center">
              <svg viewBox="0 0 24 24" fill="white" className="w-5 h-5">
                <path d="M18.92 6.01C18.72 5.42 18.16 5 17.5 5h-11c-.66 0-1.21.42-1.42 1.01L3 12v8c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-1h12v1c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-8l-2.08-5.99z"/>
                <circle cx="7.5" cy="14.5" r="1.5"/>
                <circle cx="16.5" cy="14.5" r="1.5"/>
              </svg>
            </div>
            <span className="text-xl font-bold text-blue-600 tracking-tight">CoVoSIO</span>
          </Link>
        </div>
      </header>

      <main className="flex-1 flex items-center justify-center px-4 py-12">
        <div className="w-full max-w-sm">
          <h1 className="text-2xl font-bold text-gray-800 mb-1">Welcome back</h1>
          <p className="text-sm text-gray-500 mb-8">Log in to your CoVoSIO account</p>

          <form onSubmit={handleSubmit} noValidate className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6 flex flex-col gap-4">
            {serverError && (
              <div className="text-sm text-red-600 bg-red-50 border border-red-200 rounded-lg px-3 py-2">
                {serverError}
              </div>
            )}

            {/* Email */}
            <div className="flex flex-col gap-1">
              <label htmlFor="email" className="text-xs font-semibold text-gray-600 uppercase tracking-wide">
                Email
              </label>
              <input
                id="email"
                name="email"
                type="email"
                autoComplete="email"
                value={form.email}
                onChange={handleChange}
                onBlur={handleBlur}
                className={`border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:border-transparent transition ${
                  emailErr ? 'border-red-400 focus:ring-red-400' : 'border-gray-200 focus:ring-blue-500'
                }`}
                placeholder="you@example.com"
              />
              {emailErr && <p className="text-xs text-red-500 mt-0.5">{emailErr}</p>}
            </div>

            {/* Password */}
            <div className="flex flex-col gap-1">
              <label htmlFor="password" className="text-xs font-semibold text-gray-600 uppercase tracking-wide">
                Password
              </label>
              <div className="relative">
                <input
                  id="password"
                  name="password"
                  type={showPwd ? 'text' : 'password'}
                  autoComplete="current-password"
                  value={form.password}
                  onChange={handleChange}
                  onBlur={handleBlur}
                  className={`w-full border rounded-lg px-3 py-2 pr-10 text-sm focus:outline-none focus:ring-2 focus:border-transparent transition ${
                    missingPwd.length > 0 ? 'border-red-400 focus:ring-red-400' : 'border-gray-200 focus:ring-blue-500'
                  }`}
                  placeholder="••••••••"
                />
                <button
                  type="button"
                  onClick={() => setShowPwd((v) => !v)}
                  className="absolute inset-y-0 right-0 flex items-center px-3 text-gray-400 hover:text-gray-600 transition-colors"
                  tabIndex={-1}
                  aria-label={showPwd ? 'Hide password' : 'Show password'}
                >
                  <EyeIcon open={showPwd} />
                </button>
              </div>
              {missingPwd.length > 0 && (
                <ul className="mt-1 flex flex-col gap-0.5">
                  {missingPwd.map((rule) => (
                    <li key={rule} className="flex items-center gap-1.5 text-xs text-red-500">
                      <span className="w-3.5 h-3.5 rounded-full border border-red-400 flex items-center justify-center shrink-0">
                        <svg className="w-2 h-2" viewBox="0 0 8 8" fill="none" stroke="currentColor" strokeWidth="1.5">
                          <path d="M1.5 1.5l5 5M6.5 1.5l-5 5"/>
                        </svg>
                      </span>
                      {rule}
                    </li>
                  ))}
                </ul>
              )}
            </div>

            <button
              type="submit"
              disabled={loading}
              className="mt-1 w-full py-2.5 text-sm font-semibold text-white bg-blue-600 rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              {loading ? 'Signing in…' : 'Sign in'}
            </button>
          </form>

          <p className="text-center text-sm text-gray-500 mt-5">
            No account yet?{' '}
            <Link to="/register" className="text-blue-600 font-medium hover:underline">
              Create one
            </Link>
          </p>
        </div>
      </main>
    </div>
  )
}
