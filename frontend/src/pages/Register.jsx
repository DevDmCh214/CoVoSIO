import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { registerUser } from '../api/auth'
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

export default function Register() {
  const navigate  = useNavigate()
  const { login } = useAuth()

  const [form, setForm] = useState({
    firstName: '',
    lastName:  '',
    email:     '',
    phone:     '',
    password:  '',
    confirm:   '',
  })
  const [touched, setTouched] = useState({
    firstName: false, lastName: false,
    email: false, password: false, confirm: false,
  })
  const [showPwd, setShowPwd]         = useState(false)
  const [showConfirm, setShowConfirm] = useState(false)
  const [serverError, setServerError] = useState('')
  const [loading, setLoading]         = useState(false)

  function handleChange(e) {
    setForm((f) => ({ ...f, [e.target.name]: e.target.value }))
  }

  function handleBlur(e) {
    setTouched((t) => ({ ...t, [e.target.name]: true }))
  }

  const pwdRules = PASSWORD_RULES.map((r) => ({ label: r.label, met: r.test(form.password) }))
  const pwdValid = pwdRules.every((r) => r.met)

  const emailErr   = touched.email   ? emailError(form.email) : ''
  const confirmErr = touched.confirm && form.confirm && form.password !== form.confirm
    ? 'Passwords do not match.' : ''

  async function handleSubmit(e) {
    e.preventDefault()
    setTouched({ firstName: true, lastName: true, email: true, password: true, confirm: true })
    setServerError('')

    if (emailError(form.email)) return
    if (!pwdValid)              return
    if (form.password !== form.confirm) return

    setLoading(true)
    try {
      const { confirm, ...payload } = form
      const data = await registerUser(payload)
      if (!data) throw new Error('Registration failed. Please try again.')
      login(data)
      navigate('/')
    } catch (err) {
      setServerError(err.message || 'Registration failed.')
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
          <h1 className="text-2xl font-bold text-gray-800 mb-1">Create an account</h1>
          <p className="text-sm text-gray-500 mb-8">Join CoVoSIO and start sharing rides</p>

          <form onSubmit={handleSubmit} noValidate className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6 flex flex-col gap-4">
            {serverError && (
              <div className="text-sm text-red-600 bg-red-50 border border-red-200 rounded-lg px-3 py-2">
                {serverError}
              </div>
            )}

            {/* Name row */}
            <div className="grid grid-cols-2 gap-3">
              <div className="flex flex-col gap-1">
                <label htmlFor="firstName" className="text-xs font-semibold text-gray-600 uppercase tracking-wide">
                  First name
                </label>
                <input
                  id="firstName"
                  name="firstName"
                  type="text"
                  required
                  autoComplete="given-name"
                  value={form.firstName}
                  onChange={handleChange}
                  onBlur={handleBlur}
                  className={`border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:border-transparent transition ${
                    touched.firstName && !form.firstName
                      ? 'border-red-400 focus:ring-red-400'
                      : 'border-gray-200 focus:ring-blue-500'
                  }`}
                  placeholder="Alice"
                />
                {touched.firstName && !form.firstName && (
                  <p className="text-xs text-red-500 mt-0.5">Required.</p>
                )}
              </div>
              <div className="flex flex-col gap-1">
                <label htmlFor="lastName" className="text-xs font-semibold text-gray-600 uppercase tracking-wide">
                  Last name
                </label>
                <input
                  id="lastName"
                  name="lastName"
                  type="text"
                  required
                  autoComplete="family-name"
                  value={form.lastName}
                  onChange={handleChange}
                  onBlur={handleBlur}
                  className={`border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:border-transparent transition ${
                    touched.lastName && !form.lastName
                      ? 'border-red-400 focus:ring-red-400'
                      : 'border-gray-200 focus:ring-blue-500'
                  }`}
                  placeholder="Martin"
                />
                {touched.lastName && !form.lastName && (
                  <p className="text-xs text-red-500 mt-0.5">Required.</p>
                )}
              </div>
            </div>

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

            {/* Phone */}
            <div className="flex flex-col gap-1">
              <label htmlFor="phone" className="text-xs font-semibold text-gray-600 uppercase tracking-wide">
                Phone <span className="text-gray-400 normal-case font-normal">(optional)</span>
              </label>
              <input
                id="phone"
                name="phone"
                type="tel"
                autoComplete="tel"
                value={form.phone}
                onChange={handleChange}
                className="border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
                placeholder="+33 6 00 00 00 00"
              />
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
                  autoComplete="new-password"
                  value={form.password}
                  onChange={handleChange}
                  onBlur={handleBlur}
                  className={`w-full border rounded-lg px-3 py-2 pr-10 text-sm focus:outline-none focus:ring-2 focus:border-transparent transition ${
                    touched.password && !pwdValid
                      ? 'border-red-400 focus:ring-red-400'
                      : 'border-gray-200 focus:ring-blue-500'
                  }`}
                  placeholder="Min. 12 characters"
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
              {/* Live checklist */}
              {(touched.password || form.password) && (
                <ul className="mt-1.5 flex flex-col gap-1">
                  {pwdRules.map((rule) => (
                    <li key={rule.label} className={`flex items-center gap-1.5 text-xs transition-colors ${rule.met ? 'text-green-600' : 'text-gray-400'}`}>
                      <span className={`w-3.5 h-3.5 rounded-full flex items-center justify-center shrink-0 transition-colors ${
                        rule.met ? 'bg-green-100 border border-green-400' : 'border border-gray-300'
                      }`}>
                        {rule.met ? (
                          <svg className="w-2 h-2" viewBox="0 0 8 8" fill="none" stroke="currentColor" strokeWidth="1.8">
                            <path d="M1 4l2 2 4-4"/>
                          </svg>
                        ) : (
                          <span className="w-1 h-1 rounded-full bg-gray-300" />
                        )}
                      </span>
                      {rule.label}
                    </li>
                  ))}
                </ul>
              )}
            </div>

            {/* Confirm password */}
            <div className="flex flex-col gap-1">
              <label htmlFor="confirm" className="text-xs font-semibold text-gray-600 uppercase tracking-wide">
                Confirm password
              </label>
              <div className="relative">
                <input
                  id="confirm"
                  name="confirm"
                  type={showConfirm ? 'text' : 'password'}
                  autoComplete="new-password"
                  value={form.confirm}
                  onChange={handleChange}
                  onBlur={handleBlur}
                  className={`w-full border rounded-lg px-3 py-2 pr-10 text-sm focus:outline-none focus:ring-2 focus:border-transparent transition ${
                    confirmErr ? 'border-red-400 focus:ring-red-400' : 'border-gray-200 focus:ring-blue-500'
                  }`}
                  placeholder="••••••••"
                />
                <button
                  type="button"
                  onClick={() => setShowConfirm((v) => !v)}
                  className="absolute inset-y-0 right-0 flex items-center px-3 text-gray-400 hover:text-gray-600 transition-colors"
                  tabIndex={-1}
                  aria-label={showConfirm ? 'Hide password' : 'Show password'}
                >
                  <EyeIcon open={showConfirm} />
                </button>
              </div>
              {confirmErr && <p className="text-xs text-red-500 mt-0.5">{confirmErr}</p>}
            </div>

            <button
              type="submit"
              disabled={loading}
              className="mt-1 w-full py-2.5 text-sm font-semibold text-white bg-blue-600 rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              {loading ? 'Creating account…' : 'Create account'}
            </button>
          </form>

          <p className="text-center text-sm text-gray-500 mt-5">
            Already have an account?{' '}
            <Link to="/login" className="text-blue-600 font-medium hover:underline">
              Sign in
            </Link>
          </p>
        </div>
      </main>
    </div>
  )
}
