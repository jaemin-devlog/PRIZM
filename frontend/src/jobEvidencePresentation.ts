import type { CareerEvidenceSearchResult } from './api/searchApi'
import { getEvidenceHighlight } from './searchEvidencePresentation.ts'

export type JobEvidenceDocumentGroup = {
  key: string
  documentId: number
  documentVersionId: number
  documentTitle: string
  results: CareerEvidenceSearchResult[]
}

function normalizedVisibleEvidence(value: string): string {
  return value.trim().replace(/\s+/gu, ' ')
}

export function visibleJobEvidenceResults(
  query: string,
  results: readonly CareerEvidenceSearchResult[],
): CareerEvidenceSearchResult[] {
  const visible: CareerEvidenceSearchResult[] = []
  const seen = new Set<string>()
  for (const result of results) {
    const identity = [
      result.documentId,
      result.documentVersionId,
      result.evidenceSourceType,
      result.evidenceSourceIndex,
      normalizedVisibleEvidence(getEvidenceHighlight(query, result)),
    ].join(':')
    if (seen.has(identity)) {
      continue
    }
    seen.add(identity)
    visible.push(result)
  }
  return visible
}

export function groupVisibleJobEvidenceByDocument(
  query: string,
  results: readonly CareerEvidenceSearchResult[],
): JobEvidenceDocumentGroup[] {
  const groups = new Map<string, JobEvidenceDocumentGroup>()
  for (const result of visibleJobEvidenceResults(query, results)) {
    const key = `${result.documentId}:${result.documentVersionId}`
    const group = groups.get(key)
    if (group === undefined) {
      groups.set(key, {
        key,
        documentId: result.documentId,
        documentVersionId: result.documentVersionId,
        documentTitle: result.documentTitle,
        results: [result],
      })
    } else {
      group.results.push(result)
    }
  }
  return [...groups.values()]
}
