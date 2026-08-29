import assert from 'node:assert/strict'
import test from 'node:test'
import type { ReactNode } from 'react'

import type { CareerEvidenceSearchResult } from '../src/api/searchApi.ts'
import { EvidenceSourceAction } from '../src/evidenceSourceAction.ts'
import { elementText, findElements } from './componentTestSupport.ts'

function result(
  evidenceSourceType: 'TEXT_CHUNK' | 'PAGE',
  evidenceSourceIndex: number,
): CareerEvidenceSearchResult {
  return {
    chunkId: evidenceSourceType === 'PAGE' ? 11 : 12,
    documentId: evidenceSourceType === 'PAGE' ? 101 : 102,
    documentVersionId: evidenceSourceType === 'PAGE' ? 201 : 202,
    documentTitle: evidenceSourceType === 'PAGE' ? 'PDF 포트폴리오' : 'TXT 프로젝트 기록',
    versionNo: 3,
    content: '검증할 합성 원문입니다.',
    snippet: '검증할 합성 원문입니다.',
    sourceType: evidenceSourceType,
    sourceIndex: evidenceSourceIndex,
    sourceLabel: evidenceSourceType === 'PAGE' ? '2페이지' : '텍스트 구간 1',
    evidenceChunkId: evidenceSourceType === 'PAGE' ? 11 : 12,
    evidenceSourceType,
    evidenceSourceIndex,
    evidenceSourceLabel: evidenceSourceType === 'PAGE' ? '2페이지' : '텍스트 구간 1',
    distance: 0.2,
    score: 0.8,
  }
}

test('PAGE action opens the exact PDF version and evidence page without document navigation', () => {
  const opened: unknown[] = []
  const navigated: unknown[] = []
  const tree = EvidenceSourceAction({
    result: result('PAGE', 2),
    onOpenPdf: (target) => opened.push(target),
    onNavigateToDocument: (target) => navigated.push(target),
  }) as ReactNode
  const button = findElements(
    tree,
    (element) => elementText(element.props?.children) === '문서에서 보기',
  )[0]

  assert.ok(button)
  ;(button.props?.onClick as () => void)()
  assert.deepEqual(opened, [{
    documentId: 101,
    documentVersionId: 201,
    documentTitle: 'PDF 포트폴리오',
    pageNumber: 2,
  }])
  assert.deepEqual(navigated, [])
})

test('TEXT_CHUNK action navigates to the exact document and version without opening PDF', () => {
  const opened: unknown[] = []
  const navigated: unknown[] = []
  const tree = EvidenceSourceAction({
    result: result('TEXT_CHUNK', 1),
    onOpenPdf: (target) => opened.push(target),
    onNavigateToDocument: (target) => navigated.push(target),
  }) as ReactNode
  const button = findElements(
    tree,
    (element) => elementText(element.props?.children) === '문서에서 보기',
  )[0]

  assert.ok(button)
  assert.equal(button.props?.['aria-label'], 'TXT 프로젝트 기록 3버전 문서 상세에서 보기')
  ;(button.props?.onClick as () => void)()
  assert.deepEqual(opened, [])
  assert.deepEqual(navigated, [{ documentId: 102, documentVersionId: 202 }])
})

test('invalid PAGE source keeps the prior no-action behavior', () => {
  const opened: unknown[] = []
  const navigated: unknown[] = []
  const tree = EvidenceSourceAction({
    result: result('PAGE', 0),
    onOpenPdf: (target) => opened.push(target),
    onNavigateToDocument: (target) => navigated.push(target),
  })

  assert.equal(tree, null)
  assert.deepEqual(opened, [])
  assert.deepEqual(navigated, [])
})
