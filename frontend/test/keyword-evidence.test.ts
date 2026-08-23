import assert from 'node:assert/strict'
import test from 'node:test'

import type { DocumentSummary } from '../src/api/documentApi.ts'
import type { CareerEvidenceSearchResult } from '../src/api/searchApi.ts'
import { loadKeywordEvidence, relatedEvidenceDocumentCount } from '../src/keywordEvidence.ts'

const springBootEvidence: CareerEvidenceSearchResult[] = [
  {
    chunkId: 11,
    documentId: 101,
    documentVersionId: 201,
    documentTitle: '백엔드 이력서',
    versionNo: 1,
    content: 'Spring Boot 기반으로 API 서버를 구축했습니다.',
    snippet: 'Spring Boot 기반으로 API 서버를 구축했습니다.',
    sourceType: 'PAGE',
    sourceIndex: 2,
    sourceLabel: '2페이지',
    evidenceChunkId: 11,
    evidenceSourceType: 'PAGE',
    evidenceSourceIndex: 2,
    evidenceSourceLabel: '2페이지',
    distance: 0.3,
    score: 0.7,
  },
  {
    chunkId: 12,
    documentId: 102,
    documentVersionId: 202,
    documentTitle: '프로젝트 기록',
    versionNo: 1,
    content: 'Spring Boot 환경에서 배포 자동화를 개선했습니다.',
    snippet: 'Spring Boot 환경에서 배포 자동화를 개선했습니다.',
    sourceType: 'TEXT_CHUNK',
    sourceIndex: 3,
    sourceLabel: '텍스트 구간 3',
    evidenceChunkId: 12,
    evidenceSourceType: 'TEXT_CHUNK',
    evidenceSourceIndex: 3,
    evidenceSourceLabel: '텍스트 구간 3',
    distance: 0.4,
    score: 0.6,
  },
]

test('tag detail uses the tag name as the existing Search query and maps all result documents', async () => {
  const queries: string[] = []
  const documents = [
    { documentId: 101, documentType: 'RESUME' },
    { documentId: 102, documentType: 'PROJECT_REPORT' },
    { documentId: 999, documentType: 'OTHER' },
  ] as DocumentSummary[]

  const loaded = await loadKeywordEvidence('Spring Boot', {
    search: async (query) => {
      queries.push(query)
      return springBootEvidence
    },
    listDocuments: async () => documents,
  })

  assert.deepEqual(queries, ['Spring Boot'])
  assert.equal(loaded.results, springBootEvidence)
  assert.equal(loaded.documentTypes.get(101), 'RESUME')
  assert.equal(loaded.documentTypes.get(102), 'PROJECT_REPORT')
  assert.equal(loaded.documentTypes.has(999), false)
  assert.equal(relatedEvidenceDocumentCount(loaded.results), 2)
})

test('zero Search results remain a valid empty state without loading document metadata', async () => {
  let documentRequests = 0
  const loaded = await loadKeywordEvidence('No Evidence Tag', {
    search: async () => [],
    listDocuments: async () => {
      documentRequests += 1
      return []
    },
  })

  assert.deepEqual(loaded.results, [])
  assert.equal(documentRequests, 0)
})

test('Search errors are preserved for the page error state', async () => {
  await assert.rejects(
    loadKeywordEvidence('Spring Boot', {
      search: async () => {
        throw new Error('search unavailable')
      },
      listDocuments: async () => [],
    }),
    /search unavailable/,
  )
})
