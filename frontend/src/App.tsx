import {
  type ChangeEvent,
  type FormEvent,
  type KeyboardEvent as ReactKeyboardEvent,
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
  getTagUsage,
  removeDocumentTag,
  replaceDocumentTags,
  TagApiError,
  type Tag,
  type TagUsage,
} from './api/tagApi'
import { TagModal } from './TagModal'
import {
  DocumentApiError,
  deleteDocument,
  deleteDocumentVersion,
  getDocument,
  getDocumentOriginal,
  getDocumentPdf,
  getDocuments,
  getDocumentThumbnail,
  updateDocumentMetadata,
  type DocumentDetail,
  type DocumentVersion,
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
  JobPostingApiError,
  segmentJobPosting,
  type JobPostingItem,
} from './api/jobPostingApi'
import {
  getEvidenceContext,
  getEvidenceHighlight,
  getEvidenceSourceLabel,
} from './searchEvidencePresentation'
import { loadKeywordEvidence } from './keywordEvidence'
import {
  getEvidencePdfViewerTarget,
  KeywordEvidencePanel,
  type EvidencePdfViewerTarget,
} from './keywordEvidencePanel'
import {
  keywordEvidenceRetryTarget,
  linkedDocumentCountLabel,
  resolveSelectedTag,
  selectedTagIdFromSearch,
  sortTagUsage,
  tagDetailPath,
} from './tagSelection'
import { DocumentTagEditor } from './tagComponents'
import {
  clearSession,
  getAccessToken,
  getStoredCurrentUser,
  saveAccessToken,
  saveCurrentUser,
} from './auth/tokenStorage'
import { expireSessionIfUnauthorized } from './auth/sessionPolicy'
import { focusModalEntry, keepFocusWithinModal, restoreModalTrigger } from './modalFocus'
import { progressSummary } from './documentProcessingPresentation'
import {
  clearJobPostingItemSelection,
  findJobEvidence,
  loadingJobEvidenceGroups,
  selectAllJobPostingItems,
  selectedJobPostingItems,
  toggleJobPostingItemSelection,
  type JobEvidenceGroup,
} from './jobEvidence'
import {
  JobEvidencePanel,
  type JobEvidenceSegmentationState,
} from './jobEvidencePanel'
import {
  JobEvidenceResultsWorkspace,
  JobRequirementSelectionModal,
} from './jobEvidenceWorkspace'
import {
  documentDetailPath,
  documentFolderPath,
  documentListPathAfterDetailClose,
  groupDocumentsByType,
  selectedDocumentIdFromSearch,
  selectedDocumentFolderFromSearch,
} from './documentFolderPresentation'
import { txtPreviewText } from './documentOriginalPresentation'

const LOGIN_PATH = '/login'
const CAREER_VAULT_PATH = '/career-vault'
const DOCUMENTS_PATH = '/career-vault/documents'
const KEYWORDS_PATH = '/career-vault/keywords'
const JOB_EVIDENCE_PATH = '/career-vault/job-evidence'
const JOB_EVIDENCE_RESULTS_PATH = '/career-vault/job-evidence/results'
const EVIDENCE_PATH = '/career-vault/evidence'
const UPLOAD_PATH = '/career-vault/upload'
const MAX_UPLOAD_FILE_SIZE_BYTES = 10 * 1024 * 1024
const DOCUMENT_STATUS_POLL_INTERVAL_MS = 2_000

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
  PENDING: '준비 중',
  QUARANTINED: '준비 중',
  PROCESSING: '검색 준비 중',
  RETRY_WAIT: '잠시 후 다시 준비',
  COMPLETED: '검색 준비 완료',
  ACTIVE: '검색에 사용 중',
  FAILED: '준비에 실패함',
}

const PROCESSING_ERROR_MESSAGES: Readonly<Record<string, string>> = {
  OLLAMA_UNAVAILABLE: 'Ollama가 실행되지 않았거나 연결할 수 없어 임베딩을 만들 수 없습니다.',
  OLLAMA_MODEL_NOT_INSTALLED: '설정된 embedding model이 Ollama에 설치되지 않았습니다.',
  OLLAMA_RUNTIME_FAILURE: 'Ollama가 GPU 또는 embedding model을 실행하지 못했습니다.',
  DOCUMENT_PROCESSING_FAILED: '문서를 처리하지 못했습니다. 파일 형식과 내용을 확인해 주세요.',
}

const UPLOAD_DOCUMENT_TYPE_OPTIONS = DOCUMENT_TYPE_OPTIONS.filter(
  (option): option is { value: DocumentType; label: string } => option.value !== undefined,
)

const NAVIGATION_ITEMS: ReadonlyArray<{
  path: VaultPath
  label: string
  marker: string
}> = [
  { path: DOCUMENTS_PATH, label: '문서 보관함', marker: '문' },
  { path: KEYWORDS_PATH, label: '경력 키워드', marker: '키' },
  { path: JOB_EVIDENCE_PATH, label: '채용공고 경력 찾기', marker: '공' },
  { path: EVIDENCE_PATH, label: '내 경험 찾기', marker: '경' },
  { path: UPLOAD_PATH, label: '문서 업로드', marker: '+' },
]

type VaultPath =
  | typeof DOCUMENTS_PATH
  | typeof KEYWORDS_PATH
  | typeof JOB_EVIDENCE_PATH
  | typeof JOB_EVIDENCE_RESULTS_PATH
  | typeof EVIDENCE_PATH
  | typeof UPLOAD_PATH
type AppPath = typeof LOGIN_PATH | VaultPath
type SearchState = 'idle' | 'loading' | 'result' | 'empty' | 'error'
type UploadErrorTarget = 'file' | 'title' | 'form' | null
type ThumbnailState = 'idle' | 'ready' | 'fallback'
type OriginalViewerTarget = {
  documentId: number
  versionId: number
  versionNo: number
  originalFileName: string
  fileType: 'TXT' | 'PDF'
}

function isVaultPath(pathname: string): pathname is VaultPath {
  return pathname === DOCUMENTS_PATH
    || pathname === KEYWORDS_PATH
    || pathname === JOB_EVIDENCE_PATH
    || pathname === JOB_EVIDENCE_RESULTS_PATH
    || pathname === EVIDENCE_PATH
    || pathname === UPLOAD_PATH
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

  const replace = useCallback((nextPath: AppPath) => {
    if (window.location.pathname !== nextPath) {
      window.history.replaceState(null, '', nextPath)
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
        onReplace={replace}
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
  onReplace,
  onLogout,
  onSessionExpired,
}: {
  path: VaultPath
  currentUser: CurrentUser
  onNavigate: (path: AppPath) => void
  onReplace: (path: AppPath) => void
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
              aria-current={path === item.path
                || (item.path === JOB_EVIDENCE_PATH && path === JOB_EVIDENCE_RESULTS_PATH)
                ? 'page'
                : undefined}
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
          {path === KEYWORDS_PATH && (
            <CareerKeywordsPage
              onSessionExpired={onSessionExpired}
              onNavigateToDocument={(documentId) => {
                window.history.pushState(null, '', documentDetailPath(documentId))
                onNavigate(DOCUMENTS_PATH)
              }}
            />
          )}
          {(path === JOB_EVIDENCE_PATH || path === JOB_EVIDENCE_RESULTS_PATH) && (
            <JobEvidencePage
              view={path === JOB_EVIDENCE_RESULTS_PATH ? 'results' : 'editor'}
              onSessionExpired={onSessionExpired}
              onNavigateToEditor={() => onNavigate(JOB_EVIDENCE_PATH)}
              onReplaceToEditor={() => onReplace(JOB_EVIDENCE_PATH)}
              onNavigateToResults={() => onNavigate(JOB_EVIDENCE_RESULTS_PATH)}
              onNavigateToDocument={(documentId) => {
                window.history.pushState(null, '', documentDetailPath(documentId))
                onNavigate(DOCUMENTS_PATH)
              }}
            />
          )}
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
  const [selectedDocumentType, setSelectedDocumentType] = useState<DocumentType | undefined>(() =>
    selectedDocumentFolderFromSearch(window.location.search),
  )
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
  const [selectedPreviewVersionId, setSelectedPreviewVersionId] = useState<number | null>(null)
  const [isDetailLoading, setIsDetailLoading] = useState(false)
  const [detailErrorMessage, setDetailErrorMessage] = useState<string | null>(null)
  const [editTitle, setEditTitle] = useState('')
  const [editDocumentType, setEditDocumentType] = useState<DocumentType>('OTHER')
  const [isSaving, setIsSaving] = useState(false)
  const [isDeleteConfirming, setIsDeleteConfirming] = useState(false)
  const [isDeleting, setIsDeleting] = useState(false)
  const [versionDeleteTarget, setVersionDeleteTarget] = useState<DocumentVersion | null>(null)
  const [isVersionDeleting, setIsVersionDeleting] = useState(false)
  const [isVersionUploadFormOpen, setIsVersionUploadFormOpen] = useState(false)
  const [versionUploadFile, setVersionUploadFile] = useState<File | null>(null)
  const [versionUploadInputKey, setVersionUploadInputKey] = useState(0)
  const [isVersionUploading, setIsVersionUploading] = useState(false)
  const [isTagModalOpen, setIsTagModalOpen] = useState(false)
  const [removingTagId, setRemovingTagId] = useState<number | null>(null)
  const [actionMessage, setActionMessage] = useState<string | null>(null)
  const [originalViewerTarget, setOriginalViewerTarget] = useState<OriginalViewerTarget | null>(null)
  const [originalViewerUrl, setOriginalViewerUrl] = useState<string | null>(null)
  const [originalViewerText, setOriginalViewerText] = useState<string | null>(null)
  const [originalViewerErrorMessage, setOriginalViewerErrorMessage] = useState<string | null>(null)
  const [isOriginalViewerLoading, setIsOriginalViewerLoading] = useState(false)
  const [processingClock, setProcessingClock] = useState(() => Date.now())
  const detailRequestId = useRef(0)
  const originalRequestId = useRef(0)
  const originalAbortController = useRef<AbortController | null>(null)
  const originalObjectUrl = useRef<string | null>(null)

  const selectDocumentFolder = useCallback((documentType: DocumentType | undefined) => {
    const nextPath = documentFolderPath(documentType)
    if (`${window.location.pathname}${window.location.search}` !== nextPath) {
      window.history.pushState(null, '', nextPath)
    }
    setSelectedDocumentType(documentType)
    setSelectedProcessingStatus(undefined)
  }, [])

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

        if (error instanceof DocumentApiError && expireSessionIfUnauthorized(error, onSessionExpired)) {
          isCurrentRequest = false
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

  const shouldPollDocumentStatus =
    documents.some(isDocumentSummaryInFlight) ||
    (selectedDocument?.versions.some(isDocumentVersionInFlight) ?? false)

  useEffect(() => {
    if (!shouldPollDocumentStatus) {
      return
    }

    let cancelled = false
    let requestInFlight = false
    const refreshStatus = async () => {
      if (requestInFlight) {
        return
      }
      requestInFlight = true
      try {
        const documentId = selectedDocument?.documentId ?? null
        const [nextDocuments, nextDetail] = await Promise.all([
          getDocuments({
            documentType: selectedDocumentType,
            title: appliedTitleQuery,
            processingStatus: selectedProcessingStatus,
          }),
          documentId === null ? Promise.resolve(null) : getDocument(documentId),
        ])
        if (cancelled) {
          return
        }
        setDocuments(nextDocuments)
        if (nextDetail !== null && detailRequestId.current > 0) {
          setSelectedDocument(nextDetail)
        }
      } catch (error) {
        if (cancelled) {
          return
        }
        if (error instanceof DocumentApiError && expireSessionIfUnauthorized(error, onSessionExpired)) {
          cancelled = true
        }
        // 일시적인 polling 실패는 기존 화면을 유지하고 다음 주기에 다시 시도한다.
      } finally {
        requestInFlight = false
      }
    }
    const timer = window.setInterval(() => {
      void refreshStatus()
    }, DOCUMENT_STATUS_POLL_INTERVAL_MS)

    return () => {
      cancelled = true
      window.clearInterval(timer)
    }
  }, [
    appliedTitleQuery,
    documents,
    onSessionExpired,
    selectedDocument,
    selectedDocumentType,
    selectedProcessingStatus,
    shouldPollDocumentStatus,
  ])

  const hasRetryCountdown =
    documents.some((document) => document.latestProcessingStatus === 'RETRY_WAIT') ||
    (selectedDocument?.versions.some((version) => version.processingStatus === 'RETRY_WAIT') ?? false)

  useEffect(() => {
    if (!hasRetryCountdown) {
      return
    }
    const timer = window.setInterval(() => setProcessingClock(Date.now()), 1_000)
    return () => window.clearInterval(timer)
  }, [hasRetryCountdown])

  const handleTitleFilterSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const normalizedTitle = titleQuery.trim()
    if (normalizedTitle === appliedTitleQuery) {
      setReloadKey((value) => value + 1)
      return
    }
    if (normalizedTitle !== '') {
      selectDocumentFolder(undefined)
    }
    setAppliedTitleQuery(normalizedTitle)
  }

  const closeOriginalViewer = useCallback(() => {
    originalRequestId.current += 1
    originalAbortController.current?.abort()
    originalAbortController.current = null
    if (originalObjectUrl.current !== null) {
      URL.revokeObjectURL(originalObjectUrl.current)
      originalObjectUrl.current = null
    }
    setOriginalViewerTarget(null)
    setOriginalViewerUrl(null)
    setOriginalViewerText(null)
    setOriginalViewerErrorMessage(null)
    setIsOriginalViewerLoading(false)
  }, [])

  useEffect(() => () => {
    originalAbortController.current?.abort()
    if (originalObjectUrl.current !== null) {
      URL.revokeObjectURL(originalObjectUrl.current)
    }
  }, [])

  const resetDocumentDetailState = useCallback(() => {
    closeOriginalViewer()
    detailRequestId.current += 1
    setSelectedDocument(null)
    setSelectedPreviewVersionId(null)
    setDetailErrorMessage(null)
    setIsDetailLoading(false)
    setIsDeleteConfirming(false)
    setVersionDeleteTarget(null)
    setIsVersionDeleting(false)
    setIsVersionUploadFormOpen(false)
    setVersionUploadFile(null)
    setVersionUploadInputKey((value) => value + 1)
    setIsVersionUploading(false)
    setIsTagModalOpen(false)
    setRemovingTagId(null)
  }, [closeOriginalViewer])

  const closeDocumentDetail = useCallback(() => {
    const nextPath = documentListPathAfterDetailClose(window.location.search)
    if (`${window.location.pathname}${window.location.search}` !== nextPath) {
      window.history.replaceState(null, '', nextPath)
    }
    resetDocumentDetailState()
  }, [resetDocumentDetailState])

  const isDocumentDetailOpen =
    isDetailLoading || selectedDocument !== null || detailErrorMessage !== null

  useEffect(() => {
    if (originalViewerTarget === null) {
      return
    }

    const previousOverflow = document.body.style.overflow
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        closeOriginalViewer()
      }
    }
    document.body.style.overflow = 'hidden'
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.body.style.overflow = previousOverflow
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [closeOriginalViewer, originalViewerTarget])

  const handleOpenDocument = useCallback(async (documentId: number) => {
    closeOriginalViewer()
    const requestId = detailRequestId.current + 1
    detailRequestId.current = requestId
    setIsDetailLoading(true)
    setSelectedDocument(null)
    setDetailErrorMessage(null)
    setActionMessage(null)
    setIsDeleteConfirming(false)
    setVersionDeleteTarget(null)
    setIsVersionDeleting(false)
    setIsVersionUploadFormOpen(false)
    setVersionUploadFile(null)
    setVersionUploadInputKey((value) => value + 1)
    try {
      const detail = await getDocument(documentId)
      if (detailRequestId.current !== requestId) {
        return
      }
      setSelectedDocument(detail)
      setSelectedPreviewVersionId(detail.activeVersionId ?? detail.versions[0]?.versionId ?? null)
      setEditTitle(detail.title)
      setEditDocumentType(detail.documentType)
    } catch (error) {
      if (detailRequestId.current !== requestId) {
        return
      }
      if (error instanceof DocumentApiError && expireSessionIfUnauthorized(error, onSessionExpired)) {
        return
      }
      setDetailErrorMessage('문서 상세 정보를 불러오지 못했습니다.')
    } finally {
      if (detailRequestId.current === requestId) {
        setIsDetailLoading(false)
      }
    }
  }, [closeOriginalViewer, onSessionExpired])

  useEffect(() => {
    const syncLocation = () => {
      setSelectedDocumentType(selectedDocumentFolderFromSearch(window.location.search))
      const documentId = selectedDocumentIdFromSearch(window.location.search)
      if (documentId === null) {
        resetDocumentDetailState()
        return
      }
      if (selectedDocument?.documentId !== documentId) {
        void handleOpenDocument(documentId)
      }
    }

    const timeout = window.setTimeout(syncLocation, 0)
    window.addEventListener('popstate', syncLocation)
    return () => {
      window.clearTimeout(timeout)
      window.removeEventListener('popstate', syncLocation)
    }
  }, [handleOpenDocument, resetDocumentDetailState, selectedDocument?.documentId])

  const navigateToDocumentDetail = useCallback((documentId: number) => {
    const nextPath = documentDetailPath(documentId, window.location.search)
    if (`${window.location.pathname}${window.location.search}` !== nextPath) {
      window.history.pushState(null, '', nextPath)
    }
    void handleOpenDocument(documentId)
  }, [handleOpenDocument])

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
      if (error instanceof DocumentApiError && expireSessionIfUnauthorized(error, onSessionExpired)) {
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
      closeDocumentDetail()
      setActionMessage('문서를 삭제했습니다. 원본 파일 정리는 안전하게 백그라운드에서 진행됩니다.')
      setReloadKey((value) => value + 1)
    } catch (error) {
      if (error instanceof DocumentApiError && expireSessionIfUnauthorized(error, onSessionExpired)) {
        return
      }
      if (error instanceof DocumentApiError && error.code === 'DOCUMENT_PROCESSING') {
        setDetailErrorMessage('문서를 읽고 검색할 수 있게 준비 중에는 삭제할 수 없습니다. 준비가 끝난 뒤 다시 시도해 주세요.')
      } else {
        setDetailErrorMessage('문서를 삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.')
      }
    } finally {
      setIsDeleting(false)
    }
  }

  const handleVersionDelete = async () => {
    if (selectedDocument === null || versionDeleteTarget === null || isVersionDeleting) {
      return
    }

    const documentId = selectedDocument.documentId
    const versionId = versionDeleteTarget.versionId
    const versionNo = versionDeleteTarget.versionNo
    const requestId = detailRequestId.current
    setIsVersionDeleting(true)
    setDetailErrorMessage(null)
    try {
      await deleteDocumentVersion(documentId, versionId)
      const updated = await getDocument(documentId)
      if (detailRequestId.current !== requestId) {
        return
      }
      setSelectedDocument(updated)
      setVersionDeleteTarget(null)
      setActionMessage(`v${versionNo} 이전 버전을 삭제했어요. 원본 파일은 안전하게 정리됩니다.`)
      setReloadKey((value) => value + 1)
    } catch (error) {
      if (detailRequestId.current !== requestId) {
        return
      }
      if (error instanceof DocumentApiError && expireSessionIfUnauthorized(error, onSessionExpired)) {
        return
      }
      if (error instanceof DocumentApiError && error.code === 'DOCUMENT_VERSION_ACTIVE') {
        setDetailErrorMessage('검색에 사용 중인 버전은 지울 수 없어요. 새 버전이 검색에 사용되기 시작한 뒤 정리해 주세요.')
      } else if (error instanceof DocumentApiError && error.code === 'DOCUMENT_PROCESSING') {
        setDetailErrorMessage('아직 문서를 읽고 검색할 수 있게 준비 중인 버전은 지울 수 없어요.')
      } else {
        setDetailErrorMessage('이전 버전을 삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.')
      }
    } finally {
      if (detailRequestId.current === requestId) {
        setIsVersionDeleting(false)
      }
    }
  }

  const handleVersionFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0] ?? null
    setVersionUploadFile(file)
    setActionMessage(null)
    setDetailErrorMessage(uploadFileValidationMessage(file))
  }

  const handleSaveDocumentTags = async (tags: Tag[]) => {
    if (selectedDocument === null) return
    const documentId = selectedDocument.documentId
    const updatedTags = await replaceDocumentTags(
      documentId,
      tags.map((tag) => tag.tagId),
    )
    setSelectedDocument((current) =>
      current?.documentId === documentId ? { ...current, tags: updatedTags } : current,
    )
    setIsTagModalOpen(false)
    setActionMessage('문서 태그를 저장했습니다.')
  }

  const handleRemoveDocumentTag = async (tagId: number) => {
    if (selectedDocument === null || removingTagId !== null) return
    const documentId = selectedDocument.documentId
    setRemovingTagId(tagId)
    setDetailErrorMessage(null)
    try {
      await removeDocumentTag(documentId, tagId)
      setSelectedDocument((current) => current?.documentId === documentId
        ? { ...current, tags: current.tags.filter((tag) => tag.tagId !== tagId) }
        : current)
      setActionMessage('문서 태그를 제거했습니다.')
    } catch (error) {
      if (error instanceof TagApiError && expireSessionIfUnauthorized(error, onSessionExpired)) {
        return
      }
      setDetailErrorMessage('문서 태그를 제거하지 못했습니다.')
    } finally {
      setRemovingTagId(null)
    }
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
      setIsVersionUploadFormOpen(false)
      setVersionUploadFile(null)
      setVersionUploadInputKey((value) => value + 1)
      setActionMessage(
        `버전 ${uploadedVersion?.versionNo ?? updated.versions.length}을 등록했어요. 문서를 읽고 검색할 수 있게 준비가 끝나면 검색에 사용되는 버전으로 바뀝니다.`,
      )
      setReloadKey((value) => value + 1)
    } catch (error) {
      if (detailRequestId.current !== requestId) {
        return
      }
      if (error instanceof DocumentApiError && expireSessionIfUnauthorized(error, onSessionExpired)) {
        return
      }
      if (error instanceof DocumentApiError && error.code === 'DOCUMENT_PROCESSING') {
        setDetailErrorMessage('현재 버전을 읽고 검색할 수 있게 준비하는 중이에요. 준비가 끝난 뒤 새 버전을 등록할 수 있습니다.')
      } else {
        setDetailErrorMessage('새 버전을 등록하지 못했습니다. 파일을 확인한 뒤 다시 시도해 주세요.')
      }
    } finally {
      if (detailRequestId.current === requestId) {
        setIsVersionUploading(false)
      }
    }
  }

  const handleOpenOriginal = async (target: OriginalViewerTarget) => {
    closeOriginalViewer()
    const requestId = originalRequestId.current + 1
    originalRequestId.current = requestId
    const controller = new AbortController()
    originalAbortController.current = controller
    setOriginalViewerTarget(target)
    setOriginalViewerErrorMessage(null)
    setIsOriginalViewerLoading(true)

    try {
      const original = await getDocumentOriginal(target.documentId, target.versionId, controller.signal)
      if (originalRequestId.current !== requestId) {
        return
      }
      if (original.fileType !== target.fileType) {
        throw new DocumentApiError(502, 'INVALID_ORIGINAL_RESPONSE')
      }
      if (original.fileType === 'PDF') {
        const objectUrl = URL.createObjectURL(original.blob)
        if (originalRequestId.current !== requestId) {
          URL.revokeObjectURL(objectUrl)
          return
        }
        originalObjectUrl.current = objectUrl
        setOriginalViewerUrl(objectUrl)
      } else {
        const text = await original.blob.text()
        if (originalRequestId.current !== requestId) {
          return
        }
        setOriginalViewerText(text)
      }
    } catch (error) {
      if (originalRequestId.current !== requestId) {
        return
      }
      if (error instanceof DOMException && error.name === 'AbortError') {
        return
      }
      if (error instanceof DocumentApiError && expireSessionIfUnauthorized(error, onSessionExpired)) {
        closeOriginalViewer()
        return
      }
      if (error instanceof DocumentApiError && error.code === 'ORIGINAL_FILE_NOT_FOUND') {
        setOriginalViewerErrorMessage('첨부한 원본 파일을 찾지 못했습니다.')
      } else {
        setOriginalViewerErrorMessage('원본 파일을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.')
      }
    } finally {
      if (originalRequestId.current === requestId) {
        originalAbortController.current = null
        setIsOriginalViewerLoading(false)
      }
    }
  }

  const latestVersion = selectedDocument?.versions[0] ?? null
  const hasInFlightVersion =
    latestVersion?.status === 'QUARANTINED' ||
    latestVersion?.status === 'PROCESSING' ||
    (selectedDocument?.versions.some((version) =>
      version.processingStatus === 'PENDING' ||
      version.processingStatus === 'PROCESSING' ||
      version.processingStatus === 'RETRY_WAIT'
    ) ?? false)
  const activeVersion = selectedDocument?.versions.find(
    (version) => version.versionId === selectedDocument.activeVersionId,
  ) ?? null
  const previewVersion = selectedDocument?.versions.find(
    (version) => version.versionId === selectedPreviewVersionId,
  ) ?? activeVersion ?? latestVersion
  const hasMetadataChanges = selectedDocument !== null && (
    editTitle.trim() !== selectedDocument.title || editDocumentType !== selectedDocument.documentType
  )
  const documentFolders = groupDocumentsByType(documents)
  const isSearchingDocuments = appliedTitleQuery !== ''

  return (
    <section
      className={'vault-page documents-page' + (isDocumentDetailOpen ? ' is-detail' : '')}
      aria-labelledby={isDocumentDetailOpen ? 'document-detail-title' : 'documents-title'}
    >
      {!isDocumentDetailOpen && <>
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
          {selectedDocumentType !== undefined && !isSearchingDocuments && <label className="document-filter" htmlFor="processing-status">
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
              {[
                { value: undefined, label: '전체' },
                { value: 'COMPLETED' as ProcessingJobStatus, label: '검색 가능' },
                { value: 'PROCESSING' as ProcessingJobStatus, label: '검색 준비 중' },
                { value: 'FAILED' as ProcessingJobStatus, label: '실패' },
              ].map((option) => (
                <option key={option.label} value={option.value ?? ''}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>}
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
        {!isLoading && errorMessage === null && selectedDocumentType === undefined && !isSearchingDocuments && documents.length > 0 && (
          <div className="document-folder-section">
            <div className="document-folder-heading">
              <div>
                <p className="section-kicker">BROWSE BY TYPE</p>
                <h2>문서 유형</h2>
              </div>
              <span>등록된 유형 {documentFolders.length}개</span>
            </div>
            <ul className="document-folder-grid">
              {documentFolders.map((folder) => (
                <li key={folder.documentType}>
                  <button type="button" className="document-folder-card" onClick={() => selectDocumentFolder(folder.documentType)}>
                    <img src="/assets/prizm-document-folder.png" alt="" aria-hidden="true" />
                    <span className="document-folder-copy">
                      <strong>{documentTypeLabel(folder.documentType)}</strong>
                      <small>{folder.documents.length}개</small>
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          </div>
        )}
        {!isLoading && errorMessage === null && (selectedDocumentType !== undefined || isSearchingDocuments) && documents.length > 0 && (
          <>
            <div className="document-folder-breadcrumb" aria-label="문서 보관함 위치">
              <button type="button" onClick={() => selectDocumentFolder(undefined)}>문서 보관함</button>
              {selectedDocumentType !== undefined && <><span>›</span><span aria-current="page">{documentTypeLabel(selectedDocumentType)}</span></>}
              {isSearchingDocuments && <><span>›</span><span aria-current="page">검색 결과</span></>}
              <span className="document-folder-count">등록된 문서 {documents.length}개</span>
            </div>
            {isSearchingDocuments && <button type="button" className="document-folder-reset" onClick={() => { setTitleQuery(''); setAppliedTitleQuery('') }}>폴더로 돌아가기</button>}
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
                    {document.latestProcessingStatus !== null && (
                      <p className="document-processing-summary">
                        {document.latestProcessingStatus === 'RETRY_WAIT'
                          ? retrySummary(
                              document.latestRetryCount,
                              document.maxRetries,
                              document.latestNextRetryAt,
                              processingClock,
                            )
                          : progressSummary(
                              document.latestProcessingStatus,
                              document.latestProcessingStage,
                              document.latestCompletedChunks,
                              document.latestTotalChunks,
                              document.latestProgressPercent,
                            )}
                      </p>
                    )}
                    {document.latestProcessingErrorCode !== null && (
                      <p className="processing-safe-message">
                        {processingErrorMessage(document.latestProcessingErrorCode)}
                      </p>
                    )}
                    <p className="document-version-count">버전 {document.versionCount}개</p>
                    <time dateTime={document.createdAt}>{formatCreatedAt(document.createdAt)}</time>
                    <button
                      type="button"
                      className="document-detail-button"
                      onClick={() => navigateToDocumentDetail(document.documentId)}
                    >
                      상세 보기
                    </button>
                  </div>
                </article>
              </li>
            ))}
          </ul>
          </>
        )}
      </div>

      </>}

      {isDocumentDetailOpen && (
        <section
          className="document-detail-page"
          aria-labelledby="document-detail-title"
          aria-hidden={originalViewerTarget !== null}
          aria-live="polite"
          aria-busy={isDetailLoading}
        >
          <div className="document-detail-heading">
            <div>
              <p className="document-detail-breadcrumb">문서 보관함 / 문서 상세</p>
              <h2 id="document-detail-title">
                {selectedDocument?.title ?? '문서 상세'}
              </h2>
            </div>
            <button
              type="button"
              className="secondary-button document-detail-back-button"
              onClick={closeDocumentDetail}
            >
              목록으로
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
            <div className="document-detail-layout">
              <div className="document-detail-main">
              <section className="document-detail-preview" aria-labelledby="document-preview-heading">
                <header className="document-preview-toolbar">
                  <div>
                    <h3 id="document-preview-heading">
                      {previewVersion?.originalFileName ?? '미리볼 버전이 없습니다.'}
                    </h3>
                    {previewVersion !== null && <span>v{previewVersion.versionNo}</span>}
                  </div>
                  {previewVersion !== null && (
                    <button
                      type="button"
                      className="primary-button document-original-open-button"
                      onClick={() => void handleOpenOriginal({
                        documentId: selectedDocument.documentId,
                        versionId: previewVersion.versionId,
                        versionNo: previewVersion.versionNo,
                        originalFileName: previewVersion.originalFileName,
                        fileType: previewVersion.fileType,
                      })}
                      disabled={isDeleting}
                    >
                      원문 열기
                    </button>
                  )}
                </header>

                <DocumentVersionPreview
                  key={previewVersion?.versionId ?? 'empty'}
                  documentId={selectedDocument.documentId}
                  documentTitle={selectedDocument.title}
                  version={previewVersion}
                  onSessionExpired={onSessionExpired}
                />

                <footer className="document-preview-summary">
                  <span className="type-badge">{documentTypeLabel(selectedDocument.documentType)}</span>
                  <span>{selectedDocument.versions.length}개 버전</span>
                  {previewVersion?.versionId === selectedDocument.activeVersionId && (
                    <span className="active-version-label">검색에 사용 중</span>
                  )}
                  {previewVersion !== null && (
                    <time dateTime={previewVersion.createdAt}>
                      {formatCreatedAt(previewVersion.createdAt)}
                    </time>
                  )}
                </footer>
              </section>

              {actionMessage !== null && (
                <p className="feedback-message document-detail-success" role="status">
                  {actionMessage}
                </p>
              )}

              <section
                className="document-detail-section document-management-card"
                aria-label="문서 기본 정보 및 태그"
              >
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
                    disabled={!hasMetadataChanges || isSaving || isDeleting || isVersionUploading}
                  >
                    {isSaving ? '저장 중' : '정보 저장'}
                  </button>
                </form>

                <div className="document-management-tag-section">
                  <div className="document-detail-section-heading">
                    <div>
                      <p className="section-kicker">DOCUMENT TAGS</p>
                      <h3 id="document-tags-heading">태그</h3>
                    </div>
                    <p>문서를 분류할 태그를 직접 관리할 수 있습니다.</p>
                  </div>
                  <DocumentTagEditor
                    tags={selectedDocument.tags}
                    emptyMessage="연결된 태그가 없습니다."
                    removeLabel={(tag) => `${tag.name} 태그 제거`}
                    disabled={isDeleting}
                    removingTagId={removingTagId}
                    onRemove={(tagId) => void handleRemoveDocumentTag(tagId)}
                    onAdd={() => setIsTagModalOpen(true)}
                  />
                </div>
              </section>
              </div>

              <section className="document-detail-section document-version-section" aria-labelledby="versions-heading">
                <div className="document-version-heading">
                  <div>
                    <p className="section-kicker">VERSION HISTORY</p>
                    <h3 id="versions-heading">버전</h3>
                  </div>
                  <button
                    type="button"
                    className="secondary-button version-upload-toggle"
                    aria-expanded={isVersionUploadFormOpen}
                    aria-controls="version-upload-form"
                    onClick={() => {
                      const nextIsOpen = !isVersionUploadFormOpen
                      setIsVersionUploadFormOpen(nextIsOpen)
                      if (!nextIsOpen) {
                        setVersionUploadFile(null)
                        setVersionUploadInputKey((value) => value + 1)
                      }
                    }}
                    disabled={isDeleting || isVersionUploading}
                  >
                    {isVersionUploadFormOpen ? '닫기' : '+ 추가'}
                  </button>
                </div>

                {isVersionUploadFormOpen && (
                  <form id="version-upload-form" className="version-upload-form" onSubmit={handleVersionUpload}>
                    <div>
                      <strong>새 수정본 추가</strong>
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
                      className="primary-button version-upload-button"
                      disabled={
                        hasInFlightVersion ||
                        versionUploadFile === null ||
                        isVersionUploading ||
                        isDeleting
                      }
                    >
                      {isVersionUploading ? '등록 중' : '등록'}
                    </button>
                    {hasInFlightVersion && (
                      <p>현재 버전을 검색할 수 있게 준비한 뒤 새 수정본을 등록할 수 있어요.</p>
                    )}
                  </form>
                )}

                <ul className="document-version-list">
                  {selectedDocument.versions.map((version) => (
                    <li
                      key={version.versionId}
                      className={version.versionId === previewVersion?.versionId ? 'is-selected' : ''}
                    >
                      <button
                        type="button"
                        className="version-select-button"
                        aria-pressed={version.versionId === previewVersion?.versionId}
                        onClick={() => setSelectedPreviewVersionId(version.versionId)}
                      >
                        <strong>v{version.versionNo}</strong>
                        <span>
                          {version.versionId === selectedDocument.activeVersionId
                            ? '현재 · ACTIVE'
                            : versionHistoryStatusLabel(version)}
                        </span>
                        <time dateTime={version.createdAt}>{formatCreatedAt(version.createdAt)}</time>
                      </button>
                      <div className="version-row-actions">
                        {version.versionId !== selectedDocument.activeVersionId && !isDocumentVersionInFlight(version) && (
                          <button
                            type="button"
                            className="version-delete-button"
                            aria-label={`${version.originalFileName} v${version.versionNo} 삭제`}
                            title="이전 버전 삭제"
                            onClick={() => setVersionDeleteTarget(version)}
                            disabled={isDeleting || isVersionDeleting || isVersionUploading}
                          >
                            <svg viewBox="0 0 24 24" aria-hidden="true">
                              <path d="M4 7h16M9 7V4h6v3M7 7l1 13h8l1-13M10 11v5M14 11v5" />
                            </svg>
                          </button>
                        )}
                      </div>
                      {versionDeleteTarget?.versionId === version.versionId && (
                        <div className="version-delete-confirmation" role="alert">
                          <p>v{version.versionNo} 이전 버전을 삭제할까요? 이 버전의 원본과 검색용 내용이 정리되며 되돌릴 수 없어요.</p>
                          <button
                            type="button"
                            className="danger-button"
                            onClick={() => void handleVersionDelete()}
                            disabled={isVersionDeleting}
                          >
                            {isVersionDeleting ? '삭제 중' : '삭제'}
                          </button>
                          <button
                            type="button"
                            className="secondary-button"
                            onClick={() => setVersionDeleteTarget(null)}
                            disabled={isVersionDeleting}
                          >
                            취소
                          </button>
                        </div>
                      )}
                      {version.processingErrorCode !== null && (
                        <p className="processing-safe-message">
                          {processingErrorMessage(version.processingErrorCode)}
                        </p>
                      )}
                      {version.processingStatus !== null && isDocumentVersionInFlight(version) && (
                        <div className="processing-progress" role="status">
                          <p>
                            {version.progressPercent === null &&
                              version.processingStatus !== 'RETRY_WAIT' &&
                              version.processingStatus !== 'FAILED' && (
                                <span className="state-spinner" aria-hidden="true" />
                              )}
                            {version.processingStatus === 'RETRY_WAIT'
                              ? retrySummary(
                                  version.retryCount,
                                  version.maxRetries,
                                  version.nextRetryAt,
                                  processingClock,
                                )
                              : progressSummary(
                                  version.processingStatus,
                                  version.processingStage,
                                  version.completedChunks,
                                  version.totalChunks,
                                  version.progressPercent,
                                )}
                          </p>
                          {version.progressPercent !== null &&
                            version.processingStatus !== 'FAILED' &&
                            version.processingStatus !== 'RETRY_WAIT' && (
                            <progress
                              max="100"
                              value={version.progressPercent}
                              aria-label={`문서 처리 ${version.progressPercent}%`}
                            />
                          )}
                        </div>
                      )}
                    </li>
                  ))}
                </ul>
              </section>

              <section className="document-delete-section">
                <div>
                  <p className="section-kicker">DOCUMENT MANAGEMENT</p>
                  <h3>문서 관리</h3>
                </div>
                <p>이 문서를 더 이상 보관하지 않으려면 모든 버전을 함께 삭제할 수 있어요. 검색 준비 중인 문서는 삭제할 수 없습니다.</p>
                {!isDeleteConfirming ? (
                  <button
                    type="button"
                    className="danger-button"
                    onClick={() => setIsDeleteConfirming(true)}
                    disabled={hasInFlightVersion || isSaving || isDeleting || isVersionUploading}
                  >
                    문서 삭제
                  </button>
                ) : (
                  <div className="delete-confirmation" role="alert">
                    <p>삭제하면 모든 버전과 검색용 데이터가 사라지며 되돌릴 수 없습니다. 계속할까요?</p>
                    <button
                      type="button"
                      className="danger-button"
                      onClick={() => void handleDelete()}
                      disabled={hasInFlightVersion || isDeleting}
                    >
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
            </div>
          )}
        </section>
      )}

      {isTagModalOpen && selectedDocument !== null && (
        <TagModal
          selectedTags={selectedDocument.tags}
          onSave={handleSaveDocumentTags}
          onClose={() => setIsTagModalOpen(false)}
          onSessionExpired={onSessionExpired}
        />
      )}

      {originalViewerTarget !== null && (
        <>
          <button
            type="button"
            className="pdf-viewer-backdrop"
            aria-label="원문 뷰어 닫기"
            onClick={closeOriginalViewer}
          />
          <section
            className="pdf-viewer-panel"
            role="dialog"
            aria-modal="true"
            aria-labelledby="original-viewer-title"
            aria-busy={isOriginalViewerLoading}
          >
            <header className="pdf-viewer-heading">
              <div>
                <p className="section-kicker">
                  ORIGINAL {originalViewerTarget.fileType} · VERSION {originalViewerTarget.versionNo}
                </p>
                <h2 id="original-viewer-title">{originalViewerTarget.originalFileName}</h2>
              </div>
              <button
                type="button"
                className="pdf-viewer-close-button"
                aria-label="원문 뷰어 닫기"
                onClick={closeOriginalViewer}
                autoFocus
              >
                ×
              </button>
            </header>
            <div className="pdf-viewer-content">
              {isOriginalViewerLoading && (
                <p className="pdf-viewer-state">
                  <span className="state-spinner" aria-hidden="true" />
                  원본 파일을 불러오는 중입니다.
                </p>
              )}
              {originalViewerErrorMessage !== null && !isOriginalViewerLoading && (
                <div className="pdf-viewer-error" role="alert">
                  <strong>원본 파일을 열 수 없습니다.</strong>
                  <p>{originalViewerErrorMessage}</p>
                  <button
                    type="button"
                    className="secondary-button"
                    onClick={() => void handleOpenOriginal(originalViewerTarget)}
                  >
                    다시 시도
                  </button>
                </div>
              )}
              {originalViewerUrl !== null && !isOriginalViewerLoading && (
                <iframe
                  className="pdf-viewer-frame"
                  src={originalViewerUrl}
                  title={`${originalViewerTarget.originalFileName} PDF 원문`}
                />
              )}
              {originalViewerText !== null && !isOriginalViewerLoading && (
                <div className="txt-viewer-scroll" role="document">
                  {originalViewerText === '' ? (
                    <p className="txt-viewer-empty">내용이 없는 TXT 문서입니다.</p>
                  ) : (
                    <pre className="txt-viewer-text">{originalViewerText}</pre>
                  )}
                </div>
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
  const canLoadPdf = document.latestFileType === 'PDF' && document.latestVersionId !== null
  const canLoadTxt = document.latestFileType === 'TXT' && document.latestVersionId !== null
  const canLoad = canLoadPdf || canLoadTxt
  const latestVersionId = document.latestVersionId
  const [thumbnailState, setThumbnailState] = useState<ThumbnailState>('idle')
  const [thumbnailUrl, setThumbnailUrl] = useState<string | null>(null)
  const [textPreview, setTextPreview] = useState<string | null>(null)
  const [thumbnailRetryKey, setThumbnailRetryKey] = useState(0)

  useEffect(() => {
    if (!canLoad || latestVersionId === null) {
      return
    }

    const controller = new AbortController()
    let objectUrl: string | null = null
    void (async () => {
      try {
        if (canLoadPdf) {
          const blob = await getDocumentThumbnail(
            document.documentId,
            latestVersionId,
            controller.signal,
          )
          if (controller.signal.aborted) {
            return
          }
          objectUrl = URL.createObjectURL(blob)
          setThumbnailUrl(objectUrl)
        } else {
          const original = await getDocumentOriginal(
            document.documentId,
            latestVersionId,
            controller.signal,
          )
          if (controller.signal.aborted) {
            return
          }
          if (original.fileType !== 'TXT') {
            throw new DocumentApiError(502, 'INVALID_ORIGINAL_RESPONSE')
          }
          const text = await original.blob.text()
          if (controller.signal.aborted) {
            return
          }
          setTextPreview(txtPreviewText(text, 260))
        }
        if (!controller.signal.aborted) {
          setThumbnailState('ready')
        }
      } catch (error: unknown) {
        if (controller.signal.aborted) {
          return
        }
        if (error instanceof DocumentApiError && expireSessionIfUnauthorized(error, onSessionExpired)) {
          return
        }
        setThumbnailState('fallback')
      }
    })()

    return () => {
      controller.abort()
      if (objectUrl !== null) {
        URL.revokeObjectURL(objectUrl)
      }
    }
  }, [
    canLoad,
    canLoadPdf,
    document.documentId,
    latestVersionId,
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
      ) : thumbnailState === 'ready' && textPreview !== null ? (
        <div
          className="txt-thumbnail-preview"
          role="img"
          aria-label={`${originalFileName} TXT 내용 미리보기`}
        >
          <strong aria-hidden="true">TXT</strong>
          <p>{textPreview === '' ? '내용이 없는 문서' : textPreview}</p>
        </div>
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
                setTextPreview(null)
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

function DocumentVersionPreview({
  documentId,
  documentTitle,
  version,
  onSessionExpired,
}: {
  documentId: number
  documentTitle: string
  version: DocumentVersion | null
  onSessionExpired: () => void
}) {
  const canLoadPdf = version?.fileType === 'PDF'
  const canLoadTxt = version?.fileType === 'TXT'
  const canLoad = canLoadPdf || canLoadTxt
  const versionId = version?.versionId ?? null
  const [thumbnailState, setThumbnailState] = useState<ThumbnailState>('idle')
  const [thumbnailUrl, setThumbnailUrl] = useState<string | null>(null)
  const [textPreview, setTextPreview] = useState<string | null>(null)
  const [thumbnailRetryKey, setThumbnailRetryKey] = useState(0)

  useEffect(() => {
    if (!canLoad || versionId === null) {
      return
    }

    const controller = new AbortController()
    let objectUrl: string | null = null
    void (async () => {
      try {
        if (canLoadPdf) {
          const blob = await getDocumentThumbnail(documentId, versionId, controller.signal)
          if (controller.signal.aborted) {
            return
          }
          objectUrl = URL.createObjectURL(blob)
          setThumbnailUrl(objectUrl)
        } else {
          const original = await getDocumentOriginal(documentId, versionId, controller.signal)
          if (controller.signal.aborted) {
            return
          }
          if (original.fileType !== 'TXT') {
            throw new DocumentApiError(502, 'INVALID_ORIGINAL_RESPONSE')
          }
          const text = await original.blob.text()
          if (controller.signal.aborted) {
            return
          }
          setTextPreview(txtPreviewText(text, 2_000))
        }
        if (!controller.signal.aborted) {
          setThumbnailState('ready')
        }
      } catch (error: unknown) {
        if (controller.signal.aborted) {
          return
        }
        if (error instanceof DocumentApiError && expireSessionIfUnauthorized(error, onSessionExpired)) {
          return
        }
        setThumbnailState('fallback')
      }
    })()

    return () => {
      controller.abort()
      if (objectUrl !== null) {
        URL.revokeObjectURL(objectUrl)
      }
    }
  }, [canLoad, canLoadPdf, documentId, onSessionExpired, thumbnailRetryKey, versionId])

  if (version === null) {
    return (
      <div className="document-version-preview-canvas is-empty">
        <p>미리볼 문서 버전이 없습니다.</p>
      </div>
    )
  }

  const isLoading = canLoad && thumbnailState === 'idle'
  return (
    <div className="document-version-preview-canvas" aria-busy={isLoading}>
      {thumbnailState === 'ready' && thumbnailUrl !== null ? (
        <img
          src={thumbnailUrl}
          alt={`${documentTitle} v${version.versionNo} 첫 페이지 미리보기`}
          decoding="async"
        />
      ) : thumbnailState === 'ready' && textPreview !== null ? (
        <div className="document-txt-preview" role="document" aria-label={`${documentTitle} TXT 내용 미리보기`}>
          {textPreview === '' ? (
            <p>내용이 없는 TXT 문서입니다.</p>
          ) : (
            <pre>{textPreview}</pre>
          )}
        </div>
      ) : (
        <div className="document-version-preview-fallback">
          {isLoading && <span className="state-spinner" aria-hidden="true" />}
          {!isLoading && (
            <div className="document-preview-sheet">
              <strong aria-hidden="true">{version.fileType}</strong>
              <span aria-hidden="true" />
              <span aria-hidden="true" />
              <span aria-hidden="true" />
              <span aria-hidden="true" />
            </div>
          )}
          <p>
            {isLoading
              ? version.fileType === 'TXT'
                ? 'TXT 내용을 불러오는 중입니다.'
                : '첫 페이지 미리보기를 불러오는 중입니다.'
              : version.fileType === 'TXT'
                ? 'TXT 내용을 미리볼 수 없습니다.'
                : '첫 페이지 미리보기를 표시할 수 없습니다.'}
          </p>
          {thumbnailState === 'fallback' && canLoad && (
            <button
              type="button"
              className="secondary-button thumbnail-retry-button"
              onClick={() => {
                setThumbnailState('idle')
                setThumbnailUrl(null)
                setTextPreview(null)
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

function CareerKeywordsPage({
  onSessionExpired,
  onNavigateToDocument,
}: {
  onSessionExpired: () => void
  onNavigateToDocument: (documentId: number) => void
}) {
  const [tags, setTags] = useState<TagUsage[]>([])
  const [tagState, setTagState] = useState<SearchState>('loading')
  const [reloadKey, setReloadKey] = useState(0)
  const [selectedTagId, setSelectedTagId] = useState<number | null>(() =>
    selectedTagIdFromSearch(window.location.search),
  )
  const [searchResults, setSearchResults] = useState<CareerEvidenceSearchResult[]>([])
  const [documentTypes, setDocumentTypes] = useState<Map<number, DocumentType>>(new Map())
  const [evidenceState, setEvidenceState] = useState<SearchState>(
    selectedTagId === null ? 'idle' : 'loading',
  )
  const [detailReloadKey, setDetailReloadKey] = useState(0)
  const [pdfViewerTarget, setPdfViewerTarget] = useState<EvidencePdfViewerTarget | null>(null)
  const closeKeywordEvidencePdfViewer = useCallback(() => setPdfViewerTarget(null), [])
  const selectedTag = selectedTagId === null
    ? null
    : tags.find((tag) => tag.tagId === selectedTagId) ?? null

  useEffect(() => {
    const controller = new AbortController()
    void getTagUsage(controller.signal)
      .then((response) => {
        const ordered = sortTagUsage(response)
        setTags(ordered)
        setTagState(ordered.length === 0 ? 'empty' : 'result')
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return
        if (error instanceof TagApiError && expireSessionIfUnauthorized(error, onSessionExpired)) {
          return
        }
        setTags([])
        setTagState('error')
      })
    return () => controller.abort()
  }, [onSessionExpired, reloadKey])

  useEffect(() => {
    const resolution = resolveSelectedTag(selectedTagId, tagState, tags)
    if (resolution.status === 'idle' || resolution.status === 'waiting') {
      return
    }
    if (resolution.status === 'unavailable') {
      queueMicrotask(() => {
        setSearchResults([])
        setDocumentTypes(new Map())
        setEvidenceState('error')
      })
      return
    }

    let cancelled = false
    void loadKeywordEvidence(resolution.tag.name, {
      search: searchCareerEvidence,
      listDocuments: getDocuments,
    })
      .then((loaded) => {
        if (cancelled) return
        setSearchResults(loaded.results)
        setDocumentTypes(loaded.documentTypes)
        setEvidenceState(loaded.results.length === 0 ? 'empty' : 'result')
      })
      .catch((error: unknown) => {
        if (cancelled) return
        if (
          (error instanceof SearchApiError || error instanceof DocumentApiError)
          && expireSessionIfUnauthorized(error, onSessionExpired)
        ) {
          return
        }
        setSearchResults([])
        setDocumentTypes(new Map())
        setEvidenceState('error')
      })
    return () => {
      cancelled = true
    }
  }, [detailReloadKey, onSessionExpired, selectedTagId, tagState, tags])

  useEffect(() => {
    const syncSelectedTag = () => {
      const nextTagId = selectedTagIdFromSearch(window.location.search)
      setEvidenceState(nextTagId === null ? 'idle' : 'loading')
      setSearchResults([])
      setDocumentTypes(new Map())
      if (nextTagId === null) {
        setPdfViewerTarget(null)
      }
      setSelectedTagId(nextTagId)
    }
    window.addEventListener('popstate', syncSelectedTag)
    return () => window.removeEventListener('popstate', syncSelectedTag)
  }, [])

  const handleTagSelect = (tagId: number) => {
    const nextPath = tagDetailPath(tagId)
    if (`${window.location.pathname}${window.location.search}` !== nextPath) {
      window.history.pushState(null, '', nextPath)
    }
    setSearchResults([])
    setDocumentTypes(new Map())
    setEvidenceState('loading')
    setSelectedTagId(tagId)
  }

  const handleTagBack = () => {
    const nextPath = tagDetailPath(null)
    if (`${window.location.pathname}${window.location.search}` !== nextPath) {
      window.history.pushState(null, '', nextPath)
    }
    setSearchResults([])
    setDocumentTypes(new Map())
    setEvidenceState('idle')
    setPdfViewerTarget(null)
    setSelectedTagId(null)
  }

  return (
    <section className="vault-page keyword-page" aria-labelledby="keyword-title">
      <header className="page-heading keyword-page-heading">
        <p className="eyebrow">{selectedTagId === null ? 'MY DOCUMENT TAGS' : 'TAG EVIDENCE'}</p>
        <h1 id="keyword-title">경력 키워드</h1>
        {selectedTagId === null ? (
          <p>내 문서에 직접 연결한 태그를 확인하세요. 연결 문서 수는 본문 속 키워드 출현 수가 아닙니다.</p>
        ) : (
          <nav className="keyword-breadcrumb" aria-label="경력 키워드 위치">
            <button type="button" onClick={handleTagBack}>경력 키워드</button>
            <span aria-hidden="true">›</span>
            <span aria-current="page">{selectedTag?.name ?? '태그'}</span>
          </nav>
        )}
      </header>

      <div className={'keyword-workspace' + (selectedTagId === null ? '' : ' is-detail')}>
        {selectedTagId === null && (
          <section className="keyword-cloud-panel" aria-labelledby="keyword-cloud-title">
            <header className="keyword-panel-heading">
              <div>
                <p className="section-kicker">DOCUMENT TAGS</p>
                <h2 id="keyword-cloud-title">내 문서의 태그</h2>
              </div>
              <span>{tags.length}개 태그</span>
            </header>
            <div className="keyword-cloud" aria-live="polite" aria-busy={tagState === 'loading'}>
              {tagState === 'loading' && (
                <p className="keyword-state"><span className="state-spinner" aria-hidden="true" />태그를 불러오는 중입니다.</p>
              )}
              {tagState === 'empty' && (
                <p className="keyword-state">아직 문서에 연결된 태그가 없습니다. 문서 업로드나 상세 화면에서 태그를 추가해 주세요.</p>
              )}
              {tagState === 'error' && (
                <div className="keyword-state" role="alert">
                  <p>태그를 불러오지 못했습니다.</p>
                  <button
                    type="button"
                    className="secondary-button"
                    onClick={() => {
                      setTagState('loading')
                      setReloadKey((value) => value + 1)
                    }}
                  >
                    다시 시도
                  </button>
                </div>
              )}
              {tagState === 'result' && tags.map((tag) => (
                <button
                  key={tag.tagId}
                  type="button"
                  className="keyword-cloud-item"
                  aria-label={`${tag.name}, ${linkedDocumentCountLabel(tag.documentCount)}`}
                  onClick={() => handleTagSelect(tag.tagId)}
                >
                  <span>{tag.name}</span>
                  <small>{linkedDocumentCountLabel(tag.documentCount)}</small>
                </button>
              ))}
            </div>
          </section>
        )}

        {selectedTagId !== null && (
          <KeywordEvidencePanel
            query={selectedTag?.name ?? ''}
            state={evidenceState === 'idle' ? 'loading' : evidenceState}
            results={searchResults}
            documentTypes={documentTypes}
            documentTypeLabel={documentTypeLabel}
            onRetry={() => {
              setEvidenceState('loading')
              if (keywordEvidenceRetryTarget(selectedTagId, tagState, tags) === 'usage') {
                setTagState('loading')
                setReloadKey((value) => value + 1)
              } else {
                setDetailReloadKey((value) => value + 1)
              }
            }}
            onOpenPdf={setPdfViewerTarget}
            onNavigateToDocument={onNavigateToDocument}
          />
        )}
      </div>
      {pdfViewerTarget !== null && (
        <KeywordEvidencePdfViewer
          target={pdfViewerTarget}
          onClose={closeKeywordEvidencePdfViewer}
          onSessionExpired={onSessionExpired}
        />
      )}
    </section>
  )
}

function JobEvidencePage({
  view,
  onSessionExpired,
  onNavigateToEditor,
  onReplaceToEditor,
  onNavigateToResults,
  onNavigateToDocument,
}: {
  view: 'editor' | 'results'
  onSessionExpired: () => void
  onNavigateToEditor: () => void
  onReplaceToEditor: () => void
  onNavigateToResults: () => void
  onNavigateToDocument: (documentId: number) => void
}) {
  const [content, setContent] = useState('')
  const [items, setItems] = useState<JobPostingItem[]>([])
  const [selectedItemIds, setSelectedItemIds] = useState<Set<number>>(new Set())
  const [draftSelectedItemIds, setDraftSelectedItemIds] = useState<Set<number>>(new Set())
  const [segmentationState, setSegmentationState] = useState<JobEvidenceSegmentationState>('idle')
  const [groups, setGroups] = useState<JobEvidenceGroup[]>([])
  const [documentTypes, setDocumentTypes] = useState<Map<number, DocumentType>>(new Map())
  const [pdfViewerTarget, setPdfViewerTarget] = useState<EvidencePdfViewerTarget | null>(null)
  const [isSelectionOpen, setSelectionOpen] = useState(false)
  const requestGeneration = useRef(0)
  const segmentationAbortController = useRef<AbortController | null>(null)
  const closePdfViewer = useCallback(() => setPdfViewerTarget(null), [])

  useEffect(() => () => {
    requestGeneration.current += 1
    segmentationAbortController.current?.abort()
  }, [])

  useEffect(() => {
    if (view === 'results' && groups.length === 0) {
      onReplaceToEditor()
    }
  }, [groups.length, onReplaceToEditor, view])

  const handleContentChange = (nextContent: string) => {
    segmentationAbortController.current?.abort()
    segmentationAbortController.current = null
    requestGeneration.current += 1
    setContent(nextContent)
    setItems([])
    setSelectedItemIds(clearJobPostingItemSelection())
    setDraftSelectedItemIds(clearJobPostingItemSelection())
    setSegmentationState('idle')
    setGroups([])
    setDocumentTypes(new Map())
    closePdfViewer()
  }

  const handleSegment = async () => {
    if (content.trim() === '' || segmentationState === 'loading') {
      return
    }

    const generation = requestGeneration.current + 1
    requestGeneration.current = generation
    segmentationAbortController.current?.abort()
    const controller = new AbortController()
    segmentationAbortController.current = controller
    setSegmentationState('loading')
    setItems([])
    setSelectedItemIds(clearJobPostingItemSelection())
    setDraftSelectedItemIds(clearJobPostingItemSelection())
    setGroups([])
    setDocumentTypes(new Map())
    closePdfViewer()

    try {
      const segmentedItems = await segmentJobPosting(content, controller.signal)
      if (requestGeneration.current !== generation) {
        return
      }
      setItems(segmentedItems)
      const initialSelection = selectAllJobPostingItems(segmentedItems)
      setSelectedItemIds(initialSelection)
      setDraftSelectedItemIds(new Set(initialSelection))
      setSegmentationState('ready')
      setSelectionOpen(true)
    } catch (error) {
      if (controller.signal.aborted || requestGeneration.current !== generation) {
        return
      }
      if (error instanceof JobPostingApiError && expireSessionIfUnauthorized(error, onSessionExpired)) {
        return
      }
      setSegmentationState('error')
      setSelectionOpen(false)
    } finally {
      if (segmentationAbortController.current === controller) {
        segmentationAbortController.current = null
      }
    }
  }

  const handleOpenSelection = () => {
    setDraftSelectedItemIds(new Set(selectedItemIds))
    setSelectionOpen(true)
  }

  const handleCloseSelection = () => {
    setDraftSelectedItemIds(new Set(selectedItemIds))
    setSelectionOpen(false)
  }

  const handleToggleItem = (itemId: number) => {
    setDraftSelectedItemIds((selection) => toggleJobPostingItemSelection(selection, itemId))
  }

  const handleSelectAll = () => {
    setDraftSelectedItemIds(selectAllJobPostingItems(items))
  }

  const handleClearAll = () => {
    setDraftSelectedItemIds(clearJobPostingItemSelection())
  }

  const handleSearch = async () => {
    const selectedItems = selectedJobPostingItems(items, draftSelectedItemIds)
    if (selectedItems.length === 0 || groups.some((group) => group.state === 'loading')) {
      return
    }

    const generation = requestGeneration.current + 1
    requestGeneration.current = generation
    setSelectedItemIds(new Set(draftSelectedItemIds))
    setGroups(loadingJobEvidenceGroups(items, draftSelectedItemIds))
    setDocumentTypes(new Map())
    setSelectionOpen(false)
    closePdfViewer()
    onNavigateToResults()

    try {
      const search = await findJobEvidence(items, draftSelectedItemIds, {
        search: searchCareerEvidence,
        listDocuments: getDocuments,
      })
      if (requestGeneration.current !== generation) {
        return
      }
      setGroups(search.groups)
      setDocumentTypes(search.documentTypes)
    } catch (error) {
      if (requestGeneration.current !== generation) {
        return
      }
      if (expireSessionIfUnauthorized(error, onSessionExpired)) {
        return
      }
      setGroups((currentGroups) => currentGroups.map((group) => (
        group.state === 'loading'
          ? { ...group, state: 'error', candidates: [], error }
          : group
      )))
    }
  }

  const handleRetry = async (itemId: number) => {
    const retryGroup = groups.find((group) => group.item.itemId === itemId)
    if (retryGroup === undefined || groups.some((group) => group.state === 'loading')) {
      return
    }

    const generation = requestGeneration.current + 1
    requestGeneration.current = generation
    setGroups((currentGroups) => currentGroups.map((group) => (
      group.item.itemId === itemId
        ? { ...group, state: 'loading', candidates: [], error: null }
        : group
    )))

    try {
      const search = await findJobEvidence(
        [retryGroup.item],
        new Set([retryGroup.item.itemId]),
        {
          search: searchCareerEvidence,
          listDocuments: getDocuments,
        },
      )
      if (requestGeneration.current !== generation) {
        return
      }

      const retriedGroup = search.groups[0]
      if (retriedGroup === undefined) {
        return
      }
      setGroups((currentGroups) => currentGroups.map((group) => (
        group.item.itemId === itemId ? retriedGroup : group
      )))
      if (search.documentTypes.size > 0) {
        setDocumentTypes((currentTypes) => new Map([
          ...currentTypes,
          ...search.documentTypes,
        ]))
      }
    } catch (error) {
      if (requestGeneration.current !== generation) {
        return
      }
      if (expireSessionIfUnauthorized(error, onSessionExpired)) {
        return
      }
      setGroups((currentGroups) => currentGroups.map((group) => (
        group.item.itemId === itemId
          ? { ...group, state: 'error', candidates: [], error }
          : group
      )))
    }
  }

  return (
    <section className="vault-page job-evidence-page" aria-labelledby="job-evidence-title">
      {view === 'editor' ? (
        <>
          <header className="page-heading">
            <p className="eyebrow">JOB POSTING EVIDENCE</p>
            <h1 id="job-evidence-title">채용공고에서 관련 경력 찾기</h1>
            <p>필요한 항목을 직접 선택하면 내 문서에서 관련 원문 기록을 찾습니다.</p>
          </header>
          <JobEvidencePanel
            content={content}
            itemCount={items.length}
            selectedCount={selectedJobPostingItems(items, selectedItemIds).length}
            segmentationState={segmentationState}
            onContentChange={handleContentChange}
            onSegment={() => void handleSegment()}
            onOpenSelection={handleOpenSelection}
          />
        </>
      ) : (
        <JobEvidenceResultsWorkspace
          groups={groups}
          documentTypes={documentTypes}
          documentTypeLabel={documentTypeLabel}
          onEditPosting={() => {
            setSelectionOpen(false)
            onNavigateToEditor()
          }}
          onEditSelection={handleOpenSelection}
          onRetry={(itemId) => void handleRetry(itemId)}
          onOpenPdf={setPdfViewerTarget}
          onNavigateToDocument={onNavigateToDocument}
        />
      )}
      {isSelectionOpen && (
        <JobRequirementSelectionModal
          items={items}
          selectedItemIds={draftSelectedItemIds}
          onToggleItem={handleToggleItem}
          onSelectAll={handleSelectAll}
          onClearAll={handleClearAll}
          onSearch={() => void handleSearch()}
          onClose={handleCloseSelection}
        />
      )}
      {pdfViewerTarget !== null && (
        <KeywordEvidencePdfViewer
          target={pdfViewerTarget}
          onClose={closePdfViewer}
          onSessionExpired={onSessionExpired}
        />
      )}
    </section>
  )
}

function KeywordEvidencePdfViewer({
  target,
  onClose,
  onSessionExpired,
}: {
  target: EvidencePdfViewerTarget
  onClose: () => void
  onSessionExpired: () => void
}) {
  const [viewerUrl, setViewerUrl] = useState<string | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const panelRef = useRef<HTMLElement>(null)
  const returnFocusRef = useRef<HTMLElement | null>(
    typeof document !== 'undefined' && document.activeElement instanceof HTMLElement
      ? document.activeElement
      : null,
  )

  useEffect(() => {
    const controller = new AbortController()
    let objectUrl: string | null = null
    void getDocumentPdf(target.documentId, target.documentVersionId, controller.signal)
      .then((pdf) => {
        objectUrl = URL.createObjectURL(pdf)
        setViewerUrl(`${objectUrl}#page=${target.pageNumber}&zoom=page-width`)
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return
        if (error instanceof DocumentApiError && expireSessionIfUnauthorized(error, onSessionExpired)) {
          onClose()
          return
        }
        if (error instanceof DocumentApiError && error.code === 'ORIGINAL_FILE_NOT_FOUND') {
          setErrorMessage('첨부한 PDF 파일을 찾지 못했습니다.')
        } else {
          setErrorMessage('문서를 열지 못했습니다. 잠시 후 다시 시도해 주세요.')
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setIsLoading(false)
        }
      })

    return () => {
      controller.abort()
      if (objectUrl !== null) {
        URL.revokeObjectURL(objectUrl)
      }
    }
  }, [onClose, onSessionExpired, target])

  useEffect(() => {
    const previousOverflow = document.body.style.overflow
    const returnFocusTarget = returnFocusRef.current
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    document.body.style.overflow = 'hidden'
    focusModalEntry(panelRef.current)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.body.style.overflow = previousOverflow
      document.removeEventListener('keydown', handleKeyDown)
      restoreModalTrigger(returnFocusTarget)
    }
  }, [onClose])

  const handlePanelKeyDown = (event: ReactKeyboardEvent<HTMLElement>) => {
    if (event.key === 'Escape') {
      event.stopPropagation()
      onClose()
      return
    }
    if (event.key !== 'Tab' || panelRef.current === null) {
      return
    }
    const focusableTargets = Array.from(panelRef.current.querySelectorAll<HTMLElement>(
      'button:not(:disabled), input:not(:disabled), iframe, [href], [tabindex]:not([tabindex="-1"])',
    ))
    if (focusableTargets.length === 0) {
      panelRef.current.focus()
      event.preventDefault()
      return
    }
    if (keepFocusWithinModal(focusableTargets, document.activeElement, event.shiftKey)) {
      event.preventDefault()
    }
  }

  return (
    <>
      <button
        type="button"
        className="pdf-viewer-backdrop"
        aria-label="문서 확인 닫기"
        onClick={onClose}
      />
      <section
        ref={panelRef}
        className="pdf-viewer-panel"
        role="dialog"
        aria-modal="true"
        aria-labelledby="keyword-pdf-viewer-title"
        aria-busy={isLoading}
        tabIndex={-1}
        onKeyDown={handlePanelKeyDown}
      >
        <header className="pdf-viewer-heading">
          <div>
            <p className="section-kicker">문서 · {target.pageNumber}페이지</p>
            <h2 id="keyword-pdf-viewer-title">{target.documentTitle}</h2>
          </div>
          <button type="button" className="icon-button" aria-label="문서 확인 닫기" onClick={onClose}>×</button>
        </header>
        <div className="pdf-viewer-content">
          {isLoading && (
            <p className="state-message search-state">
              <span className="state-spinner" aria-hidden="true" />
              문서를 여는 중입니다.
            </p>
          )}
          {errorMessage !== null && <p className="pdf-viewer-error" role="alert">{errorMessage}</p>}
          {viewerUrl !== null && errorMessage === null && (
            <iframe
              className="pdf-viewer-frame"
              src={viewerUrl}
              title={`${target.documentTitle} ${target.pageNumber}페이지`}
            />
          )}
        </div>
      </section>
    </>
  )
}
function EvidencePage({ onSessionExpired }: { onSessionExpired: () => void }) {
  const [searchQuery, setSearchQuery] = useState('')
  const [searchResults, setSearchResults] = useState<CareerEvidenceSearchResult[]>([])
  const [searchState, setSearchState] = useState<SearchState>('idle')
  const [pdfViewerTarget, setPdfViewerTarget] = useState<EvidencePdfViewerTarget | null>(null)
  const [pdfViewerUrl, setPdfViewerUrl] = useState<string | null>(null)
  const [pdfViewerErrorMessage, setPdfViewerErrorMessage] = useState<string | null>(null)
  const [isPdfViewerLoading, setIsPdfViewerLoading] = useState(false)
  const pdfViewerRequestId = useRef(0)
  const pdfViewerAbortController = useRef<AbortController | null>(null)
  const pdfViewerObjectUrl = useRef<string | null>(null)

  const closePdfViewer = useCallback(() => {
    pdfViewerRequestId.current += 1
    pdfViewerAbortController.current?.abort()
    pdfViewerAbortController.current = null
    if (pdfViewerObjectUrl.current !== null) {
      URL.revokeObjectURL(pdfViewerObjectUrl.current)
      pdfViewerObjectUrl.current = null
    }
    setPdfViewerTarget(null)
    setPdfViewerUrl(null)
    setPdfViewerErrorMessage(null)
    setIsPdfViewerLoading(false)
  }, [])

  useEffect(() => () => {
    pdfViewerAbortController.current?.abort()
    if (pdfViewerObjectUrl.current !== null) {
      URL.revokeObjectURL(pdfViewerObjectUrl.current)
    }
  }, [])

  useEffect(() => {
    if (pdfViewerTarget === null) {
      return
    }
    const previousOverflow = document.body.style.overflow
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        closePdfViewer()
      }
    }
    document.body.style.overflow = 'hidden'
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.body.style.overflow = previousOverflow
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [closePdfViewer, pdfViewerTarget])

  const handleOpenPdf = async (target: EvidencePdfViewerTarget) => {
    closePdfViewer()
    const requestId = pdfViewerRequestId.current + 1
    pdfViewerRequestId.current = requestId
    const controller = new AbortController()
    pdfViewerAbortController.current = controller
    setPdfViewerTarget(target)
    setPdfViewerErrorMessage(null)
    setIsPdfViewerLoading(true)

    try {
      const pdf = await getDocumentPdf(target.documentId, target.documentVersionId, controller.signal)
      if (pdfViewerRequestId.current !== requestId) {
        return
      }
      const objectUrl = URL.createObjectURL(pdf)
      if (pdfViewerRequestId.current !== requestId) {
        URL.revokeObjectURL(objectUrl)
        return
      }
      pdfViewerObjectUrl.current = objectUrl
      setPdfViewerUrl(`${objectUrl}#page=${target.pageNumber}&zoom=page-width`)
    } catch (error) {
      if (pdfViewerRequestId.current !== requestId) {
        return
      }
      if (error instanceof DOMException && error.name === 'AbortError') {
        return
      }
      if (error instanceof DocumentApiError && expireSessionIfUnauthorized(error, onSessionExpired)) {
        closePdfViewer()
        return
      }
      if (error instanceof DocumentApiError && error.code === 'ORIGINAL_FILE_NOT_FOUND') {
        setPdfViewerErrorMessage('첨부한 PDF 파일을 찾지 못했습니다.')
      } else {
        setPdfViewerErrorMessage('문서를 열지 못했습니다. 잠시 후 다시 시도해 주세요.')
      }
    } finally {
      if (pdfViewerRequestId.current === requestId) {
        pdfViewerAbortController.current = null
        setIsPdfViewerLoading(false)
      }
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
      if (error instanceof SearchApiError && expireSessionIfUnauthorized(error, onSessionExpired)) {
        return
      }
      setSearchState('error')
    }
  }

  return (
    <section className="vault-page evidence-page" aria-labelledby="evidence-title">
      <header className="page-heading">
        <p className="eyebrow">MY EXPERIENCE</p>
        <h1 id="evidence-title">내 경험 찾기</h1>
        <p>질문을 입력하면 등록된 문서에서 관련 내용을 최대 5개까지 찾습니다.</p>
      </header>

      <div className="evidence-search-panel">
        <form
          className="document-search-form"
          onSubmit={handleSearchSubmit}
          noValidate
          aria-busy={searchState === 'loading'}
        >
          <label className="visually-hidden" htmlFor="document-search-query">
            검색어
          </label>
          <div className="document-search-controls">
            <input
              id="document-search-query"
              name="query"
              type="text"
              maxLength={500}
              placeholder="예: Spring Boot를 사용한 프로젝트가 있나요?"
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
              {searchState === 'loading' ? '검색 중' : '찾기'}
            </button>
          </div>
          {isShortSearchInput(searchQuery) && (
            <div className="search-query-guidance" role="status">
              <p>문장으로 질문하면 더 정확하게 찾을 수 있어요.</p>
              <small>
                예: Spring Boot를 사용한 경험이 있나요? · 동시성 문제를 어떻게 해결했나요?
              </small>
            </div>
          )}
        </form>

        <p className="search-processing-note">
          <span aria-hidden="true">i</span>
          아직 처리 중인 문서는 검색되지 않을 수 있어요.
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
            관련 내용을 찾는 중입니다.
          </p>
        )}
        {searchState === 'empty' && (
          <p className="state-message search-empty-state">
            현재 PRIZM에 등록된 문서에서 관련 내용을 찾지 못했습니다.
          </p>
        )}
        {searchState === 'error' && (
          <p className="form-error feedback-message" role="alert">
            내 경험 찾기 중 문제가 발생했습니다.
          </p>
        )}
        {searchState === 'result' && searchResults.length > 0 && (
          <section className="search-result-panel" aria-label="검색 결과">
            <header className="search-result-heading">
              <div>
                <p className="search-result-kicker">검색 결과</p>
                <h2>질문과 관련된 내용</h2>
              </div>
              <span className="search-result-count">{searchResults.length}개</span>
            </header>
            <ol className="search-result-list">
              {searchResults.map((result, index) => {
                const displayedEvidence = getEvidenceHighlight(searchQuery, result)
                const viewerTarget = getEvidencePdfViewerTarget(result)
                return (
                  <li key={result.chunkId}>
                    <article className="search-result-card">
                      <header className="search-result-card-heading">
                        <span className="result-index">
                          결과 {String(index + 1).padStart(2, '0')}
                        </span>
                      </header>
                      <section className="search-result-evidence" aria-label="찾은 내용">
                        <p className="search-result-section-label">찾은 내용</p>
                        <blockquote className="search-result-snippet">
                          {displayedEvidence}
                        </blockquote>
                      </section>
                      <footer className="search-result-source" aria-label="문서">
                        <div>
                          <p className="search-result-source-label">문서</p>
                          <h3>{result.documentTitle} · {getEvidenceSourceLabel(result)}</h3>
                        </div>
                        {viewerTarget !== null && (
                          <button
                            type="button"
                            className="search-result-document-button"
                            onClick={() => void handleOpenPdf(viewerTarget)}
                            aria-label={`${result.documentTitle} ${viewerTarget.pageNumber}페이지에서 보기`}
                          >
                            문서에서 보기
                          </button>
                        )}
                      </footer>
                      <details className="search-result-full-content">
                        <summary>
                          <span className="full-content-open-label">주변 내용 보기</span>
                          <span className="full-content-close-label">주변 내용 닫기</span>
                        </summary>
                        <EvidenceContextReader
                          query={searchQuery}
                          content={result.content}
                          snippet={result.snippet}
                        />
                      </details>
                    </article>
                  </li>
                )
              })}
            </ol>
          </section>
        )}
      </div>
      {pdfViewerTarget !== null && (
        <>
          <button
            type="button"
            className="pdf-viewer-backdrop"
            aria-label="문서 확인 닫기"
            onClick={closePdfViewer}
          />
          <section
            className="pdf-viewer-panel"
            role="dialog"
            aria-modal="true"
            aria-labelledby="evidence-pdf-viewer-title"
            aria-busy={isPdfViewerLoading}
          >
            <header className="pdf-viewer-heading">
              <div>
                <p className="section-kicker">문서 · {pdfViewerTarget.pageNumber}페이지</p>
                <h2 id="evidence-pdf-viewer-title">{pdfViewerTarget.documentTitle}</h2>
              </div>
              <button
                type="button"
                className="icon-button"
                aria-label="문서 확인 닫기"
                onClick={closePdfViewer}
              >
                ×
              </button>
            </header>
            <div className="pdf-viewer-content">
              {isPdfViewerLoading && (
                <p className="state-message search-state">
                  <span className="state-spinner" aria-hidden="true" />
                  문서를 여는 중입니다.
                </p>
              )}
              {pdfViewerErrorMessage !== null && (
                <div className="pdf-viewer-error" role="alert">
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
              {pdfViewerUrl !== null && pdfViewerErrorMessage === null && (
                <iframe
                  className="pdf-viewer-frame"
                  src={pdfViewerUrl}
                  title={`${pdfViewerTarget.documentTitle} ${pdfViewerTarget.pageNumber}페이지`}
                />
              )}
            </div>
          </section>
        </>
      )}
    </section>
  )
}

function isShortSearchInput(value: string): boolean {
  const words = value.trim().split(/\s+/).filter(Boolean)
  return words.length > 0 && words.length <= 2
}

function EvidenceContextReader({
  query,
  content,
  snippet,
}: {
  query: string
  content: string
  snippet: string
}) {
  const context = getEvidenceContext(query, content, snippet)
  return (
    <div className="search-result-document-reader" role="document">
      {context}
    </div>
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
  const [selectedUploadTags, setSelectedUploadTags] = useState<Tag[]>([])
  const [isTagModalOpen, setIsTagModalOpen] = useState(false)
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
      await uploadDocument(
        normalizedTitle,
        uploadDocumentType,
        uploadFile,
        selectedUploadTags.map((tag) => tag.tagId),
      )
      setUploadSuccessMessage('문서가 등록되었습니다. 처리 상태는 문서 보관함에서 확인할 수 있습니다.')
      setUploadTitle('')
      setUploadDocumentType('OTHER')
      setUploadFile(null)
      setSelectedUploadTags([])
      setUploadFormKey((value) => value + 1)
    } catch (error) {
      if (error instanceof DocumentApiError && expireSessionIfUnauthorized(error, onSessionExpired)) {
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

          <div className="form-field form-field-full upload-tag-field">
            <label>태그</label>
            <DocumentTagEditor
              tags={selectedUploadTags}
              emptyMessage="선택한 태그가 없습니다."
              removeLabel={(tag) => `${tag.name} 선택 해제`}
              disabled={isUploading}
              onRemove={(tagId) => setSelectedUploadTags((current) => current.filter((item) => item.tagId !== tagId))}
              onAdd={() => setIsTagModalOpen(true)}
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
      {isTagModalOpen && (
        <TagModal
          selectedTags={selectedUploadTags}
          onSave={(tags) => {
            setSelectedUploadTags(tags)
            setIsTagModalOpen(false)
          }}
          onClose={() => setIsTagModalOpen(false)}
          onSessionExpired={onSessionExpired}
        />
      )}
    </section>
  )
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

function isDocumentSummaryInFlight(document: DocumentSummary): boolean {
  return document.latestVersionStatus === 'QUARANTINED' ||
    document.latestVersionStatus === 'PROCESSING' ||
    document.latestProcessingStatus === 'PENDING' ||
    document.latestProcessingStatus === 'PROCESSING' ||
    document.latestProcessingStatus === 'RETRY_WAIT'
}

function isDocumentVersionInFlight(version: DocumentVersion): boolean {
  return version.status === 'QUARANTINED' ||
    version.status === 'PROCESSING' ||
    version.processingStatus === 'PENDING' ||
    version.processingStatus === 'PROCESSING' ||
    version.processingStatus === 'RETRY_WAIT'
}

function versionHistoryStatusLabel(version: DocumentVersion): string {
  const status = version.processingStatus ?? version.status
  if (isDocumentVersionInFlight(version) || status === 'FAILED') {
    return documentStatusLabel(status)
  }
  return '이전 버전 · 검색 제외'
}

function retrySummary(
  retryCount: number,
  maxRetries: number,
  nextRetryAt: string | null,
  now: number,
): string {
  const count = `${maxRetries}회 중 ${retryCount}회 재시도`
  if (nextRetryAt === null) {
    return count
  }
  const remainingSeconds = Math.max(0, Math.floor((Date.parse(nextRetryAt) - now) / 1_000))
  if (remainingSeconds === 0) {
    return `곧 재시도 · ${count}`
  }
  const minutes = Math.floor(remainingSeconds / 60)
  const seconds = remainingSeconds % 60
  const delay = minutes > 0 ? `${minutes}분 ${seconds}초 후 재시도` : `${seconds}초 후 재시도`
  return `${delay} · ${count}`
}

function processingErrorMessage(code: string): string {
  return PROCESSING_ERROR_MESSAGES[code] ?? PROCESSING_ERROR_MESSAGES.DOCUMENT_PROCESSING_FAILED
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
