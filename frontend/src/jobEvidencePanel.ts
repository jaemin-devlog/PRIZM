import { createElement, type ChangeEvent, type FormEvent } from 'react'

export type JobEvidenceSegmentationState = 'idle' | 'loading' | 'ready' | 'error'

export type JobEvidencePanelProps = {
  content: string
  itemCount: number
  selectedCount: number
  segmentationState: JobEvidenceSegmentationState
  onContentChange: (content: string) => void
  onSegment: () => void
  onOpenSelection: () => void
}

export function JobEvidencePanel({
  content,
  itemCount,
  selectedCount,
  segmentationState,
  onContentChange,
  onSegment,
  onOpenSelection,
}: JobEvidencePanelProps) {
  const handleSegmentSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (content.trim() !== '' && segmentationState !== 'loading') {
      onSegment()
    }
  }

  return createElement(
    'div',
    { className: 'job-evidence-workspace' },
    createElement(
      'section',
      { className: 'job-posting-input-panel', 'aria-labelledby': 'job-posting-input-title' },
      createElement(
        'header',
        { className: 'job-evidence-section-heading' },
        createElement(
          'div',
          null,
          createElement('p', { className: 'section-kicker' }, 'JOB POSTING'),
          createElement('h2', { id: 'job-posting-input-title' }, '채용공고 붙여넣기'),
        ),
        createElement('span', null, '저장되지 않음'),
      ),
      createElement(
        'form',
        { className: 'job-posting-form', onSubmit: handleSegmentSubmit, noValidate: true },
        createElement('label', { htmlFor: 'job-posting-content' }, '채용공고 내용'),
        createElement('textarea', {
          id: 'job-posting-content',
          name: 'content',
          rows: 10,
          maxLength: 20_000,
          placeholder: '자격요건과 우대사항을 포함한 채용공고를 붙여넣어 주세요.',
          value: content,
          onChange: (event: ChangeEvent<HTMLTextAreaElement>) => onContentChange(event.target.value),
          'aria-invalid': segmentationState === 'error',
          'aria-describedby': segmentationState === 'error' ? 'job-posting-segmentation-error' : undefined,
        }),
        segmentationState === 'error'
          ? createElement(
            'p',
            { id: 'job-posting-segmentation-error', className: 'form-error feedback-message', role: 'alert' },
            '채용공고 항목을 나누지 못했습니다. 내용을 확인하고 다시 시도해 주세요.',
          )
          : null,
        createElement(
          'div',
          { className: 'job-posting-form-actions' },
          createElement(
            'button',
            {
              type: 'submit',
              className: 'primary-button button-large',
              disabled: content.trim() === '' || segmentationState === 'loading',
              'aria-busy': segmentationState === 'loading',
            },
            segmentationState === 'loading'
              ? createElement('span', { className: 'button-spinner', 'aria-hidden': true })
              : null,
            segmentationState === 'loading'
              ? '나누는 중'
              : segmentationState === 'ready' ? '항목 다시 나누기' : '항목 나누기',
          ),
        ),
        segmentationState === 'ready'
          ? createElement(
            'div',
            { className: 'job-posting-segmentation-summary', role: 'status' },
            createElement(
              'div',
              null,
              createElement('strong', null, `${itemCount}개의 검색 가능 항목`),
              createElement('span', null, `${selectedCount}개 선택됨`),
            ),
            createElement(
              'button',
              {
                type: 'button',
                className: 'secondary-button',
                disabled: itemCount === 0,
                onClick: onOpenSelection,
              },
              '선택 항목 보기',
            ),
          )
          : null,
      ),
    ),
  )
}
