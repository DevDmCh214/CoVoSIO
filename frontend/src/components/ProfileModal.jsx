import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getMyProfile, updateMyProfile } from '../api/user'
import { useAuth } from '../context/AuthContext'

function Avatar({ avatarUrl, firstName, lastName, onClick }) {
  const initials = `${firstName?.[0] ?? ''}${lastName?.[0] ?? ''}`.toUpperCase()

  const inner = avatarUrl ? (
    <img
      src={avatarUrl}
      alt="Profile"
      className="w-20 h-20 rounded-full object-cover"
    />
  ) : (
    <div className="w-20 h-20 rounded-full bg-blue-100 text-blue-600 text-2xl font-bold flex items-center justify-center select-none">
      {initials || '?'}
    </div>
  )

  if (onClick) {
    return (
      <button
        type="button"
        onClick={onClick}
        className="relative group rounded-full focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
        aria-label="Change profile picture"
      >
        {inner}
        {/* Camera overlay — visible on hover, ready for upload handler */}
        <span className="absolute inset-0 rounded-full bg-black/40 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity">
          <svg className="w-6 h-6 text-white" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M23 19a2 2 0 01-2 2H3a2 2 0 01-2-2V8a2 2 0 012-2h4l2-3h6l2 3h4a2 2 0 012 2z"/>
            <circle cx="12" cy="13" r="4"/>
          </svg>
        </span>
      </button>
    )
  }

  return inner
}

function buildForm(data) {
  return {
    firstName: data?.firstName ?? '',
    lastName:  data?.lastName  ?? '',
    phone:     data?.phone     ?? '',
  }
}

export default function ProfileModal({ onClose }) {
  const { user, updateUser } = useAuth()
  const navigate = useNavigate()

  // Seed from AuthContext immediately so email/name are visible before the API responds
  const [profile, setProfile] = useState(() =>
    user ? { email: user.email, firstName: user.firstName, lastName: user.lastName, role: user.role } : null
  )
  const [editing, setEditing] = useState(false)
  const [form, setForm]       = useState(() => buildForm(
    user ? { firstName: user.firstName, lastName: user.lastName, phone: '' } : {}
  ))
  const [loading, setLoading] = useState(true)
  const [saving, setSaving]   = useState(false)
  const [error, setError]     = useState('')
  const backdropRef           = useRef(null)

  useEffect(() => {
    getMyProfile()
      .then((data) => {
        if (data) {
          setProfile(data)
          setForm(buildForm(data))
        }
      })
      .finally(() => setLoading(false))
  }, [])

  function handleBackdrop(e) {
    if (e.target === backdropRef.current) onClose()
  }

  function handleChange(e) {
    setForm((f) => ({ ...f, [e.target.name]: e.target.value }))
  }

  function handleEdit() {
    // Reset form to current saved profile before opening edit mode
    setForm(buildForm(profile))
    setError('')
    setEditing(true)
  }

  function handleCancel() {
    setForm(buildForm(profile))
    setError('')
    setEditing(false)
  }

  async function handleSave() {
    // Nothing changed — just close without a round-trip
    const unchanged =
      form.firstName === (profile?.firstName ?? '') &&
      form.lastName  === (profile?.lastName  ?? '') &&
      form.phone     === (profile?.phone     ?? '')
    if (unchanged) {
      setEditing(false)
      return
    }

    setError('')
    setSaving(true)
    try {
      const updated = await updateMyProfile({
        firstName: form.firstName,
        lastName:  form.lastName,
        phone:     form.phone,
        avatarUrl: profile?.avatarUrl ?? '',
      })
      if (!updated) throw new Error('Update failed.')
      setProfile(updated)
      setForm(buildForm(updated))
      updateUser({ firstName: updated.firstName, lastName: updated.lastName })
      setEditing(false)
    } catch (err) {
      setError(err.message || 'Could not save changes.')
    } finally {
      setSaving(false)
    }
  }

  // Placeholder — will open picture upload in a future implementation
  function handleAvatarClick() {
    // TODO: open picture upload window
  }

  const isDriver = user?.role === 'ROLE_DRIVER'

  return (
    <div
      ref={backdropRef}
      onClick={handleBackdrop}
      className="fixed inset-0 z-[2000] flex items-center justify-center bg-black/40 backdrop-blur-sm px-4"
    >
      <div className="w-full max-w-sm bg-white rounded-2xl shadow-2xl border border-gray-100">
        {/* Top bar: close + edit */}
        <div className="flex items-center justify-between px-5 pt-4 pb-2">
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 transition-colors"
            aria-label="Close"
          >
            <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
              <path d="M18 6L6 18M6 6l12 12"/>
            </svg>
          </button>

          {!editing && !loading && (
            <button
              onClick={handleEdit}
              className="text-xs font-semibold text-blue-600 border border-blue-200 rounded-md px-3 py-1 hover:bg-blue-50 transition-colors"
            >
              Edit
            </button>
          )}
        </div>

        <div className="px-5 pb-6 flex flex-col items-center gap-4">
          {loading && !profile && (
            <p className="py-4 text-sm text-gray-400">Loading…</p>
          )}
            {/* Avatar */}
            <Avatar
              avatarUrl={profile?.avatarUrl}
              firstName={profile?.firstName}
              lastName={profile?.lastName}
              onClick={editing ? handleAvatarClick : undefined}
            />

            {error && (
              <p className="w-full text-xs text-red-500 bg-red-50 border border-red-200 rounded-lg px-3 py-2">
                {error}
              </p>
            )}

            {editing ? (
              /* ── Edit form ── */
              <div className="w-full flex flex-col gap-3">
                <div className="grid grid-cols-2 gap-2">
                  <Field label="First name">
                    <input
                      name="firstName"
                      value={form.firstName}
                      onChange={handleChange}
                      className="w-full border border-gray-200 rounded-lg px-2.5 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                      placeholder="Alice"
                    />
                  </Field>
                  <Field label="Last name">
                    <input
                      name="lastName"
                      value={form.lastName}
                      onChange={handleChange}
                      className="w-full border border-gray-200 rounded-lg px-2.5 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                      placeholder="Martin"
                    />
                  </Field>
                </div>

                <Field label="Phone">
                  <input
                    name="phone"
                    value={form.phone}
                    onChange={handleChange}
                    className="w-full border border-gray-200 rounded-lg px-2.5 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    placeholder="+33 6 00 00 00 00"
                  />
                </Field>

                <div className="flex gap-2 pt-1">
                  <button
                    type="button"
                    onClick={handleCancel}
                    className="flex-1 py-2 text-sm font-medium text-gray-600 border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors"
                  >
                    Cancel
                  </button>
                  <button
                    type="button"
                    onClick={handleSave}
                    disabled={saving}
                    className="flex-1 py-2 text-sm font-semibold text-white bg-blue-600 rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors"
                  >
                    {saving ? 'Saving…' : 'Save'}
                  </button>
                </div>
              </div>
            ) : (
              /* ── View mode ── */
              <div className="w-full flex flex-col items-center gap-4">
                <div className="text-center">
                  <p className="text-lg font-bold text-gray-800">
                    {profile?.firstName} {profile?.lastName}
                  </p>
                  <p className="text-xs text-gray-400">
                    {isDriver ? 'Driver' : 'Passenger'}
                    {profile?.avgRating != null && (
                      <span className="ml-2 inline-flex items-center gap-0.5 text-yellow-500">
                        <svg className="w-3 h-3" viewBox="0 0 24 24" fill="currentColor">
                          <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/>
                        </svg>
                        {Number(profile.avgRating).toFixed(1)}
                      </span>
                    )}
                  </p>
                </div>

                <div className="w-full flex flex-col gap-1.5">
                  <InfoRow icon="email" label={profile?.email} />
                  <InfoRow
                    icon="phone"
                    label={profile?.phone || <span className="text-gray-300 italic">No phone</span>}
                  />
                </div>

                <div className="w-full flex flex-col gap-2">
                  <button
                    onClick={() => { onClose(); navigate('/my-rides') }}
                    className="w-full py-2.5 text-sm font-semibold text-blue-600 border-2 border-blue-600 rounded-lg hover:bg-blue-50 transition-colors"
                  >
                    My rides
                  </button>

                  {isDriver ? (
                    <button
                      onClick={() => { onClose(); navigate('/driver') }}
                      className="w-full py-2.5 text-sm font-semibold text-white bg-blue-600 rounded-lg hover:bg-blue-700 transition-colors"
                    >
                      Driver's account
                    </button>
                  ) : (
                    <button
                      onClick={() => { onClose(); navigate('/become-driver') }}
                      className="w-full py-2.5 text-sm font-semibold text-white bg-blue-600 rounded-lg hover:bg-blue-700 transition-colors"
                    >
                      Become a driver
                    </button>
                  )}
                </div>
              </div>
            )}
        </div>
      </div>
    </div>
  )
}


function Field({ label, children }) {
  return (
    <div className="flex flex-col gap-0.5">
      <label className="text-[10px] font-semibold text-gray-500 uppercase tracking-wide">
        {label}
      </label>
      {children}
    </div>
  )
}

function InfoRow({ icon, label }) {
  const icons = {
    email: (
      <svg className="w-3.5 h-3.5 text-gray-400 shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <rect x="2" y="4" width="20" height="16" rx="2"/>
        <path d="M22 7l-10 7L2 7"/>
      </svg>
    ),
    phone: (
      <svg className="w-3.5 h-3.5 text-gray-400 shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <path d="M22 16.92v3a2 2 0 01-2.18 2 19.79 19.79 0 01-8.63-3.07A19.5 19.5 0 013.07 10.8 19.79 19.79 0 01.01 2.18 2 2 0 012 0h3a2 2 0 012 1.72c.127.96.361 1.903.7 2.81a2 2 0 01-.45 2.11L6.09 7.91a16 16 0 006 6l1.27-1.27a2 2 0 012.11-.45c.907.339 1.85.573 2.81.7A2 2 0 0122 16.92z"/>
      </svg>
    ),
  }
  return (
    <div className="flex items-center gap-2 text-gray-600">
      {icons[icon]}
      <span className="text-sm truncate">{label}</span>
    </div>
  )
}
