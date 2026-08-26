import type { JobEvidenceCandidate } from './jobEvidence.ts'
import { getEvidenceContext, getEvidenceHighlight } from './searchEvidencePresentation.ts'

const MAX_JOB_EVIDENCE_PREVIEW_LENGTH = 360

export type JobEvidenceDocumentGroup = {
  key: string
  documentId: number
  documentVersionId: number
  documentTitle: string
  versionNo: number
  candidates: JobEvidenceCandidate[]
}

function normalizedVisibleEvidence(value: string): string {
  return value.trim().replace(/\s+/gu, ' ')
}

export function visibleJobEvidenceResults(
  candidates: readonly JobEvidenceCandidate[],
): JobEvidenceCandidate[] {
  const visible: JobEvidenceCandidate[] = []
  const seen = new Set<string>()
  for (const candidate of candidates) {
    const { result } = candidate
    const identity = [
      result.documentId,
      result.documentVersionId,
      result.evidenceSourceType,
      result.evidenceSourceIndex,
      normalizedVisibleEvidence(getJobEvidenceHighlight(candidate)),
    ].join(':')
    if (seen.has(identity)) {
      continue
    }
    seen.add(identity)
    visible.push(candidate)
  }
  return visible
}

export function groupVisibleJobEvidenceByDocument(
  candidates: readonly JobEvidenceCandidate[],
): JobEvidenceDocumentGroup[] {
  const groups = new Map<string, JobEvidenceDocumentGroup>()
  for (const candidate of visibleJobEvidenceResults(candidates)) {
    const { result } = candidate
    const key = `${result.documentId}:${result.documentVersionId}`
    const group = groups.get(key)
    if (group === undefined) {
      groups.set(key, {
        key,
        documentId: result.documentId,
        documentVersionId: result.documentVersionId,
        documentTitle: result.documentTitle,
        versionNo: result.versionNo,
        candidates: [candidate],
      })
    } else {
      group.candidates.push(candidate)
    }
  }
  return [...groups.values()]
}

export function getJobEvidenceHighlight(candidate: JobEvidenceCandidate): string {
  const highlight = getEvidenceHighlight(displayAnchorQuery(candidate), candidate.result)
  if (highlight.length <= MAX_JOB_EVIDENCE_PREVIEW_LENGTH) {
    return highlight
  }

  const context = getJobEvidenceContext(candidate).trim()
  return context !== '' && context.length < highlight.length
    ? context
    : highlight
}

export function getJobEvidenceContext(candidate: JobEvidenceCandidate): string {
  return getEvidenceContext(
    displayAnchorQuery(candidate),
    candidate.result.content,
    candidate.result.snippet,
  )
}

export function hasAdditionalJobEvidenceContext(candidate: JobEvidenceCandidate): boolean {
  const preview = normalizedVisibleEvidence(getJobEvidenceHighlight(candidate))
  const context = normalizedVisibleEvidence(getJobEvidenceContext(candidate))
  if (preview === '' || !context.includes(preview)) {
    return false
  }

  return context.length > preview.length
    || preview.length > MAX_JOB_EVIDENCE_PREVIEW_LENGTH
}

function displayAnchorQuery(candidate: JobEvidenceCandidate): string {
  return candidate.displayQueryIsDirectIdentifier
    ? `${candidate.displayQuery} 사용 경험`
    : candidate.displayQuery
}
