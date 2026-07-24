import request from '@/utils/request'

export interface LoginForm {
  username: string
  password: string
}

export interface LoginResult {
  accessToken: string
  refreshToken: string
  expiresIn: number
  user: {
    id: number
    username: string
    displayName: string
    avatar: string
    roles: string[]
  }
}

export function login(data: LoginForm) {
  return request.post<{ data: LoginResult }>('/auth/login', data)
}

export function refreshToken() {
  return request.post<{ data: LoginResult }>('/auth/refresh')
}

export function getCurrentUser() {
  return request.get<{ data: LoginResult['user'] }>('/auth/me')
}
