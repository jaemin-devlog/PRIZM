import { getAccessToken } from '../auth/tokenStorage'

export type SearchResult = {
  documentId: number
  documentVersionId: number
  documentTitle: string
  versionNo: number
  chunkNo: number
  pageNo: number | null
  sourceType: 'TEXT_CHUNK' | 'PAGE'
  sourceIndex: number
  sourceLabel: string
  content: string
  distance: number
  score: number
}

export type CareerEvidenceSearchResult = {
  chunkId: number
  documentId: number
  documentVersionId: number
  documentTitle: string
  versionNo: number
  content: string
  sourceType: 'TEXT_CHUNK' | 'PAGE'
  sourceIndex: number
  sourceLabel: string
  distance: number
  score: number
}

export class SearchApiError extends Error {
  readonly status: number

  constructor(status: number) {
    super('Search request failed')
    this.status = status
  }
}

export async function searchDocuments(query: string): Promise<SearchResult> {
  const accessToken = getAccessToken()

  if (accessToken === null) {
    throw new SearchApiError(401)
  }

  const response = await fetch('/api/search', {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ query }),
  })

  if (!response.ok) {
    throw new SearchApiError(response.status)
  }

  return (await response.json()) as SearchResult
}

export async function searchCareerEvidence(query: string): Promise<CareerEvidenceSearchResult[]> {
  const accessToken = getAccessToken()

  if (accessToken === null) {
    throw new SearchApiError(401)
  }

  const response = await fetch('/api/career-evidence/search', {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ query }),
  })

  if (!response.ok) {
    throw new SearchApiError(response.status)
  }

  return (await response.json()) as CareerEvidenceSearchResult[]
}
