export type UserRole = 'USER' | 'SYSTEM_ADMIN'

export type CurrentUser = {
  id: number
  email: string
  role: UserRole
}

export type LoginResponse = {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
  user: CurrentUser
}

export class AuthApiError extends Error {
  readonly status: number

  constructor(status: number) {
    super('Authentication request failed')
    this.status = status
  }
}

async function requestJson<T>(path: string, init: RequestInit): Promise<T> {
  const response = await fetch(path, init)

  if (!response.ok) {
    throw new AuthApiError(response.status)
  }

  return (await response.json()) as T
}

export function login(email: string, password: string): Promise<LoginResponse> {
  return requestJson<LoginResponse>('/api/auth/login', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ email, password }),
  })
}

export async function signup(email: string, password: string): Promise<void> {
  const response = await fetch('/api/auth/signup', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ email, password }),
  })

  if (!response.ok) {
    throw new AuthApiError(response.status)
  }
}

export function getCurrentUser(accessToken: string): Promise<CurrentUser> {
  return requestJson<CurrentUser>('/api/users/me', {
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
  })
}
