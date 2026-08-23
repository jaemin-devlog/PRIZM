import assert from 'node:assert/strict'
import test from 'node:test'
import { createElement, type ReactNode } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'

import type { DocumentType } from '../src/api/documentApi.ts'
import type { CareerEvidenceSearchResult } from '../src/api/searchApi.ts'
import {
  KeywordEvidencePanel,
  type KeywordEvidencePanelProps,
} from '../src/keywordEvidencePanel.ts'
import { elementText, findElements } from './componentTestSupport.ts'

const evidence: CareerEvidenceSearchResult[] = [
  {
    chunkId: 11,
    documentId: 101,
    documentVersionId: 201,
    documentTitle: '백엔드 이력서',
    versionNo: 1,
    content: '프로젝트 소개입니다. Spring Boot 기반으로 API 서버를 구축했습니다.',
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

function panelProps(overrides: Partial<KeywordEvidencePanelProps> = {}): KeywordEvidencePanelProps {
  return {
    query: 'Spring Boot',
    state: 'result',
    results: evidence,
    documentTypes: new Map<number, DocumentType>([[101, 'RESUME'], [102, 'PROJECT_REPORT']]),
    documentTypeLabel: (type) => type === 'RESUME' ? '이력서' : '프로젝트 보고서',
    onRetry: () => undefined,
    onOpenPdf: () => undefined,
    onNavigateToDocument: () => undefined,
    ...overrides,
  }
}

test('keyword detail DOM renders multiple Search evidence cards, snippets, context, and PDF page', () => {
  const html = renderToStaticMarkup(createElement(KeywordEvidencePanel, panelProps()))

  assert.match(html, /Spring Boot 관련 기록/)
  assert.match(html, /2건 · 2개 문서/)
  assert.match(html, /백엔드 이력서/)
  assert.match(html, /프로젝트 기록/)
  assert.match(html, /Spring Boot 기반으로 API 서버를 구축했습니다/)
  assert.match(html, /Spring Boot 환경에서 배포 자동화를 개선했습니다/)
  assert.match(html, /2페이지/)
  assert.match(html, /주변 내용 보기/)
})

test('keyword detail document actions open a PDF page or owner-scoped TXT document detail', () => {
  const openedPages: number[] = []
  const navigatedDocuments: number[] = []
  const tree = KeywordEvidencePanel(panelProps({
    onOpenPdf: (target) => openedPages.push(target.pageNumber),
    onNavigateToDocument: (documentId) => navigatedDocuments.push(documentId),
  })) as ReactNode
  const documentButtons = findElements(
    tree,
    (element) => typeof element.props?.onClick === 'function'
      && elementText(element.props?.children) === '문서에서 보기',
  )
  assert.equal(documentButtons.length, 2)
  for (const button of documentButtons) {
    ;(button.props?.onClick as () => void)()
  }

  assert.deepEqual(openedPages, [2])
  assert.deepEqual(navigatedDocuments, [102])
})

test('keyword detail DOM distinguishes loading, empty, and error with a working retry action', () => {
  const loading = renderToStaticMarkup(createElement(KeywordEvidencePanel, panelProps({ state: 'loading', results: [] })))
  const empty = renderToStaticMarkup(createElement(KeywordEvidencePanel, panelProps({ state: 'empty', results: [] })))
  let retries = 0
  const errorTree = KeywordEvidencePanel(panelProps({
    state: 'error',
    results: [],
    onRetry: () => { retries += 1 },
  })) as ReactNode
  const error = renderToStaticMarkup(errorTree)
  const retryButton = findElements(
    errorTree,
    (element) => typeof element.props?.onClick === 'function'
      && elementText(element.props?.children) === '다시 시도',
  )[0]

  assert.match(loading, /관련 내용을 찾는 중입니다/)
  assert.match(empty, /관련된 내용을 문서에서 찾지 못했습니다/)
  assert.match(error, /관련 내용을 불러오지 못했습니다/)
  assert.ok(retryButton)
  ;(retryButton.props?.onClick as () => void)()
  assert.equal(retries, 1)
})
