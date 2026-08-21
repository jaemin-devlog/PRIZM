import assert from 'node:assert/strict'
import test from 'node:test'

import { progressSummary } from '../src/documentProcessingPresentation.ts'

test('FAILED takes precedence over a 100 percent chunk checkpoint', () => {
  assert.equal(progressSummary('FAILED', 'SAVING', 10, 10, 100), '준비에 실패했어요')
})

test('PROCESSING SAVING is not presented as completed at 100 percent', () => {
  assert.equal(progressSummary('PROCESSING', 'SAVING', 10, 10, 100), '준비 내용을 저장하는 중')
})

test('only COMPLETED is presented as completed', () => {
  assert.equal(progressSummary('COMPLETED', 'COMPLETED', 10, 10, 100), '검색 준비 완료')
  assert.equal(progressSummary('PROCESSING', 'EMBEDDING', 10, 10, 100), '검색 준비 중 10/10 · 100%')
})

test('RETRY_WAIT takes precedence over stale progress', () => {
  assert.equal(progressSummary('RETRY_WAIT', 'SAVING', 10, 10, 100), '잠시 후 다시 준비해요')
})

test('null progress retains the actual non-terminal stage or preparation state', () => {
  assert.equal(progressSummary('PROCESSING', 'TEXT_EXTRACTION', null, null, null), '문서 내용을 확인하는 중')
  assert.equal(progressSummary('PENDING', null, null, null, null), '문서를 읽고 검색할 수 있게 준비 중')
})
