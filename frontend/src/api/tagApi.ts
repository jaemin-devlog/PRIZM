import { getAccessToken } from '../auth/tokenStorage'
import type { DocumentType } from './documentApi'

export type TagSource = 'SYSTEM' | 'USER'

export type Tag = {
  tagId: number
  name: string
  source: TagSource
}

export type TagUsage = Tag & {
  documentCount: number
}

export type TaggedDocument = {
  documentId: number
  title: string
  documentType: DocumentType
}

export type TaggedDocuments = {
  tag: Tag
  documents: TaggedDocument[]
}

export class TagApiError extends Error {
  readonly status: number
  readonly code: string | null

  constructor(status: number, code: string | null = null) {
    super('Tag request failed')
    this.status = status
    this.code = code
  }
}

export async function searchTags(query: string, signal?: AbortSignal): Promise<Tag[]> {
  const parameters = new URLSearchParams()
  if (query.trim() !== '') {
    parameters.set('query', query.trim())
  }
  const suffix = parameters.toString()
  const response = await tagRequest(suffix === '' ? '/api/tags' : `/api/tags?${suffix}`, { signal })
  return (await response.json()) as Tag[]
}

export async function createTag(name: string): Promise<Tag> {
  const response = await tagRequest('/api/tags', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name }),
  })
  return (await response.json()) as Tag
}

export async function replaceDocumentTags(documentId: number, tagIds: number[]): Promise<Tag[]> {
  const response = await tagRequest(`/api/documents/${documentId}/tags`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ tagIds }),
  })
  return (await response.json()) as Tag[]
}

export async function removeDocumentTag(documentId: number, tagId: number): Promise<void> {
  await tagRequest(`/api/documents/${documentId}/tags/${tagId}`, { method: 'DELETE' })
}

export async function getTagUsage(signal?: AbortSignal): Promise<TagUsage[]> {
  const response = await tagRequest('/api/tags/usage', { signal })
  return (await response.json()) as TagUsage[]
}

export async function getTaggedDocuments(tagId: number, signal?: AbortSignal): Promise<TaggedDocuments> {
  const response = await tagRequest(`/api/tags/${tagId}/documents`, { signal })
  return (await response.json()) as TaggedDocuments
}

async function tagRequest(path: string, init: RequestInit = {}): Promise<Response> {
  const accessToken = getAccessToken()
  if (accessToken === null) {
    throw new TagApiError(401)
  }
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  headers.set('Authorization', `Bearer ${accessToken}`)
  const response = await fetch(path, { ...init, headers })
  if (!response.ok) {
    throw new TagApiError(response.status, await responseErrorCode(response))
  }
  return response
}

async function responseErrorCode(response: Response): Promise<string | null> {
  try {
    const body: unknown = await response.json()
    if (typeof body === 'object' && body !== null && 'code' in body && typeof body.code === 'string') {
      return body.code
    }
  } catch {
    // 오류 본문을 사용자 화면에 노출하지 않는다.
  }
  return null
}
