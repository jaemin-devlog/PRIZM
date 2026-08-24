import { createElement } from 'react'

import type { DocumentType } from './api/documentApi'
import type { CareerEvidenceSearchResult } from './api/searchApi'
import { relatedEvidenceDocumentCount } from './keywordEvidence.ts'
import {
  getEvidenceContext,
  getEvidenceHighlight,
  getEvidencePdfPage,
  getEvidenceSourceLabel,
} from './searchEvidencePresentation.ts'

export type EvidencePdfViewerTarget = {
  documentId: number
  documentVersionId: number
  documentTitle: string
  pageNumber: number
}

export type KeywordEvidenceState = 'loading' | 'result' | 'empty' | 'error'

export function getEvidencePdfViewerTarget(
  result: CareerEvidenceSearchResult,
): EvidencePdfViewerTarget | null {
  const pageNumber = getEvidencePdfPage(result)
  if (pageNumber === null) {
    return null
  }
  return {
    documentId: result.documentId,
    documentVersionId: result.documentVersionId,
    documentTitle: result.documentTitle,
    pageNumber,
  }
}

export type KeywordEvidencePanelProps = {
  headingId?: string
  query: string
  state: KeywordEvidenceState
  results: CareerEvidenceSearchResult[]
  documentTypes: Map<number, DocumentType>
  documentTypeLabel: (documentType: DocumentType) => string
  emptyMessage?: string
  onRetry: () => void
  onOpenPdf: (target: EvidencePdfViewerTarget) => void
  onNavigateToDocument: (documentId: number) => void
}

export function KeywordEvidencePanel({
  headingId = 'tag-evidence-title',
  query,
  state,
  results,
  documentTypes,
  documentTypeLabel,
  emptyMessage = '이 키워드와 관련된 내용을 문서에서 찾지 못했습니다.',
  onRetry,
  onOpenPdf,
  onNavigateToDocument,
}: KeywordEvidencePanelProps) {
  return createElement(
    'section',
    { className: 'keyword-evidence-panel', 'aria-labelledby': headingId },
    createElement(
      'header',
      { className: 'keyword-panel-heading' },
      createElement(
        'div',
        null,
        createElement('p', { className: 'section-kicker' }, 'EVIDENCE RETRIEVAL'),
        createElement('h2', { id: headingId }, `${query || '키워드'} 관련 기록`),
      ),
      state === 'result'
        ? createElement('span', null, `${results.length}건 · ${relatedEvidenceDocumentCount(results)}개 문서`)
        : null,
    ),
    createElement(
      'div',
      { className: 'keyword-evidence-content', 'aria-live': 'polite', 'aria-busy': state === 'loading' },
      state === 'loading'
        ? createElement(
          'p',
          { className: 'keyword-state' },
          createElement('span', { className: 'state-spinner', 'aria-hidden': true }),
          '관련 내용을 찾는 중입니다.',
        )
        : null,
      state === 'empty'
        ? createElement('p', { className: 'keyword-state' }, emptyMessage)
        : null,
      state === 'error'
        ? createElement(
          'div',
          { className: 'keyword-state', role: 'alert' },
          createElement('p', null, '관련 내용을 불러오지 못했습니다.'),
          createElement('button', {
            type: 'button',
            className: 'secondary-button',
            onClick: onRetry,
          }, '다시 시도'),
        )
        : null,
      state === 'result'
        ? createElement(
          'ol',
          { className: 'keyword-evidence-list' },
          ...results.map((result, index) => {
            const page = getEvidencePdfPage(result)
            const viewerTarget = getEvidencePdfViewerTarget(result)
            const documentType = documentTypes.get(result.documentId)
            return createElement(
              'li',
              { key: result.chunkId },
              createElement(
                'article',
                { className: 'keyword-evidence-card' },
                createElement(
                  'header',
                  null,
                  createElement(
                    'div',
                    null,
                    createElement('span', null, `관련 기록 ${String(index + 1).padStart(2, '0')}`),
                    createElement('h3', null, result.documentTitle),
                    createElement(
                      'small',
                      null,
                      `${documentType === undefined ? '문서' : documentTypeLabel(documentType)} · ${getEvidenceSourceLabel(result)}`,
                    ),
                  ),
                  page === null ? null : createElement('strong', null, `${page}페이지`),
                ),
                createElement('p', { className: 'keyword-context-label' }, '관련 원문'),
                createElement('blockquote', null, getEvidenceHighlight(query, result)),
                createElement(
                  'div',
                  { className: 'keyword-evidence-actions' },
                  createElement('button', {
                    type: 'button',
                    className: 'keyword-document-button',
                    onClick: () => viewerTarget === null
                      ? onNavigateToDocument(result.documentId)
                      : onOpenPdf(viewerTarget),
                  }, '문서에서 보기'),
                  createElement(
                    'details',
                    { className: 'keyword-context-details' },
                    createElement(
                      'summary',
                      null,
                      createElement('span', { className: 'keyword-context-open-label' }, '주변 내용 보기'),
                      createElement('span', { className: 'keyword-context-close-label' }, '주변 내용 닫기'),
                    ),
                    createElement(
                      'div',
                      { className: 'search-result-document-reader', role: 'document' },
                      getEvidenceContext(query, result.content, result.snippet),
                    ),
                  ),
                ),
              ),
            )
          }),
        )
        : null,
    ),
  )
}
