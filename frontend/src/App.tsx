import { type ChangeEvent, type FormEvent, useCallback, useEffect, useState } from 'react'
import {
  AuthApiError,
  getCurrentUser,
  login,
  type CurrentUser,
} from './api/authApi'
import {
  DocumentApiError,
  getDocuments,
  uploadDocument,
  type DocumentSummary,
  type DocumentType,
} from './api/documentApi'
import {
  SearchApiError,
  searchCareerEvidence,
  type CareerEvidenceSearchResult,
} from './api/searchApi'
import {
  clearSession,
  getAccessToken,
  getStoredCurrentUser,
  saveAccessToken,
  saveCurrentUser,
} from './auth/tokenStorage'

const LOGIN_PATH = '/login'
const CAREER_VAULT_PATH = '/career-vault'
const MAX_UPLOAD_FILE_SIZE_BYTES = 10 * 1024 * 1024

const DOCUMENT_TYPE_OPTIONS: ReadonlyArray<{ value: DocumentType | undefined; label: string }> = [
  { value: undefined, label: '전체' },
  { value: 'RESUME', label: '이력서' },
  { value: 'COVER_LETTER', label: '자기소개서' },
  { value: 'PORTFOLIO', label: '포트폴리오' },
  { value: 'PROJECT_REPORT', label: '프로젝트 보고서' },
  { value: 'PRESENTATION', label: '발표자료' },
  { value: 'CERTIFICATE', label: '자격증' },
  { value: 'COURSE_COMPLETION', label: '교육 수료' },
  { value: 'SCHOOL_ASSIGNMENT', label: '학교 과제' },
  { value: 'CAREER_REVIEW', label: '커리어 회고' },
  { value: 'JOB_POSTING', label: '채용공고' },
  { value: 'INTERVIEW_FEEDBACK', label: '면접 피드백' },
  { value: 'OTHER', label: '기타' },
]

const DOCUMENT_TYPE_LABELS: Readonly<Record<DocumentType, string>> = Object.fromEntries(
  DOCUMENT_TYPE_OPTIONS.filter((option): option is { value: DocumentType; label: string } => option.value !== undefined)
    .map((option) => [option.value, option.label]),
) as Record<DocumentType, string>

const DOCUMENT_STATUS_LABELS: Readonly<Record<string, string>> = {
  QUARANTINED: '처리 대기',
  PROCESSING: '처리 중',
  ACTIVE: '검색 가능',
  FAILED: '처리 실패',
}

const UPLOAD_DOCUMENT_TYPE_OPTIONS = DOCUMENT_TYPE_OPTIONS.filter(
  (option): option is { value: DocumentType; label: string } => option.value !== undefined,
)

type AppPath = typeof LOGIN_PATH | typeof CAREER_VAULT_PATH
type SearchState = 'idle' | 'loading' | 'result' | 'empty' | 'error'

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

  const navigate = useCallback((nextPath: AppPath) => {
    window.history.pushState(null, '', nextPath)
    setPath(nextPath)
  }, [])

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

  const handleLogout = useCallback(() => {
    clearSession()
    setCurrentUser(null)
    navigate(LOGIN_PATH)
  }, [navigate])

  if (path === CAREER_VAULT_PATH && currentUser !== null && getAccessToken() !== null) {
    return <CareerVault currentUser={currentUser} onLogout={handleLogout} onSessionExpired={handleLogout} />
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

function CareerVault({
  currentUser,
  onLogout,
  onSessionExpired,
}: {
  currentUser: CurrentUser
  onLogout: () => void
  onSessionExpired: () => void
}) {
  const [selectedDocumentType, setSelectedDocumentType] = useState<DocumentType | undefined>()
  const [documents, setDocuments] = useState<DocumentSummary[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [refreshSequence, setRefreshSequence] = useState(0)
  const [isUploadOpen, setIsUploadOpen] = useState(false)
  const [uploadTitle, setUploadTitle] = useState('')
  const [uploadDocumentType, setUploadDocumentType] = useState<DocumentType>('OTHER')
  const [uploadFile, setUploadFile] = useState<File | null>(null)
  const [uploadFormKey, setUploadFormKey] = useState(0)
  const [isUploading, setIsUploading] = useState(false)
  const [uploadErrorMessage, setUploadErrorMessage] = useState<string | null>(null)
  const [uploadSuccessMessage, setUploadSuccessMessage] = useState<string | null>(null)
  const [searchQuery, setSearchQuery] = useState('')
  const [searchResults, setSearchResults] = useState<CareerEvidenceSearchResult[]>([])
  const [searchState, setSearchState] = useState<SearchState>('idle')

  useEffect(() => {
    let isCurrentRequest = true

    const loadDocuments = async () => {
      setIsLoading(true)
      setErrorMessage(null)

      try {
        const response = await getDocuments(selectedDocumentType)
        if (isCurrentRequest) {
          setDocuments(response)
        }
      } catch (error) {
        if (!isCurrentRequest) {
          return
        }

        if (error instanceof DocumentApiError && (error.status === 401 || error.status === 403)) {
          isCurrentRequest = false
          onSessionExpired()
          return
        }

        setDocuments([])
        setErrorMessage('문서 목록을 불러오지 못했습니다.')
      } finally {
        if (isCurrentRequest) {
          setIsLoading(false)
        }
      }
    }

    void loadDocuments()
    return () => {
      isCurrentRequest = false
    }
  }, [onSessionExpired, refreshSequence, selectedDocumentType])

  const handleUploadFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0] ?? null
    const fileValidationMessage = uploadFileValidationMessage(file)
    setUploadFile(file)
    setUploadErrorMessage(fileValidationMessage)
    setUploadSuccessMessage(null)

    if (file !== null && fileValidationMessage === null && uploadTitle.trim() === '') {
      setUploadTitle(titleFromFileName(file.name))
    }
  }

  const handleUploadSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()

    if (isUploading) {
      return
    }

    if (uploadFile === null) {
      setUploadErrorMessage('파일을 선택해 주세요.')
      return
    }

    const fileValidationMessage = uploadFileValidationMessage(uploadFile)
    if (fileValidationMessage !== null) {
      setUploadErrorMessage(fileValidationMessage)
      return
    }

    const normalizedTitle = uploadTitle.trim()
    if (normalizedTitle === '') {
      setUploadErrorMessage('문서 제목을 입력해 주세요.')
      return
    }

    setIsUploading(true)
    setUploadErrorMessage(null)
    setUploadSuccessMessage(null)

    try {
      await uploadDocument(normalizedTitle, uploadDocumentType, uploadFile)
      setUploadSuccessMessage('문서가 등록되었습니다.')
      setUploadTitle('')
      setUploadDocumentType('OTHER')
      setUploadFile(null)
      setUploadFormKey((value) => value + 1)
      setIsUploadOpen(false)
      setRefreshSequence((value) => value + 1)
    } catch (error) {
      if (error instanceof DocumentApiError && (error.status === 401 || error.status === 403)) {
        onSessionExpired()
        return
      }

      setUploadErrorMessage(uploadFailureMessage(error))
    } finally {
      setIsUploading(false)
    }
  }

  const handleSearchQueryChange = (value: string) => {
    setSearchQuery(value)
    setSearchResults([])
    setSearchState('idle')
  }

  const handleSearchSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()

    const normalizedQuery = searchQuery.trim()
    if (normalizedQuery === '' || searchState === 'loading') {
      return
    }

    setSearchState('loading')
    setSearchResults([])

    try {
      const results = await searchCareerEvidence(normalizedQuery)
      setSearchResults(results)
      setSearchState(results.length === 0 ? 'empty' : 'result')
    } catch (error) {
      if (error instanceof SearchApiError && (error.status === 401 || error.status === 403)) {
        onSessionExpired()
        return
      }

      setSearchState('error')
    }
  }

  return (
    <main className="shell shell-wide">
      <header className="vault-header">
        <div>
          <p className="eyebrow">PRIZM</p>
          <p className="account-email">{currentUser.email}</p>
        </div>
        <button type="button" className="secondary-button" onClick={onLogout}>
          로그아웃
        </button>
      </header>

      <h1>Career Vault</h1>
      <p className="description">등록한 커리어 문서를 문서 유형별로 확인합니다.</p>

      <div className="vault-actions">
        <button
          type="button"
          className="secondary-button"
          onClick={() => {
            setIsUploadOpen((value) => !value)
            setUploadErrorMessage(null)
            setUploadSuccessMessage(null)
          }}
          disabled={isUploading}
        >
          문서 업로드
        </button>
      </div>

      {uploadSuccessMessage !== null && (
        <p className="form-success" role="status">
          {uploadSuccessMessage}
        </p>
      )}

      <section className="document-search" aria-labelledby="document-search-title">
        <h2 id="document-search-title">경력 근거 검색</h2>
        <form className="document-search-form" onSubmit={handleSearchSubmit} noValidate>
          <label htmlFor="document-search-query">자연어 검색어</label>
          <div className="document-search-controls">
            <input
              id="document-search-query"
              name="query"
              type="text"
              maxLength={500}
              placeholder="Spring Boot와 Redis를 사용한 경험을 찾아보세요."
              value={searchQuery}
              onChange={(event) => handleSearchQueryChange(event.target.value)}
              disabled={searchState === 'loading'}
            />
            <button type="submit" disabled={searchQuery.trim() === '' || searchState === 'loading'}>
              검색
            </button>
          </div>
        </form>

        <p className="search-processing-note">처리 중인 문서는 검색 결과에 포함되지 않을 수 있습니다.</p>

        <div className="search-result" aria-live="polite">
          {searchState === 'idle' && (
            <p className="state-message">등록된 문서에서 관련 경험의 원문 근거를 찾아보세요.</p>
          )}
          {searchState === 'loading' && <p className="state-message">관련 근거를 찾는 중입니다.</p>}
          {searchState === 'empty' && (
            <p className="state-message">현재 PRIZM에 등록된 문서에서는 관련 근거를 찾지 못했습니다.</p>
          )}
          {searchState === 'error' && <p className="form-error">경력 근거 검색 중 문제가 발생했습니다.</p>}
          {searchState === 'result' && searchResults.length > 0 && (
            <>
              <p className="search-result-summary">관련 근거 {searchResults.length}개</p>
              <ol className="search-result-list">
                {searchResults.map((result) => (
                  <li key={result.chunkId}>
                    <article className="search-result-card">
                      <h3>{result.documentTitle}</h3>
                      <p className="search-result-meta">
                        버전 {result.versionNo} · {result.sourceLabel}
                      </p>
                      <blockquote>{result.content}</blockquote>
                      <p className="search-result-score">관련도 점수 {formatSearchScore(result.score)}</p>
                    </article>
                  </li>
                ))}
              </ol>
            </>
          )}
        </div>
      </section>

      {isUploadOpen && (
        <section className="upload-panel" aria-labelledby="upload-title">
          <h2 id="upload-title">TXT·PDF 문서 업로드</h2>
          <p className="upload-guide">TXT 또는 텍스트가 포함된 PDF 파일을 업로드할 수 있습니다.</p>
          <ul className="upload-restrictions">
            <li>최대 10MB</li>
            <li>스캔 PDF와 암호화 PDF는 지원하지 않음</li>
            <li>PDF는 텍스트가 포함된 파일만 지원</li>
          </ul>
          <form className="upload-form" onSubmit={handleUploadSubmit} noValidate>
            <label htmlFor="upload-file">TXT 또는 PDF 파일</label>
            <input
              key={uploadFormKey}
              id="upload-file"
              name="file"
              type="file"
              accept=".txt,.pdf"
              onChange={handleUploadFileChange}
              disabled={isUploading}
            />

            <label htmlFor="upload-document-type">문서 유형</label>
            <select
              id="upload-document-type"
              value={uploadDocumentType}
              onChange={(event) => setUploadDocumentType(event.target.value as DocumentType)}
              disabled={isUploading}
            >
              {UPLOAD_DOCUMENT_TYPE_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>

            <label htmlFor="upload-title-input">문서 제목</label>
            <input
              id="upload-title-input"
              name="title"
              type="text"
              maxLength={200}
              value={uploadTitle}
              onChange={(event) => setUploadTitle(event.target.value)}
              disabled={isUploading}
            />

            {uploadErrorMessage !== null && (
              <p className="form-error" role="alert">
                {uploadErrorMessage}
              </p>
            )}

            <button type="submit" disabled={isUploading}>
              {isUploading ? '업로드 중…' : '업로드'}
            </button>
          </form>
        </section>
      )}

      <label className="document-filter" htmlFor="document-type">
        <span>문서 유형</span>
        <select
          id="document-type"
          value={selectedDocumentType ?? ''}
          onChange={(event) => setSelectedDocumentType(toDocumentType(event.target.value))}
          disabled={isLoading}
        >
          {DOCUMENT_TYPE_OPTIONS.map((option) => (
            <option key={option.label} value={option.value ?? ''}>
              {option.label}
            </option>
          ))}
        </select>
      </label>

      <section className="document-section" aria-live="polite">
        {isLoading && <p className="state-message">문서를 불러오는 중입니다.</p>}
        {!isLoading && errorMessage !== null && <p className="form-error">{errorMessage}</p>}
        {!isLoading && errorMessage === null && documents.length === 0 && (
          <p className="state-message">
            {selectedDocumentType === undefined
              ? '아직 등록된 문서가 없습니다.'
              : '선택한 유형의 문서가 없습니다.'}
          </p>
        )}
        {!isLoading && errorMessage === null && documents.length > 0 && (
          <ul className="document-list">
            {documents.map((document) => (
              <li key={document.documentId}>
                <article className="document-card">
                  <div>
                    <h2>{document.title}</h2>
                    <p className="document-meta">
                      {documentTypeLabel(document.documentType)} · {documentStatusLabel(document.latestVersionStatus)}
                    </p>
                  </div>
                  <time dateTime={document.createdAt}>{formatCreatedAt(document.createdAt)}</time>
                </article>
              </li>
            ))}
          </ul>
        )}
      </section>
    </main>
  )
}

function toDocumentType(value: string): DocumentType | undefined {
  return value === '' ? undefined : (value as DocumentType)
}

function titleFromFileName(fileName: string): string {
  return fileName.replace(/\.(txt|pdf)$/i, '').trim() || fileName
}

function uploadFileValidationMessage(file: File | null): string | null {
  if (file === null) {
    return null
  }

  const normalizedFileName = file.name.toLowerCase()
  if (!normalizedFileName.endsWith('.txt') && !normalizedFileName.endsWith('.pdf')) {
    return '지원하는 파일 형식은 TXT와 PDF입니다.'
  }

  if (file.size > MAX_UPLOAD_FILE_SIZE_BYTES) {
    return '파일 크기가 10MB를 초과했습니다.'
  }

  return null
}

function documentTypeLabel(documentType: string): string {
  return DOCUMENT_TYPE_LABELS[documentType as DocumentType] ?? documentType
}

function documentStatusLabel(status: string | null): string {
  if (status === null) {
    return '상태 확인 중'
  }

  return DOCUMENT_STATUS_LABELS[status] ?? status
}

function formatSearchScore(score: number): string {
  return score.toFixed(2)
}

function formatCreatedAt(createdAt: string): string {
  const date = new Date(createdAt)

  if (Number.isNaN(date.getTime())) {
    return createdAt
  }

  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  }).format(date)
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

function uploadFailureMessage(error: unknown): string {
  if (error instanceof DocumentApiError) {
    if (error.code === 'INVALID_DOCUMENT_CONTENT') {
      return 'PDF에서 처리 가능한 텍스트를 찾지 못했습니다. 스캔 파일이나 암호화된 PDF인지 확인해 주세요.'
    }

    if (error.status === 400) {
      return '파일 형식과 내용을 확인해 주세요.'
    }

    if (error.status === 413) {
      return '파일 크기가 10MB를 초과했습니다.'
    }
  }

  return '문서를 업로드하지 못했습니다.'
}

export default App
