import { getAccessToken } from '../auth/tokenStorage'

export type DocumentType =
  | 'RESUME'
  | 'COVER_LETTER'
  | 'PORTFOLIO'
  | 'PROJECT_REPORT'
  | 'PRESENTATION'
  | 'CERTIFICATE'
  | 'COURSE_COMPLETION'
  | 'SCHOOL_ASSIGNMENT'
  | 'CAREER_REVIEW'
  | 'JOB_POSTING'
  | 'INTERVIEW_FEEDBACK'
  | 'OTHER'

export type DocumentFileType = 'TXT' | 'PDF'

export type DocumentSummary = {
  documentId: number
  title: string
  documentType: string
  activeVersionId: number | null
  latestVersionId: number | null
  latestVersionStatus: string | null
  latestOriginalFileName: string | null
  latestFileType: DocumentFileType | null
  latestProcessingStatus: ProcessingJobStatus | null
  latestProcessingStage: ProcessingProgressStage | null
  latestCompletedChunks: number | null
  latestTotalChunks: number | null
  latestProgressPercent: number | null
  latestProcessingErrorCode: string | null
  latestRetryCount: number
  maxRetries: number
  latestNextRetryAt: string | null
  activeVersionStatus: string | null
  versionCount: number
  createdAt: string
  updatedAt: string
}

export type ProcessingJobStatus =
  | 'PENDING'
  | 'PROCESSING'
  | 'RETRY_WAIT'
  | 'COMPLETED'
  | 'FAILED'

export type ProcessingProgressStage =
  | 'FILE_READING'
  | 'TEXT_EXTRACTION'
  | 'CHUNK_CREATION'
  | 'EMBEDDING'
  | 'SAVING'
  | 'COMPLETED'

export type DocumentVersion = {
  versionId: number
  versionNo: number
  originalFileName: string
  fileType: DocumentFileType
  status: string
  processingStatus: ProcessingJobStatus | null
  processingStage: ProcessingProgressStage | null
  completedChunks: number | null
  totalChunks: number | null
  progressPercent: number | null
  processingErrorCode: string | null
  retryScheduled: boolean
  retryCount: number
  maxRetries: number
  nextRetryAt: string | null
  createdAt: string
}

export type DocumentDetail = {
  documentId: number
  title: string
  documentType: DocumentType
  ownerConfirmed: boolean
  activeVersionId: number | null
  createdAt: string
  updatedAt: string
  versions: DocumentVersion[]
}

export type DocumentListFilters = {
  documentType?: DocumentType
  title?: string
  processingStatus?: ProcessingJobStatus
}

export type DocumentUploadResponse = {
  documentId: number
  versionId: number
  title: string
  originalFileName: string
  documentType: DocumentType
  status: string
  createdAt: string
}

export class DocumentApiError extends Error {
  readonly status: number
  readonly code: string | null

  constructor(status: number, code: string | null = null) {
    super('Document request failed')
    this.status = status
    this.code = code
  }
}

export async function getDocuments(filters: DocumentListFilters = {}): Promise<DocumentSummary[]> {
  const accessToken = getAccessToken()

  if (accessToken === null) {
    throw new DocumentApiError(401)
  }

  const parameters = new URLSearchParams()
  if (filters.documentType !== undefined) {
    parameters.set('documentType', filters.documentType)
  }
  if (filters.title !== undefined && filters.title.trim() !== '') {
    parameters.set('title', filters.title.trim())
  }
  if (filters.processingStatus !== undefined) {
    parameters.set('processingStatus', filters.processingStatus)
  }
  const query = parameters.toString()
  const response = await fetch(query === '' ? '/api/documents' : `/api/documents?${query}`, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
  })

  if (!response.ok) {
    throw new DocumentApiError(response.status)
  }

  return (await response.json()) as DocumentSummary[]
}

export async function getDocument(documentId: number): Promise<DocumentDetail> {
  const response = await documentRequest(`/api/documents/${documentId}`)
  return (await response.json()) as DocumentDetail
}

export async function updateDocumentMetadata(
  documentId: number,
  title: string,
  documentType: DocumentType,
): Promise<DocumentDetail> {
  const response = await documentRequest(`/api/documents/${documentId}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ title, documentType }),
  })
  return (await response.json()) as DocumentDetail
}

export async function deleteDocument(documentId: number): Promise<void> {
  await documentRequest(`/api/documents/${documentId}`, { method: 'DELETE' })
}

export async function deleteDocumentVersion(documentId: number, versionId: number): Promise<void> {
  await documentRequest(`/api/documents/${documentId}/versions/${versionId}`, { method: 'DELETE' })
}

export async function uploadDocument(
  title: string,
  documentType: DocumentType,
  file: File,
): Promise<DocumentUploadResponse> {
  const accessToken = getAccessToken()

  if (accessToken === null) {
    throw new DocumentApiError(401)
  }

  const formData = new FormData()
  formData.set('title', title)
  formData.set('documentType', documentType)
  formData.set('file', file)

  const response = await fetch('/api/documents', {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
    body: formData,
  })

  if (!response.ok) {
    throw new DocumentApiError(response.status, await responseErrorCode(response))
  }

  return (await response.json()) as DocumentUploadResponse
}

export async function uploadDocumentVersion(
  documentId: number,
  file: File,
): Promise<DocumentUploadResponse> {
  const formData = new FormData()
  formData.set('file', file)
  const response = await documentRequest(`/api/documents/${documentId}/versions`, {
    method: 'POST',
    body: formData,
  })
  return (await response.json()) as DocumentUploadResponse
}

export async function getDocumentThumbnail(
  documentId: number,
  versionId: number,
  signal?: AbortSignal,
): Promise<Blob> {
  const accessToken = getAccessToken()

  if (accessToken === null) {
    throw new DocumentApiError(401)
  }

  const response = await fetch(
    `/api/documents/${documentId}/versions/${versionId}/thumbnail`,
    {
      headers: {
        Accept: 'image/png, application/json',
        Authorization: `Bearer ${accessToken}`,
      },
      signal,
    },
  )

  if (!response.ok) {
    throw new DocumentApiError(response.status, await responseErrorCode(response))
  }

  return response.blob()
}

export async function getDocumentPdf(
  documentId: number,
  versionId: number,
  signal?: AbortSignal,
): Promise<Blob> {
  const response = await documentRequest(
    `/api/documents/${documentId}/versions/${versionId}/original`,
    {
      headers: { Accept: 'application/pdf, application/json' },
      signal,
    },
  )

  const contentType = response.headers.get('Content-Type') ?? ''
  if (!contentType.toLowerCase().startsWith('application/pdf')) {
    throw new DocumentApiError(502, 'INVALID_PDF_RESPONSE')
  }

  return response.blob()
}

export type DocumentOriginal = {
  blob: Blob
  fileType: DocumentFileType
}

export async function getDocumentOriginal(
  documentId: number,
  versionId: number,
  signal?: AbortSignal,
): Promise<DocumentOriginal> {
  const response = await documentRequest(
    `/api/documents/${documentId}/versions/${versionId}/original`,
    {
      headers: { Accept: 'application/pdf, text/plain, application/json' },
      signal,
    },
  )

  const contentType = (response.headers.get('Content-Type') ?? '').toLowerCase()
  if (contentType.startsWith('application/pdf')) {
    return { blob: await response.blob(), fileType: 'PDF' }
  }
  if (contentType.startsWith('text/plain')) {
    return { blob: await response.blob(), fileType: 'TXT' }
  }
  throw new DocumentApiError(502, 'INVALID_ORIGINAL_RESPONSE')
}

async function responseErrorCode(response: Response): Promise<string | null> {
  try {
    const body: unknown = await response.json()
    if (typeof body === 'object' && body !== null && 'code' in body && typeof body.code === 'string') {
      return body.code
    }
  } catch {
    // 서버 오류 본문은 사용자에게 표시하지 않는다.
  }

  return null
}

async function documentRequest(path: string, init: RequestInit = {}): Promise<Response> {
  const accessToken = getAccessToken()
  if (accessToken === null) {
    throw new DocumentApiError(401)
  }

  const headers = new Headers(init.headers)
  headers.set('Authorization', `Bearer ${accessToken}`)
  const response = await fetch(path, { ...init, headers })
  if (!response.ok) {
    throw new DocumentApiError(response.status, await responseErrorCode(response))
  }
  return response
}
