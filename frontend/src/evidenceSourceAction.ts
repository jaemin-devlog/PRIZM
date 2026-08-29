import { createElement } from 'react'

import type { CareerEvidenceSearchResult } from './api/searchApi'
import {
  getEvidencePdfViewerTarget,
  type EvidencePdfViewerTarget,
} from './keywordEvidencePanel.ts'

export type EvidenceDocumentDetailTarget = {
  documentId: number
  documentVersionId: number
}

export type EvidenceSourceActionProps = {
  result: CareerEvidenceSearchResult
  onOpenPdf: (target: EvidencePdfViewerTarget) => void
  onNavigateToDocument: (target: EvidenceDocumentDetailTarget) => void
}

export function EvidenceSourceAction({
  result,
  onOpenPdf,
  onNavigateToDocument,
}: EvidenceSourceActionProps) {
  const pdfTarget = getEvidencePdfViewerTarget(result)
  if (result.evidenceSourceType === 'PAGE' && pdfTarget === null) {
    return null
  }
  const detailTarget = {
    documentId: result.documentId,
    documentVersionId: result.documentVersionId,
  }

  return createElement('button', {
    type: 'button',
    className: 'search-result-document-button',
    onClick: () => result.evidenceSourceType === 'TEXT_CHUNK'
      ? onNavigateToDocument(detailTarget)
      : onOpenPdf(pdfTarget!),
    'aria-label': result.evidenceSourceType === 'TEXT_CHUNK'
      ? `${result.documentTitle} ${result.versionNo}버전 문서 상세에서 보기`
      : `${result.documentTitle} ${pdfTarget!.pageNumber}페이지에서 보기`,
  }, '문서에서 보기')
}
