import type { CareerKeywordCategory, CareerKeywordSummary } from './api/careerKeywordApi'

export type KeywordCategoryFilter = CareerKeywordCategory | 'ALL'

export const KEYWORD_CATEGORY_LABELS: Record<CareerKeywordCategory, string> = {
  LANGUAGE: '언어',
  FRAMEWORK: '프레임워크',
  DATABASE: '데이터베이스',
  INFRASTRUCTURE: '인프라',
  MESSAGING: '메시징',
  SECURITY: '보안',
  TESTING: '테스트',
  WEB: '웹·애플리케이션',
  TOOLING: '개발 도구',
  ENGINEERING_CONCEPT: '공학 개념',
}

export const KEYWORD_CATEGORY_ORDER = Object.keys(
  KEYWORD_CATEGORY_LABELS,
) as CareerKeywordCategory[]

export function getVisibleKeywords(
  keywords: CareerKeywordSummary[],
  category: KeywordCategoryFilter,
): CareerKeywordSummary[] {
  return keywords
    .filter((keyword) => category === 'ALL' || keyword.category === category)
    .sort(
      (left, right) =>
        right.frequency - left.frequency || left.keyword.localeCompare(right.keyword, 'ko'),
    )
}

export function keywordDetailPath(keyword: string | null): string {
  if (keyword === null) {
    return '/career-vault/keywords'
  }
  return `/career-vault/keywords?keyword=${encodeURIComponent(keyword)}`
}

export function selectedKeywordFromSearch(search: string): string | null {
  const keyword = new URLSearchParams(search).get('keyword')?.trim()
  return keyword === undefined || keyword === '' ? null : keyword
}

export function keywordMentionLabel(frequency: number): string {
  return `문서에서 확인된 언급 ${frequency}회`
}

export function categoryKeywordCountLabel(keywordCount: number): string {
  return `키워드 ${keywordCount}개`
}

export function keywordEvidenceCountLabel(
  evidenceCount: number,
  totalFrequency: number,
): string {
  return `관련 기록 ${evidenceCount}개 · 총 ${totalFrequency}회 언급`
}

/**
 * Keeps a keyword evidence preview focused on the document content. Original files
 * remain available unchanged through the existing owner-scoped viewer.
 */
export function getConciseKeywordEvidence(excerpt: string): string {
  return getSafeKeywordEvidenceUnits(excerpt).slice(0, 2).join('\n')
}

export function getKeywordEvidenceContext(excerpt: string): string {
  return getSafeKeywordEvidenceUnits(excerpt).slice(2, 6).join('\n')
}

function getSafeKeywordEvidenceUnits(excerpt: string): string[] {
  return segmentKeywordEvidenceUnits(excerpt)
    .map(sanitizeKeywordEvidenceUnit)
    .filter((unit) => unit !== '' && !isKeywordEvidenceMetadata(unit))
}

function segmentKeywordEvidenceUnits(value: string): string[] {
  const lines = value.split(/\r?\n/).map((line) => line.trim()).filter(Boolean)
  if (lines.length >= 2) {
    return lines
  }

  const segmenter = new Intl.Segmenter('ko', { granularity: 'sentence' })
  return Array.from(segmenter.segment(value), ({ segment }) => segment.trim()).filter(Boolean)
}

function isKeywordEvidenceMetadata(value: string): boolean {
  const trimmed = value.trim()
  const contactLabel = /^(?:contact|email|phone|github|profile|연락처|이메일|전화번호|깃허브|이름|프로필)\b/i.test(trimmed)
  const nameOnlyHeader = /^(?:[가-힣]{2,4}|[A-Z][a-z]+(?:\s+[A-Z][a-z]+){0,2})$/.test(trimmed)
  return contactLabel || nameOnlyHeader
}

function sanitizeKeywordEvidenceUnit(value: string): string {
  const withoutContact = value
    .replace(/[\w.%+-]+@[\w.-]+\.[a-z]{2,}/gi, ' ')
    .replace(/https?:\/\/\S+|www\.\S+|github\.com\/\S+/gi, ' ')
    .replace(/(?:\+?82[- .]?)?0(?:10|2|[3-6][1-5])[- .]?\d{3,4}[- .]?\d{4}/g, ' ')
  const withoutEnglishProfileName = withoutContact.replace(
    /^(?:[A-Z][a-z]+\s+[A-Z][a-z]+)\s+(?=[A-Za-z0-9+#.]+\s*\/)/,
    '',
  )

  return withoutEnglishProfileName
    .replace(/[|·•]+/g, ' ')
    .replace(/\s{2,}/g, ' ')
    .replace(/\s*[,;:/-]\s*$/g, '')
    .trim()
}
