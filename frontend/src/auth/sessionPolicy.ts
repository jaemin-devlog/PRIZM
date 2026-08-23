export function isSessionExpiredError(error: unknown): boolean {
  if (typeof error !== 'object' || error === null || !('status' in error)) {
    return false
  }
  return error.status === 401
}

export function expireSessionIfUnauthorized(
  error: unknown,
  onSessionExpired: () => void,
): boolean {
  if (!isSessionExpiredError(error)) {
    return false
  }
  onSessionExpired()
  return true
}
