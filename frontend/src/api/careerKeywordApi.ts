import { getAccessToken } from '../auth/tokenStorage'
import type { DocumentFileType, DocumentType } from './documentApi'

export type CareerKeywordCategory =
  | 'LANGUAGE'
  | 'FRAMEWORK'
  | 'DATABASE'
  | 'INFRASTRUCTURE'
  | 'MESSAGING'
  | 'SECURITY'
  | 'TESTING'
  | 'WEB'
  | 'TOOLING'
  | 'ENGINEERING_CONCEPT'

export type CareerKeywordSummary = {
  keyword: string
  category: CareerKeywordCategory
  frequency: number
  documentCount: number
  variants: string[]
}

export type CareerKeywordMap = {
  documentCount: number
  keywords: CareerKeywordSummary[]
}

export type CareerKeywordEvidenceItem = {
  documentId: number
  documentVersionId: number
  documentTitle: string
  documentType: DocumentType
  versionNo: number
  originalFileName: string
  fileType: DocumentFileType
  sourceType: 'TEXT_CHUNK' | 'PAGE'
  sourceIndex: number
  sourceLabel: string
  occurrenceCount: number
  excerpt: string
  matchedTerms: string[]
}

export type CareerKeywordEvidence = {
  keyword: string
  totalFrequency: number
  evidence: CareerKeywordEvidenceItem[]
}

export class CareerKeywordApiError extends Error {
  readonly status: number

  constructor(status: number) {
    super('Career keyword request failed')
    this.status = status
  }
}

export async function getCareerKeywordMap(signal?: AbortSignal): Promise<CareerKeywordMap> {
  const response = await careerKeywordRequest('/api/career-keywords', signal)
  return (await response.json()) as CareerKeywordMap
}

export async function getCareerKeywordEvidence(
  keyword: string,
  signal?: AbortSignal,
): Promise<CareerKeywordEvidence> {
  const parameters = new URLSearchParams({ keyword })
  const response = await careerKeywordRequest(
    `/api/career-keywords/evidence?${parameters.toString()}`,
    signal,
  )
  return (await response.json()) as CareerKeywordEvidence
}

async function careerKeywordRequest(path: string, signal?: AbortSignal): Promise<Response> {
  const accessToken = getAccessToken()
  if (accessToken === null) {
    throw new CareerKeywordApiError(401)
  }

  const response = await fetch(path, {
    headers: {
      Accept: 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
    signal,
  })
  if (!response.ok) {
    throw new CareerKeywordApiError(response.status)
  }
  return response
}
