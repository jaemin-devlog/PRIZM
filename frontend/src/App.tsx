import { type FormEvent, useEffect, useState } from 'react'
import {
  AuthApiError,
  getCurrentUser,
  login,
  type CurrentUser,
} from './api/authApi'
import {
  clearSession,
  getAccessToken,
  getStoredCurrentUser,
  saveAccessToken,
  saveCurrentUser,
} from './auth/tokenStorage'

const LOGIN_PATH = '/login'
const CAREER_VAULT_PATH = '/career-vault'

type AppPath = typeof LOGIN_PATH | typeof CAREER_VAULT_PATH

function toAppPath(pathname: string): AppPath {
  return pathname === CAREER_VAULT_PATH ? CAREER_VAULT_PATH : LOGIN_PATH
}

function App() {
  const [path, setPath] = useState<AppPath>(() => toAppPath(window.location.pathname))
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>(() => {
    return window.location.pathname === CAREER_VAULT_PATH ? getStoredCurrentUser() : null
  })

  useEffect(() => {
    const handlePopState = () => setPath(toAppPath(window.location.pathname))
    window.addEventListener('popstate', handlePopState)
    return () => window.removeEventListener('popstate', handlePopState)
  }, [])

  const navigate = (nextPath: AppPath) => {
    window.history.pushState(null, '', nextPath)
    setPath(nextPath)
  }

  const handleLogin = async (email: string, password: string): Promise<string | null> => {
    clearSession()

    let accessToken: string
    try {
      const response = await login(email, password)
      accessToken = response.accessToken
    } catch (error) {
      clearSession()
      return loginErrorMessage(error)
    }

    try {
      saveAccessToken(accessToken)
      const user = await getCurrentUser(accessToken)
      saveCurrentUser(user)
      setCurrentUser(user)
      navigate(CAREER_VAULT_PATH)
      return null
    } catch {
      clearSession()
      return '로그인 상태를 확인하지 못했습니다. 다시 로그인해 주세요.'
    }
  }

  const handleLogout = () => {
    clearSession()
    setCurrentUser(null)
    navigate(LOGIN_PATH)
  }

  if (path === CAREER_VAULT_PATH && currentUser !== null && getAccessToken() !== null) {
    return <CareerVault currentUser={currentUser} onLogout={handleLogout} />
  }

  return <LoginPage onLogin={handleLogin} />
}

function LoginPage({ onLogin }: { onLogin: (email: string, password: string) => Promise<string | null> }) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()

    if (email.trim() === '' || password === '') {
      setErrorMessage('이메일과 비밀번호를 입력해 주세요.')
      return
    }

    setErrorMessage(null)
    setIsSubmitting(true)
    const error = await onLogin(email.trim(), password)
    setIsSubmitting(false)

    if (error !== null) {
      setErrorMessage(error)
    }
  }

  return (
    <main className="shell">
      <p className="eyebrow">PRIZM</p>
      <h1>로그인</h1>
      <p className="description">개인의 커리어 문서와 원문 근거를 안전하게 관리합니다.</p>

      <form className="login-form" onSubmit={handleSubmit} noValidate>
        <label htmlFor="email">이메일</label>
        <input
          id="email"
          name="email"
          type="email"
          autoComplete="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          disabled={isSubmitting}
        />

        <label htmlFor="password">비밀번호</label>
        <input
          id="password"
          name="password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          disabled={isSubmitting}
        />

        {errorMessage !== null && (
          <p className="form-error" role="alert">
            {errorMessage}
          </p>
        )}

        <button type="submit" disabled={isSubmitting}>
          {isSubmitting ? '로그인 중…' : '로그인'}
        </button>
      </form>
    </main>
  )
}

function CareerVault({ currentUser, onLogout }: { currentUser: CurrentUser; onLogout: () => void }) {
  return (
    <main className="shell">
      <p className="eyebrow">PRIZM</p>
      <h1>로그인 성공</h1>
      <p className="description">{currentUser.email}</p>
      <p className="notice">Career Vault는 다음 단계에서 구현됩니다.</p>
      <button type="button" className="secondary-button" onClick={onLogout}>
        로그아웃
      </button>
    </main>
  )
}

function loginErrorMessage(error: unknown): string {
  if (error instanceof AuthApiError) {
    if (error.status === 400 || error.status === 401) {
      return '이메일 또는 비밀번호를 확인해 주세요.'
    }

    if (error.status === 403) {
      return '로그인할 수 없는 계정입니다.'
    }
  }

  return '로그인 처리 중 문제가 발생했습니다.'
}

export default App
