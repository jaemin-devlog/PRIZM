import type { CareerEvidenceSearchResult } from './api/searchApi'

export function getEvidenceSourceLabel(
  result: Pick<CareerEvidenceSearchResult, 'evidenceSourceLabel'>,
): string {
  return result.evidenceSourceLabel
}

/** Returns the 1-based PDF page that supplies displayed evidence, if one is available. */
export function getEvidencePdfPage(
  result: Pick<CareerEvidenceSearchResult, 'evidenceSourceType' | 'evidenceSourceIndex'>,
): number | null {
  if (result.evidenceSourceType !== 'PAGE' || result.evidenceSourceIndex < 1) {
    return null
  }
  return result.evidenceSourceIndex
}

const NARRATIVE_CONTEXT_UNITS = 3
const STRUCTURED_CONTEXT_UNITS = 6
const SHORT_TECHNOLOGY_LINE_LENGTH = 280

/**
 * Returns a compact, extractive display for simple technology-use questions.
 * It never changes a search result: it only selects text already present in its content.
 */
export function getEvidenceHighlight(
  query: string,
  result: Pick<CareerEvidenceSearchResult, 'content' | 'snippet'>,
): string {
  const snippet = result.snippet.trim()
  if (snippet === '') {
    return snippet
  }

  if (!isSimpleTechnologyQuestion(query)) {
    return selectDirectClaimWindow(query, snippet)
  }

  const units = segmentEvidenceUnits(result.content)
  const matchedIndexes = findTechnologyMatchIndexes(units, query)
  if (matchedIndexes.length === 0) {
    return snippet
  }

  if (technologyIdentifierPhrases(query).length > 1) {
    return matchedIndexes
      .slice(0, 3)
      .map((index) => shortenExtractiveUnit(units[index], meaningfulQueryTerms(query)))
      .join('\n')
  }

  const matchedIndex = matchedIndexes[0]
  const matched = shortenExtractiveUnit(units[matchedIndex], meaningfulQueryTerms(query))
  const projectIndex = findProjectHeading(units, matchedIndex)
  if (projectIndex >= 0 && projectIndex !== matchedIndex) {
    return `${units[projectIndex]}\n${matched}`
  }
  return matched
}

export function getEvidenceContext(query: string, content: string, snippet: string): string {
  const evidence = snippet.trim()
  if (evidence === '') {
    return ''
  }

  const units = segmentEvidenceUnits(content)
  const anchorIndex = isSimpleTechnologyQuestion(query)
    ? findTechnologyMatchIndexes(units, query)[0] ?? -1
    : findSnippetAnchor(units, evidence)
  if (anchorIndex < 0) {
    return evidence
  }

  const structuredDocument = isStructuredDocument(units)
  const maximumUnits = structuredDocument
    ? STRUCTURED_CONTEXT_UNITS
    : NARRATIVE_CONTEXT_UNITS
  const projectIndex = structuredDocument ? findProjectHeading(units, anchorIndex) : -1
  const start = projectIndex >= 0
    ? projectIndex
    : Math.max(0, anchorIndex - (structuredDocument ? 3 : 1))
  const end = Math.min(units.length, start + maximumUnits)

  return units
    .slice(start, end)
    .filter((unit) => !isMetadataLike(unit))
    .join('\n')
}

function segmentEvidenceUnits(value: string): string[] {
  const lines = value.split(/\r?\n/).map((line) => line.trim()).filter(Boolean)
  if (lines.length >= 2) {
    return lines
  }

  const segmenter = new Intl.Segmenter('ko', { granularity: 'sentence' })
  return Array.from(segmenter.segment(value), ({ segment }) => segment.trim()).filter(Boolean)
}

function normalizeText(value: string): string {
  return value.toLocaleLowerCase('ko').replace(/\s+/g, '')
}

function isSimpleTechnologyQuestion(query: string): boolean {
  const normalized = normalizeText(query)
  const asksForUse = /(?:사용한|사용했|사용경험|활용한|활용했|활용경험|기술스택)/.test(normalized)
  const asksForExplanation = /(?:어떻게|왜|문제|실패|복구|개선|성능|동시|설계|유지|막)/.test(normalized)
  return asksForUse && !asksForExplanation
}

function meaningfulQueryTerms(query: string): string[] {
  return Array.from(query.matchAll(/[A-Za-z][A-Za-z0-9+#._-]*/g), (match) => match[0])
    .filter((term) => term.length >= 2)
    .filter((term) => !/^(?:api|and|the|with|using|used|project|experience)$/i.test(term))
}

function containsAllQueryTerms(value: string, queryTerms: string[]): boolean {
  if (queryTerms.length === 0) {
    return false
  }
  const normalized = normalizeText(value)
  return queryTerms.every((term) => normalized.includes(normalizeText(term)))
}

function findTechnologyMatchIndexes(units: string[], query: string): number[] {
  const identifiers = technologyIdentifierPhrases(query)
  if (identifiers.length === 0) {
    return []
  }

  return identifiers
    .map((identifier) => units.findIndex((unit) => normalizeText(unit).includes(normalizeText(identifier))))
    .filter((index, position, indexes) => index >= 0 && indexes.indexOf(index) === position)
    .sort((left, right) => left - right)
}

function technologyIdentifierPhrases(query: string): string[] {
  return Array.from(
    query.matchAll(/[A-Za-z][A-Za-z0-9+#._-]*(?:\s+[A-Za-z][A-Za-z0-9+#._-]*)*/g),
    (match) => match[0].trim(),
  ).filter((phrase) => !/^(?:api|and|the|with|using|used|project|experience)$/i.test(phrase))
}

function selectDirectClaimWindow(query: string, snippet: string): string {
  const units = segmentEvidenceUnits(snippet)
  if (units.length <= 1) {
    return snippet
  }

  const numericTerms = numericQueryTerms(query)
  const identifierTerms = meaningfulQueryTerms(query)
  const anchorIndex = numericTerms.length > 0
    ? units.findIndex((unit) => containsAllQueryTerms(unit, numericTerms))
    : units.findIndex((unit) => containsAllQueryTerms(unit, identifierTerms))

  if (anchorIndex < 0) {
    return snippet
  }

  return units[anchorIndex]
}

function numericQueryTerms(query: string): string[] {
  return Array.from(query.matchAll(/\d[\d,]*(?:\.\d+)?/g), (match) => match[0])
}

function findSnippetAnchor(units: string[], snippet: string): number {
  const normalizedSnippet = normalizeText(snippet)
  return units.findIndex((unit) => {
    const normalizedUnit = normalizeText(unit)
    return normalizedSnippet.includes(normalizedUnit) || normalizedUnit.includes(normalizedSnippet)
  })
}

function findProjectHeading(units: string[], anchorIndex: number): number {
  for (let index = anchorIndex - 1; index >= Math.max(0, anchorIndex - 5); index -= 1) {
    if (isProjectHeading(units[index])) {
      return index
    }
  }
  return -1
}

function shortenExtractiveUnit(value: string, queryTerms: string[]): string {
  if (value.length <= SHORT_TECHNOLOGY_LINE_LENGTH) {
    return value
  }

  const matchedTerm = queryTerms.find((term) => normalizeText(value).includes(normalizeText(term)))
  if (matchedTerm === undefined) {
    return value
  }

  const normalizedValue = value.toLocaleLowerCase('ko')
  const start = Math.max(0, normalizedValue.indexOf(matchedTerm.toLocaleLowerCase('ko')) - 110)
  const end = Math.min(value.length, start + SHORT_TECHNOLOGY_LINE_LENGTH)
  return value.slice(start, end).trim()
}

function isProjectHeading(value: string): boolean {
  const trimmed = value.trim()
  return trimmed.length > 0
    && trimmed.length <= 180
    && !isMetadataLike(trimmed)
    && (/\|/.test(trimmed) || /(?:프로젝트|포트폴리오)/.test(trimmed))
}

function isStructuredDocument(units: string[]): boolean {
  return units.filter((unit) => /^(?:[^:|]{1,36}[:|])/.test(unit.trim())).length >= 2
}

function isMetadataLike(value: string): boolean {
  return /(?:[\w.%+-]+@[\w.-]+\.[a-z]{2,}|https?:\/\/|www\.|github\.com|(?:\+?82[- .]?)?0(?:10|2|[3-6][1-5])[- .]?\d{3,4}[- .]?\d{4})/i.test(value)
    || /^(?:contact|email|phone|github|education|gpa|name|school|major|status)\b/i.test(value.trim())
}
