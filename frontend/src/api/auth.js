import { api } from './client'

export function loginUser({ email, password }) {
  return api.post('/auth/login', { email, password })
}

export function registerUser({ email, password, firstName, lastName, phone }) {
  return api.post('/auth/register', { email, password, firstName, lastName, ...(phone ? { phone } : {}) })
}

export function logoutUser(refreshToken) {
  return api.post('/auth/logout', { refreshToken })
}
