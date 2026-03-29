import { api } from './client'

export const getMyProfile   = ()     => api.get('/users/me')
export const updateMyProfile = (data) => api.put('/users/me', data)
