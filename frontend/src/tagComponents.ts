import { createElement, type KeyboardEvent, type Ref } from 'react'

import type { Tag } from './api/tagApi'
import { normalizeTagName } from './tagSelection.ts'

export type TagModalViewProps = {
  query: string
  selectedTags: Tag[]
  results: Tag[]
  isLoading: boolean
  isCreating: boolean
  isSaving: boolean
  canCreate: boolean
  canRetrySearch: boolean
  errorMessage: string | null
  panelRef: Ref<HTMLElement>
  onPanelKeyDown: (event: KeyboardEvent<HTMLElement>) => void
  onQueryChange: (query: string) => void
  onSelect: (tag: Tag) => void
  onCreate: () => void
  onRetrySearch: () => void
  onRemove: (tagId: number) => void
  onClose: () => void
  onSave: () => void
}

export function TagModalView({
  query,
  selectedTags,
  results,
  isLoading,
  isCreating,
  isSaving,
  canCreate,
  canRetrySearch,
  errorMessage,
  panelRef,
  onPanelKeyDown,
  onQueryChange,
  onSelect,
  onCreate,
  onRetrySearch,
  onRemove,
  onClose,
  onSave,
}: TagModalViewProps) {
  const isBusy = isCreating || isSaving
  return createElement(
    'div',
    { className: 'tag-modal-layer' },
    createElement('button', {
      type: 'button',
      className: 'tag-modal-backdrop',
      'aria-label': '태그 추가 닫기',
      disabled: isBusy,
      tabIndex: -1,
      onClick: onClose,
    }),
    createElement(
      'section',
      {
        className: 'tag-modal-panel',
        role: 'dialog',
        'aria-modal': true,
        'aria-labelledby': 'tag-modal-title',
        'aria-busy': isBusy,
        tabIndex: -1,
        ref: panelRef,
        onKeyDown: onPanelKeyDown,
      },
      createElement(
        'header',
        { className: 'tag-modal-heading' },
        createElement(
          'div',
          null,
          createElement('p', { className: 'section-kicker' }, 'DOCUMENT TAGS'),
          createElement('h2', { id: 'tag-modal-title' }, '태그 추가'),
        ),
        createElement('button', {
          type: 'button',
          className: 'icon-close-button',
          'aria-label': '태그 추가 닫기',
          disabled: isBusy,
          onClick: onClose,
        }, '×'),
      ),
      createElement(
        'label',
        { className: 'tag-search-field', htmlFor: 'tag-search-input' },
        createElement('span', null, '태그 검색'),
        createElement('input', {
          id: 'tag-search-input',
          type: 'search',
          maxLength: 100,
          autoFocus: true,
          disabled: isBusy,
          placeholder: 'Spring Boot, Redis, Tauri',
          value: query,
          onChange: (event: { target: { value: string } }) => onQueryChange(event.target.value),
        }),
      ),
      createElement(
        'div',
        { className: 'tag-modal-results', 'aria-live': 'polite', 'aria-busy': isLoading },
        isLoading
          ? createElement(
            'p',
            { className: 'tag-modal-state' },
            createElement('span', { className: 'state-spinner', 'aria-hidden': true }),
            '태그를 찾는 중입니다.',
          )
          : results.map((tag) => {
            const selected = selectedTags.some(
              (item) => item.tagId === tag.tagId || normalizeTagName(item.name) === normalizeTagName(tag.name),
            )
            return createElement(
              'button',
              {
                key: tag.tagId,
                type: 'button',
                className: 'tag-result-button',
                disabled: selected || isBusy,
                onClick: () => onSelect(tag),
              },
              createElement('span', null, tag.name),
              createElement('small', null, selected ? '선택됨' : tag.source === 'SYSTEM' ? '추천 태그' : '내 태그'),
            )
          }),
        !isLoading && canCreate
          ? createElement(
            'button',
            {
              type: 'button',
              className: 'tag-create-button',
              disabled: isBusy,
              onClick: onCreate,
            },
            `+ “${query.trim()}” 새 태그 만들기`,
          )
          : null,
        !isLoading && results.length === 0 && !canCreate && query.trim() === ''
          ? createElement(
            'p',
            { className: 'tag-modal-state' },
            '추천 태그가 없습니다. 검색어를 입력해 새 태그를 만들 수 있습니다.',
          )
          : null,
      ),
      createElement(
        'div',
        { className: 'tag-modal-selection' },
        createElement('strong', null, '선택'),
        createElement(
          'div',
          { className: 'tag-chip-list' },
          selectedTags.length === 0
            ? createElement('span', { className: 'tag-empty-selection' }, '선택한 태그가 없습니다.')
            : null,
          ...selectedTags.map((tag) => createElement(
            'span',
            { key: tag.tagId, className: 'tag-chip' },
            tag.name,
            createElement('button', {
              type: 'button',
              'aria-label': `${tag.name} 선택 해제`,
              disabled: isBusy,
              onClick: () => onRemove(tag.tagId),
            }, '×'),
          )),
        ),
      ),
      errorMessage === null
        ? null
        : createElement(
          'div',
          { className: 'form-error feedback-message', role: 'alert' },
          createElement('p', null, errorMessage),
          canRetrySearch
            ? createElement('button', {
              type: 'button',
              className: 'secondary-button',
              disabled: isBusy,
              onClick: onRetrySearch,
            }, '검색 다시 시도')
            : null,
        ),
      createElement(
        'footer',
        { className: 'tag-modal-actions' },
        createElement('button', {
          type: 'button',
          className: 'secondary-button',
          disabled: isBusy,
          onClick: onClose,
        }, '취소'),
        createElement('button', {
          type: 'button',
          className: 'primary-button',
          disabled: isBusy,
          onClick: onSave,
        }, isSaving ? '저장 중' : isCreating ? '태그 생성 중' : '저장'),
      ),
    ),
  )
}

export type DocumentTagEditorProps = {
  tags: Tag[]
  emptyMessage: string
  removeLabel: (tag: Tag) => string
  disabled?: boolean
  removingTagId?: number | null
  onRemove: (tagId: number) => void
  onAdd: () => void
}

export function DocumentTagEditor({
  tags,
  emptyMessage,
  removeLabel,
  disabled = false,
  removingTagId = null,
  onRemove,
  onAdd,
}: DocumentTagEditorProps) {
  return createElement(
    'div',
    { className: 'document-tag-editor' },
    createElement(
      'div',
      { className: 'tag-chip-list' },
      tags.length === 0 ? createElement('span', { className: 'tag-empty-selection' }, emptyMessage) : null,
      ...tags.map((tag) => createElement(
        'span',
        { key: tag.tagId, className: 'tag-chip' },
        tag.name,
        createElement('button', {
          type: 'button',
          'aria-label': removeLabel(tag),
          disabled: disabled || removingTagId !== null,
          onClick: () => onRemove(tag.tagId),
        }, '×'),
      )),
    ),
    createElement('button', {
      type: 'button',
      className: 'tag-add-button',
      disabled: disabled || removingTagId !== null,
      onClick: onAdd,
    }, '+ 추가'),
  )
}
