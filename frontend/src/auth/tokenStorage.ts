import type { CurrentUser } from '../api/authApi'

export const ACCESS_TOKEN_STORAGE_KEY = 'prizm_access_token'
const CURRENT_USER_STORAGE_KEY = 'prizm_current_user'

export function saveAccessToken(accessToken: string): void {
  localStorage.setItem(ACCESS_TOKEN_STORAGE_KEY, accessToken)
}

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_STORAGE_KEY)
}

export function saveCurrentUser(currentUser: CurrentUser): void {
  localStorage.setItem(CURRENT_USER_STORAGE_KEY, JSON.stringify(currentUser))
}

export function getStoredCurrentUser(): CurrentUser | null {
  const value = localStorage.getItem(CURRENT_USER_STORAGE_KEY)

  if (value === null) {
    return null
  }

  try {
    return JSON.parse(value) as CurrentUser
  } catch {
    localStorage.removeItem(CURRENT_USER_STORAGE_KEY)
    return null
  }
}

export function clearSession(): void {
  localStorage.removeItem(ACCESS_TOKEN_STORAGE_KEY)
  localStorage.removeItem(CURRENT_USER_STORAGE_KEY)
}
