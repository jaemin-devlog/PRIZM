import assert from 'node:assert/strict'
import test from 'node:test'

import { getEvidenceContext, getEvidenceSourceLabel } from '../src/searchEvidencePresentation.ts'

test('expanded evidence displays its actual source instead of the ranked chunk source', () => {
  assert.equal(
    getEvidenceSourceLabel({ evidenceSourceLabel: '5페이지' }),
    '5페이지',
  )
})

test('evidence context keeps nearby source sentences without dumping the raw chunk', () => {
  const content = [
    'EMAIL developer@example.com',
    '여러 Worker가 같은 이벤트를 처리하는 문제가 있었습니다.',
    'FOR UPDATE SKIP LOCKED로 처리 대상을 선점했습니다.',
    '통합 테스트에서 중복 처리 0건을 검증했습니다.',
    '검색과 관련 없는 긴 기술 스택 설명입니다.',
  ].join('\n')

  const context = getEvidenceContext(content, 'FOR UPDATE SKIP LOCKED로 처리 대상을 선점했습니다.')

  assert.match(context, /같은 이벤트를 처리하는 문제/)
  assert.match(context, /중복 처리 0건/)
  assert.doesNotMatch(context, /developer@example.com/)
  assert.doesNotMatch(context, /관련 없는 긴 기술 스택/)
})

test('expanded evidence outside the ranked chunk remains the displayed context', () => {
  assert.equal(
    getEvidenceContext('현재 검색 chunk의 다른 원문입니다.', '이메일과 Kakao 인증을 통합했습니다.'),
    '이메일과 Kakao 인증을 통합했습니다.',
  )
})
