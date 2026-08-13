import assert from 'node:assert/strict'
import test from 'node:test'

import { progressSummary } from '../src/documentProcessingPresentation.ts'

test('FAILED takes precedence over a 100 percent chunk checkpoint', () => {
  assert.equal(progressSummary('FAILED', 'SAVING', 10, 10, 100), '처리 실패')
})

test('PROCESSING SAVING is not presented as completed at 100 percent', () => {
  assert.equal(progressSummary('PROCESSING', 'SAVING', 10, 10, 100), '저장 중')
})

test('only COMPLETED is presented as completed', () => {
  assert.equal(progressSummary('COMPLETED', 'COMPLETED', 10, 10, 100), '완료 · 100%')
  assert.equal(progressSummary('PROCESSING', 'EMBEDDING', 10, 10, 100), '임베딩 10/10 · 100%')
})

test('RETRY_WAIT takes precedence over stale progress', () => {
  assert.equal(progressSummary('RETRY_WAIT', 'SAVING', 10, 10, 100), '재시도 대기')
})

test('null progress retains the actual non-terminal stage or preparation state', () => {
  assert.equal(progressSummary('PROCESSING', 'TEXT_EXTRACTION', null, null, null), '텍스트 추출')
  assert.equal(progressSummary('PENDING', null, null, null, null), '처리 준비')
})
