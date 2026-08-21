import assert from 'node:assert/strict'
import test from 'node:test'
import {
  categoryKeywordCountLabel,
  getConciseKeywordEvidence,
  getKeywordEvidenceContext,
  getVisibleKeywords,
  keywordDetailPath,
  keywordEvidenceCountLabel,
  keywordMentionLabel,
  selectedKeywordFromSearch,
} from '../src/careerKeywordPresentation.ts'
import type { CareerKeywordSummary } from '../src/api/careerKeywordApi.ts'

const keywords: CareerKeywordSummary[] = [
  { keyword: 'Redis', category: 'DATABASE', frequency: 2, documentCount: 1, variants: ['Redis'] },
  { keyword: 'Java', category: 'LANGUAGE', frequency: 3, documentCount: 2, variants: ['Java 21'] },
  { keyword: 'Docker', category: 'INFRASTRUCTURE', frequency: 2, documentCount: 2, variants: ['Docker'] },
]

test('keyword tags use the existing frequency order with a stable name tie-breaker', () => {
  assert.deepEqual(
    getVisibleKeywords(keywords, 'ALL').map((keyword) => keyword.keyword),
    ['Java', 'Docker', 'Redis'],
  )
})

test('category filtering can return an empty tag collection without hiding the selected category', () => {
  assert.deepEqual(getVisibleKeywords(keywords, 'SECURITY'), [])
})

test('keyword detail URL round-trips the canonical keyword for browser navigation', () => {
  assert.equal(keywordDetailPath('Spring Boot'), '/career-vault/keywords?keyword=Spring%20Boot')
  assert.equal(selectedKeywordFromSearch('?keyword=Spring%20Boot'), 'Spring Boot')
  assert.equal(selectedKeywordFromSearch('?keyword=%20'), null)
})

test('the tag count is presented as a document mention rather than a score', () => {
  assert.equal(keywordMentionLabel(4), '문서에서 확인된 언급 4회')
})

test('category keyword counts and evidence counts use their distinct existing API meanings', () => {
  assert.equal(categoryKeywordCountLabel(5), '키워드 5개')
  assert.equal(keywordEvidenceCountLabel(2, 8), '관련 기록 2개 · 총 8회 언급')
})

test('concise keyword evidence preserves a meaningful profile line after synthetic contact removal', () => {
  const excerpt = [
    'Example Person Java / Spring Backend Developer user@example.com | 010-1234-5678 | github.com/example',
    'Example Person',
    '역할: 그룹 매칭, 알림, 채팅, 통계 백엔드 및 배포를 담당했습니다.',
    '기술: Java 17, Spring Boot 3.4.3, Redis를 사용했습니다.',
    'https://example.com/profile',
  ].join('\n')

  const concise = getConciseKeywordEvidence(excerpt)
  const context = getKeywordEvidenceContext(excerpt)

  assert.match(concise, /Java \/ Spring Backend Developer/)
  assert.match(concise, /그룹 매칭/)
  assert.doesNotMatch(concise, /user@example\.com|010-1234-5678|github\.com|Example Person/)
  assert.doesNotMatch(context, /user@example\.com|010-1234-5678|github\.com|example\.com\/profile/)
  assert.match(context, /Spring Boot/)
  assert.doesNotMatch(context, /그룹 매칭/)
})

test('keyword context only returns additional safe content after the concise preview', () => {
  const excerpt = [
    '역할: Project Atlas API를 구현했습니다.',
    '기술: Java, Spring Boot를 사용했습니다.',
    '결과: 배포 자동화 시간을 줄였습니다.',
  ].join('\n')

  assert.equal(
    getConciseKeywordEvidence(excerpt),
    '역할: Project Atlas API를 구현했습니다.\n기술: Java, Spring Boot를 사용했습니다.',
  )
  assert.equal(getKeywordEvidenceContext(excerpt), '결과: 배포 자동화 시간을 줄였습니다.')
})

test('generic fallback is reserved for evidence with no safe preview content', () => {
  const excerpt = 'Email: user@example.com | 010-1234-5678 | https://example.com/profile'

  assert.equal(getConciseKeywordEvidence(excerpt), '')
  assert.equal(getKeywordEvidenceContext(excerpt), '')
})
