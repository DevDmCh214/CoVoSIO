import { createContext, useContext, useState } from 'react'
import { logoutUser } from '../api/auth'

const AuthContext = createContext(null)

function readStoredUser() {
  try {
    const raw = localStorage.getItem('user')
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(readStoredUser)

  function login(authResponse) {
    const { accessToken, refreshToken, role, email, firstName, lastName } = authResponse
    localStorage.setItem('access_token', accessToken)
    localStorage.setItem('refresh_token', refreshToken)
    const userInfo = { email, firstName, lastName, role }
    localStorage.setItem('user', JSON.stringify(userInfo))
    setUser(userInfo)
  }

  async function logout() {
    const refreshToken = localStorage.getItem('refresh_token')
    try {
      if (refreshToken) await logoutUser(refreshToken)
    } catch {
      // proceed with local logout even if server call fails
    }
    localStorage.removeItem('access_token')
    localStorage.removeItem('refresh_token')
    localStorage.removeItem('user')
    setUser(null)
  }

  return (
    <AuthContext.Provider value={{ user, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  return useContext(AuthContext)
}
