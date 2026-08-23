import assert from 'node:assert/strict'
import test from 'node:test'
import { createElement, type ReactNode } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'

import {
  keepFocusWithinModal,
  focusModalEntry,
  restoreModalTrigger,
  type FocusableTarget,
} from '../src/modalFocus.ts'
import { DocumentTagEditor, TagModalView, type TagModalViewProps } from '../src/tagComponents.ts'
import type { Tag } from '../src/api/tagApi.ts'
import { elementText, findElements } from './componentTestSupport.ts'

const springBoot: Tag = { tagId: 1, name: 'Spring Boot', source: 'SYSTEM' }
const redis: Tag = { tagId: 2, name: 'Redis', source: 'SYSTEM' }

function modalProps(overrides: Partial<TagModalViewProps> = {}): TagModalViewProps {
  return {
    query: 'Redis',
    selectedTags: [springBoot],
    results: [springBoot, redis],
    isLoading: false,
    isCreating: false,
    isSaving: false,
    canCreate: false,
    canRetrySearch: false,
    errorMessage: null,
    panelRef: { current: null },
    onPanelKeyDown: () => undefined,
    onQueryChange: () => undefined,
    onSelect: () => undefined,
    onCreate: () => undefined,
    onRetrySearch: () => undefined,
    onRemove: () => undefined,
    onClose: () => undefined,
    onSave: () => undefined,
    ...overrides,
  }
}

test('TagModal DOM keeps multiple selections, duplicate state, USER creation, and save actions visible', () => {
  const html = renderToStaticMarkup(createElement(TagModalView, modalProps({
    query: 'Tauri',
    canCreate: true,
  })))

  assert.match(html, /role="dialog"/)
  assert.match(html, /Spring Boot/)
  assert.match(html, /선택됨/)
  assert.match(html, /“Tauri” 새 태그 만들기/)
  assert.match(html, /aria-label="Spring Boot 선택 해제"/)
  assert.match(html, />저장</)
})

test('TagModal result, create, remove, and save controls invoke the page controller callbacks', () => {
  const selectedIds: number[] = []
  const removedIds: number[] = []
  let createRequests = 0
  let saveRequests = 0
  const tree = TagModalView(modalProps({
    query: 'Tauri',
    canCreate: true,
    onSelect: (tag) => selectedIds.push(tag.tagId),
    onCreate: () => { createRequests += 1 },
    onRemove: (tagId) => removedIds.push(tagId),
    onSave: () => { saveRequests += 1 },
  })) as ReactNode
  const buttons = findElements(tree, (element) => typeof element.props?.onClick === 'function')

  const clickByText = (text: string) => {
    const button = buttons.find((candidate) => elementText(candidate.props?.children).includes(text))
    assert.ok(button)
    ;(button.props?.onClick as () => void)()
  }
  clickByText('Redis')
  clickByText('새 태그 만들기')
  const removeButton = buttons.find((candidate) => candidate.props?.['aria-label'] === 'Spring Boot 선택 해제')
  assert.ok(removeButton)
  ;(removeButton.props?.onClick as () => void)()
  clickByText('저장')

  assert.deepEqual(selectedIds, [2])
  assert.deepEqual(removedIds, [1])
  assert.equal(createRequests, 1)
  assert.equal(saveRequests, 1)
})

test('TagModal cannot close or save while a USER tag creation or document-tag save is in flight', () => {
  const creatingHtml = renderToStaticMarkup(createElement(TagModalView, modalProps({
    isCreating: true,
    canCreate: true,
  })))
  const savingHtml = renderToStaticMarkup(createElement(TagModalView, modalProps({ isSaving: true })))

  assert.match(creatingHtml, /aria-busy="true"/)
  assert.match(creatingHtml, /tabindex="-1"/)
  assert.match(creatingHtml, /태그 생성 중/)
  assert.match(savingHtml, /aria-busy="true"/)
  assert.match(savingHtml, /저장 중/)
  assert.doesNotMatch(creatingHtml, /class="primary-button">/)
  assert.doesNotMatch(savingHtml, /class="primary-button">/)
})

test('TagModal search failure exposes the controller retry action', () => {
  let retries = 0
  const tree = TagModalView(modalProps({
    results: [],
    errorMessage: '태그를 검색하지 못했습니다.',
    canRetrySearch: true,
    onRetrySearch: () => { retries += 1 },
  })) as ReactNode
  const retry = findElements(
    tree,
    (element) => typeof element.props?.onClick === 'function'
      && elementText(element.props?.children) === '검색 다시 시도',
  )[0]
  assert.ok(retry)
  ;(retry.props?.onClick as () => void)()
  assert.equal(retries, 1)
})

test('document tag editor invokes add and remove controls used by upload and document detail', () => {
  const removed: number[] = []
  let addRequests = 0
  const tree = DocumentTagEditor({
    tags: [springBoot, redis],
    emptyMessage: '연결된 태그가 없습니다.',
    removeLabel: (tag) => `${tag.name} 태그 제거`,
    onRemove: (tagId) => removed.push(tagId),
    onAdd: () => { addRequests += 1 },
  }) as ReactNode
  const buttons = findElements(tree, (element) => typeof element.props?.onClick === 'function')
  const removeRedis = buttons.find((button) => button.props?.['aria-label'] === 'Redis 태그 제거')
  const add = buttons.find((button) => elementText(button.props?.children) === '+ 추가')
  assert.ok(removeRedis)
  assert.ok(add)
  ;(removeRedis.props?.onClick as () => void)()
  ;(add.props?.onClick as () => void)()

  assert.deepEqual(removed, [2])
  assert.equal(addRequests, 1)
})

test('document detail disables add and remove controls while a tag removal is in flight', () => {
  const html = renderToStaticMarkup(createElement(DocumentTagEditor, {
    tags: [springBoot],
    emptyMessage: '연결된 태그가 없습니다.',
    removeLabel: (tag: Tag) => `${tag.name} 태그 제거`,
    removingTagId: springBoot.tagId,
    onRemove: () => undefined,
    onAdd: () => undefined,
  }))
  assert.match(html, /aria-label="Spring Boot 태그 제거" disabled=""/)
  assert.match(html, /class="tag-add-button" disabled=""/)
})

test('modal keyboard focus wraps at both edges and recovers focus entering from outside', () => {
  const focused: string[] = []
  const first: FocusableTarget = { focus: () => focused.push('first') }
  const last: FocusableTarget = { focus: () => focused.push('last') }
  const targets = [first, last]

  assert.equal(keepFocusWithinModal(targets, last, false), true)
  assert.equal(keepFocusWithinModal(targets, first, true), true)
  assert.equal(keepFocusWithinModal(targets, {}, false), true)
  assert.deepEqual(focused, ['first', 'last', 'first'])
})

test('closing the modal restores the connected trigger without focusing a removed control', () => {
  const focused: string[] = []
  assert.equal(restoreModalTrigger({ isConnected: true, focus: () => focused.push('trigger') }), true)
  assert.equal(restoreModalTrigger({ isConnected: false, focus: () => focused.push('removed') }), false)
  assert.equal(restoreModalTrigger(null), false)
  assert.deepEqual(focused, ['trigger'])
})

test('opening a modal moves focus into its dialog panel', () => {
  const focused: string[] = []
  assert.equal(focusModalEntry({ focus: () => focused.push('panel') }), true)
  assert.equal(focusModalEntry(null), false)
  assert.deepEqual(focused, ['panel'])
})
