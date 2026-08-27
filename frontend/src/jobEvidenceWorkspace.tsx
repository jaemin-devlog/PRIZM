import {
  type KeyboardEvent,
  type RefObject,
  useEffect,
  useRef,
  useState,
} from 'react'

import type { DocumentType } from './api/documentApi'
import type { JobPostingItem } from './api/jobPostingApi'
import { groupJobPostingItems, type JobEvidenceGroup } from './jobEvidence'
import {
  getJobEvidenceContext,
  getJobEvidenceHighlight,
  groupVisibleJobEvidenceByDocument,
  hasAdditionalJobEvidenceContext,
  type JobEvidenceDocumentGroup,
  visibleJobEvidenceResults,
} from './jobEvidencePresentation'
import {
  getEvidencePdfViewerTarget,
  type EvidencePdfViewerTarget,
} from './keywordEvidencePanel'
import { focusModalEntry, keepFocusWithinModal, restoreModalTrigger } from './modalFocus'
import {
  getEvidencePdfPage,
  getEvidenceSourceLabel,
} from './searchEvidencePresentation'

export type JobRequirementSelectionModalProps = {
  items: JobPostingItem[]
  selectedItemIds: ReadonlySet<number>
  onToggleItem: (itemId: number) => void
  onSelectAll: () => void
  onClearAll: () => void
  onSearch: () => void
  onClose: () => void
}

export function JobRequirementSelectionModal({
  items,
  selectedItemIds,
  onToggleItem,
  onSelectAll,
  onClearAll,
  onSearch,
  onClose,
}: JobRequirementSelectionModalProps) {
  const panelRef = useRef<HTMLElement>(null)
  const returnFocusRef = useRef<HTMLElement | null>(
    typeof document !== 'undefined' && document.activeElement instanceof HTMLElement
      ? document.activeElement
      : null,
  )
  const selectedCount = items.filter((item) => selectedItemIds.has(item.itemId)).length
  const itemGroups = groupJobPostingItems(items)

  useEffect(() => {
    const previousOverflow = document.body.style.overflow
    const returnFocusTarget = returnFocusRef.current
    document.body.style.overflow = 'hidden'
    focusModalEntry(panelRef.current)
    return () => {
      document.body.style.overflow = previousOverflow
      restoreModalTrigger(returnFocusTarget)
    }
  }, [])

  const handleKeyDown = (event: KeyboardEvent<HTMLElement>) => {
    if (event.key === 'Escape') {
      event.stopPropagation()
      onClose()
      return
    }
    if (event.key !== 'Tab' || panelRef.current === null) {
      return
    }
    const focusableTargets = Array.from(panelRef.current.querySelectorAll<HTMLElement>(
      'button:not(:disabled), input:not(:disabled), [href], [tabindex]:not([tabindex="-1"])',
    ))
    if (focusableTargets.length === 0) {
      panelRef.current.focus()
      event.preventDefault()
      return
    }
    if (keepFocusWithinModal(focusableTargets, document.activeElement, event.shiftKey)) {
      event.preventDefault()
    }
  }

  return (
    <div className="job-requirement-modal-layer">
      <button
        type="button"
        className="job-requirement-modal-backdrop"
        aria-label="항목 선택 닫기"
        onClick={onClose}
      />
      <section
        ref={panelRef}
        className="job-requirement-modal-panel"
        role="dialog"
        aria-modal="true"
        aria-labelledby="job-requirement-modal-title"
        tabIndex={-1}
        onKeyDown={handleKeyDown}
      >
        <header className="job-requirement-modal-heading">
          <div>
            <p className="section-kicker">SELECT REQUIREMENTS</p>
            <h2 id="job-requirement-modal-title">검색할 채용공고 항목 선택</h2>
            <p>section 제목은 문맥으로만 보여주며, 실제 내용 문장만 선택할 수 있습니다.</p>
          </div>
          <button type="button" className="job-requirement-modal-close" onClick={onClose}>
            닫기
          </button>
        </header>

        <div className="job-requirement-modal-toolbar">
          <strong className="job-selection-count" role="status">
            {items.length}개 중 {selectedCount}개 선택
          </strong>
          <div className="job-selection-actions">
            <button
              type="button"
              className="secondary-button"
              disabled={selectedCount === items.length}
              onClick={onSelectAll}
            >
              전체 선택
            </button>
            <button
              type="button"
              className="secondary-button"
              disabled={selectedCount === 0}
              onClick={onClearAll}
            >
              전체 해제
            </button>
          </div>
        </div>

        <div className="job-requirement-modal-body">
          {items.length === 0 ? (
            <p className="job-requirement-empty">나눌 수 있는 채용공고 항목을 찾지 못했습니다.</p>
          ) : (
            <div className="job-requirement-groups">
              {itemGroups.map((group) => {
                const firstItem = group.items[0]
                const headingId = group.section === null || firstItem === undefined
                  ? undefined
                  : `job-requirement-section-${firstItem.itemId}`
                return (
                  <section
                    key={`${group.section ?? 'ungrouped'}-${firstItem?.itemId ?? 0}`}
                    className="job-requirement-group"
                    aria-labelledby={headingId}
                  >
                    {group.section === null ? null : (
                      <h3 id={headingId} className="job-requirement-section-title">
                        {group.section}
                      </h3>
                    )}
                    <ol className="job-requirement-list">
                      {group.items.map((item) => (
                        <li key={item.itemId}>
                          <label className="job-requirement-item">
                            <input
                              type="checkbox"
                              checked={selectedItemIds.has(item.itemId)}
                              onChange={() => onToggleItem(item.itemId)}
                            />
                            <span><strong>{item.text}</strong></span>
                          </label>
                        </li>
                      ))}
                    </ol>
                  </section>
                )
              })}
            </div>
          )}
        </div>

        <footer className="job-requirement-modal-actions">
          <button type="button" className="secondary-button button-large" onClick={onClose}>
            취소
          </button>
          <button
            type="button"
            className="primary-button button-large"
            disabled={selectedCount === 0}
            onClick={onSearch}
          >
            선택한 {selectedCount}개 항목에서 원문 후보 찾기
          </button>
        </footer>
      </section>
    </div>
  )
}

export type JobEvidenceResultsWorkspaceProps = {
  groups: JobEvidenceGroup[]
  documentTypes: Map<number, DocumentType>
  documentTypeLabel: (documentType: DocumentType) => string
  onEditPosting: () => void
  onEditSelection: () => void
  onRetry: (itemId: number) => void
  onOpenPdf: (target: EvidencePdfViewerTarget) => void
  onNavigateToDocument: (documentId: number) => void
}

export type JobEvidenceResultsViewProps = JobEvidenceResultsWorkspaceProps & {
  activeItemId: number | null
  onSelectItem: (itemId: number) => void
  activeFilter: JobEvidenceResultFilter
  onSelectFilter: (filter: JobEvidenceResultFilter) => void
  headingRef?: RefObject<HTMLHeadingElement | null>
}

export type JobEvidenceResultFilter = 'found' | 'empty' | 'pending'

function resultFilter(group: JobEvidenceGroup): JobEvidenceResultFilter {
  if (group.state === 'loading' || group.state === 'error') {
    return 'pending'
  }
  return visibleJobEvidenceResults(group.candidates).length > 0
    ? 'found'
    : 'empty'
}

function resultCountLabel(group: JobEvidenceGroup): string {
  if (group.state === 'loading') return '찾는 중'
  if (group.state === 'error') return '불러오기 실패'
  const count = visibleJobEvidenceResults(group.candidates).length
  return count === 0 ? '검색된 후보 없음' : `원문 후보 ${count}건`
}

function normalizedDocumentTitle(value: string): string {
  return value.trim().replace(/\s+/gu, ' ').toLocaleLowerCase('ko')
}

function documentGroupMetadata(
  documentGroup: JobEvidenceDocumentGroup,
  documentGroups: readonly JobEvidenceDocumentGroup[],
  documentTypeName: string,
): string {
  const titleKey = normalizedDocumentTitle(documentGroup.documentTitle)
  const sameTitleGroups = documentGroups.filter((group) => (
    normalizedDocumentTitle(group.documentTitle) === titleKey
  ))
  const duplicateTitleLabel = sameTitleGroups.length > 1
    ? `같은 제목 문서 ${sameTitleGroups.indexOf(documentGroup) + 1}/${sameTitleGroups.length}`
    : null

  return [
    documentTypeName,
    `버전 ${documentGroup.versionNo}`,
    duplicateTitleLabel,
    `확인할 원문 후보 ${documentGroup.candidates.length}건`,
  ].filter((value): value is string => value !== null).join(' · ')
}

export function JobEvidenceResultsWorkspace(props: JobEvidenceResultsWorkspaceProps) {
  const [activeItemId, setActiveItemId] = useState<number | null>(null)
  const [activeFilter, setActiveFilter] = useState<JobEvidenceResultFilter>('found')
  const headingRef = useRef<HTMLHeadingElement>(null)

  useEffect(() => {
    headingRef.current?.focus()
  }, [])

  return (
    <JobEvidenceResultsView
      {...props}
      activeItemId={activeItemId}
      onSelectItem={setActiveItemId}
      activeFilter={activeFilter}
      onSelectFilter={(filter) => {
        setActiveFilter(filter)
        setActiveItemId(null)
      }}
      headingRef={headingRef}
    />
  )
}

export function JobEvidenceResultsView({
  groups,
  documentTypes,
  documentTypeLabel,
  onEditPosting,
  onEditSelection,
  onRetry,
  onOpenPdf,
  onNavigateToDocument,
  activeItemId,
  onSelectItem,
  activeFilter,
  onSelectFilter,
  headingRef,
}: JobEvidenceResultsViewProps) {
  const indexedGroups = groups.map((group, index) => ({ group, index }))
  const filterCounts = indexedGroups.reduce<Record<JobEvidenceResultFilter, number>>(
    (counts, entry) => {
      counts[resultFilter(entry.group)] += 1
      return counts
    },
    { found: 0, empty: 0, pending: 0 },
  )
  const resolvedFilter = filterCounts[activeFilter] > 0
    ? activeFilter
    : (['found', 'empty', 'pending'] as const).find((filter) => filterCounts[filter] > 0)
      ?? activeFilter
  const filteredGroups = indexedGroups.filter((entry) => resultFilter(entry.group) === resolvedFilter)
  const activeGroup = filteredGroups.find((entry) => entry.group.item.itemId === activeItemId)?.group
    ?? filteredGroups[0]?.group
  const searchInFlight = groups.some((group) => group.state === 'loading')
  const filterOptions: Array<{ filter: JobEvidenceResultFilter; label: string }> = [
    { filter: 'found', label: '검색 후보 있음' },
    { filter: 'empty', label: '검색된 후보 없음' },
    ...(filterCounts.pending > 0
      ? [{ filter: 'pending' as const, label: '확인 필요' }]
      : []),
  ]

  return (
    <div className="job-results-page">
      <header className="job-results-page-heading">
        <div>
          <p className="eyebrow">JOB POSTING EVIDENCE</p>
          <h1
            ref={headingRef}
            className="job-results-page-title"
            id="job-evidence-title"
            tabIndex={-1}
          >
            채용공고 관련 원문 후보
          </h1>
          <p>선택한 채용공고 항목별로 문서에서 찾은 원문 후보를 확인할 수 있습니다.</p>
        </div>
        <div className="job-results-page-actions">
          <button type="button" className="secondary-button" onClick={onEditPosting}>
            채용공고 수정
          </button>
          <button
            type="button"
            className="primary-button"
            disabled={searchInFlight}
            onClick={onEditSelection}
          >
            항목 다시 선택
          </button>
        </div>
      </header>

      <div className="job-results-summary" role="status">
        <strong>선택 항목 {groups.length}개</strong>
        <span>선택한 항목의 검색 후보를 확인하세요.</span>
        <span role="note">
          내 문서에서 항목과 관련된 원문과 위치를 보여줍니다. 경험의 진위나 채용 요건 충족 여부를 판정하지 않습니다.
        </span>
      </div>

      <div className="job-results-workspace">
        <nav className="job-requirement-navigation" aria-label="검색한 채용공고 항목">
          <p className="section-kicker">REQUIREMENTS</p>
          <div className="job-requirement-filter-tabs" role="group" aria-label="검색 결과 상태별 보기">
            {filterOptions.map((option) => (
              <button
                key={option.filter}
                type="button"
                className={`job-requirement-filter-tab${resolvedFilter === option.filter ? ' is-active' : ''}`}
                aria-pressed={resolvedFilter === option.filter}
                disabled={filterCounts[option.filter] === 0}
                onClick={() => onSelectFilter(option.filter)}
              >
                <span>{option.label}</span>
                <strong>{filterCounts[option.filter]}</strong>
              </button>
            ))}
          </div>
          <div className="job-requirement-navigation-list">
            {filteredGroups.map(({ group, index }) => {
              const active = group.item.itemId === activeGroup?.item.itemId
              return (
                <button
                  key={group.item.itemId}
                  type="button"
                  className={`job-requirement-navigation-item${active ? ' is-active' : ''}`}
                  aria-pressed={active}
                  onClick={() => onSelectItem(group.item.itemId)}
                >
                  <span className="job-requirement-navigation-index">
                    {String(index + 1).padStart(2, '0')}
                  </span>
                  <span className="job-requirement-navigation-copy">{group.item.text}</span>
                  <span className="job-requirement-navigation-count">{resultCountLabel(group)}</span>
                </button>
              )
            })}
          </div>
        </nav>

        <section
          className="job-active-evidence"
          aria-live="polite"
          aria-busy={activeGroup?.state === 'loading'}
        >
          {activeGroup === undefined ? (
            <p className="job-result-state">표시할 검색 항목이 없습니다.</p>
          ) : (
            <JobActiveEvidence
              group={activeGroup}
              documentTypes={documentTypes}
              documentTypeLabel={documentTypeLabel}
              onRetry={onRetry}
              onOpenPdf={onOpenPdf}
              onNavigateToDocument={onNavigateToDocument}
            />
          )}
        </section>
      </div>
    </div>
  )
}

export function JobActiveEvidence({
  group,
  documentTypes,
  documentTypeLabel,
  onRetry,
  onOpenPdf,
  onNavigateToDocument,
}: {
  group: JobEvidenceGroup
  documentTypes: Map<number, DocumentType>
  documentTypeLabel: (documentType: DocumentType) => string
  onRetry: (itemId: number) => void
  onOpenPdf: (target: EvidencePdfViewerTarget) => void
  onNavigateToDocument: (documentId: number) => void
}) {
  const documentGroups = groupVisibleJobEvidenceByDocument(group.candidates)

  return (
    <>
      <header className="job-active-requirement-heading">
        <p className="section-kicker">SELECTED REQUIREMENT</p>
        <h2>{group.item.text}</h2>
        {group.state === 'result' ? (
          <span>
            확인할 원문 후보 {documentGroups.reduce(
              (count, documentGroup) => count + documentGroup.candidates.length,
              0,
            )}건 · {documentGroups.length}개 문서
          </span>
        ) : null}
      </header>

      {group.state === 'loading' ? (
        <p className="job-result-state">
          <span className="state-spinner" aria-hidden="true" />
          확인할 원문 후보를 찾는 중입니다.
        </p>
      ) : null}
      {group.state === 'empty' ? (
        <div className="job-result-state">
          <strong>검색된 후보가 없습니다.</strong>
          <span>현재 등록된 문서에서 관련 원문을 찾지 못했습니다. 경험이 없다는 판정도, 채용 요건을 충족하지 못한다는 판정도 아닙니다.</span>
        </div>
      ) : null}
      {group.state === 'error' ? (
        <div className="job-result-state" role="alert">
          <strong>검색 후보를 불러오지 못했습니다.</strong>
          <button type="button" className="secondary-button" onClick={() => onRetry(group.item.itemId)}>
            다시 시도
          </button>
        </div>
      ) : null}
      {group.state === 'result' ? (
        <div className="job-document-groups">
          {documentGroups.map((documentGroup) => {
            const documentType = documentTypes.get(documentGroup.documentId)
            const documentTypeName = documentType === undefined
              ? '문서'
              : documentTypeLabel(documentType)
            return (
              <article key={documentGroup.key} className="job-document-group">
                <header className="job-document-group-heading">
                  <div className="job-document-symbol" aria-hidden="true">문</div>
                  <div>
                    <h3>{documentGroup.documentTitle}</h3>
                    <p>{documentGroupMetadata(documentGroup, documentGroups, documentTypeName)}</p>
                  </div>
                </header>
                <ol className="job-document-evidence-list">
                  {documentGroup.candidates.map((candidate) => {
                    const { result } = candidate
                    const viewerTarget = getEvidencePdfViewerTarget(result)
                    const page = getEvidencePdfPage(result)
                    const context = getJobEvidenceContext(candidate)
                    const hasAdditionalContext = hasAdditionalJobEvidenceContext(candidate)
                    return (
                      <li key={`${result.documentVersionId}:${result.chunkId}:${result.evidenceSourceType}:${result.evidenceSourceIndex}`}>
                        <article className="job-document-evidence-row">
                          <div className="job-evidence-source-marker">
                            <strong>{getEvidenceSourceLabel(result)}</strong>
                            <span>{page === null ? 'TXT' : 'PDF'}</span>
                          </div>
                          <div className="job-evidence-row-content">
                            <p className="keyword-context-label">확인할 원문 후보</p>
                            <blockquote className="job-evidence-preview">
                              {getJobEvidenceHighlight(candidate)}
                            </blockquote>
                            <div className="job-evidence-row-actions">
                              <button
                                type="button"
                                className="keyword-document-button"
                                onClick={() => viewerTarget === null
                                  ? onNavigateToDocument(result.documentId)
                                  : onOpenPdf(viewerTarget)}
                              >
                                {page === null ? '문서에서 보기' : `${page}페이지에서 보기`}
                              </button>
                              {hasAdditionalContext ? (
                                <details className="keyword-context-details job-evidence-context-details">
                                  <summary>
                                    <span className="keyword-context-open-label">주변 내용 보기</span>
                                    <span className="keyword-context-close-label">주변 내용 닫기</span>
                                  </summary>
                                  <div className="search-result-document-reader" role="document">
                                    {context}
                                  </div>
                                </details>
                              ) : (
                                <button
                                  type="button"
                                  className="keyword-document-button job-evidence-context-unavailable"
                                  disabled
                                  title="현재 표시된 원문 외에 추가로 보여줄 주변 내용이 없습니다."
                                >
                                  추가 문맥 없음
                                </button>
                              )}
                            </div>
                          </div>
                        </article>
                      </li>
                    )
                  })}
                </ol>
              </article>
            )
          })}
        </div>
      ) : null}
    </>
  )
}
