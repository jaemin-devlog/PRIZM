import assert from 'node:assert/strict'
import test from 'node:test'

import type { DocumentSummary } from '../src/api/documentApi.ts'
import type { JobPostingItem } from '../src/api/jobPostingApi.ts'
import type { CareerEvidenceSearchResult } from '../src/api/searchApi.ts'
import {
  clearJobPostingItemSelection,
  findJobEvidence,
  groupJobPostingItems,
  jobEvidenceSearchQueries,
  loadingJobEvidenceGroups,
  mergeJobEvidenceResults,
  selectAllJobPostingItems,
  selectedJobPostingItemCount,
  selectedJobPostingItems,
  toggleJobPostingItemSelection,
} from '../src/jobEvidence.ts'

const items: JobPostingItem[] = [
  { itemId: 1, section: '자격요건', text: '분산 시스템 운영 경험' },
  { itemId: 2, section: '자격요건', text: '관측 가능성 개선 경험' },
  { itemId: 3, section: '우대사항', text: '클라우드 비용 최적화 경험' },
]

test('section grouping preserves source order and keeps repeated non-contiguous sections separate', () => {
  const sourceItems: JobPostingItem[] = [
    { itemId: 1, section: '담당업무', text: '첫 번째 업무' },
    { itemId: 2, section: '담당업무', text: '두 번째 업무' },
    { itemId: 3, section: '자격요건', text: '필수 경험' },
    { itemId: 4, section: null, text: '독립 요구사항' },
    { itemId: 5, section: '담당업무', text: '뒤에 다시 나온 업무' },
  ]

  assert.deepEqual(groupJobPostingItems(sourceItems), [
    { section: '담당업무', items: sourceItems.slice(0, 2) },
    { section: '자격요건', items: sourceItems.slice(2, 3) },
    { section: null, items: sourceItems.slice(3, 4) },
    { section: '담당업무', items: sourceItems.slice(4, 5) },
  ])
})

test('compound query planning keeps the original and only expands explicit alternatives', () => {
  assert.deepEqual(jobEvidenceSearchQueries('Spring Boot 개발 경험'), [
    'Spring Boot 개발 경험',
  ])
  assert.deepEqual(jobEvidenceSearchQueries('Java 또는 Kotlin 개발 경험'), [
    'Java 또는 Kotlin 개발 경험',
    'Java',
    'Kotlin 개발 경험',
  ])
  assert.deepEqual(jobEvidenceSearchQueries('Java or Kotlin development experience'), [
    'Java or Kotlin development experience',
    'Java',
    'Kotlin development experience',
  ])
  assert.deepEqual(jobEvidenceSearchQueries('Java / Kotlin 개발 경험'), [
    'Java / Kotlin 개발 경험',
    'Java',
    'Kotlin 개발 경험',
  ])
  assert.deepEqual(jobEvidenceSearchQueries('Docker/Kubernetes/Cloud 사용 경험'), [
    'Docker/Kubernetes/Cloud 사용 경험',
    'Docker',
    'Kubernetes',
    'Cloud 사용 경험',
  ])
  assert.deepEqual(jobEvidenceSearchQueries('Docker, Kubernetes 또는 Cloud 환경 사용 경험'), [
    'Docker, Kubernetes 또는 Cloud 환경 사용 경험',
    'Docker',
    'Kubernetes',
    'Cloud 환경 사용 경험',
  ])
  assert.deepEqual(jobEvidenceSearchQueries('요구사항, 일정, API를 조율한 경험'), [
    '요구사항, 일정, API를 조율한 경험',
  ])
  assert.deepEqual(jobEvidenceSearchQueries('3년 이상 개발하고, Java 또는 Kotlin 경험'), [
    '3년 이상 개발하고, Java 또는 Kotlin 경험',
  ])
  assert.deepEqual(jobEvidenceSearchQueries('1,000건 처리 또는 장애 복구 경험'), [
    '1,000건 처리 또는 장애 복구 경험',
  ])
  assert.deepEqual(jobEvidenceSearchQueries('CI/CD 파이프라인 경험'), [
    'CI/CD 파이프라인 경험',
  ])
  assert.deepEqual(jobEvidenceSearchQueries('OAuth2/JWT 인증 경험'), [
    'OAuth2/JWT 인증 경험',
  ])
  assert.deepEqual(jobEvidenceSearchQueries('Docker and/or Kubernetes 경험'), [
    'Docker and/or Kubernetes 경험',
  ])
  assert.deepEqual(jobEvidenceSearchQueries('src/main/java'), [
    'src/main/java',
  ])
  assert.deepEqual(jobEvidenceSearchQueries('src/main/java 구조 이해'), [
    'src/main/java 구조 이해',
  ])
  assert.deepEqual(jobEvidenceSearchQueries('frontend/src/App.tsx'), [
    'frontend/src/App.tsx',
  ])
  assert.deepEqual(jobEvidenceSearchQueries('C:/Users/USER'), [
    'C:/Users/USER',
  ])
  assert.deepEqual(jobEvidenceSearchQueries('R 또는 Python 경험'), [
    'R 또는 Python 경험',
    'R',
    'Python 경험',
  ])
})

test('compound query planning conservatively expands explicit counted alternatives', () => {
  const counted = 'Kotlin, TypeScript, Python, Go, Java 중 1개 이상 개발 가능자'
  assert.deepEqual(jobEvidenceSearchQueries(counted), [
    counted,
    'Kotlin',
    'TypeScript',
    'Python',
    'Go',
    'Java',
  ])
  assert.deepEqual(jobEvidenceSearchQueries('Docker, Kubernetes 중 하나 이상 운영 경험'), [
    'Docker, Kubernetes 중 하나 이상 운영 경험',
    'Docker',
    'Kubernetes',
  ])
  const capped = '기술01, 기술02, 기술03, 기술04, 기술05, 기술06, 기술07 중 1개 이상 사용 경험'
  assert.deepEqual(jobEvidenceSearchQueries(capped), [
    capped,
    '기술01',
    '기술02',
    '기술03',
    '기술04',
    '기술05',
  ])
  assert.deepEqual(jobEvidenceSearchQueries('요구사항, 일정, API를 조율한 경험'), [
    '요구사항, 일정, API를 조율한 경험',
  ])
  assert.deepEqual(jobEvidenceSearchQueries('1,000건 처리 또는 장애 복구 경험'), [
    '1,000건 처리 또는 장애 복구 경험',
  ])
  assert.deepEqual(jobEvidenceSearchQueries('CI/CD 파이프라인 경험'), [
    'CI/CD 파이프라인 경험',
  ])
  assert.deepEqual(jobEvidenceSearchQueries('src/main/java'), [
    'src/main/java',
  ])
})

test('compound query planning conservatively expands explicit enumeration suffixes', () => {
  assert.deepEqual(jobEvidenceSearchQueries('Alpha/Beta 등 관련 기술 사용 경험'), [
    'Alpha/Beta 등 관련 기술 사용 경험',
    'Alpha',
    'Beta',
  ])
  assert.deepEqual(jobEvidenceSearchQueries('Docker, Git, Gradle 등 개발 도구 사용 경험'), [
    'Docker, Git, Gradle 등 개발 도구 사용 경험',
    'Docker',
    'Git',
    'Gradle',
  ])
  assert.deepEqual(jobEvidenceSearchQueries('Java, Kotlin 등의 언어 사용 경험'), [
    'Java, Kotlin 등의 언어 사용 경험',
    'Java',
    'Kotlin',
  ])
  assert.deepEqual(jobEvidenceSearchQueries('요구사항, 일정, API를 조율한 경험'), [
    '요구사항, 일정, API를 조율한 경험',
  ])
  assert.deepEqual(jobEvidenceSearchQueries('1,000건 처리 경험'), ['1,000건 처리 경험'])
  assert.deepEqual(jobEvidenceSearchQueries('CI/CD 경험'), ['CI/CD 경험'])
  assert.deepEqual(jobEvidenceSearchQueries('OAuth2/JWT 경험'), ['OAuth2/JWT 경험'])
  assert.deepEqual(jobEvidenceSearchQueries('src/main/java'), ['src/main/java'])
})

test('compound query planning removes duplicate variants and limits variants to five', () => {
  assert.deepEqual(jobEvidenceSearchQueries('Docker, Docker 또는 Docker 사용 경험'), [
    'Docker, Docker 또는 Docker 사용 경험',
    'Docker',
    'Docker 사용 경험',
  ])

  const original = '기술01, 기술02, 기술03, 기술04, 기술05, 기술06 또는 기술07 사용 경험'
  assert.deepEqual(jobEvidenceSearchQueries(original), [
    original,
    '기술01',
    '기술02',
    '기술03',
    '기술04',
    '기술05',
  ])
})

function evidence(
  chunkId: number,
  documentId: number,
  text: string,
): CareerEvidenceSearchResult {
  return {
    chunkId,
    documentId,
    documentVersionId: documentId + 100,
    documentTitle: `문서 ${documentId}`,
    versionNo: 1,
    content: text,
    snippet: text,
    sourceType: 'TEXT_CHUNK',
    sourceIndex: 1,
    sourceLabel: '텍스트 구간 1',
    evidenceChunkId: chunkId,
    evidenceSourceType: 'TEXT_CHUNK',
    evidenceSourceIndex: 1,
    evidenceSourceLabel: '텍스트 구간 1',
    distance: 0.2,
    score: 0.8,
  }
}

test('job posting item selection supports default all, individual toggle, clear, count, and original order', () => {
  const selectedAll = selectAllJobPostingItems(items)
  assert.deepEqual([...selectedAll], [1, 2, 3])
  assert.equal(selectedJobPostingItemCount(items, selectedAll), 3)

  const toggled = toggleJobPostingItemSelection(selectedAll, 2)
  assert.deepEqual(selectedJobPostingItems(items, toggled).map((item) => item.itemId), [1, 3])
  assert.equal(selectedJobPostingItemCount(items, toggled), 2)
  assert.deepEqual(loadingJobEvidenceGroups(items, toggled).map((group) => group.item.itemId), [1, 3])

  const selectedAgain = toggleJobPostingItemSelection(toggled, 2)
  assert.deepEqual(selectedJobPostingItems(items, selectedAgain).map((item) => item.itemId), [1, 2, 3])
  assert.equal(clearJobPostingItemSelection().size, 0)
})

test('selected items call the existing Search once with unchanged text and preserve group order', async () => {
  const queries: string[] = []
  let documentRequests = 0
  const search = await findJobEvidence(items, new Set([1, 3]), {
    search: async (query) => {
      queries.push(query)
      return query === items[0].text
        ? [evidence(11, 101, '첫 번째 관련 기록')]
        : [evidence(31, 103, '세 번째 관련 기록')]
    },
    listDocuments: async () => {
      documentRequests += 1
      return [
        { documentId: 101, documentType: 'RESUME' },
        { documentId: 103, documentType: 'PROJECT_REPORT' },
        { documentId: 999, documentType: 'OTHER' },
      ] as DocumentSummary[]
    },
  })

  assert.deepEqual(queries, [items[0].text, items[2].text])
  assert.deepEqual(search.groups.map((group) => group.item.itemId), [1, 3])
  assert.deepEqual(search.groups.map((group) => group.state), ['result', 'result'])
  assert.equal(documentRequests, 1)
  assert.equal(search.documentTypes.get(101), 'RESUME')
  assert.equal(search.documentTypes.get(103), 'PROJECT_REPORT')
  assert.equal(search.documentTypes.has(999), false)
})

test('compound items search original and variants in order and show variant Evidence under the original item', async () => {
  const compoundItem: JobPostingItem = {
    itemId: 10,
    section: '우대사항',
    text: 'Docker, Kubernetes 또는 Cloud 환경 사용 경험',
  }
  const queries: string[] = []
  const search = await findJobEvidence([compoundItem], new Set([compoundItem.itemId]), {
    search: async (query) => {
      queries.push(query)
      return query === 'Docker'
        ? [evidence(41, 105, 'Docker Compose 기반 배포 기록')]
        : []
    },
    listDocuments: async () => [
      { documentId: 105, documentType: 'PROJECT_REPORT' },
    ] as DocumentSummary[],
  })

  assert.deepEqual(queries, [
    compoundItem.text,
    'Docker',
    'Kubernetes',
    'Cloud 환경 사용 경험',
  ])
  assert.equal(search.groups[0]?.state, 'result')
  assert.equal(search.groups[0]?.item.text, compoundItem.text)
  assert.deepEqual(search.groups[0]?.candidates.map(({ result }) => result.chunkId), [41])
  assert.deepEqual(search.groups[0]?.candidates[0]?.matchedQueries, ['Docker'])
  assert.equal(search.groups[0]?.candidates[0]?.displayQuery, 'Docker')
  assert.equal(search.groups[0]?.candidates[0]?.displayQueryIsDirectIdentifier, true)
})

test('short identifier variants keep only direct Evidence and preserve matched query provenance', async () => {
  const compoundItem: JobPostingItem = {
    itemId: 13,
    section: '자격요건',
    text: 'Kotlin, TypeScript, Python, Go, Java 중 1개 이상 개발 가능자',
  }
  let javaRawResults = 0
  const search = await findJobEvidence([compoundItem], new Set([compoundItem.itemId]), {
    search: async (query) => {
      if (query !== 'Java') return []
      const results = [
        evidence(71, 130, 'Java 17, Spring Boot 기반으로 개발했습니다.'),
        evidence(72, 130, 'Java와 Spring으로 백엔드를 개발했습니다.'),
        evidence(73, 130, '예산에 맞춰 제주 여행 일정을 만드는 서비스'),
      ]
      javaRawResults = results.length
      return results
    },
    listDocuments: async () => [],
  })

  assert.equal(javaRawResults, 3)
  assert.deepEqual(search.groups[0]?.candidates.map(({ result }) => result.chunkId), [71, 72])
  assert.deepEqual(search.groups[0]?.candidates.map(({ matchedQueries }) => matchedQueries), [
    ['Java'],
    ['Java'],
  ])
  assert.deepEqual(search.groups[0]?.candidates.map(({ displayQuery }) => displayQuery), [
    'Java',
    'Java',
  ])
})

test('a standalone identifier prioritizes an independent source token without deleting semantic candidates', async () => {
  const identifierItem: JobPostingItem = {
    itemId: 14,
    section: '기술 스택',
    text: 'ZephyrDB',
  }
  const search = await findJobEvidence([identifierItem], new Set([identifierItem.itemId]), {
    search: async () => [
      evidence(74, 131, 'ZephyrDB로 검색 인덱스를 운영했습니다.'),
      evidence(75, 131, 'ZephyrDBCloud를 검토했습니다.'),
    ],
    listDocuments: async () => [],
  })

  assert.deepEqual(search.groups[0]?.candidates.map(({ result }) => result.chunkId), [74, 75])
  assert.equal(search.groups[0]?.candidates[0]?.displayQueryIsDirectIdentifier, true)
  assert.equal(search.groups[0]?.candidates[1]?.displayQueryIsDirectIdentifier, false)
})

test('a natural one-word requirement keeps its original semantic Search result', async () => {
  const requirementItem: JobPostingItem = {
    itemId: 15,
    section: 'Qualifications',
    text: 'Ownership',
  }
  const search = await findJobEvidence([requirementItem], new Set([requirementItem.itemId]), {
    search: async () => [evidence(76, 132, '제품 결과를 끝까지 책임졌습니다.')],
    listDocuments: async () => [],
  })

  assert.equal(search.groups[0]?.state, 'result')
  assert.deepEqual(search.groups[0]?.candidates.map(({ result }) => result.chunkId), [76])
  assert.equal(search.groups[0]?.candidates[0]?.displayQueryIsDirectIdentifier, false)
})

test('a dot-prefixed standalone identifier uses the same independent-token priority', async () => {
  const identifierItem: JobPostingItem = {
    itemId: 16,
    section: '기술 스택',
    text: '.NET',
  }
  const search = await findJobEvidence([identifierItem], new Set([identifierItem.itemId]), {
    search: async () => [
      evidence(77, 133, '.NET 8로 API를 개발했습니다.'),
      evidence(78, 133, 'Python으로 배치 서비스를 개발했습니다.'),
    ],
    listDocuments: async () => [],
  })

  assert.deepEqual(search.groups[0]?.candidates.map(({ result }) => result.chunkId), [77, 78])
  assert.equal(search.groups[0]?.candidates[0]?.displayQueryIsDirectIdentifier, true)
  assert.equal(search.groups[0]?.candidates[1]?.displayQueryIsDirectIdentifier, false)
})

test('direct Evidence matching respects identifier boundaries and never filters original queries', () => {
  const original = evidence(81, 140, '원문 query가 찾은 semantic 후보')
  const docker = evidence(82, 140, 'Docker Compose로 서비스를 실행했습니다.')
  const githubOnly = evidence(83, 140, 'GitHub Actions로 배포했습니다.')
  const cpp = evidence(84, 140, 'C++ 기반 모듈을 개발했습니다.')
  const csharp = evidence(85, 140, 'C# 기반 서비스를 개발했습니다.')
  const node = evidence(86, 140, 'Node.js 서버를 운영했습니다.')

  const candidates = mergeJobEvidenceResults([
    { query: '원래 긴 요구사항', original: true, directIdentifier: false, results: [original] },
    { query: 'Docker', original: false, directIdentifier: true, results: [docker] },
    { query: 'Git', original: false, directIdentifier: true, results: [githubOnly] },
    { query: 'C++', original: false, directIdentifier: true, results: [cpp] },
    { query: 'C#', original: false, directIdentifier: true, results: [csharp] },
    { query: 'Node.js', original: false, directIdentifier: true, results: [node] },
  ])

  assert.deepEqual(candidates.map(({ result }) => result.chunkId), [81, 82, 84, 85, 86])
})

test('original-first merge deduplicates selected chunks, preserves distinct Evidence, and caps at five', async () => {
  const compoundItem: JobPostingItem = {
    itemId: 11,
    section: '자격요건',
    text: 'Java 또는 Kotlin 개발 경험',
  }
  const originalEvidence = evidence(51, 110, 'Java 원문 query가 찾은 기록')
  const duplicateFromVariant = {
    ...originalEvidence,
    snippet: 'Java variant가 다시 찾은 같은 기록',
    score: 0.99,
  }
  const distinctSameDocument = {
    ...evidence(52, 110, 'Java 같은 문서의 다른 기록'),
    sourceType: 'PAGE' as const,
    sourceIndex: 1,
    sourceLabel: '1페이지',
    evidenceSourceType: 'PAGE' as const,
    evidenceSourceIndex: 1,
    evidenceSourceLabel: '1페이지',
  }
  const search = await findJobEvidence([compoundItem], new Set([compoundItem.itemId]), {
    search: async (query) => {
      if (query === compoundItem.text) return [originalEvidence]
      if (query === 'Java') {
        return [
          duplicateFromVariant,
          distinctSameDocument,
          evidence(53, 111, 'Java 세 번째 기록'),
          evidence(54, 112, 'Java 네 번째 기록'),
        ]
      }
      return [
        evidence(55, 113, '다섯 번째 기록'),
        evidence(56, 114, '제한 밖의 여섯 번째 기록'),
      ]
    },
    listDocuments: async () => [],
  })

  assert.deepEqual(
    search.groups[0]?.candidates.map(({ result }) => result.chunkId),
    [51, 52, 53, 54, 55],
  )
  assert.equal(search.groups[0]?.candidates[0]?.result.snippet, 'Java 원문 query가 찾은 기록')
  assert.equal(search.groups[0]?.candidates[1]?.result.sourceLabel, '1페이지')
  assert.deepEqual(search.groups[0]?.candidates[0]?.matchedQueries, [compoundItem.text, 'Java'])
  assert.equal(search.groups[0]?.candidates[0]?.displayQuery, 'Java')
  assert.equal(search.groups[0]?.candidates[0]?.displayQueryIsDirectIdentifier, true)
})

test('result merging keeps query order and only removes the same document version chunk', () => {
  const first = evidence(61, 120, '첫 query 결과')
  const sameChunkInAnotherVersion = {
    ...evidence(61, 120, '다른 버전의 같은 chunk id'),
    documentVersionId: 999,
  }
  const duplicate = { ...first, snippet: '나중 query 중복' }

  assert.deepEqual(
    mergeJobEvidenceResults([
      { query: '원문', original: true, directIdentifier: false, results: [first] },
      { query: '다른 query', original: false, directIdentifier: false, results: [duplicate, sameChunkInAnotherVersion] },
    ]).map(({ result }) => (
      [result.documentVersionId, result.chunkId, result.snippet]
    )),
    [
      [220, 61, '첫 query 결과'],
      [999, 61, '다른 버전의 같은 chunk id'],
    ],
  )
})

test('all compound queries returning no Evidence produce the neutral empty state', async () => {
  const compoundItem: JobPostingItem = {
    itemId: 12,
    section: '우대사항',
    text: 'Git을 활용한 프로젝트 또는 협업 경험',
  }
  let searchRequests = 0
  const search = await findJobEvidence([compoundItem], new Set([compoundItem.itemId]), {
    search: async () => {
      searchRequests += 1
      return []
    },
    listDocuments: async () => [],
  })

  assert.equal(searchRequests, 3)
  assert.equal(search.groups[0]?.state, 'empty')
  assert.deepEqual(search.groups[0]?.candidates, [])
})

test('empty and failed Search groups stay independent while metadata failure keeps Evidence visible', async () => {
  const metadataFailure = new Error('document metadata unavailable')
  const searchFailure = { status: 403 }
  const search = await findJobEvidence(items, new Set([1, 2, 3]), {
    search: async (query) => {
      if (query === items[0].text) return []
      if (query === items[1].text) throw searchFailure
      return [evidence(33, 103, '성공한 다른 그룹의 관련 기록')]
    },
    listDocuments: async () => {
      throw metadataFailure
    },
  })

  assert.deepEqual(search.groups.map((group) => group.state), ['empty', 'error', 'result'])
  assert.equal(search.groups[1]?.error, searchFailure)
  assert.equal(search.groups[2]?.candidates[0]?.result.snippet, '성공한 다른 그룹의 관련 기록')
  assert.equal(search.metadataError, metadataFailure)
  assert.equal(search.documentTypes.size, 0)
})

test('zero selected items prevent Search and document metadata requests', async () => {
  let searchRequests = 0
  let documentRequests = 0
  const search = await findJobEvidence(items, clearJobPostingItemSelection(), {
    search: async () => {
      searchRequests += 1
      return []
    },
    listDocuments: async () => {
      documentRequests += 1
      return []
    },
  })

  assert.deepEqual(search.groups, [])
  assert.equal(searchRequests, 0)
  assert.equal(documentRequests, 0)
})

test('authentication failure stops other compound workers before their next variant', async () => {
  const compoundItems: JobPostingItem[] = [
    { itemId: 21, section: null, text: 'Java 또는 Kotlin 개발 경험' },
    { itemId: 22, section: null, text: 'Docker 또는 Podman 운영 경험' },
  ]
  const queries: string[] = []
  let documentRequests = 0
  let notifySecondStarted: (() => void) | undefined
  let releaseSecondSearch: (() => void) | undefined
  const secondStarted = new Promise<void>((resolve) => {
    notifySecondStarted = resolve
  })
  const secondSearchCanFinish = new Promise<void>((resolve) => {
    releaseSecondSearch = resolve
  })

  const pendingSearch = findJobEvidence(compoundItems, selectAllJobPostingItems(compoundItems), {
    search: async (query) => {
      queries.push(query)
      if (query === compoundItems[0]?.text) {
        await secondStarted
        throw { status: 401 }
      }
      if (query === compoundItems[1]?.text) {
        notifySecondStarted?.()
        await secondSearchCanFinish
      }
      return []
    },
    listDocuments: async () => {
      documentRequests += 1
      return []
    },
  })

  await assert.rejects(
    pendingSearch,
    (error: unknown) => typeof error === 'object'
      && error !== null
      && 'status' in error
      && error.status === 401,
  )
  releaseSecondSearch?.()
  await new Promise((resolve) => setTimeout(resolve, 0))

  assert.deepEqual(queries, [compoundItems[0]?.text, compoundItems[1]?.text])
  assert.equal(documentRequests, 0)
})

test('large compound selections keep physical Search concurrency bounded while preserving every group', async () => {
  const manyItems = Array.from({ length: 8 }, (_, index): JobPostingItem => ({
    itemId: index + 1,
    section: null,
    text: `일반 요구사항 ${index + 1} 또는 대안 요구사항 ${index + 1}`,
  }))
  let activeRequests = 0
  let maximumActiveRequests = 0
  let totalRequests = 0
  const search = await findJobEvidence(manyItems, selectAllJobPostingItems(manyItems), {
    search: async () => {
      totalRequests += 1
      activeRequests += 1
      maximumActiveRequests = Math.max(maximumActiveRequests, activeRequests)
      await new Promise((resolve) => setTimeout(resolve, 1))
      activeRequests -= 1
      return []
    },
    listDocuments: async () => [],
  })

  assert.equal(search.groups.length, 8)
  assert.deepEqual(search.groups.map((group) => group.item.itemId), [1, 2, 3, 4, 5, 6, 7, 8])
  assert.equal(totalRequests, 24)
  assert.ok(maximumActiveRequests <= 3)
})
