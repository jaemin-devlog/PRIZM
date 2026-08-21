import assert from 'node:assert/strict'
import test from 'node:test'

import {
  getEvidenceContext,
  getEvidenceHighlight,
  getEvidencePdfPage,
  getEvidenceSourceLabel,
} from '../src/searchEvidencePresentation.ts'

test('expanded evidence displays its actual source instead of the ranked chunk source', () => {
  assert.equal(
    getEvidenceSourceLabel({ evidenceSourceLabel: '5페이지' }),
    '5페이지',
  )
})

test('PDF document viewer uses the displayed evidence page and never a TXT chunk position', () => {
  assert.equal(
    getEvidencePdfPage({ evidenceSourceType: 'PAGE', evidenceSourceIndex: 2 }),
    2,
  )
  assert.equal(
    getEvidencePdfPage({ evidenceSourceType: 'TEXT_CHUNK', evidenceSourceIndex: 2 }),
    null,
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

  const context = getEvidenceContext(
    '중복 처리를 어떻게 막았나요?',
    content,
    'FOR UPDATE SKIP LOCKED로 처리 대상을 선점했습니다.',
  )

  assert.match(context, /같은 이벤트를 처리하는 문제/)
  assert.match(context, /중복 처리 0건/)
  assert.doesNotMatch(context, /developer@example.com/)
  assert.doesNotMatch(context, /관련 없는 긴 기술 스택/)
})

test('expanded evidence outside the ranked chunk remains the displayed context', () => {
  assert.equal(
    getEvidenceContext(
      'Kakao 인증 경험이 있나요?',
      '현재 검색 chunk의 다른 원문입니다.',
      '이메일과 Kakao 인증을 통합했습니다.',
    ),
    '이메일과 Kakao 인증을 통합했습니다.',
  )
})

test('simple technology questions select a project heading and technology declaration from content', () => {
  const content = [
    'Java / Spring Backend Developer',
    'Project Compass | 지역 커뮤니티 일정 공유 서비스',
    '역할: 일정 추천, 알림, 채팅 API 개발 및 운영',
    '기술: Java 17, Spring Boot 3.4.3, JPA, MySQL, Redis, Docker, AWS',
    '여러 요청이 같은 팀을 동시에 확정하지 않도록 처리 순서를 설계했습니다.',
  ].join('\n')

  const highlight = getEvidenceHighlight('Spring Boot를 사용한 프로젝트가 있나요?', {
    content,
    snippet: content,
  })

  assert.match(highlight, /Project Compass/)
  assert.match(highlight, /Spring Boot 3\.4\.3/)
  assert.doesNotMatch(highlight, /^Java \/ Spring Backend Developer$/)
  assert.doesNotMatch(highlight, /동시에 확정/)
})

test('multiple technology questions show only source lines that contain each requested technology', () => {
  const content = [
    'Backend : Java, Spring Boot, Spring Security, JPA, REST API',
    'Database / Data : PostgreSQL, MySQL, Redis, pgvector',
    '프로젝트 전체를 설명하는 긴 문장입니다.',
    '동시성 문제는 별도 문서에서 다뤘습니다.',
  ].join('\n')

  assert.equal(
    getEvidenceHighlight('Spring Boot와 Redis를 활용한 경험이 있나요?', { content, snippet: content }),
    'Backend : Java, Spring Boot, Spring Security, JPA, REST API\nDatabase / Data : PostgreSQL, MySQL, Redis, pgvector',
  )
})

test('complex identifier questions keep only the direct claim window', () => {
  const snippet = [
    '낙관적 잠금으로 동시 요청을 조정했습니다.',
    'PushGate 전송 실패가 주문 저장과 핵심 기능을 함께 취소하지 않도록 발송 요청과 전송 작업을 별도 Worker로 분리했습니다.',
    '이벤트 대기열 상태는 별도 Worker가 복구했습니다.',
  ].join('\n')

  assert.equal(
    getEvidenceHighlight('PushGate 전송 실패가 핵심 기능에 영향을 주지 않게 어떻게 설계했나요?', {
      content: snippet,
      snippet,
    }),
    'PushGate 전송 실패가 주문 저장과 핵심 기능을 함께 취소하지 않도록 발송 요청과 전송 작업을 별도 Worker로 분리했습니다.',
  )
})

test('numeric questions keep the exact sentence that contains the queried value', () => {
  const snippet = [
    'Project Compass | 지역 커뮤니티 일정 공유 서비스',
    '서비스 : 약 2,400명이 이용한 기존 지역 웹서비스의 접근성 문제를 개선해 모바일 앱으로 재설계했습니다.',
    '동시 요청에는 낙관적 잠금을 적용했습니다.',
  ].join('\n')

  assert.equal(
    getEvidenceHighlight('Project Compass 앱이 약 2,400명의 사용자를 기록했나요?', {
      content: snippet,
      snippet,
    }),
    '서비스 : 약 2,400명이 이용한 기존 지역 웹서비스의 접근성 문제를 개선해 모바일 앱으로 재설계했습니다.',
  )
})

test('OAuth2 questions use the matching project content rather than an oversized snippet', () => {
  const content = [
    'Project Ledger | 팀 예산 정산 서비스',
    '역할: 사용자 가입과 정산 API 개발',
    '기술: Kotlin, Spring Boot, PostgreSQL, Redis',
    'OAuth2로 가입한 계정을 기존 사용자 정보와 연결했습니다.',
    '정산 통계를 주 단위로 집계했습니다.',
  ].join('\n')

  assert.equal(
    getEvidenceHighlight('OAuth2를 사용한 경험이 있나요?', { content, snippet: content }),
    'Project Ledger | 팀 예산 정산 서비스\nOAuth2로 가입한 계정을 기존 사용자 정보와 연결했습니다.',
  )
  assert.equal(
    getEvidenceContext('OAuth2를 사용한 경험이 있나요?', content, content),
    content,
  )
})

test('complex questions preserve the API-selected extractive snippet', () => {
  const snippet = [
    '메모리 캐시로 후보를 찾았습니다.',
    '낙관적 잠금과 고유 제약으로 중복 예약을 막았습니다.',
  ].join('\n')

  assert.equal(
    getEvidenceHighlight('여러 요청이 동시에 같은 좌석을 예약할 때 중복 예약을 어떻게 막았나요?', {
      content: snippet,
      snippet,
    }),
    snippet,
  )
})

test('metric questions keep the selected sentence instead of treating 사용자 as technology use', () => {
  const snippet = '약 2,400명이 이용한 기존 지역 웹서비스의 접근성 문제를 개선해 모바일 앱으로 재설계했습니다.'

  assert.equal(
    getEvidenceHighlight('Project Compass 앱이 약 2,400명의 사용자를 기록했나요?', {
      content: snippet,
      snippet,
    }),
    snippet,
  )
})

test('structured nearby content can show up to six consecutive source units', () => {
  const content = [
    'Project Compass | 지역 커뮤니티 일정 공유 서비스',
    '역할: 일정 추천, 알림, 채팅, 통계 API 개발 및 배포 운영',
    '기술: Java 17, Spring Boot 3.4.3, JPA, MySQL, Redis',
    '서비스: 약 2,400명이 이용한 기존 지역 웹서비스를 모바일 앱으로 재설계했습니다.',
    '여러 요청이 같은 좌석을 동시에 예약할 수 있어 낙관적 잠금을 적용했습니다.',
    '운영 환경에서 중복 매칭을 지속적으로 확인했습니다.',
    '이 문장은 선택 범위를 넘어서는 원문입니다.',
  ].join('\n')

  const context = getEvidenceContext(
    'Spring Boot를 사용한 프로젝트가 있나요?',
    content,
    '기술: Java 17, Spring Boot 3.4.3, JPA, MySQL, Redis',
  )

  assert.match(context, /Project Compass/)
  assert.match(context, /기존 지역 웹서비스/)
  assert.match(context, /운영 환경/)
  assert.doesNotMatch(context, /선택 범위를 넘어서는/)
})
