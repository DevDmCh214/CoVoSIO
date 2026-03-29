import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import ProfileModal from './ProfileModal'

function NavAvatar({ user }) {
  const initials = `${user.firstName?.[0] ?? ''}${user.lastName?.[0] ?? ''}`.toUpperCase()
  if (user.avatarUrl) {
    return (
      <img
        src={user.avatarUrl}
        alt="Profile"
        className="w-8 h-8 rounded-full object-cover border border-gray-200"
      />
    )
  }
  return (
    <div className="w-8 h-8 rounded-full bg-blue-100 text-blue-600 text-xs font-bold flex items-center justify-center select-none border border-blue-200">
      {initials || '?'}
    </div>
  )
}

export default function Navbar() {
  const { user, logout }          = useAuth()
  const navigate                  = useNavigate()
  const [profileOpen, setProfileOpen] = useState(false)

  async function handleLogout() {
    await logout()
    navigate('/')
  }

  return (
    <>
      <header className="bg-white shadow-sm sticky top-0 z-[1000]">
        <div className="max-w-6xl mx-auto px-4 h-16 flex items-center justify-between gap-4">

          {/* Logo */}
          <Link to="/" className="flex items-center gap-2 shrink-0">
            <div className="w-8 h-8 rounded-full bg-blue-600 flex items-center justify-center">
              <svg viewBox="0 0 24 24" fill="white" className="w-5 h-5">
                <path d="M18.92 6.01C18.72 5.42 18.16 5 17.5 5h-11c-.66 0-1.21.42-1.42 1.01L3 12v8c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-1h12v1c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-8l-2.08-5.99z"/>
                <circle cx="7.5" cy="14.5" r="1.5"/>
                <circle cx="16.5" cy="14.5" r="1.5"/>
              </svg>
            </div>
            <span className="text-xl font-bold text-blue-600 tracking-tight">CoVoSIO</span>
          </Link>

          {/* Tagline */}
          <p className="hidden md:block text-sm text-gray-400 text-center">
            Share the road, share the journey
          </p>

          {/* Auth area */}
          <div className="flex items-center gap-2 shrink-0">
            {user ? (
              <>
                <span className="hidden sm:block text-sm text-gray-600">
                  Hi, <span className="font-semibold text-gray-800">{user.firstName}</span>
                </span>

                {/* Avatar — opens profile modal */}
                <button
                  onClick={() => setProfileOpen(true)}
                  className="rounded-full focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-1"
                  aria-label="Open profile"
                >
                  <NavAvatar user={user} />
                </button>

                <button
                  onClick={handleLogout}
                  className="px-4 py-2 text-sm font-medium text-gray-600 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
                >
                  Logout
                </button>
              </>
            ) : (
              <>
                <Link
                  to="/login"
                  className="px-4 py-2 text-sm font-medium text-blue-600 border border-blue-600 rounded-lg hover:bg-blue-50 transition-colors"
                >
                  Login
                </Link>
                <Link
                  to="/register"
                  className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700 transition-colors"
                >
                  Register
                </Link>
              </>
            )}
          </div>
        </div>
      </header>

      {profileOpen && <ProfileModal onClose={() => setProfileOpen(false)} />}
    </>
  )
}
