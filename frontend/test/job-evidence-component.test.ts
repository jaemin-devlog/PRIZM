import assert from 'node:assert/strict'
import test, { after } from 'node:test'
import { fileURLToPath } from 'node:url'
import { createElement, type ReactNode } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { createServer } from 'vite'

import type { DocumentType } from '../src/api/documentApi.ts'
import type { JobPostingItem } from '../src/api/jobPostingApi.ts'
import type { CareerEvidenceSearchResult } from '../src/api/searchApi.ts'
import {
  JobEvidencePanel,
  type JobEvidencePanelProps,
} from '../src/jobEvidencePanel.ts'
import type { JobEvidenceGroup } from '../src/jobEvidence.ts'
import {
  groupVisibleJobEvidenceByDocument,
  visibleJobEvidenceResults,
} from '../src/jobEvidencePresentation.ts'
import { elementText, findElements } from './componentTestSupport.ts'

const viteServer = await createServer({
  root: fileURLToPath(new URL('..', import.meta.url)),
  appType: 'custom',
  logLevel: 'silent',
  server: { middlewareMode: true },
})
const {
  JobActiveEvidence,
  JobEvidenceResultsView,
  JobRequirementSelectionModal,
} = await viteServer.ssrLoadModule('/src/jobEvidenceWorkspace.tsx')

after(async () => {
  await viteServer.close()
})

const items: JobPostingItem[] = [
  { itemId: 1, section: '자격요건', text: '서버 애플리케이션 개발 경험' },
  { itemId: 2, section: '우대사항', text: '캐시 운영 경험' },
]

const pdfEvidence: CareerEvidenceSearchResult = {
  chunkId: 11,
  documentId: 101,
  documentVersionId: 201,
  documentTitle: '백엔드 이력서',
  versionNo: 1,
  content: '서버 애플리케이션을 개발하고 운영했습니다.',
  snippet: '서버 애플리케이션을 개발하고 운영했습니다.',
  sourceType: 'PAGE',
  sourceIndex: 2,
  sourceLabel: '2페이지',
  evidenceChunkId: 11,
  evidenceSourceType: 'PAGE',
  evidenceSourceIndex: 2,
  evidenceSourceLabel: '2페이지',
  distance: 0.3,
  score: 0.7,
}

const samePageDifferentEvidence: CareerEvidenceSearchResult = {
  ...pdfEvidence,
  chunkId: 13,
  evidenceChunkId: 13,
  content: '같은 페이지에서 API 경계를 설계했습니다.',
  snippet: '같은 페이지에서 API 경계를 설계했습니다.',
}

const sameTextOtherPageEvidence: CareerEvidenceSearchResult = {
  ...pdfEvidence,
  chunkId: 14,
  evidenceChunkId: 14,
  sourceIndex: 1,
  sourceLabel: '1페이지',
  evidenceSourceIndex: 1,
  evidenceSourceLabel: '1페이지',
}

const textEvidence: CareerEvidenceSearchResult = {
  ...pdfEvidence,
  chunkId: 12,
  documentId: 102,
  documentVersionId: 202,
  documentTitle: '프로젝트 기록',
  content: '캐시 만료 정책을 운영했습니다.',
  snippet: '캐시 만료 정책을 운영했습니다.',
  sourceType: 'TEXT_CHUNK',
  sourceIndex: 3,
  sourceLabel: '텍스트 구간 3',
  evidenceChunkId: 12,
  evidenceSourceType: 'TEXT_CHUNK',
  evidenceSourceIndex: 3,
  evidenceSourceLabel: '텍스트 구간 3',
}

function resultGroup(item: JobPostingItem, results: CareerEvidenceSearchResult[]): JobEvidenceGroup {
  return { item, state: results.length === 0 ? 'empty' : 'result', results, error: null }
}

function panelProps(overrides: Partial<JobEvidencePanelProps> = {}): JobEvidencePanelProps {
  return {
    content: '자격요건\n- 서버 애플리케이션 개발 경험\n우대사항\n- 캐시 운영 경험',
    itemCount: 2,
    selectedCount: 2,
    segmentationState: 'ready',
    onContentChange: () => undefined,
    onSegment: () => undefined,
    onOpenSelection: () => undefined,
    ...overrides,
  }
}

function modalProps(overrides: Record<string, unknown> = {}) {
  return {
    items,
    selectedItemIds: new Set([1, 2]),
    onToggleItem: () => undefined,
    onSelectAll: () => undefined,
    onClearAll: () => undefined,
    onSearch: () => undefined,
    onClose: () => undefined,
    ...overrides,
  }
}

function resultsViewProps(overrides: Record<string, unknown> = {}) {
  return {
    groups: [],
    documentTypes: new Map<number, DocumentType>([[101, 'RESUME'], [102, 'PROJECT_REPORT']]),
    documentTypeLabel: (type) => type === 'RESUME' ? '이력서' : '프로젝트 보고서',
    activeItemId: null,
    onSelectItem: () => undefined,
    activeFilter: 'found',
    onSelectFilter: () => undefined,
    onEditPosting: () => undefined,
    onEditSelection: () => undefined,
    onRetry: () => undefined,
    onOpenPdf: () => undefined,
    onNavigateToDocument: () => undefined,
    ...overrides,
  }
}

test('job posting editor keeps segmentation actions while selection stays outside the page flow', () => {
  const changedValues: string[] = []
  let segmentRequests = 0
  let openSelectionRequests = 0
  const tree = JobEvidencePanel(panelProps({
    onContentChange: (value) => changedValues.push(value),
    onSegment: () => { segmentRequests += 1 },
    onOpenSelection: () => { openSelectionRequests += 1 },
  })) as ReactNode
  const html = renderToStaticMarkup(tree)
  const textarea = findElements(tree, (element) => element.props?.name === 'content')[0]
  const segmentForm = findElements(tree, (element) => typeof element.props?.onSubmit === 'function')[0]
  const openSelection = findElements(
    tree,
    (element) => element.type === 'button'
      && elementText(element.props?.children) === '선택 항목 보기',
  )[0]

  assert.match(html, /채용공고 붙여넣기/)
  assert.match(html, /2개의 검색 가능 항목/)
  assert.match(html, /2개 선택됨/)
  assert.doesNotMatch(html, /job-requirement-modal-panel|type="checkbox"|채용공고 관련 경력/)
  assert.ok(textarea)
  assert.ok(segmentForm)
  assert.ok(openSelection)
  ;(textarea.props?.onChange as (event: { target: { value: string } }) => void)({
    target: { value: '수정한 채용공고' },
  })
  ;(segmentForm.props?.onSubmit as (event: { preventDefault: () => void }) => void)({
    preventDefault: () => undefined,
  })
  ;(openSelection.props?.onClick as () => void)()
  assert.deepEqual(changedValues, ['수정한 채용공고'])
  assert.equal(segmentRequests, 1)
  assert.equal(openSelectionRequests, 1)
})

test('segmented items render in an accessible sectioned selection dialog', () => {
  const html = renderToStaticMarkup(createElement(
    JobRequirementSelectionModal,
    modalProps({ selectedItemIds: new Set([1]) }),
  ))

  assert.match(html, /role="dialog"/)
  assert.match(html, /aria-modal="true"/)
  assert.match(html, /aria-labelledby="job-requirement-modal-title"/)
  assert.match(html, /자격요건/)
  assert.match(html, /우대사항/)
  assert.match(html, /2개 중 1개 선택/)
  assert.equal((html.match(/type="checkbox"/g) ?? []).length, 2)
  assert.equal((html.match(/job-requirement-section-title/g) ?? []).length, 2)
  assert.match(html, /선택한 1개 항목에서 관련 경력 찾기/)
})

test('zero selected items disable Search in the selection dialog', () => {
  const html = renderToStaticMarkup(createElement(
    JobRequirementSelectionModal,
    modalProps({ selectedItemIds: new Set() }),
  ))

  assert.match(html, /2개 중 0개 선택/)
  assert.match(html, /disabled=""[^>]*>선택한 0개 항목에서 관련 경력 찾기/)
})

test('presentation dedup only collapses the same document version, source location, and visible text', () => {
  const exactVisualDuplicate = {
    ...pdfEvidence,
    chunkId: 99,
    evidenceChunkId: 99,
  }
  const sameTextOtherVersion = {
    ...pdfEvidence,
    chunkId: 98,
    evidenceChunkId: 98,
    documentVersionId: 999,
  }
  const sameTextOtherDocument = {
    ...pdfEvidence,
    chunkId: 97,
    evidenceChunkId: 97,
    documentId: 103,
    documentVersionId: 203,
    documentTitle: '다른 이력서',
  }
  const original = [
    pdfEvidence,
    exactVisualDuplicate,
    samePageDifferentEvidence,
    sameTextOtherPageEvidence,
    sameTextOtherVersion,
    sameTextOtherDocument,
    textEvidence,
  ]

  const visible = visibleJobEvidenceResults(items[0].text, original)
  const grouped = groupVisibleJobEvidenceByDocument(items[0].text, original)

  assert.deepEqual(visible.map((result) => result.chunkId), [11, 13, 14, 98, 97, 12])
  assert.deepEqual(grouped.map((group) => [group.key, group.results.map((result) => result.chunkId)]), [
    ['101:201', [11, 13, 14]],
    ['101:999', [98]],
    ['103:203', [97]],
    ['102:202', [12]],
  ])
  assert.equal(original.length, 7)
})

test('results workspace keeps requirement order and renders one active document-grouped detail', () => {
  const exactVisualDuplicate = {
    ...pdfEvidence,
    chunkId: 99,
    evidenceChunkId: 99,
  }
  const errorGroup: JobEvidenceGroup = {
    item: { itemId: 3, section: null, text: '장애 대응 경험' },
    state: 'error',
    results: [],
    error: { status: 403 },
  }
  const groups: JobEvidenceGroup[] = [
    resultGroup(items[0], [
      pdfEvidence,
      exactVisualDuplicate,
      samePageDifferentEvidence,
      sameTextOtherPageEvidence,
      textEvidence,
    ]),
    resultGroup(items[1], []),
    errorGroup,
  ]
  const html = renderToStaticMarkup(createElement(JobEvidenceResultsView, resultsViewProps({ groups })))

  assert.match(html, /id="job-evidence-title" tabindex="-1"/)
  assert.match(html, /aria-pressed="true"/)
  assert.match(html, /기록 있음<\/span><strong>1<\/strong>/)
  assert.match(html, /기록 없음<\/span><strong>1<\/strong>/)
  assert.match(html, /확인 필요<\/span><strong>1<\/strong>/)
  assert.doesNotMatch(html, new RegExp(items[1].text))
  assert.doesNotMatch(html, new RegExp(errorGroup.item.text))
  assert.match(html, /기록 4건/)
  assert.match(html, /백엔드 이력서/)
  assert.match(html, /이력서 · 관련 원문 3건/)
  assert.match(html, /프로젝트 기록/)
  assert.match(html, /프로젝트 보고서 · 관련 원문 1건/)
  assert.equal((html.match(/<h3>백엔드 이력서<\/h3>/g) ?? []).length, 1)
  assert.equal((html.match(/job-document-evidence-row/g) ?? []).length, 4)
  assert.doesNotMatch(html, /관련 기록 0[1-9]/)
  assert.doesNotMatch(html, /적합도|합격 가능성|충족|불충족|PASS|FAIL|score|distance/)
})

test('requirement navigator changes the active item without issuing Search', () => {
  const selectedItems: number[] = []
  const groups = [resultGroup(items[0], [pdfEvidence]), resultGroup(items[1], [textEvidence])]
  const tree = JobEvidenceResultsView(resultsViewProps({
    groups,
    activeItemId: items[0].itemId,
    onSelectItem: (itemId) => selectedItems.push(itemId),
  })) as ReactNode
  const navigationButtons = findElements(
    tree,
    (element) => typeof element.props?.className === 'string'
      && element.props.className.includes('job-requirement-navigation-item'),
  )

  assert.equal(navigationButtons.length, 2)
  ;(navigationButtons[1]?.props?.onClick as () => void)()
  assert.deepEqual(selectedItems, [2])

  const emptyGroups = [resultGroup(items[0], [pdfEvidence]), resultGroup(items[1], [])]
  const emptyHtml = renderToStaticMarkup(createElement(JobEvidenceResultsView, resultsViewProps({
    groups: emptyGroups,
    activeItemId: items[1].itemId,
    activeFilter: 'empty',
  })))
  assert.match(emptyHtml, /관련 경력 기록을 찾지 못했습니다/)
  assert.doesNotMatch(emptyHtml, /job-document-group-heading/)
})

test('result status tabs separate found, empty, and unresolved requirements without losing source order', () => {
  const selectedFilters: string[] = []
  const errorGroup: JobEvidenceGroup = {
    item: { itemId: 3, section: null, text: '장애 대응 경험' },
    state: 'error',
    results: [],
    error: { status: 503 },
  }
  const laterFoundGroup = resultGroup(
    { itemId: 4, section: null, text: '클라우드 운영 경험' },
    [textEvidence],
  )
  const groups = [
    resultGroup(items[0], [pdfEvidence]),
    resultGroup(items[1], []),
    errorGroup,
    laterFoundGroup,
  ]
  const foundTree = JobEvidenceResultsView(resultsViewProps({
    groups,
    onSelectFilter: (filter: string) => selectedFilters.push(filter),
  })) as ReactNode
  const foundHtml = renderToStaticMarkup(foundTree)
  const filterButtons = findElements(
    foundTree,
    (element) => element.type === 'button'
      && typeof element.props?.className === 'string'
      && element.props.className.includes('job-requirement-filter-tab'),
  )

  assert.equal(filterButtons.length, 3)
  assert.match(foundHtml, /기록 있음<\/span><strong>2<\/strong>/)
  assert.match(foundHtml, /기록 없음<\/span><strong>1<\/strong>/)
  assert.match(foundHtml, /확인 필요<\/span><strong>1<\/strong>/)
  assert.match(foundHtml, /job-requirement-navigation-index">01<\/span>/)
  assert.match(foundHtml, /job-requirement-navigation-index">04<\/span>/)
  assert.doesNotMatch(foundHtml, new RegExp(items[1].text))
  assert.doesNotMatch(foundHtml, new RegExp(errorGroup.item.text))

  ;(filterButtons[1]?.props?.onClick as () => void)()
  ;(filterButtons[2]?.props?.onClick as () => void)()
  assert.deepEqual(selectedFilters, ['empty', 'pending'])

  const emptyHtml = renderToStaticMarkup(createElement(JobEvidenceResultsView, resultsViewProps({
    groups,
    activeFilter: 'empty',
  })))
  assert.match(emptyHtml, /job-requirement-navigation-index">02<\/span>/)
  assert.match(emptyHtml, /관련 경력 기록을 찾지 못했습니다/)
  assert.doesNotMatch(emptyHtml, new RegExp(items[0].text))

  const pendingHtml = renderToStaticMarkup(createElement(JobEvidenceResultsView, resultsViewProps({
    groups,
    activeFilter: 'pending',
  })))
  assert.match(pendingHtml, /job-requirement-navigation-index">03<\/span>/)
  assert.match(pendingHtml, /관련 경력 기록을 불러오지 못했습니다/)
  assert.doesNotMatch(pendingHtml, /관련 경력 기록을 찾지 못했습니다/)
})

test('active Evidence rows preserve PDF page, TXT document, context, and retry callbacks', () => {
  const openedPages: number[] = []
  const navigatedDocuments: number[] = []
  const retriedItems: number[] = []
  const resultTree = JobActiveEvidence({
    group: resultGroup(items[0], [pdfEvidence, textEvidence]),
    documentTypes: new Map<number, DocumentType>([[101, 'RESUME'], [102, 'PROJECT_REPORT']]),
    documentTypeLabel: (type) => type === 'RESUME' ? '이력서' : '프로젝트 보고서',
    onRetry: (itemId) => retriedItems.push(itemId),
    onOpenPdf: (target) => openedPages.push(target.pageNumber),
    onNavigateToDocument: (documentId) => navigatedDocuments.push(documentId),
  }) as ReactNode
  const documentButtons = findElements(
    resultTree,
    (element) => typeof element.props?.onClick === 'function'
      && ['2페이지에서 보기', '문서에서 보기'].includes(elementText(element.props?.children)),
  )
  assert.equal(documentButtons.length, 2)
  for (const button of documentButtons) {
    ;(button.props?.onClick as () => void)()
  }

  const errorTree = JobActiveEvidence({
    group: { item: items[1], state: 'error', results: [], error: { status: 403 } },
    documentTypes: new Map(),
    documentTypeLabel: () => '문서',
    onRetry: (itemId) => retriedItems.push(itemId),
    onOpenPdf: () => undefined,
    onNavigateToDocument: () => undefined,
  }) as ReactNode
  const retry = findElements(
    errorTree,
    (element) => elementText(element.props?.children) === '다시 시도',
  )[0]
  ;(retry?.props?.onClick as () => void)()

  assert.deepEqual(openedPages, [2])
  assert.deepEqual(navigatedDocuments, [102])
  assert.deepEqual(retriedItems, [2])
  assert.match(renderToStaticMarkup(resultTree), /주변 내용 보기/)
})

test('compound Search results keep one original requirement in the workspace', () => {
  const compoundItem: JobPostingItem = {
    itemId: 10,
    section: '우대사항',
    text: 'Docker, Kubernetes 또는 Cloud 환경 사용 경험',
  }
  const html = renderToStaticMarkup(createElement(JobEvidenceResultsView, resultsViewProps({
    groups: [resultGroup(compoundItem, [pdfEvidence])],
  })))

  assert.match(html, /Docker, Kubernetes 또는 Cloud 환경 사용 경험/)
  assert.doesNotMatch(html, />Docker 관련 기록<|>Kubernetes 관련 기록<|>Cloud 환경 사용 경험 관련 기록</)
})
