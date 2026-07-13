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

export type DocumentSummary = {
  documentId: number
  title: string
  documentType: string
  activeVersionId: number | null
  latestVersionId: number | null
  latestVersionStatus: string | null
  createdAt: string
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

  constructor(status: number) {
    super('Document request failed')
    this.status = status
  }
}

export async function getDocuments(documentType?: DocumentType): Promise<DocumentSummary[]> {
  const accessToken = getAccessToken()

  if (accessToken === null) {
    throw new DocumentApiError(401)
  }

  const parameters = new URLSearchParams()
  if (documentType !== undefined) {
    parameters.set('documentType', documentType)
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
    throw new DocumentApiError(response.status)
  }

  return (await response.json()) as DocumentUploadResponse
}
