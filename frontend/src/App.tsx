import {
  type ChangeEvent,
  type FormEvent,
  type MouseEvent as ReactMouseEvent,
  useCallback,
  useEffect,
  useRef,
  useState,
} from 'react'
import {
  AuthApiError,
  getCurrentUser,
  login,
  signup,
  type CurrentUser,
  type LoginResponse,
} from './api/authApi'
import {
  DocumentApiError,
  deleteDocument,
  getDocument,
  getDocumentPdf,
  getDocuments,
  getDocumentThumbnail,
  updateDocumentMetadata,
  type DocumentDetail,
  uploadDocument,
  uploadDocumentVersion,
  type DocumentSummary,
  type DocumentType,
  type ProcessingJobStatus,
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
const DOCUMENTS_PATH = '/career-vault/documents'
const EVIDENCE_PATH = '/career-vault/evidence'
const UPLOAD_PATH = '/career-vault/upload'
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
  DOCUMENT_TYPE_OPTIONS.filter(
    (option): option is { value: DocumentType; label: string } => option.value !== undefined,
  ).map((option) => [option.value, option.label]),
) as Record<DocumentType, string>

const DOCUMENT_STATUS_LABELS: Readonly<Record<string, string>> = {
  PENDING: '처리 대기',
  QUARANTINED: '처리 대기',
  PROCESSING: '처리 중',
  RETRY_WAIT: '재시도 대기',
  COMPLETED: '처리 완료',
  ACTIVE: '검색 가능',
  FAILED: '처리 실패',
}

const PROCESSING_STATUS_OPTIONS: ReadonlyArray<{
  value: ProcessingJobStatus | undefined
  label: string
}> = [
  { value: undefined, label: '전체 상태' },
  { value: 'PENDING', label: '처리 대기' },
  { value: 'PROCESSING', label: '처리 중' },
  { value: 'RETRY_WAIT', label: '재시도 대기' },
  { value: 'COMPLETED', label: '처리 완료' },
  { value: 'FAILED', label: '처리 실패' },
]

const UPLOAD_DOCUMENT_TYPE_OPTIONS = DOCUMENT_TYPE_OPTIONS.filter(
  (option): option is { value: DocumentType; label: string } => option.value !== undefined,
)

const NAVIGATION_ITEMS: ReadonlyArray<{
  path: VaultPath
  label: string
  marker: string
}> = [
  { path: DOCUMENTS_PATH, label: '문서 보관함', marker: '문' },
  { path: EVIDENCE_PATH, label: '경력 근거 검색', marker: '근' },
  { path: UPLOAD_PATH, label: '문서 업로드', marker: '+' },
]

type VaultPath = typeof DOCUMENTS_PATH | typeof EVIDENCE_PATH | typeof UPLOAD_PATH
type AppPath = typeof LOGIN_PATH | VaultPath
type SearchState = 'idle' | 'loading' | 'result' | 'empty' | 'error'
type UploadErrorTarget = 'file' | 'title' | 'form' | null
type ThumbnailState = 'idle' | 'ready' | 'fallback'
type PdfViewerTarget = {
  documentId: number
  versionId: number
  versionNo: number
  originalFileName: string
}

function isVaultPath(pathname: string): pathname is VaultPath {
  return pathname === DOCUMENTS_PATH || pathname === EVIDENCE_PATH || pathname === UPLOAD_PATH
}

function toAppPath(pathname: string): AppPath {
  if (pathname === CAREER_VAULT_PATH) {
    return DOCUMENTS_PATH
  }

  return isVaultPath(pathname) ? pathname : LOGIN_PATH
}

function App() {
  const [path, setPath] = useState<AppPath>(() => toAppPath(window.location.pathname))
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>(() => {
    const pathname = window.location.pathname
    return pathname === CAREER_VAULT_PATH || isVaultPath(pathname)
      ? getStoredCurrentUser()
      : null
  })

  useEffect(() => {
    const syncPath = () => {
      if (window.location.pathname === CAREER_VAULT_PATH) {
        window.history.replaceState(null, '', DOCUMENTS_PATH)
      }
      setPath(toAppPath(window.location.pathname))
    }

    syncPath()
    window.addEventListener('popstate', syncPath)
    return () => window.removeEventListener('popstate', syncPath)
  }, [])

  const navigate = useCallback((nextPath: AppPath) => {
    if (window.location.pathname !== nextPath) {
      window.history.pushState(null, '', nextPath)
    }
    setPath(nextPath)
  }, [])

  const establishSession = async (requestSession: () => Promise<LoginResponse>): Promise<string | null> => {
    clearSession()

    let accessToken: string
    try {
      const response = await requestSession()
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
      navigate(DOCUMENTS_PATH)
      return null
    } catch {
      clearSession()
      return '로그인 상태를 확인하지 못했습니다. 다시 로그인해 주세요.'
    }
  }

  const handleLogin = (email: string, password: string): Promise<string | null> =>
    establishSession(() => login(email, password))

  const handleSignup = async (email: string, password: string): Promise<string | null> => {
    try {
      await signup(email, password)
      return null
    } catch (error) {
      return signupErrorMessage(error)
    }
  }

  const handleLogout = useCallback(() => {
    clearSession()
    setCurrentUser(null)
    navigate(LOGIN_PATH)
  }, [navigate])

  if (path !== LOGIN_PATH && currentUser !== null && getAccessToken() !== null) {
    return (
      <CareerVaultShell
        path={path}
        currentUser={currentUser}
        onNavigate={navigate}
        onLogout={handleLogout}
        onSessionExpired={handleLogout}
      />
    )
  }

  return (
    <LoginPage onLogin={handleLogin} onSignup={handleSignup} />
  )
}

function LoginPage({
  onLogin,
  onSignup,
}: {
  onLogin: (email: string, password: string) => Promise<string | null>
  onSignup: (email: string, password: string) => Promise<string | null>
}) {
  const [mode, setMode] = useState<'signup' | 'login'>('signup')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [passwordConfirmation, setPasswordConfirmation] = useState('')
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()

    if (email.trim() === '' || password === '' || (mode === 'signup' && passwordConfirmation === '')) {
      setErrorMessage(mode === 'signup'
        ? '이메일, 비밀번호, 비밀번호 확인을 입력해 주세요.'
        : '이메일과 비밀번호를 입력해 주세요.')
      return
    }

    if (mode === 'signup' && password !== passwordConfirmation) {
      setErrorMessage('비밀번호가 일치하지 않습니다.')
      return
    }

    setErrorMessage(null)
    setIsSubmitting(true)
    const error = mode === 'signup'
      ? await onSignup(email.trim(), password)
      : await onLogin(email.trim(), password)
    setIsSubmitting(false)

    if (error !== null) {
      setErrorMessage(error)
      return
    }

    if (mode === 'signup') {
      setMode('login')
      setPassword('')
      setPasswordConfirmation('')
    }
  }

  const switchMode = () => {
    setErrorMessage(null)
    setPassword('')
    setPasswordConfirmation('')
    setMode((currentMode) => currentMode === 'signup' ? 'login' : 'signup')
  }

  return (
    <main className="login-page">
      <section className="login-intro" aria-labelledby="login-intro-title">
        <div className="brand">
          <span className="brand-symbol" aria-hidden="true" />
          <span className="brand-name">PRIZM</span>
        </div>

        <div className="login-intro-copy">
          <p className="eyebrow">CAREER VAULT · REFERENCE APP</p>
          <h2 id="login-intro-title">
            흩어진 커리어 기록을
            <span>한곳에</span>
          </h2>
          <p className="description">
            등록한 문서에서 필요한 내용을 찾고,
            <br />
            필요할 때 바로 열어보세요.
          </p>
        </div>

        <div className="evidence-principle">
          <span className="principle-dot" aria-hidden="true" />
          <div>
            <strong>등록한 문서를 바탕으로</strong>
            <p>관련 내용을 찾지 못하면 찾지 못했다고 분명하게 안내합니다.</p>
          </div>
        </div>
      </section>

      <section className="login-panel" aria-labelledby="login-title">
        <div className="login-heading">
          <p className="eyebrow">CAREER VAULT</p>
          <h1 id="login-title">{mode === 'signup' ? '회원가입' : '로그인'}</h1>
          <p>{mode === 'signup' ? '일반 계정을 만든 후 로그인하세요.' : '내 커리어 문서를 모아 관리하세요.'}</p>
        </div>

        <form className="login-form" onSubmit={handleSubmit} noValidate aria-busy={isSubmitting}>
            <div className="form-field">
              <label htmlFor="email">이메일</label>
              <input
                id="email"
                name="email"
                type="email"
                autoComplete="email"
                placeholder="name@example.com"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                disabled={isSubmitting}
                aria-invalid={errorMessage !== null}
                aria-describedby={errorMessage !== null ? 'login-form-error' : undefined}
              />
            </div>

            <div className="form-field">
              <label htmlFor="password">비밀번호</label>
              <input
                id="password"
                name="password"
                type="password"
                autoComplete={mode === 'signup' ? 'new-password' : 'current-password'}
                placeholder="비밀번호를 입력하세요"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                disabled={isSubmitting}
                aria-invalid={errorMessage !== null}
                aria-describedby={errorMessage !== null ? 'login-form-error' : undefined}
              />
            </div>

            {mode === 'signup' && (
              <div className="form-field">
                <label htmlFor="password-confirmation">비밀번호 확인</label>
                <input
                  id="password-confirmation"
                  name="password-confirmation"
                  type="password"
                  autoComplete="new-password"
                  placeholder="비밀번호를 다시 입력하세요"
                  value={passwordConfirmation}
                  onChange={(event) => setPasswordConfirmation(event.target.value)}
                  disabled={isSubmitting}
                  aria-invalid={errorMessage !== null}
                  aria-describedby={errorMessage !== null ? 'login-form-error' : undefined}
                />
              </div>
            )}

            {errorMessage !== null && (
              <p id="login-form-error" className="form-error feedback-message" role="alert">
                {errorMessage}
              </p>
            )}

            <button
              type="submit"
              className="primary-button button-xlarge"
              disabled={isSubmitting}
              aria-busy={isSubmitting}
            >
              {isSubmitting && <span className="button-spinner" aria-hidden="true" />}
              {isSubmitting
                ? (mode === 'signup' ? '가입 중' : '로그인 중')
                : (mode === 'signup' ? '회원가입' : '로그인')}
            </button>

            <button
              type="button"
              className="secondary-button button-xlarge"
              disabled={isSubmitting}
              onClick={switchMode}
            >
              {mode === 'signup' ? '이미 계정이 있나요? 로그인' : '계정이 없나요? 회원가입'}
            </button>
          </form>

        <p className="login-footnote">PRIZM은 등록된 문서에 없는 경험을 만들지 않습니다.</p>
      </section>
    </main>
  )
}

function CareerVaultShell({
  path,
  currentUser,
  onNavigate,
  onLogout,
  onSessionExpired,
}: {
  path: VaultPath
  currentUser: CurrentUser
  onNavigate: (path: AppPath) => void
  onLogout: () => void
  onSessionExpired: () => void
}) {
  const [openMenuPath, setOpenMenuPath] = useState<VaultPath | null>(null)
  const isMenuOpen = openMenuPath === path

  useEffect(() => {
    if (!isMenuOpen) {
      return
    }

    const previousOverflow = document.body.style.overflow
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpenMenuPath(null)
      }
    }

    document.body.style.overflow = 'hidden'
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.body.style.overflow = previousOverflow
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [isMenuOpen])

  const handleNavigationClick = (
    event: ReactMouseEvent<HTMLAnchorElement>,
    destination: VaultPath,
  ) => {
    if (
      event.button !== 0 ||
      event.metaKey ||
      event.ctrlKey ||
      event.shiftKey ||
      event.altKey
    ) {
      return
    }

    event.preventDefault()
    setOpenMenuPath(null)
    onNavigate(destination)
  }

  return (
    <div className="vault-shell">
      <header className="mobile-vault-header">
        <button
          type="button"
          className="menu-button"
          aria-label="메뉴 열기"
          aria-expanded={isMenuOpen}
          aria-controls="career-vault-navigation"
          onClick={() => setOpenMenuPath((openPath) => (openPath === path ? null : path))}
        >
          <span aria-hidden="true" />
          <span aria-hidden="true" />
          <span aria-hidden="true" />
        </button>
        <div className="brand brand-compact">
          <span className="brand-symbol" aria-hidden="true" />
          <span className="brand-name">PRIZM</span>
        </div>
      </header>

      <aside
        id="career-vault-navigation"
        className={'vault-sidebar' + (isMenuOpen ? ' is-open' : '')}
        aria-label="Career Vault 메뉴"
      >
        <div className="sidebar-brand brand">
          <span className="brand-symbol" aria-hidden="true" />
          <span className="brand-name">PRIZM</span>
        </div>

        <nav className="vault-navigation">
          {NAVIGATION_ITEMS.map((item) => (
            <a
              key={item.path}
              href={item.path}
              className="vault-navigation-link"
              aria-current={path === item.path ? 'page' : undefined}
              onClick={(event) => handleNavigationClick(event, item.path)}
            >
              <span className="navigation-marker" aria-hidden="true">
                {item.marker}
              </span>
              <span>{item.label}</span>
            </a>
          ))}
        </nav>

        <div className="sidebar-account">
          <span title={currentUser.email}>{currentUser.email}</span>
          <button type="button" className="sidebar-logout" onClick={onLogout}>
            로그아웃
          </button>
        </div>
      </aside>

      <button
        type="button"
        className={'drawer-backdrop' + (isMenuOpen ? ' is-open' : '')}
        aria-label="메뉴 닫기"
        tabIndex={isMenuOpen ? 0 : -1}
        onClick={() => setOpenMenuPath(null)}
      />

      <main className="vault-content">
        <div className="vault-page-container">
          {path === DOCUMENTS_PATH && <DocumentsPage onSessionExpired={onSessionExpired} />}
          {path === EVIDENCE_PATH && <EvidencePage onSessionExpired={onSessionExpired} />}
          {path === UPLOAD_PATH && (
            <UploadPage
              onSessionExpired={onSessionExpired}
              onNavigateToDocuments={() => onNavigate(DOCUMENTS_PATH)}
            />
          )}
        </div>
      </main>
    </div>
  )
}

function DocumentsPage({ onSessionExpired }: { onSessionExpired: () => void }) {
  const [selectedDocumentType, setSelectedDocumentType] = useState<DocumentType | undefined>()
  const [selectedProcessingStatus, setSelectedProcessingStatus] = useState<
    ProcessingJobStatus | undefined
  >()
  const [titleQuery, setTitleQuery] = useState('')
  const [appliedTitleQuery, setAppliedTitleQuery] = useState('')
  const [documents, setDocuments] = useState<DocumentSummary[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [selectedDocument, setSelectedDocument] = useState<DocumentDetail | null>(null)
  const [isDetailLoading, setIsDetailLoading] = useState(false)
  const [detailErrorMessage, setDetailErrorMessage] = useState<string | null>(null)
  const [editTitle, setEditTitle] = useState('')
  const [editDocumentType, setEditDocumentType] = useState<DocumentType>('OTHER')
  const [isSaving, setIsSaving] = useState(false)
  const [isDeleteConfirming, setIsDeleteConfirming] = useState(false)
  const [isDeleting, setIsDeleting] = useState(false)
  const [versionUploadFile, setVersionUploadFile] = useState<File | null>(null)
  const [versionUploadInputKey, setVersionUploadInputKey] = useState(0)
  const [isVersionUploading, setIsVersionUploading] = useState(false)
  const [actionMessage, setActionMessage] = useState<string | null>(null)
  const [pdfViewerTarget, setPdfViewerTarget] = useState<PdfViewerTarget | null>(null)
  const [pdfViewerUrl, setPdfViewerUrl] = useState<string | null>(null)
  const [pdfViewerErrorMessage, setPdfViewerErrorMessage] = useState<string | null>(null)
  const [isPdfViewerLoading, setIsPdfViewerLoading] = useState(false)
  const detailRequestId = useRef(0)
  const pdfRequestId = useRef(0)
  const pdfAbortController = useRef<AbortController | null>(null)
  const pdfObjectUrl = useRef<string | null>(null)

  useEffect(() => {
    let isCurrentRequest = true

    const loadDocuments = async () => {
      setIsLoading(true)
      setErrorMessage(null)

      try {
        const response = await getDocuments({
          documentType: selectedDocumentType,
          title: appliedTitleQuery,
          processingStatus: selectedProcessingStatus,
        })
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
  }, [appliedTitleQuery, onSessionExpired, reloadKey, selectedDocumentType, selectedProcessingStatus])

  const handleTitleFilterSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const normalizedTitle = titleQuery.trim()
    if (normalizedTitle === appliedTitleQuery) {
      setReloadKey((value) => value + 1)
      return
    }
    setAppliedTitleQuery(normalizedTitle)
  }

  const closePdfViewer = useCallback(() => {
    pdfRequestId.current += 1
    pdfAbortController.current?.abort()
    pdfAbortController.current = null
    if (pdfObjectUrl.current !== null) {
      URL.revokeObjectURL(pdfObjectUrl.current)
      pdfObjectUrl.current = null
    }
    setPdfViewerTarget(null)
    setPdfViewerUrl(null)
    setPdfViewerErrorMessage(null)
    setIsPdfViewerLoading(false)
  }, [])

  useEffect(() => () => {
    pdfAbortController.current?.abort()
    if (pdfObjectUrl.current !== null) {
      URL.revokeObjectURL(pdfObjectUrl.current)
    }
  }, [])

  const closeDocumentDetail = useCallback(() => {
    closePdfViewer()
    detailRequestId.current += 1
    setSelectedDocument(null)
    setDetailErrorMessage(null)
    setIsDetailLoading(false)
    setIsDeleteConfirming(false)
    setVersionUploadFile(null)
    setVersionUploadInputKey((value) => value + 1)
    setIsVersionUploading(false)
  }, [closePdfViewer])

  const isDocumentDialogOpen =
    isDetailLoading || selectedDocument !== null || detailErrorMessage !== null

  useEffect(() => {
    if (!isDocumentDialogOpen) {
      return
    }

    const previousOverflow = document.body.style.overflow
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        if (pdfViewerTarget !== null) {
          closePdfViewer()
        } else {
          closeDocumentDetail()
        }
      }
    }
    document.body.style.overflow = 'hidden'
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.body.style.overflow = previousOverflow
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [closeDocumentDetail, closePdfViewer, isDocumentDialogOpen, pdfViewerTarget])

  const handleOpenDocument = async (documentId: number) => {
    closePdfViewer()
    const requestId = detailRequestId.current + 1
    detailRequestId.current = requestId
    setIsDetailLoading(true)
    setSelectedDocument(null)
    setDetailErrorMessage(null)
    setActionMessage(null)
    setIsDeleteConfirming(false)
    setVersionUploadFile(null)
    setVersionUploadInputKey((value) => value + 1)
    try {
      const detail = await getDocument(documentId)
      if (detailRequestId.current !== requestId) {
        return
      }
      setSelectedDocument(detail)
      setEditTitle(detail.title)
      setEditDocumentType(detail.documentType)
    } catch (error) {
      if (detailRequestId.current !== requestId) {
        return
      }
      if (error instanceof DocumentApiError && (error.status === 401 || error.status === 403)) {
        onSessionExpired()
        return
      }
      setDetailErrorMessage('문서 상세 정보를 불러오지 못했습니다.')
    } finally {
      if (detailRequestId.current === requestId) {
        setIsDetailLoading(false)
      }
    }
  }

  const handleSaveMetadata = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (selectedDocument === null || isSaving) {
      return
    }
    const normalizedTitle = editTitle.trim()
    if (normalizedTitle === '') {
      setDetailErrorMessage('문서 제목을 입력해 주세요.')
      return
    }
    setIsSaving(true)
    setDetailErrorMessage(null)
    try {
      const updated = await updateDocumentMetadata(
        selectedDocument.documentId,
        normalizedTitle,
        editDocumentType,
      )
      setSelectedDocument(updated)
      setEditTitle(updated.title)
      setEditDocumentType(updated.documentType)
      setActionMessage('문서 정보를 저장했습니다.')
      setReloadKey((value) => value + 1)
    } catch (error) {
      if (error instanceof DocumentApiError && (error.status === 401 || error.status === 403)) {
        onSessionExpired()
        return
      }
      setDetailErrorMessage('문서 정보를 저장하지 못했습니다.')
    } finally {
      setIsSaving(false)
    }
  }

  const handleDelete = async () => {
    if (selectedDocument === null || isDeleting) {
      return
    }
    setIsDeleting(true)
    setDetailErrorMessage(null)
    try {
      await deleteDocument(selectedDocument.documentId)
      setSelectedDocument(null)
      setIsDeleteConfirming(false)
      setActionMessage('문서를 삭제했습니다. 원본 파일 정리는 안전하게 백그라운드에서 진행됩니다.')
      setReloadKey((value) => value + 1)
    } catch (error) {
      if (error instanceof DocumentApiError && (error.status === 401 || error.status === 403)) {
        onSessionExpired()
        return
      }
      if (error instanceof DocumentApiError && error.code === 'DOCUMENT_PROCESSING') {
        setDetailErrorMessage('문서 처리 중에는 삭제할 수 없습니다. 처리 완료 또는 실패 후 다시 시도해 주세요.')
      } else {
        setDetailErrorMessage('문서를 삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.')
      }
    } finally {
      setIsDeleting(false)
    }
  }

  const handleVersionFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0] ?? null
    setVersionUploadFile(file)
    setActionMessage(null)
    setDetailErrorMessage(uploadFileValidationMessage(file))
  }

  const handleVersionUpload = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (selectedDocument === null || isVersionUploading) {
      return
    }
    if (versionUploadFile === null) {
      setDetailErrorMessage('새 버전으로 등록할 TXT 또는 PDF 파일을 선택해 주세요.')
      return
    }
    const validationMessage = uploadFileValidationMessage(versionUploadFile)
    if (validationMessage !== null) {
      setDetailErrorMessage(validationMessage)
      return
    }

    const documentId = selectedDocument.documentId
    const requestId = detailRequestId.current
    setIsVersionUploading(true)
    setDetailErrorMessage(null)
    setActionMessage(null)
    try {
      const uploaded = await uploadDocumentVersion(documentId, versionUploadFile)
      if (detailRequestId.current !== requestId) {
        return
      }
      const updated = await getDocument(documentId)
      if (detailRequestId.current !== requestId) {
        return
      }
      const uploadedVersion = updated.versions.find((version) => version.versionId === uploaded.versionId)
      setSelectedDocument(updated)
      setVersionUploadFile(null)
      setVersionUploadInputKey((value) => value + 1)
      setActionMessage(
        `버전 ${uploadedVersion?.versionNo ?? updated.versions.length}의 원본을 등록했습니다. 처리가 끝나면 자동으로 ACTIVE 버전이 됩니다.`,
      )
      setReloadKey((value) => value + 1)
    } catch (error) {
      if (detailRequestId.current !== requestId) {
        return
      }
      if (error instanceof DocumentApiError && (error.status === 401 || error.status === 403)) {
        onSessionExpired()
        return
      }
      if (error instanceof DocumentApiError && error.code === 'DOCUMENT_PROCESSING') {
        setDetailErrorMessage('현재 버전 처리가 끝난 뒤 새 버전을 등록할 수 있습니다.')
      } else {
        setDetailErrorMessage('새 버전을 등록하지 못했습니다. 파일을 확인한 뒤 다시 시도해 주세요.')
      }
    } finally {
      if (detailRequestId.current === requestId) {
        setIsVersionUploading(false)
      }
    }
  }

  const handleOpenPdf = async (target: PdfViewerTarget) => {
    closePdfViewer()
    const requestId = pdfRequestId.current + 1
    pdfRequestId.current = requestId
    const controller = new AbortController()
    pdfAbortController.current = controller
    setPdfViewerTarget(target)
    setPdfViewerErrorMessage(null)
    setIsPdfViewerLoading(true)

    try {
      const pdf = await getDocumentPdf(target.documentId, target.versionId, controller.signal)
      if (pdfRequestId.current !== requestId) {
        return
      }
      const objectUrl = URL.createObjectURL(pdf)
      if (pdfRequestId.current !== requestId) {
        URL.revokeObjectURL(objectUrl)
        return
      }
      pdfObjectUrl.current = objectUrl
      setPdfViewerUrl(objectUrl)
    } catch (error) {
      if (pdfRequestId.current !== requestId) {
        return
      }
      if (error instanceof DOMException && error.name === 'AbortError') {
        return
      }
      if (error instanceof DocumentApiError && (error.status === 401 || error.status === 403)) {
        closePdfViewer()
        onSessionExpired()
        return
      }
      if (error instanceof DocumentApiError && error.code === 'ORIGINAL_FILE_NOT_FOUND') {
        setPdfViewerErrorMessage('첨부한 PDF 원본을 찾지 못했습니다.')
      } else if (error instanceof DocumentApiError && error.code === 'UNSUPPORTED_FILE_TYPE') {
        setPdfViewerErrorMessage('PDF 형식의 버전만 열 수 있습니다.')
      } else {
        setPdfViewerErrorMessage('PDF를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.')
      }
    } finally {
      if (pdfRequestId.current === requestId) {
        pdfAbortController.current = null
        setIsPdfViewerLoading(false)
      }
    }
  }

  const hasInFlightVersion = selectedDocument?.versions.some((version) =>
    version.processingStatus === 'PENDING' ||
    version.processingStatus === 'PROCESSING' ||
    version.processingStatus === 'RETRY_WAIT'
  ) ?? false
  const activeVersion = selectedDocument?.versions.find(
    (version) => version.versionId === selectedDocument.activeVersionId,
  ) ?? null

  return (
    <section className="vault-page documents-page" aria-labelledby="documents-title">
      <header className="page-heading-row">
        <div className="page-heading">
          <p className="eyebrow">MY DOCUMENTS</p>
          <h1 id="documents-title">문서 보관함</h1>
          <p>등록한 커리어 문서와 현재 처리 상태를 확인하세요.</p>
        </div>

        <div className="document-filters" aria-label="문서 필터">
          <form className="document-title-filter" onSubmit={handleTitleFilterSubmit}>
            <label htmlFor="document-title-filter">문서 제목</label>
            <div>
              <input
                id="document-title-filter"
                type="search"
                maxLength={200}
                placeholder="제목으로 찾기"
                value={titleQuery}
                onChange={(event) => setTitleQuery(event.target.value)}
                disabled={isLoading}
              />
              <button type="submit" className="secondary-button" disabled={isLoading}>
                찾기
              </button>
            </div>
          </form>
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
          <label className="document-filter" htmlFor="processing-status">
            <span>처리 상태</span>
            <select
              id="processing-status"
              value={selectedProcessingStatus ?? ''}
              onChange={(event) =>
                setSelectedProcessingStatus(
                  event.target.value === '' ? undefined : (event.target.value as ProcessingJobStatus),
                )
              }
              disabled={isLoading}
            >
              {PROCESSING_STATUS_OPTIONS.map((option) => (
                <option key={option.label} value={option.value ?? ''}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>
        </div>
      </header>

      <div className="document-section" aria-live="polite" aria-busy={isLoading}>
        {isLoading && (
          <p className="state-message document-state">
            <span className="state-spinner" aria-hidden="true" />
            문서를 불러오는 중입니다.
          </p>
        )}
        {!isLoading && errorMessage !== null && (
          <p className="form-error feedback-message" role="alert">
            {errorMessage}
          </p>
        )}
        {!isLoading && actionMessage !== null && (
          <p className="feedback-message document-action-message" role="status">
            {actionMessage}
          </p>
        )}
        {!isLoading && errorMessage === null && documents.length === 0 && (
          <p className="state-message document-empty-state">
            {selectedDocumentType === undefined
              ? '아직 등록한 문서가 없습니다. 문서 업로드 메뉴에서 첫 문서를 등록해 보세요.'
              : '선택한 조건의 문서가 없습니다.'}
          </p>
        )}
        {!isLoading && errorMessage === null && documents.length > 0 && (
          <ul className="document-grid">
            {documents.map((document) => (
              <li key={document.documentId}>
                <article className="document-card">
                  <DocumentThumbnail
                    key={String(document.latestVersionId)}
                    document={document}
                    onSessionExpired={onSessionExpired}
                  />
                  <div className="document-card-body">
                    <h2>{document.title}</h2>
                    <p className="original-file-name">
                      {document.latestOriginalFileName ?? '원본 파일 정보 없음'}
                    </p>
                    <div className="document-badges">
                      <span className="type-badge">{documentTypeLabel(document.documentType)}</span>
                      <span
                        className={
                          'status-badge ' +
                          documentStatusClassName(
                            document.latestProcessingStatus ?? document.latestVersionStatus,
                          )
                        }
                      >
                        {documentStatusLabel(
                          document.latestProcessingStatus ?? document.latestVersionStatus,
                        )}
                      </span>
                    </div>
                    <p className="document-version-count">버전 {document.versionCount}개</p>
                    <time dateTime={document.createdAt}>{formatCreatedAt(document.createdAt)}</time>
                    <button
                      type="button"
                      className="document-detail-button"
                      onClick={() => void handleOpenDocument(document.documentId)}
                    >
                      상세 보기
                    </button>
                  </div>
                </article>
              </li>
            ))}
          </ul>
        )}
      </div>

      {isDocumentDialogOpen && (
        <>
          <button
            type="button"
            className="document-detail-backdrop"
            aria-label="문서 상세 닫기"
            onClick={closeDocumentDetail}
          />
          <section
            className="document-detail-panel"
            role="dialog"
            aria-modal="true"
            aria-labelledby="document-detail-title"
            aria-hidden={pdfViewerTarget !== null}
            aria-live="polite"
            aria-busy={isDetailLoading}
          >
          <div className="document-detail-heading">
            <div>
              <p className="eyebrow">DOCUMENT MANAGEMENT</p>
              <h2 id="document-detail-title">문서 상세 및 관리</h2>
            </div>
            <button
              type="button"
              className="icon-close-button"
              aria-label="문서 상세 닫기"
              onClick={closeDocumentDetail}
            >
              ×
            </button>
          </div>
          {isDetailLoading && (
            <p className="state-message document-state">
              <span className="state-spinner" aria-hidden="true" />
              문서 상세 정보를 불러오는 중입니다.
            </p>
          )}
          {detailErrorMessage !== null && (
            <p className="form-error feedback-message" role="alert">
              {detailErrorMessage}
            </p>
          )}
          {selectedDocument !== null && !isDetailLoading && (
            <>
              <div className="document-detail-hero">
                <div className="document-detail-file-mark" aria-hidden="true">
                  {activeVersion?.fileType ?? selectedDocument.versions[0]?.fileType ?? 'DOC'}
                </div>
                <div className="document-detail-hero-copy">
                  <span className="type-badge">{documentTypeLabel(selectedDocument.documentType)}</span>
                  <h3>{selectedDocument.title}</h3>
                  <p>{activeVersion?.originalFileName ?? '아직 활성화된 원본이 없습니다.'}</p>
                </div>
                <dl className="document-detail-summary">
                  <div>
                    <dt>현재 버전</dt>
                    <dd>{activeVersion === null ? '처리 중' : `v${activeVersion.versionNo}`}</dd>
                  </div>
                  <div>
                    <dt>전체 버전</dt>
                    <dd>{selectedDocument.versions.length}개</dd>
                  </div>
                  <div>
                    <dt>최근 변경</dt>
                    <dd>{formatCreatedAt(selectedDocument.updatedAt)}</dd>
                  </div>
                </dl>
              </div>

              {actionMessage !== null && (
                <p className="feedback-message document-detail-success" role="status">
                  {actionMessage}
                </p>
              )}

              <section className="document-detail-card" aria-labelledby="metadata-heading">
                <div className="document-detail-section-heading">
                  <div>
                    <p className="section-kicker">BASIC INFORMATION</p>
                    <h3 id="metadata-heading">기본 정보</h3>
                  </div>
                  <p>제목과 문서 유형만 변경할 수 있습니다.</p>
                </div>
                <form className="document-metadata-form" onSubmit={handleSaveMetadata} noValidate>
                  <div className="form-field">
                    <label htmlFor="edit-document-title">문서 제목</label>
                    <input
                      id="edit-document-title"
                      type="text"
                      maxLength={200}
                      value={editTitle}
                      onChange={(event) => setEditTitle(event.target.value)}
                      disabled={isSaving || isDeleting || isVersionUploading}
                    />
                  </div>
                  <div className="form-field">
                    <label htmlFor="edit-document-type">문서 유형</label>
                    <select
                      id="edit-document-type"
                      value={editDocumentType}
                      onChange={(event) => setEditDocumentType(event.target.value as DocumentType)}
                      disabled={isSaving || isDeleting || isVersionUploading}
                    >
                      {UPLOAD_DOCUMENT_TYPE_OPTIONS.map((option) => (
                        <option key={option.value} value={option.value}>
                          {option.label}
                        </option>
                      ))}
                    </select>
                  </div>
                  <button
                    type="submit"
                    className="primary-button button-large metadata-save-button"
                    disabled={isSaving || isDeleting || isVersionUploading}
                  >
                    {isSaving ? '저장 중' : '정보 저장'}
                  </button>
                </form>
              </section>

              <section className="document-detail-card document-version-section" aria-labelledby="versions-heading">
                <div className="document-detail-section-heading">
                  <div>
                    <p className="section-kicker">VERSION HISTORY</p>
                    <h3 id="versions-heading">버전 및 처리 상태</h3>
                  </div>
                  <p>새 수정본은 기존 ACTIVE 버전을 유지한 채 별도로 처리됩니다.</p>
                </div>

                <form className="version-upload-form" onSubmit={handleVersionUpload}>
                  <div>
                    <strong>새 버전 추가</strong>
                    <span>TXT 또는 텍스트 레이어가 있는 PDF · 최대 10MB</span>
                  </div>
                  <label className="version-file-picker" htmlFor="document-version-file">
                    <input
                      key={versionUploadInputKey}
                      id="document-version-file"
                      type="file"
                      accept=".txt,.pdf,text/plain,application/pdf"
                      onChange={handleVersionFileChange}
                      disabled={hasInFlightVersion || isVersionUploading || isDeleting}
                    />
                    <span>{versionUploadFile?.name ?? '수정본 파일 선택'}</span>
                  </label>
                  <button
                    type="submit"
                    className="primary-button button-large version-upload-button"
                    disabled={
                      hasInFlightVersion ||
                      versionUploadFile === null ||
                      isVersionUploading ||
                      isDeleting
                    }
                  >
                    {isVersionUploading ? '등록 중' : '새 버전 등록'}
                  </button>
                  {hasInFlightVersion && (
                    <p>현재 버전 처리가 끝나면 새 수정본을 등록할 수 있습니다.</p>
                  )}
                </form>

                <ul className="document-version-list">
                  {selectedDocument.versions.map((version) => (
                    <li key={version.versionId}>
                      <div className="version-row-main">
                        <span className="version-number">v{version.versionNo}</span>
                        <div className="version-row-copy">
                          <strong>{version.originalFileName}</strong>
                          <span>{version.fileType} · {formatCreatedAt(version.createdAt)}</span>
                        </div>
                        {version.versionId === selectedDocument.activeVersionId && (
                          <span className="active-version-label">ACTIVE</span>
                        )}
                      </div>
                      <div className="version-row-actions">
                        <span
                          className={
                            'status-badge ' +
                            documentStatusClassName(version.processingStatus ?? version.status)
                          }
                        >
                          {documentStatusLabel(version.processingStatus ?? version.status)}
                        </span>
                        {version.fileType === 'PDF' && (
                          <button
                            type="button"
                            className="pdf-open-button"
                            aria-label={`${version.originalFileName} PDF 열기`}
                            onClick={() => void handleOpenPdf({
                              documentId: selectedDocument.documentId,
                              versionId: version.versionId,
                              versionNo: version.versionNo,
                              originalFileName: version.originalFileName,
                            })}
                            disabled={isDeleting}
                          >
                            <span aria-hidden="true">↗</span>
                            PDF 열기
                          </button>
                        )}
                      </div>
                      {version.processingErrorCode !== null && (
                        <p className="processing-safe-message">
                          {version.retryScheduled
                            ? '처리가 다시 시도됩니다. 실제 진행률은 제공하지 않습니다.'
                            : '처리에 실패했습니다. 파일과 형식을 확인한 후 새 문서를 등록해 주세요.'}
                        </p>
                      )}
                    </li>
                  ))}
                </ul>
              </section>

              <section className="document-delete-section">
                <div>
                  <p className="section-kicker">DANGER ZONE</p>
                  <h3>문서 삭제</h3>
                </div>
                <p>처리 중인 문서는 삭제할 수 없습니다. 원본 파일은 안전한 백그라운드 정리 작업으로 제거됩니다.</p>
                {!isDeleteConfirming ? (
                  <button
                    type="button"
                    className="danger-button"
                    onClick={() => setIsDeleteConfirming(true)}
                    disabled={isSaving || isDeleting || isVersionUploading}
                  >
                    문서 삭제
                  </button>
                ) : (
                  <div className="delete-confirmation" role="alert">
                    <p>이 문서의 모든 버전과 검색용 데이터가 삭제됩니다. 계속할까요?</p>
                    <button type="button" className="danger-button" onClick={() => void handleDelete()} disabled={isDeleting}>
                      {isDeleting ? '삭제 중' : '삭제 확인'}
                    </button>
                    <button
                      type="button"
                      className="secondary-button"
                      onClick={() => setIsDeleteConfirming(false)}
                      disabled={isDeleting}
                    >
                      취소
                    </button>
                  </div>
                )}
              </section>
            </>
          )}
          </section>
        </>
      )}

      {pdfViewerTarget !== null && (
        <>
          <button
            type="button"
            className="pdf-viewer-backdrop"
            aria-label="PDF 뷰어 닫기"
            onClick={closePdfViewer}
          />
          <section
            className="pdf-viewer-panel"
            role="dialog"
            aria-modal="true"
            aria-labelledby="pdf-viewer-title"
            aria-busy={isPdfViewerLoading}
          >
            <header className="pdf-viewer-heading">
              <div>
                <p className="section-kicker">ORIGINAL PDF · VERSION {pdfViewerTarget.versionNo}</p>
                <h2 id="pdf-viewer-title">{pdfViewerTarget.originalFileName}</h2>
              </div>
              <button
                type="button"
                className="pdf-viewer-close-button"
                aria-label="PDF 뷰어 닫기"
                onClick={closePdfViewer}
                autoFocus
              >
                ×
              </button>
            </header>
            <div className="pdf-viewer-content">
              {isPdfViewerLoading && (
                <p className="pdf-viewer-state">
                  <span className="state-spinner" aria-hidden="true" />
                  PDF를 불러오는 중입니다.
                </p>
              )}
              {pdfViewerErrorMessage !== null && !isPdfViewerLoading && (
                <div className="pdf-viewer-error" role="alert">
                  <strong>PDF를 열 수 없습니다.</strong>
                  <p>{pdfViewerErrorMessage}</p>
                  <button
                    type="button"
                    className="secondary-button"
                    onClick={() => void handleOpenPdf(pdfViewerTarget)}
                  >
                    다시 시도
                  </button>
                </div>
              )}
              {pdfViewerUrl !== null && !isPdfViewerLoading && (
                <iframe
                  className="pdf-viewer-frame"
                  src={pdfViewerUrl}
                  title={`${pdfViewerTarget.originalFileName} PDF 미리보기`}
                />
              )}
            </div>
          </section>
        </>
      )}
    </section>
  )
}

function DocumentThumbnail({
  document,
  onSessionExpired,
}: {
  document: DocumentSummary
  onSessionExpired: () => void
}) {
  const canLoad =
    document.latestFileType === 'PDF' && document.latestVersionId !== null
  const [thumbnailState, setThumbnailState] = useState<ThumbnailState>('idle')
  const [thumbnailUrl, setThumbnailUrl] = useState<string | null>(null)
  const [thumbnailRetryKey, setThumbnailRetryKey] = useState(0)

  useEffect(() => {
    if (!canLoad || document.latestVersionId === null) {
      return
    }

    const controller = new AbortController()
    let objectUrl: string | null = null
    void getDocumentThumbnail(
      document.documentId,
      document.latestVersionId,
      controller.signal,
    )
      .then((blob) => {
        if (controller.signal.aborted) {
          return
        }
        objectUrl = URL.createObjectURL(blob)
        setThumbnailUrl(objectUrl)
        setThumbnailState('ready')
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) {
          return
        }
        if (error instanceof DocumentApiError && (error.status === 401 || error.status === 403)) {
          onSessionExpired()
          return
        }
        setThumbnailState('fallback')
      })

    return () => {
      controller.abort()
      if (objectUrl !== null) {
        URL.revokeObjectURL(objectUrl)
      }
    }
  }, [
    canLoad,
    document.documentId,
    document.latestVersionId,
    onSessionExpired,
    thumbnailRetryKey,
  ])

  const originalFileName = document.latestOriginalFileName ?? document.title
  const isThumbnailLoading = canLoad && thumbnailState === 'idle'
  const placeholderLabel =
    document.latestFileType === 'TXT'
      ? 'TXT 문서 미리보기'
      : document.latestFileType === 'PDF'
        ? 'PDF 미리보기를 표시할 수 없음'
        : '문서 미리보기 없음'

  return (
    <div className="document-thumbnail">
      {thumbnailState === 'ready' && thumbnailUrl !== null ? (
        <img
          src={thumbnailUrl}
          alt={originalFileName + ' 첫 페이지 미리보기'}
          loading="lazy"
          decoding="async"
        />
      ) : (
        <div className="thumbnail-fallback">
          <div
            className={
              'thumbnail-placeholder' +
              (isThumbnailLoading ? ' is-loading' : '')
            }
            role="img"
            aria-label={
              isThumbnailLoading
                ? originalFileName + ' 미리보기 불러오는 중'
                : placeholderLabel
            }
          >
            <span aria-hidden="true">
              {isThumbnailLoading
                ? '•••'
                : document.latestFileType ?? 'FILE'}
            </span>
          </div>
          {thumbnailState === 'fallback' && canLoad && (
            <button
              type="button"
              className="thumbnail-retry-button"
              onClick={() => {
                setThumbnailState('idle')
                setThumbnailUrl(null)
                setThumbnailRetryKey((value) => value + 1)
              }}
            >
              미리보기 다시 시도
            </button>
          )}
        </div>
      )}
    </div>
  )
}

function EvidencePage({ onSessionExpired }: { onSessionExpired: () => void }) {
  const [searchQuery, setSearchQuery] = useState('')
  const [searchResults, setSearchResults] = useState<CareerEvidenceSearchResult[]>([])
  const [searchState, setSearchState] = useState<SearchState>('idle')

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
    <section className="vault-page evidence-page" aria-labelledby="evidence-title">
      <header className="page-heading">
        <p className="eyebrow">CAREER EVIDENCE</p>
        <h1 id="evidence-title">경력 근거 검색</h1>
        <p>질문을 입력하면 등록된 문서에서 관련 원문을 최대 5개까지 찾습니다.</p>
      </header>

      <div className="evidence-search-panel">
        <form
          className="document-search-form"
          onSubmit={handleSearchSubmit}
          noValidate
          aria-busy={searchState === 'loading'}
        >
          <label className="visually-hidden" htmlFor="document-search-query">
            경력 근거 검색어
          </label>
          <div className="document-search-controls">
            <input
              id="document-search-query"
              name="query"
              type="text"
              maxLength={500}
              placeholder="예: Spring Boot와 Redis를 사용한 경험"
              value={searchQuery}
              onChange={(event) => handleSearchQueryChange(event.target.value)}
              disabled={searchState === 'loading'}
            />
            <button
              type="submit"
              className="primary-button button-large search-button"
              disabled={searchQuery.trim() === '' || searchState === 'loading'}
              aria-busy={searchState === 'loading'}
            >
              {searchState === 'loading' && <span className="button-spinner" aria-hidden="true" />}
              {searchState === 'loading' ? '검색 중' : '원문 찾기'}
            </button>
          </div>
        </form>

        <p className="search-processing-note">
          <span aria-hidden="true">i</span>
          처리 중인 문서는 검색 결과에 포함되지 않을 수 있습니다.
        </p>
      </div>

      <div className="search-result" aria-live="polite" aria-busy={searchState === 'loading'}>
        {searchState === 'idle' && (
          <p className="state-message search-empty-state">
            등록한 문서에서 확인하고 싶은 경험이나 기술을 입력해 보세요.
          </p>
        )}
        {searchState === 'loading' && (
          <p className="state-message search-state">
            <span className="state-spinner" aria-hidden="true" />
            관련 원문을 찾는 중입니다.
          </p>
        )}
        {searchState === 'empty' && (
          <p className="state-message search-empty-state">
            현재 PRIZM에 등록된 문서에서 관련 근거를 찾지 못했습니다.
          </p>
        )}
        {searchState === 'error' && (
          <p className="form-error feedback-message" role="alert">
            경력 근거 검색 중 문제가 발생했습니다.
          </p>
        )}
        {searchState === 'result' && searchResults.length > 0 && (
          <>
            <p className="search-result-summary">관련 원문 {searchResults.length}개</p>
            <ol className="search-result-list">
              {searchResults.map((result, index) => (
                <li key={result.chunkId}>
                  <article className="search-result-card">
                    <header>
                      <div>
                        <span className="result-index">
                          결과 {String(index + 1).padStart(2, '0')}
                        </span>
                        <h2>{result.documentTitle}</h2>
                      </div>
                      <span className="score-badge">
                        관련도 {formatSearchScore(result.score)}
                      </span>
                    </header>
                    <p className="search-result-meta">
                      <span>버전 {result.versionNo}</span>
                      <span>{result.sourceLabel}</span>
                    </p>
                    <blockquote className="search-result-snippet">{result.snippet}</blockquote>
                    <details className="search-result-full-content">
                      <summary>
                        <span className="full-content-open-label">전체 원문 보기</span>
                        <span className="full-content-close-label">접기</span>
                      </summary>
                      <blockquote>{result.content}</blockquote>
                    </details>
                  </article>
                </li>
              ))}
            </ol>
          </>
        )}
      </div>
    </section>
  )
}

function UploadPage({
  onSessionExpired,
  onNavigateToDocuments,
}: {
  onSessionExpired: () => void
  onNavigateToDocuments: () => void
}) {
  const [uploadTitle, setUploadTitle] = useState('')
  const [uploadDocumentType, setUploadDocumentType] = useState<DocumentType>('OTHER')
  const [uploadFile, setUploadFile] = useState<File | null>(null)
  const [uploadFormKey, setUploadFormKey] = useState(0)
  const [isUploading, setIsUploading] = useState(false)
  const [uploadErrorMessage, setUploadErrorMessage] = useState<string | null>(null)
  const [uploadErrorTarget, setUploadErrorTarget] = useState<UploadErrorTarget>(null)
  const [uploadSuccessMessage, setUploadSuccessMessage] = useState<string | null>(null)

  const handleUploadFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0] ?? null
    const fileValidationMessage = uploadFileValidationMessage(file)
    setUploadFile(file)
    setUploadErrorMessage(fileValidationMessage)
    setUploadErrorTarget(fileValidationMessage === null ? null : 'file')
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
      setUploadErrorTarget('file')
      return
    }

    const fileValidationMessage = uploadFileValidationMessage(uploadFile)
    if (fileValidationMessage !== null) {
      setUploadErrorMessage(fileValidationMessage)
      setUploadErrorTarget('file')
      return
    }

    const normalizedTitle = uploadTitle.trim()
    if (normalizedTitle === '') {
      setUploadErrorMessage('문서 제목을 입력해 주세요.')
      setUploadErrorTarget('title')
      return
    }

    setIsUploading(true)
    setUploadErrorMessage(null)
    setUploadErrorTarget(null)
    setUploadSuccessMessage(null)

    try {
      await uploadDocument(normalizedTitle, uploadDocumentType, uploadFile)
      setUploadSuccessMessage('문서가 등록되었습니다. 처리 상태는 문서 보관함에서 확인할 수 있습니다.')
      setUploadTitle('')
      setUploadDocumentType('OTHER')
      setUploadFile(null)
      setUploadFormKey((value) => value + 1)
    } catch (error) {
      if (error instanceof DocumentApiError && (error.status === 401 || error.status === 403)) {
        onSessionExpired()
        return
      }
      setUploadErrorMessage(uploadFailureMessage(error))
      setUploadErrorTarget('form')
    } finally {
      setIsUploading(false)
    }
  }

  return (
    <section className="vault-page upload-page" aria-labelledby="upload-title">
      <header className="page-heading">
        <p className="eyebrow">ADD DOCUMENT</p>
        <h1 id="upload-title">문서 업로드</h1>
        <p>Career Vault에 커리어 문서 원본을 등록하세요.</p>
      </header>

      {uploadSuccessMessage !== null && (
        <div className="upload-success-panel" role="status">
          <p>{uploadSuccessMessage}</p>
          <button
            type="button"
            className="secondary-button button-large"
            onClick={onNavigateToDocuments}
          >
            문서 보관함에서 확인하기
          </button>
        </div>
      )}

      <div className="upload-layout">
        <aside className="upload-guide" aria-label="업로드 안내">
          <strong>TXT 또는 PDF</strong>
          <p>PDF는 텍스트가 포함된 파일만 처리할 수 있습니다.</p>
          <ul className="upload-restrictions">
            <li>파일당 최대 10MB</li>
            <li>스캔 PDF는 지원하지 않음</li>
            <li>암호화 PDF는 지원하지 않음</li>
          </ul>
        </aside>

        <form
          className="upload-form"
          onSubmit={handleUploadSubmit}
          noValidate
          aria-busy={isUploading}
          aria-describedby={uploadErrorTarget === 'form' ? 'upload-form-error' : undefined}
        >
          <div className="form-field form-field-full">
            <label htmlFor="upload-file">파일</label>
            <input
              key={uploadFormKey}
              id="upload-file"
              name="file"
              type="file"
              accept=".txt,.pdf"
              onChange={handleUploadFileChange}
              disabled={isUploading}
              aria-invalid={uploadErrorTarget === 'file'}
              aria-describedby={uploadErrorTarget === 'file' ? 'upload-form-error' : undefined}
            />
          </div>

          <div className="form-field">
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
          </div>

          <div className="form-field">
            <label htmlFor="upload-title-input">문서 제목</label>
            <input
              id="upload-title-input"
              name="title"
              type="text"
              maxLength={200}
              placeholder="문서 제목을 입력하세요"
              value={uploadTitle}
              onChange={(event) => {
                setUploadTitle(event.target.value)
                setUploadSuccessMessage(null)
                if (uploadErrorTarget === 'title') {
                  setUploadErrorMessage(null)
                  setUploadErrorTarget(null)
                }
              }}
              disabled={isUploading}
              aria-invalid={uploadErrorTarget === 'title'}
              aria-describedby={uploadErrorTarget === 'title' ? 'upload-form-error' : undefined}
            />
          </div>

          {uploadErrorMessage !== null && (
            <p
              id="upload-form-error"
              className="form-error feedback-message form-field-full"
              role="alert"
            >
              {uploadErrorMessage}
            </p>
          )}

          <button
            type="submit"
            className="primary-button button-xlarge upload-submit"
            disabled={isUploading}
            aria-busy={isUploading}
          >
            {isUploading && <span className="button-spinner" aria-hidden="true" />}
            {isUploading ? '업로드 중' : '문서 등록'}
          </button>
        </form>
      </div>
    </section>
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
  return status === null ? '상태 확인 중' : DOCUMENT_STATUS_LABELS[status] ?? status
}

function documentStatusClassName(status: string | null): string {
  switch (status) {
    case 'ACTIVE':
      return 'status-active'
    case 'FAILED':
      return 'status-failed'
    case 'PROCESSING':
      return 'status-processing'
    default:
      return 'status-pending'
  }
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

function signupErrorMessage(error: unknown): string {
  if (error instanceof AuthApiError) {
    if (error.status === 409) {
      return '이미 가입된 이메일입니다.'
    }
    if (error.status === 400) {
      return '이메일과 비밀번호를 확인해 주세요.'
    }
  }
  return '회원가입 처리 중 문제가 발생했습니다.'
}

function uploadFailureMessage(error: unknown): string {
  if (error instanceof DocumentApiError) {
    if (error.code === 'INVALID_DOCUMENT_CONTENT') {
      return 'PDF에서 처리 가능한 텍스트를 찾지 못했습니다. 스캔 파일이거나 암호화된 PDF인지 확인해 주세요.'
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
