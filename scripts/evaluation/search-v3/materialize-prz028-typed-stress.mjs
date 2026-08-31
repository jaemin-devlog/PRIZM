import { createHash } from 'node:crypto'
import { existsSync, readFileSync } from 'node:fs'
import { mkdir, readFile, readdir, writeFile } from 'node:fs/promises'
import path from 'node:path'

const DATASET_VERSION = 'search-v3-typed-constraints-stress-1.0.0'
const SCHEMA_VERSION = '1.0.0'
const TYPED_SCHEMA_VERSION = '1.0.0'
const FROZEN_AT = '2026-08-31T18:45:00+09:00'
const MATERIALIZATION_BASE_REVISION = 'a7dbb12ea7c0a3f4a502c1ae0252177d9c78a8b9'
const GENERATOR_REVISION = 'prz028-typed-stress-v1'
const SEALED_FINAL_SHA256 = 'e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383'
const OUTPUT_ROOT = path.resolve(
  'src/searchEvaluation/resources/search-v3-evaluation/typed-constraints-stress-1.0.0',
)
const SEALED_MANIFEST = path.resolve(
  'src/test/resources/search-v3-evaluation/sealed-final/manifest.json',
)

const LANGUAGE_CATEGORY = { KO: 'korean', EN: 'english', KO_EN_MIXED: 'korean_english_mixed' }
const MATCH_STATES = new Set(['SATISFIED', 'CONTRADICTED', 'UNKNOWN'])
const TYPED_KINDS = new Set(['QUANTITY', 'DATE', 'IDENTIFIER_NUMBER', 'LITERAL_IDENTIFIER'])
const REQUIRED_FAMILIES = new Set([
  'quantity_positive', 'quantity_boundary', 'wrong_value', 'qualifier_mismatch',
  'percentage_direction', 'duration', 'range', 'date_after', 'date_before',
  'date_range', 'identifier_number_exact', 'identifier_number_mismatch',
  'literal_exact', 'literal_near_match', 'not_supported_hard_negative',
])
const FORBIDDEN_RUNTIME_KEYS = new Set([
  'chunkId', 'expectedChunkId', 'runtimeChunkId', 'runtimeParentId',
  'databaseParentId', 'dbChunkId', 'retrievalPassageId',
])

const quantity = (surface, value, normalizedUnit, qualifier, direction = 'NONE') => ({
  kind: 'QUANTITY', surface, value, normalizedUnit, qualifier, direction,
})
const dateObservation = (surface, start, end, precision = 'FULL_DATE', qualifier = '') => ({
  kind: 'DATE', surface, start, end, precision, qualifier,
})
const identifierNumber = (surface, identifier, numberSurface) => ({
  kind: 'IDENTIFIER_NUMBER', surface, identifier, numberSurface,
  normalizedSegments: numberSurface.split('.').map((value) => Number(value)),
})
const literal = (surface, normalizedLiteral = surface.normalize('NFKC').toLocaleLowerCase('und')) => ({
  kind: 'LITERAL_IDENTIFIER', surface, normalizedLiteral,
})

const qQuantity = (surface, operator, value, normalizedUnit, qualifier, options = {}) => ({
  kind: 'QUANTITY', surface, operator, value,
  ...(options.upperValue === undefined ? {} : { upperValue: options.upperValue }),
  normalizedUnit, qualifier, direction: options.direction ?? 'NONE',
})
const qDate = (surface, operator, options) => ({
  kind: 'DATE', surface, operator,
  ...(options.value === undefined ? {} : { value: options.value }),
  ...(options.start === undefined ? {} : { start: options.start }),
  ...(options.end === undefined ? {} : { end: options.end }),
  precision: options.precision ?? 'FULL_DATE', qualifier: options.qualifier ?? '',
})
const qIdentifierNumber = (surface, identifier, numberSurface) => ({
  kind: 'IDENTIFIER_NUMBER', surface, identifier, numberSurface,
  normalizedSegments: numberSurface.split('.').map((value) => Number(value)),
})
const qLiteral = (surface, normalizedLiteral = surface.normalize('NFKC').toLocaleLowerCase('und')) => ({
  kind: 'LITERAL_IDENTIFIER', surface, normalizedLiteral,
})

const bundles = [
  {
    id: 'SV3-U31', split: 'DEV', professionGroup: 'MARKETING_SALES',
    profession: 'Partner marketing manager', language: 'KO',
    title: '파트너 마케팅 성과 기록', documentType: 'CAREER_REVIEW',
    documentStructure: 'CAREER_DESCRIPTION', template: 'SV3-TC-TEMPLATE-MARKETING-31',
    seed: 'SV3-TC-SEED-MARKETING-31-A7E1',
    facts: [
      { key: 'P01', heading: '검증된 상담 요청', text: '본인은 지역 파트너 캠페인에서 검증된 유효 상담 요청 1,300건을 생성했다.', observations: [quantity('1,300건', 1300, '건', '유효 상담 요청')] },
      { key: 'P02', heading: '설문 데이터 정리', text: '별도의 데이터 정리 작업에서는 설문 응답 데이터 1,300건을 정규화했다.', observations: [quantity('1,300건', 1300, '건', '설문 응답 데이터')] },
      { key: 'P03', heading: '후속 연락 개선', text: '후속 연락 절차를 바꿔 미응답 비율을 65% 감소시켰다.', observations: [quantity('65%', 65, '%', '미응답 비율', 'DECREASE')] },
      { key: 'P04', heading: '다른 지역의 경고 신호', text: '다른 지역에서는 같은 기간 미응답 비율이 65% 증가했다.', observations: [quantity('65%', 65, '%', '미응답 비율', 'INCREASE')] },
      { key: 'P05', heading: '상담 단계 관리', text: 'OrbitCRM에서 파트너 상담 단계와 후속 담당자를 관리했다.', observations: [literal('OrbitCRM')] },
    ],
    queries: [
      { key: 'Q01', text: '유효 상담 요청 1,000건 이상을 만든 경험이 있나요?', answerability: 'SUPPORTED', direct: 'P01', families: ['quantity_positive'], categories: ['numeric_quantity', 'numeric_range_comparison'], constraint: qQuantity('1,000건 이상', 'GTE', 1000, '건', '유효 상담 요청'), states: { P01: 'SATISFIED', P02: 'UNKNOWN' } },
      { key: 'Q02', text: '사용자 1,300명이 이용한 캠페인인가요?', answerability: 'NOT_SUPPORTED', families: ['qualifier_mismatch', 'not_supported_hard_negative'], categories: ['numeric_quantity', 'hard_negative'], constraint: qQuantity('사용자 1,300명', 'EQ', 1300, '명', '사용자'), states: { P01: 'UNKNOWN', P02: 'UNKNOWN' }, diagnostic: 'P02' },
      { key: 'Q03', text: '미응답 비율을 50% 이상 감소시킨 경험이 있나요?', answerability: 'SUPPORTED', direct: 'P03', families: ['percentage_direction', 'quantity_positive'], categories: ['numeric_quantity', 'numeric_range_comparison'], constraint: qQuantity('50% 이상 감소', 'GTE', 50, '%', '미응답 비율', { direction: 'DECREASE' }), states: { P03: 'SATISFIED', P04: 'CONTRADICTED' } },
      { key: 'Q04', text: 'OrbitCRM을 사용해 상담 단계를 관리했나요?', answerability: 'SUPPORTED', direct: 'P05', families: ['literal_exact'], categories: ['literal_identifier'], constraint: qLiteral('OrbitCRM'), states: { P05: 'SATISFIED' } },
    ],
  },
  {
    id: 'SV3-U32', split: 'DEV', professionGroup: 'DESIGN_PRODUCT',
    profession: 'Service designer', language: 'EN', title: 'Booking service case study',
    documentType: 'PORTFOLIO', documentStructure: 'LONG_PORTFOLIO',
    template: 'SV3-TC-TEMPLATE-DESIGN-32', seed: 'SV3-TC-SEED-DESIGN-32-B4D2',
    facts: [
      { key: 'P01', heading: 'Approved booking flow', text: 'The redesigned booking flow achieved an 80% task completion rate in the controlled rollout.', observations: [quantity('80%', 80, '%', 'task completion rate')] },
      { key: 'P02', heading: 'Earlier concept test', text: 'An earlier concept test reached a 72% task completion rate and was not approved.', observations: [quantity('72%', 72, '%', 'task completion rate')] },
      { key: 'P03', heading: 'Service launch', text: 'The approved service launched on 2025-07-15 after the accessibility review.', observations: [dateObservation('2025-07-15', '2025-07-15', '2025-07-15', 'FULL_DATE', 'approved service launch')] },
      { key: 'P04', heading: 'Research archive', text: 'Research notes were archived without a release date or deployment claim.', observations: [] },
    ],
    queries: [
      { key: 'Q01', text: 'Did the approved flow reach at least 80% task completion?', answerability: 'SUPPORTED', direct: 'P01', families: ['quantity_boundary'], categories: ['numeric_quantity', 'numeric_range_comparison'], constraint: qQuantity('at least 80%', 'GTE', 80, '%', 'task completion rate'), states: { P01: 'SATISFIED', P02: 'CONTRADICTED' } },
      { key: 'Q02', text: 'Did the approved flow reach at least 90% task completion?', answerability: 'NOT_SUPPORTED', families: ['wrong_value', 'not_supported_hard_negative'], categories: ['numeric_quantity', 'numeric_range_comparison', 'hard_negative'], constraint: qQuantity('at least 90%', 'GTE', 90, '%', 'task completion rate'), states: { P01: 'CONTRADICTED', P02: 'CONTRADICTED' }, diagnostic: 'P01' },
      { key: 'Q03', text: 'Did the approved service launch after 2025-06-30?', answerability: 'SUPPORTED', direct: 'P03', families: ['date_after'], categories: ['date_range'], constraint: qDate('after 2025-06-30', 'AFTER', { value: '2025-06-30', qualifier: 'approved service launch' }), states: { P03: 'SATISFIED' } },
      { key: 'Q04', text: 'Did the approved service launch before 2025-01-01?', answerability: 'NOT_SUPPORTED', families: ['date_before', 'not_supported_hard_negative'], categories: ['date_range', 'hard_negative'], constraint: qDate('before 2025-01-01', 'BEFORE', { value: '2025-01-01', qualifier: 'approved service launch' }), states: { P03: 'CONTRADICTED' }, diagnostic: 'P03' },
    ],
  },
  {
    id: 'SV3-U33', split: 'DEV', professionGroup: 'NON_DEVELOPMENT_GENERAL',
    profession: 'Community operations coordinator', language: 'KO_EN_MIXED',
    title: 'Community operations 기록', documentType: 'CAREER_REVIEW',
    documentStructure: 'NARRATIVE_SELF_INTRODUCTION',
    template: 'SV3-TC-TEMPLATE-COMMUNITY-33', seed: 'SV3-TC-SEED-COMMUNITY-33-C9F3',
    facts: [
      { key: 'P01', heading: '운영 기간', text: '본인은 community operations를 4년 동안 맡아 정기 인수인계와 현장 점검을 운영했다.', observations: [quantity('4년', 4, '년', 'community operations')] },
      { key: 'P02', heading: '한시적 pilot', text: '한시적 community operations pilot은 2년 동안 운영된 뒤 종료됐다.', observations: [quantity('2년', 2, '년', 'community operations')] },
      { key: 'P03', heading: '교육 키트 1차 점검', text: '분기 점검에서는 75건의 교육 키트를 확인하고 누락 사유를 기록했다.', observations: [quantity('75건', 75, '건', '교육 키트')] },
      { key: 'P04', heading: '교육 키트 2차 점검', text: '다음 분기에는 120건의 교육 키트를 같은 절차로 점검했다.', observations: [quantity('120건', 120, '건', '교육 키트')] },
    ],
    queries: [
      { key: 'Q01', text: 'community operations를 3년 이상 운영한 경험이 있나요?', answerability: 'SUPPORTED', direct: 'P01', families: ['duration', 'quantity_positive'], categories: ['numeric_quantity', 'numeric_range_comparison'], constraint: qQuantity('3년 이상', 'GTE', 3, '년', 'community operations'), states: { P01: 'SATISFIED', P02: 'CONTRADICTED' } },
      { key: 'Q02', text: 'community operations를 5년 이상 운영했나요?', answerability: 'NOT_SUPPORTED', families: ['duration', 'wrong_value', 'not_supported_hard_negative'], categories: ['numeric_quantity', 'numeric_range_comparison', 'hard_negative'], constraint: qQuantity('5년 이상', 'GTE', 5, '년', 'community operations'), states: { P01: 'CONTRADICTED', P02: 'CONTRADICTED' }, diagnostic: 'P01' },
      { key: 'Q03', text: '한 분기에 교육 키트 50~100건을 점검했나요?', answerability: 'SUPPORTED', direct: 'P03', families: ['range'], categories: ['numeric_quantity', 'numeric_range_comparison'], constraint: qQuantity('50~100건', 'RANGE', 50, '건', '교육 키트', { upperValue: 100 }), states: { P03: 'SATISFIED', P04: 'CONTRADICTED' } },
      { key: 'Q04', text: '한 분기에 교육 키트 130~150건을 점검했나요?', answerability: 'NOT_SUPPORTED', families: ['range', 'wrong_value', 'not_supported_hard_negative'], categories: ['numeric_quantity', 'numeric_range_comparison', 'hard_negative'], constraint: qQuantity('130~150건', 'RANGE', 130, '건', '교육 키트', { upperValue: 150 }), states: { P03: 'CONTRADICTED', P04: 'CONTRADICTED' }, diagnostic: 'P04' },
    ],
  },
  {
    id: 'SV3-U34', split: 'CALIBRATION', professionGroup: 'FRONTEND_MOBILE',
    profession: 'Mobile platform engineer', language: 'EN', title: 'Mobile delivery record',
    documentType: 'PROJECT_REPORT', documentStructure: 'PROJECT_DESCRIPTION',
    template: 'SV3-TC-TEMPLATE-MOBILE-34', seed: 'SV3-TC-SEED-MOBILE-34-D2A4',
    facts: [
      { key: 'P01', heading: 'Primary transport', text: 'The mobile gateway negotiated HTTP/2 in production and recorded the selected protocol.', observations: [identifierNumber('HTTP/2', 'HTTP', '2')] },
      { key: 'P02', heading: 'Fallback transport', text: 'A retired fallback endpoint remained on HTTP/1.1 during the migration window.', observations: [identifierNumber('HTTP/1.1', 'HTTP', '1.1')] },
      { key: 'P03', heading: 'Offline queue', text: 'The offline queue used NimbusCache to retain submitted work until connectivity returned.', observations: [literal('NimbusCache')] },
      { key: 'P04', heading: 'Unshipped prototype', text: 'A separate prototype mentioned ZephyrDBX but was never connected to the mobile gateway.', observations: [literal('ZephyrDBX')] },
    ],
    queries: [
      { key: 'Q01', text: 'Did the production gateway use HTTP/2?', answerability: 'SUPPORTED', direct: 'P01', families: ['identifier_number_exact'], categories: ['literal_identifier'], constraint: qIdentifierNumber('HTTP/2', 'HTTP', '2'), states: { P01: 'SATISFIED', P02: 'CONTRADICTED' } },
      { key: 'Q02', text: 'Did the production gateway use HTTP/3?', answerability: 'NOT_SUPPORTED', families: ['identifier_number_mismatch', 'not_supported_hard_negative'], categories: ['literal_identifier', 'hard_negative'], constraint: qIdentifierNumber('HTTP/3', 'HTTP', '3'), states: { P01: 'CONTRADICTED', P02: 'CONTRADICTED' }, diagnostic: 'P01' },
      { key: 'Q03', text: 'Did the offline queue use NimbusCache?', answerability: 'SUPPORTED', direct: 'P03', families: ['literal_exact'], categories: ['literal_identifier'], constraint: qLiteral('NimbusCache'), states: { P03: 'SATISFIED' } },
      { key: 'Q04', text: 'Did the mobile gateway use ZephyrDB?', answerability: 'NOT_SUPPORTED', families: ['literal_near_match', 'not_supported_hard_negative'], categories: ['literal_identifier', 'hard_negative'], constraint: qLiteral('ZephyrDB'), states: { P04: 'UNKNOWN' }, diagnostic: 'P04' },
    ],
  },
  {
    id: 'SV3-U35', split: 'CALIBRATION', professionGroup: 'PLANNING',
    profession: 'Public program planner', language: 'KO', title: '지역 프로그램 기획 기록',
    documentType: 'CAREER_REVIEW', documentStructure: 'CAREER_DESCRIPTION',
    template: 'SV3-TC-TEMPLATE-PLANNING-35', seed: 'SV3-TC-SEED-PLANNING-35-E8B5',
    facts: [
      { key: 'P01', heading: '정책 조사 기간', text: '정책 조사 기간은 2024-03-01부터 2025-02-28까지였고 월별 검토 기록을 남겼다.', observations: [dateObservation('2024-03-01부터 2025-02-28까지', '2024-03-01', '2025-02-28', 'FULL_DATE', '정책 조사 기간')] },
      { key: 'P02', heading: '전국 적용 시작', text: '전국 rollout은 2025-07-01에 시작됐고 지역별 담당자가 적용 상태를 확인했다.', observations: [dateObservation('2025-07-01', '2025-07-01', '2025-07-01', 'FULL_DATE', '전국 rollout 시작')] },
      { key: 'P03', heading: '계획 관리 도구', text: '프로그램 일정과 의사결정 기록은 AtlasPlan으로 관리했다.', observations: [literal('AtlasPlan')] },
      { key: 'P04', heading: '미채택 초안', text: '별도 검토 문서에는 NorthStarX라는 초안 명칭만 남아 있었다.', observations: [literal('NorthStarX')] },
    ],
    queries: [
      { key: 'Q01', text: '정책 조사를 2024-03-01부터 2025-02-28까지 진행했나요?', answerability: 'SUPPORTED', direct: 'P01', families: ['date_range'], categories: ['date_range'], constraint: qDate('2024-03-01부터 2025-02-28까지', 'RANGE', { start: '2024-03-01', end: '2025-02-28', qualifier: '정책 조사 기간' }), states: { P01: 'SATISFIED' } },
      { key: 'Q02', text: '전국 rollout이 2025-06-30 이후에 시작됐나요?', answerability: 'SUPPORTED', direct: 'P02', families: ['date_after'], categories: ['date_range'], constraint: qDate('2025-06-30 이후', 'AFTER', { value: '2025-06-30', qualifier: '전국 rollout 시작' }), states: { P01: 'UNKNOWN', P02: 'SATISFIED' } },
      { key: 'Q03', text: '전국 rollout이 2024-12-31 이전에 시작됐나요?', answerability: 'NOT_SUPPORTED', families: ['date_before', 'not_supported_hard_negative'], categories: ['date_range', 'hard_negative'], constraint: qDate('2024-12-31 이전', 'BEFORE', { value: '2024-12-31', qualifier: '전국 rollout 시작' }), states: { P01: 'UNKNOWN', P02: 'CONTRADICTED' }, diagnostic: 'P02' },
      { key: 'Q04', text: 'NorthStar를 실제 계획 관리 도구로 사용했나요?', answerability: 'NOT_SUPPORTED', families: ['literal_near_match', 'not_supported_hard_negative'], categories: ['literal_identifier', 'hard_negative'], constraint: qLiteral('NorthStar'), states: { P04: 'UNKNOWN' }, diagnostic: 'P04' },
    ],
  },
  {
    id: 'SV3-U36', split: 'CALIBRATION', professionGroup: 'DATA_AI_INFRA',
    profession: 'Data reliability engineer', language: 'KO_EN_MIXED',
    title: 'Data reliability 운영 기록', documentType: 'PROJECT_REPORT',
    documentStructure: 'TABLE_LIKE', template: 'SV3-TC-TEMPLATE-DATA-36',
    seed: 'SV3-TC-SEED-DATA-36-F6C6',
    facts: [
      { key: 'P01', heading: 'Current batch runtime', text: 'The production batch service runs on Java 17 and records its runtime version at startup.', observations: [identifierNumber('Java 17', 'Java', '17')] },
      { key: 'P02', heading: 'Retired utility', text: 'A retired import utility remained on Java 11 and was excluded from active processing.', observations: [identifierNumber('Java 11', 'Java', '11')] },
      { key: 'P03', heading: '재처리 오류 개선', text: '검증 단계를 분리한 뒤 재처리 오류율을 정확히 50% 감소시켰다.', observations: [quantity('50%', 50, '%', '재처리 오류율', 'DECREASE')] },
      { key: 'P04', heading: '데이터 검증량', text: 'The import job validated 데이터 2,329건 before publishing the daily report.', observations: [quantity('2,329건', 2329, '건', '데이터')] },
      { key: 'P05', heading: '사용자 preview', text: 'A small preview involved 사용자 300명 and did not proceed to a general release.', observations: [quantity('300명', 300, '명', '사용자')] },
    ],
    queries: [
      { key: 'Q01', text: 'Does the production batch service run on Java 17?', answerability: 'SUPPORTED', direct: 'P01', families: ['identifier_number_exact'], categories: ['literal_identifier'], constraint: qIdentifierNumber('Java 17', 'Java', '17'), states: { P01: 'SATISFIED', P02: 'CONTRADICTED' } },
      { key: 'Q02', text: 'Does the production batch service run on Java 21?', answerability: 'NOT_SUPPORTED', families: ['identifier_number_mismatch', 'not_supported_hard_negative'], categories: ['literal_identifier', 'hard_negative'], constraint: qIdentifierNumber('Java 21', 'Java', '21'), states: { P01: 'CONTRADICTED', P02: 'CONTRADICTED' }, diagnostic: 'P01' },
      { key: 'Q03', text: '재처리 오류율을 50% 이상 감소시켰나요?', answerability: 'SUPPORTED', direct: 'P03', families: ['percentage_direction', 'quantity_boundary'], categories: ['numeric_quantity', 'numeric_range_comparison'], constraint: qQuantity('50% 이상 감소', 'GTE', 50, '%', '재처리 오류율', { direction: 'DECREASE' }), states: { P03: 'SATISFIED' } },
      { key: 'Q04', text: '사용자 2,329명이 이용한 서비스인가요?', answerability: 'NOT_SUPPORTED', families: ['qualifier_mismatch', 'wrong_value', 'not_supported_hard_negative'], categories: ['numeric_quantity', 'hard_negative'], constraint: qQuantity('사용자 2,329명', 'EQ', 2329, '명', '사용자'), states: { P04: 'UNKNOWN', P05: 'CONTRADICTED' }, diagnostic: 'P04' },
    ],
  },
]

function sha256(value) {
  return createHash('sha256').update(value).digest('hex')
}

function stableJson(value) {
  return `${JSON.stringify(value, null, 2)}\n`
}

function normalize(value) {
  return value.normalize('NFKC').toLocaleLowerCase('und')
    .replace(/[\p{P}\p{S}]+/gu, ' ').replace(/\s+/gu, ' ').trim()
}

function codePointLength(value) {
  return Array.from(value).length
}

function codePointOffset(value, utf16Offset) {
  return codePointLength(value.slice(0, utf16Offset))
}

function locateCodePoints(content, surface, label) {
  const first = content.indexOf(surface)
  if (first < 0 || content.indexOf(surface, first + surface.length) >= 0) {
    throw new Error(`${label} must occur exactly once`)
  }
  return [codePointOffset(content, first), codePointOffset(content, first + surface.length)]
}

function lineAt(content, codePointOffsetValue) {
  return Array.from(content).slice(0, codePointOffsetValue).join('').split('\n').length
}

function sourceSpan(bundle, fact, content, kind) {
  const [charStart, charEnd] = locateCodePoints(content, fact.text, `${bundle.id}/${fact.key}`)
  const parentId = `${bundle.id}-${fact.key}`
  return {
    spanId: kind === 'parent' ? `${parentId}-PS01` : `${parentId}-E01-S01`,
    parentId,
    documentId: `${bundle.id}-D01`,
    versionId: `${bundle.id}-D01-V01`,
    sourcePath: `documents/${bundle.id.toLocaleLowerCase('und')}-typed-stress-v01.txt`,
    sourceType: 'TXT_TEXT', page: null, charStart, charEnd,
    lineStart: lineAt(content, charStart), lineEnd: lineAt(content, charEnd),
    text: fact.text, textSha256: sha256(Buffer.from(fact.text, 'utf8')),
  }
}

function observationArtifact(bundle, fact, observation, content, index) {
  const factSpan = sourceSpan(bundle, fact, content, 'evidence')
  const factStartUtf16 = content.indexOf(fact.text)
  const surfaceInFact = fact.text.indexOf(observation.surface)
  if (surfaceInFact < 0 || fact.text.indexOf(observation.surface, surfaceInFact + observation.surface.length) >= 0) {
    throw new Error(`${bundle.id}/${fact.key} observation surface must occur exactly once: ${observation.surface}`)
  }
  const absoluteUtf16 = factStartUtf16 + surfaceInFact
  const charStart = codePointOffset(content, absoluteUtf16)
  const charEnd = codePointOffset(content, absoluteUtf16 + observation.surface.length)
  return {
    observationId: `SV3-TC-${bundle.id.slice(4)}-${fact.key}-O${String(index + 1).padStart(2, '0')}`,
    evidenceUnitId: `${bundle.id}-${fact.key}-E01`, sourceSpanId: factSpan.spanId,
    kind: observation.kind, sourceSurface: observation.surface, charStart, charEnd,
    ...(observation.kind === 'QUANTITY' ? {
      value: observation.value, normalizedUnit: observation.normalizedUnit,
      qualifier: observation.qualifier, direction: observation.direction,
    } : {}),
    ...(observation.kind === 'DATE' ? {
      start: observation.start, end: observation.end, precision: observation.precision,
      qualifier: observation.qualifier,
    } : {}),
    ...(observation.kind === 'IDENTIFIER_NUMBER' ? {
      identifier: observation.identifier, numberSurface: observation.numberSurface,
      normalizedSegments: observation.normalizedSegments,
    } : {}),
    ...(observation.kind === 'LITERAL_IDENTIFIER' ? {
      normalizedLiteral: observation.normalizedLiteral,
    } : {}),
  }
}

function documentContent(bundle) {
  return `${bundle.title}\n\n${bundle.facts.map((fact) => `${fact.heading}\n${fact.text}`).join('\n\n')}\n`
}

function standardNumeric(observation) {
  return {
    normalizedValue: observation.value, unit: observation.normalizedUnit,
    semanticType: 'QUANTITY', sourceSurface: observation.surface,
    qualifierTokens: observation.qualifier.split(/\s+/u).filter(Boolean),
  }
}

function standardDate(observation) {
  const precision = observation.precision === 'FULL_DATE' ? 'DAY' : observation.precision
  return { start: observation.start, end: observation.end, precision, sourceSurface: observation.surface }
}

function standardEntity(observation) {
  if (observation.kind === 'IDENTIFIER_NUMBER') {
    return {
      entityType: 'IDENTIFIER_NUMBER',
      canonicalValue: `${normalize(observation.identifier).replaceAll(' ', '_')}_${observation.numberSurface.replaceAll('.', '_')}`.toUpperCase(),
      surfaceForms: [observation.surface],
    }
  }
  return {
    entityType: 'LITERAL_IDENTIFIER',
    canonicalValue: observation.normalizedLiteral.replaceAll(' ', '_').toUpperCase(),
    surfaceForms: [observation.surface],
  }
}

function standardConstraints(constraint) {
  const result = { entities: [], numerics: [], dates: [], actors: [], completionStates: [], polarity: 'POSITIVE' }
  if (constraint.kind === 'QUANTITY') {
    result.numerics.push({
      operator: constraint.operator === 'RANGE' ? 'BETWEEN' : constraint.operator,
      value: constraint.value,
      ...(constraint.upperValue === undefined ? {} : { upperValue: constraint.upperValue }),
      unit: constraint.normalizedUnit, semanticType: 'QUANTITY',
      qualifierTokens: constraint.qualifier.split(/\s+/u).filter(Boolean),
    })
  } else if (constraint.kind === 'DATE' && constraint.operator === 'RANGE') {
    result.dates.push({
      operator: 'OVERLAPS', start: constraint.start, end: constraint.end,
      precision: constraint.precision === 'FULL_DATE' ? 'DAY' : constraint.precision,
    })
  } else if (constraint.kind === 'IDENTIFIER_NUMBER') {
    result.entities.push(standardEntity(constraint))
  } else if (constraint.kind === 'LITERAL_IDENTIFIER') {
    result.entities.push(standardEntity(constraint))
  }
  return result
}

function materializeBundle(bundle) {
  const content = documentContent(bundle)
  const documentId = `${bundle.id}-D01`
  const versionId = `${documentId}-V01`
  const contentPath = `documents/${bundle.id.toLocaleLowerCase('und')}-typed-stress-v01.txt`
  const document = {
    documentId, logicalDocumentId: `${documentId}-LOGICAL`,
    versionLineageId: `${documentId}-LINEAGE`, versionId, versionNumber: 1, active: true,
    title: bundle.title, documentType: bundle.documentType,
    documentStructure: bundle.documentStructure, fileType: 'TXT', language: bundle.language,
    contentPath, contentSha256: sha256(Buffer.from(content, 'utf8')),
    supportScope: 'SUPPORTED_BY_CURRENT', visibility: 'PUBLIC_FIXTURE',
    provenance: {
      classification: 'SYNTHETIC', license: 'Apache-2.0',
      generatorName: 'materialize-prz028-typed-stress', generatorRevision: GENERATOR_REVISION,
      generatorSeedId: bundle.seed,
    },
  }
  const parents = []
  const groups = []
  const units = []
  const observations = []
  for (const fact of bundle.facts) {
    const parentId = `${bundle.id}-${fact.key}`
    const evidenceUnitId = `${parentId}-E01`
    const groupId = `${bundle.id}-G${fact.key.slice(1)}`
    const parentSpan = sourceSpan(bundle, fact, content, 'parent')
    const evidenceSpan = sourceSpan(bundle, fact, content, 'evidence')
    parents.push({ parentId, userBundleId: bundle.id, documentId, versionId, label: fact.heading, sourceSpan: parentSpan })
    groups.push({
      groupId, userBundleId: bundle.id, sourceFactId: `SV3-TC-FACT-${bundle.id.slice(4)}-${fact.key}`,
      description: fact.heading, evidenceUnitIds: [evidenceUnitId],
    })
    const standardObservations = fact.observations
    units.push({
      evidenceUnitId, userBundleId: bundle.id, parentId, groupId, documentId, versionId,
      sourceFactId: `SV3-TC-FACT-${bundle.id.slice(4)}-${fact.key}`,
      sourceSpans: [evidenceSpan], primarySpanId: evidenceSpan.spanId, contextSpanIds: [],
      actor: 'SELF', completionState: 'COMPLETED', aspects: [`typed_stress_${fact.key.toLocaleLowerCase('und')}`],
      entities: standardObservations.filter((value) => ['IDENTIFIER_NUMBER', 'LITERAL_IDENTIFIER'].includes(value.kind)).map(standardEntity),
      numerics: standardObservations.filter((value) => value.kind === 'QUANTITY').map(standardNumeric),
      dates: standardObservations.filter((value) => value.kind === 'DATE').map(standardDate),
    })
    observations.push(...fact.observations.map((value, index) => observationArtifact(bundle, fact, value, content, index)))
  }

  const queries = []
  const typedQueries = []
  const unitIds = units.map((unit) => unit.evidenceUnitId)
  for (const definition of bundle.queries) {
    const queryId = `${bundle.id}-${definition.key}`
    const aspectId = `typed_${definition.key.toLocaleLowerCase('und')}`
    const directId = definition.direct ? `${bundle.id}-${definition.direct}-E01` : null
    const diagnosticIds = Object.entries(definition.states)
      .filter(([, state]) => state !== 'SATISFIED')
      .map(([factKey, state]) => ({ evidenceUnitId: `${bundle.id}-${factKey}-E01`, state }))
    let expectedEvidence = []
    if (directId) expectedEvidence = [{ evidenceUnitId: directId, supportRelation: 'DIRECT_SUPPORT' }]
    else if (definition.diagnostic) {
      const state = definition.states[definition.diagnostic]
      expectedEvidence = [{
        evidenceUnitId: `${bundle.id}-${definition.diagnostic}-E01`,
        supportRelation: state === 'CONTRADICTED' ? 'CONTRADICTS' : 'INSUFFICIENT',
      }]
    }
    if (!directId && diagnosticIds.length > 1 && definition.key === 'Q04' && bundle.id === 'SV3-U36') {
      expectedEvidence.push({ evidenceUnitId: `${bundle.id}-P05-E01`, supportRelation: 'CONTRADICTS' })
    }
    const categories = [...new Set([...definition.categories, LANGUAGE_CATEGORY[bundle.language]])]
    queries.push({
      queryId, userBundleId: bundle.id, questionGroupId: `${queryId}-FAMILY`,
      query: definition.text, normalizedQuery: normalize(definition.text), categories,
      language: bundle.language, answerability: definition.answerability,
      aspectExpression: { operator: 'ALL', requiredAspectIds: [aspectId], minShouldMatch: 1 },
      aspects: [{
        aspectId, required: true, answerability: definition.answerability,
        expectedEvidence,
        requiredEvidenceGroupIds: directId ? [`${bundle.id}-G${definition.direct.slice(1)}`] : [],
        minEvidenceGroups: directId ? 1 : 0, constraints: standardConstraints(definition.constraint),
      }], safetyExclusions: [],
    })
    const [queryCharStart, queryCharEnd] = locateCodePoints(definition.text, definition.constraint.surface, queryId)
    const expectedStates = unitIds.map((evidenceUnitId) => {
      const factKey = evidenceUnitId.match(/-(P\d{2})-E01$/u)?.[1]
      return { evidenceUnitId, state: definition.states[factKey] ?? 'UNKNOWN' }
    })
    typedQueries.push({
      queryId, userBundleId: bundle.id, stressFamilies: definition.families,
      constraint: {
        constraintId: `${queryId}-C01`, ...definition.constraint,
        queryCharStart, queryCharEnd,
      },
      expectedEvidenceStates: expectedStates,
    })
  }

  return { bundle, content, contentPath, document, parents, groups, units, observations, queries, typedQueries }
}

function countBy(values) {
  return Object.fromEntries([...new Set(values)].sort().map((value) => [
    value, values.filter((candidate) => candidate === value).length,
  ]))
}

function fileEntry(relative, content) {
  const bytes = Buffer.from(content, 'utf8')
  return { path: relative, bytes: bytes.length, sha256: sha256(bytes) }
}

function combinedHash(entries) {
  const record = [...entries].sort((left, right) => left.path.localeCompare(right.path))
    .map((entry) => `${entry.path}\0${entry.sha256}\n`).join('')
  return sha256(Buffer.from(record, 'utf8'))
}

function splitDistributions(materialized) {
  const queries = materialized.flatMap((value) => value.queries)
  const typedQueries = materialized.flatMap((value) => value.typedQueries)
  return {
    profession: countBy(materialized.map((value) => value.bundle.professionGroup)),
    documentLanguage: countBy(materialized.map((value) => value.bundle.language)),
    queryLanguage: countBy(queries.map((value) => value.language)),
    answerability: countBy(queries.map((value) => value.answerability)),
    category: countBy(queries.flatMap((value) => value.categories)),
    typedKind: countBy(typedQueries.map((value) => value.constraint.kind)),
    stressFamily: countBy(typedQueries.flatMap((value) => value.stressFamilies)),
    expectedState: countBy(typedQueries.flatMap((value) => value.expectedEvidenceStates.map((state) => state.state))),
  }
}

function buildArtifacts() {
  const files = new Map()
  const allMaterialized = bundles.map(materializeBundle)
  const splitHashes = {}
  for (const split of ['DEV', 'CALIBRATION']) {
    const materialized = allMaterialized.filter((value) => value.bundle.split === split)
    const directory = split === 'DEV' ? 'dev' : 'calibration'
    for (const value of materialized) files.set(`${directory}/${value.contentPath}`, value.content)
    const corpus = {
      artifactType: 'CORPUS', schemaVersion: SCHEMA_VERSION, datasetVersion: DATASET_VERSION, split,
      userBundles: materialized.map((value) => ({
        userBundleId: value.bundle.id, split,
        professionGroup: value.bundle.professionGroup, profession: value.bundle.profession,
        languageProfile: value.bundle.language,
        documentFamilyId: `${value.bundle.id}-DOC-FAMILY`, templateFamilyId: value.bundle.template,
        documents: [value.document],
      })),
    }
    const gold = {
      artifactType: 'GOLD_EVIDENCE', schemaVersion: SCHEMA_VERSION,
      datasetVersion: DATASET_VERSION, split,
      parents: materialized.flatMap((value) => value.parents),
      evidenceGroups: materialized.flatMap((value) => value.groups),
      evidenceUnits: materialized.flatMap((value) => value.units),
    }
    const questions = {
      artifactType: 'QUESTIONS', schemaVersion: SCHEMA_VERSION,
      datasetVersion: DATASET_VERSION, split,
      queries: materialized.flatMap((value) => value.queries),
    }
    const typed = {
      artifactType: 'TYPED_ANNOTATIONS', schemaVersion: TYPED_SCHEMA_VERSION,
      datasetVersion: DATASET_VERSION, split,
      observations: materialized.flatMap((value) => value.observations),
      queryAnnotations: materialized.flatMap((value) => value.typedQueries),
    }
    files.set(`${directory}/corpus.json`, stableJson(corpus))
    files.set(`${directory}/gold-evidence.json`, stableJson(gold))
    files.set(`${directory}/questions.json`, stableJson(questions))
    files.set(`${directory}/typed-annotations.json`, stableJson(typed))
    const relativeInputs = [...files.keys()]
      .filter((relative) => relative.startsWith(`${directory}/`))
      .sort()
    const entries = relativeInputs.map((relative) => fileEntry(relative.slice(directory.length + 1), files.get(relative)))
    const counts = {
      userBundles: materialized.length, documents: materialized.length,
      queries: questions.queries.length, typedQueryAnnotations: typed.queryAnnotations.length,
      evidenceParents: gold.parents.length, evidenceGroups: gold.evidenceGroups.length,
      evidenceUnits: gold.evidenceUnits.length, typedObservations: typed.observations.length,
    }
    const manifest = {
      artifactType: 'MANIFEST', schemaVersion: SCHEMA_VERSION, datasetVersion: DATASET_VERSION,
      split, status: 'FRESH_BENCHMARK_SEED_FROZEN', mutable: false,
      opened: true, searchExecuted: false, sealedAt: null, frozenAt: FROZEN_AT,
      generationSourceRevision: MATERIALIZATION_BASE_REVISION,
      generatorRevision: GENERATOR_REVISION, counts,
      distributions: splitDistributions(materialized), files: entries,
      combinedSha256: combinedHash(entries),
    }
    splitHashes[split] = manifest.combinedSha256
    files.set(`${directory}/manifest.json`, stableJson(manifest))
  }

  const lineage = {
    artifactType: 'LINEAGE', schemaVersion: SCHEMA_VERSION, datasetVersion: DATASET_VERSION,
    changeReason: 'Freeze independent DEV/CAL typed-constraint inputs before PRZ-028 implementation.',
    generator: 'scripts/evaluation/search-v3/materialize-prz028-typed-stress.mjs',
    generatorRevision: GENERATOR_REVISION,
    materializationBaseRevision: MATERIALIZATION_BASE_REVISION,
    sealedFinalPolicy: 'SEALED_FINAL_TEST is not copied, opened, or searched; only manifest metadata is checked.',
    bundles: allMaterialized.map((value) => ({
      userBundleId: value.bundle.id, split: value.bundle.split,
      documentFamilyId: `${value.bundle.id}-DOC-FAMILY`, templateFamilyId: value.bundle.template,
      generatorSeedId: value.bundle.seed,
      logicalDocumentIds: [value.document.logicalDocumentId],
      versionLineageIds: [value.document.versionLineageId],
      sourceFactIds: value.units.map((unit) => unit.sourceFactId),
      sourceFactSignatures: value.units.map((unit) => sha256(Buffer.from(normalize(unit.sourceSpans.map((span) => span.text).join(' ')), 'utf8'))),
      questionGroupIds: value.queries.map((query) => query.questionGroupId),
      normalizedQueries: value.queries.map((query) => query.normalizedQuery),
    })),
  }
  files.set('lineage.json', stableJson(lineage))
  files.set('README.md', `# PRZ-028 Typed Constraint Stress DEV/CAL\n\n- dataset: \`${DATASET_VERSION}\`\n- status: \`FRESH_BENCHMARK_SEED_FROZEN\`\n- scope: synthetic DEV/CAL only; no personal data\n- queries: 24 (DEV 12, CALIBRATION 12)\n- purpose: freeze typed constraints, source-grounded observations, and expected match states before implementation\n- SEALED FINAL: not copied, not opened, not searched\n- generator: \`scripts/evaluation/search-v3/materialize-prz028-typed-stress.mjs\`\n\nRun \`node scripts/evaluation/search-v3/materialize-prz028-typed-stress.mjs --check\` for deterministic byte, hash, lineage, grounding, and metadata-only SEALED FINAL verification. A non-check run refuses to overwrite this frozen directory.\n`)

  const rootInputs = [...files.keys()].sort()
  const rootEntries = rootInputs.map((relative) => fileEntry(relative, files.get(relative)))
  const allQueries = allMaterialized.flatMap((value) => value.queries)
  const allTyped = allMaterialized.flatMap((value) => value.typedQueries)
  const allUnits = allMaterialized.flatMap((value) => value.units)
  const rootManifest = {
    artifactType: 'MANIFEST', schemaVersion: SCHEMA_VERSION, datasetVersion: DATASET_VERSION,
    split: 'ALL', status: 'FRESH_BENCHMARK_SEED_FROZEN', mutable: false,
    opened: true, searchExecuted: false, sealedAt: null, frozenAt: FROZEN_AT,
    generationSourceRevision: MATERIALIZATION_BASE_REVISION,
    generatorRevision: GENERATOR_REVISION,
    previousDatasets: [
      'search-v3-fresh-seed-1.0.1', 'search-v3-fresh-devcal-1.1.0',
      'search-v3-fresh-devcal-robustness-1.0.0',
    ],
    splitCombinedSha256: splitHashes,
    counts: {
      userBundles: allMaterialized.length, documents: allMaterialized.length,
      queries: allQueries.length, typedQueryAnnotations: allTyped.length,
      evidenceParents: allUnits.length, evidenceGroups: allUnits.length,
      evidenceUnits: allUnits.length,
      typedObservations: allMaterialized.flatMap((value) => value.observations).length,
    },
    distributions: {
      split: countBy(allMaterialized.map((value) => value.bundle.split)),
      profession: countBy(allMaterialized.map((value) => value.bundle.professionGroup)),
      documentLanguage: countBy(allMaterialized.map((value) => value.bundle.language)),
      queryLanguage: countBy(allQueries.map((value) => value.language)),
      answerability: countBy(allQueries.map((value) => value.answerability)),
      category: countBy(allQueries.flatMap((value) => value.categories)),
      typedKind: countBy(allTyped.map((value) => value.constraint.kind)),
      stressFamily: countBy(allTyped.flatMap((value) => value.stressFamilies)),
      expectedState: countBy(allTyped.flatMap((value) => value.expectedEvidenceStates.map((state) => state.state))),
    },
    files: rootEntries, combinedSha256: combinedHash(rootEntries),
  }
  files.set('manifest.json', stableJson(rootManifest))
  return files
}

function detectForbiddenRuntimeKeys(value, location, findings) {
  if (Array.isArray(value)) {
    value.forEach((child, index) => detectForbiddenRuntimeKeys(child, `${location}[${index}]`, findings))
    return
  }
  if (value === null || typeof value !== 'object') return
  for (const [key, child] of Object.entries(value)) {
    if (FORBIDDEN_RUNTIME_KEYS.has(key)) findings.push(`${location}.${key} is a forbidden runtime identifier`)
    detectForbiddenRuntimeKeys(child, `${location}.${key}`, findings)
  }
}

function collisionFindings(current, previous, prefix) {
  const findings = []
  const priorBundles = previous.bundles.filter((value) => value.split !== 'SEALED_FINAL_TEST')
  for (const field of ['userBundleId', 'documentFamilyId', 'templateFamilyId', 'generatorSeedId']) {
    const known = new Set(priorBundles.map((value) => value[field]))
    if (current.bundles.some((value) => known.has(value[field]))) findings.push(`${prefix} collision: ${field}`)
  }
  for (const field of [
    'logicalDocumentIds', 'versionLineageIds', 'sourceFactIds',
    'sourceFactSignatures', 'questionGroupIds', 'normalizedQueries',
  ]) {
    const known = new Set(priorBundles.flatMap((value) => value[field]))
    if (current.bundles.flatMap((value) => value[field]).some((value) => known.has(value))) {
      findings.push(`${prefix} collision: ${field}`)
    }
  }
  return findings
}

function validateInMemory(files) {
  const findings = []
  const parsed = (relative) => JSON.parse(files.get(relative))
  const rootManifest = parsed('manifest.json')
  const lineage = parsed('lineage.json')
  const queryIds = new Set()
  const unitIds = new Set()
  const observationIds = new Set()
  const allFamilies = new Set()
  let bundlesCount = 0
  let documentsCount = 0
  let queriesCount = 0
  let annotationsCount = 0
  let supportedCount = 0

  for (const directory of ['dev', 'calibration']) {
    const corpus = parsed(`${directory}/corpus.json`)
    const gold = parsed(`${directory}/gold-evidence.json`)
    const questions = parsed(`${directory}/questions.json`)
    const typed = parsed(`${directory}/typed-annotations.json`)
    const manifest = parsed(`${directory}/manifest.json`)
    const split = directory === 'dev' ? 'DEV' : 'CALIBRATION'
    if ([corpus.split, gold.split, questions.split, typed.split, manifest.split].some((value) => value !== split)) {
      findings.push(`${directory} split mismatch`)
    }
    if (corpus.userBundles.length !== 3 || questions.queries.length !== 12 || typed.queryAnnotations.length !== 12) {
      findings.push(`${directory} must contain 3 bundles and 12 queries/annotations`)
    }
    bundlesCount += corpus.userBundles.length
    documentsCount += corpus.userBundles.flatMap((bundle) => bundle.documents).length
    queriesCount += questions.queries.length
    annotationsCount += typed.queryAnnotations.length
    const documentMap = new Map()
    for (const bundle of corpus.userBundles) {
      for (const document of bundle.documents) {
        const content = files.get(`${directory}/${document.contentPath}`)
        if (content === undefined || sha256(Buffer.from(content, 'utf8')) !== document.contentSha256) {
          findings.push(`${document.versionId} content hash mismatch`)
        }
        documentMap.set(document.versionId, { document, content, userBundleId: bundle.userBundleId })
      }
    }
    const parentMap = new Map(gold.parents.map((value) => [value.parentId, value]))
    const unitMap = new Map(gold.evidenceUnits.map((value) => [value.evidenceUnitId, value]))
    for (const unit of gold.evidenceUnits) {
      if (unitIds.has(unit.evidenceUnitId)) findings.push(`duplicate evidence unit ${unit.evidenceUnitId}`)
      unitIds.add(unit.evidenceUnitId)
      const source = documentMap.get(unit.versionId)
      const parent = parentMap.get(unit.parentId)
      if (!source || !parent || source.userBundleId !== unit.userBundleId) {
        findings.push(`${unit.evidenceUnitId} graph mismatch`)
        continue
      }
      for (const span of unit.sourceSpans) {
        const actual = Array.from(source.content).slice(span.charStart, span.charEnd).join('')
        if (actual !== span.text || sha256(Buffer.from(actual, 'utf8')) !== span.textSha256) {
          findings.push(`${span.spanId} source span mismatch`)
        }
        if (span.charStart < parent.sourceSpan.charStart || span.charEnd > parent.sourceSpan.charEnd) {
          findings.push(`${span.spanId} escapes parent`)
        }
      }
    }
    for (const observation of typed.observations) {
      if (observationIds.has(observation.observationId)) findings.push(`duplicate observation ${observation.observationId}`)
      observationIds.add(observation.observationId)
      const unit = unitMap.get(observation.evidenceUnitId)
      if (!unit || !TYPED_KINDS.has(observation.kind)) {
        findings.push(`${observation.observationId} references invalid evidence/kind`)
        continue
      }
      const source = documentMap.get(unit.versionId)
      const actual = Array.from(source.content).slice(observation.charStart, observation.charEnd).join('')
      if (actual !== observation.sourceSurface) findings.push(`${observation.observationId} source offset mismatch`)
      const span = unit.sourceSpans.find((value) => value.spanId === observation.sourceSpanId)
      if (!span || observation.charStart < span.charStart || observation.charEnd > span.charEnd) {
        findings.push(`${observation.observationId} escapes evidence source span`)
      }
    }
    const annotationMap = new Map(typed.queryAnnotations.map((value) => [value.queryId, value]))
    for (const query of questions.queries) {
      if (queryIds.has(query.queryId)) findings.push(`duplicate query ${query.queryId}`)
      queryIds.add(query.queryId)
      if (query.answerability === 'SUPPORTED') supportedCount += 1
      const annotation = annotationMap.get(query.queryId)
      if (!annotation || annotation.userBundleId !== query.userBundleId) {
        findings.push(`${query.queryId} missing typed annotation`)
        continue
      }
      annotation.stressFamilies.forEach((value) => allFamilies.add(value))
      const surface = Array.from(query.query).slice(
        annotation.constraint.queryCharStart, annotation.constraint.queryCharEnd,
      ).join('')
      if (surface !== annotation.constraint.surface || !TYPED_KINDS.has(annotation.constraint.kind)) {
        findings.push(`${query.queryId} constraint source offset/kind mismatch`)
      }
      const bundleUnitIds = gold.evidenceUnits
        .filter((unit) => unit.userBundleId === query.userBundleId)
        .map((unit) => unit.evidenceUnitId).sort()
      const stateUnitIds = annotation.expectedEvidenceStates.map((value) => value.evidenceUnitId).sort()
      if (JSON.stringify(bundleUnitIds) !== JSON.stringify(stateUnitIds)) {
        findings.push(`${query.queryId} must label every evidence unit in its bundle exactly once`)
      }
      if (annotation.expectedEvidenceStates.some((value) => !MATCH_STATES.has(value.state))) {
        findings.push(`${query.queryId} has invalid expected match state`)
      }
      const satisfied = annotation.expectedEvidenceStates.filter((value) => value.state === 'SATISFIED')
      const direct = query.aspects.flatMap((aspect) => aspect.expectedEvidence)
        .filter((value) => value.supportRelation === 'DIRECT_SUPPORT')
      if (query.answerability === 'SUPPORTED' && (satisfied.length < 1 || direct.length < 1)) {
        findings.push(`${query.queryId} SUPPORTED must have SATISFIED and DIRECT_SUPPORT`)
      }
      if (query.answerability === 'NOT_SUPPORTED' && (satisfied.length > 0 || direct.length > 0)) {
        findings.push(`${query.queryId} NOT_SUPPORTED cannot have SATISFIED/DIRECT_SUPPORT`)
      }
    }
    const entries = manifest.files
    for (const entry of entries) {
      const content = files.get(`${directory}/${entry.path}`)
      if (content === undefined || Buffer.byteLength(content, 'utf8') !== entry.bytes
          || sha256(Buffer.from(content, 'utf8')) !== entry.sha256) {
        findings.push(`${directory} manifest mismatch: ${entry.path}`)
      }
    }
    if (combinedHash(entries) !== manifest.combinedSha256 || manifest.mutable !== false) {
      findings.push(`${directory} manifest freeze/hash mismatch`)
    }
  }
  if (bundlesCount !== 6 || documentsCount !== 6 || queriesCount !== 24 || annotationsCount !== 24) {
    findings.push('root counts must be 6 bundles/documents and 24 queries/annotations')
  }
  if (supportedCount < 10 || supportedCount > 14) findings.push('answerability balance is outside 10..14 supported')
  for (const family of REQUIRED_FAMILIES) if (!allFamilies.has(family)) findings.push(`missing stress family ${family}`)
  const professionGroups = bundles.map((value) => value.professionGroup)
  if (professionGroups.filter((value) => value === 'BACKEND').length > 0) findings.push('stress set must not be backend-centered')
  if (!['KO', 'EN', 'KO_EN_MIXED'].every((language) => bundles.some((value) => value.language === language))) {
    findings.push('language coverage is incomplete')
  }
  if (new Set(lineage.bundles.map((value) => value.userBundleId)).size !== 6) findings.push('lineage bundle IDs are not unique')
  for (const field of [
    'logicalDocumentIds', 'versionLineageIds', 'sourceFactIds',
    'sourceFactSignatures', 'questionGroupIds', 'normalizedQueries',
  ]) {
    const values = lineage.bundles.flatMap((value) => value[field])
    if (new Set(values).size !== values.length) findings.push(`lineage duplicate: ${field}`)
  }
  detectForbiddenRuntimeKeys({ lineage, typed: ['dev', 'calibration'].map((value) => parsed(`${value}/typed-annotations.json`)) }, 'stress', findings)
  const rootEntries = rootManifest.files
  for (const entry of rootEntries) {
    const content = files.get(entry.path)
    if (content === undefined || Buffer.byteLength(content, 'utf8') !== entry.bytes
        || sha256(Buffer.from(content, 'utf8')) !== entry.sha256) {
      findings.push(`root manifest mismatch: ${entry.path}`)
    }
  }
  if (combinedHash(rootEntries) !== rootManifest.combinedSha256 || rootManifest.mutable !== false) {
    findings.push('root manifest freeze/hash mismatch')
  }
  const declared = rootEntries.map((value) => value.path).sort()
  const actual = [...files.keys()].filter((value) => value !== 'manifest.json').sort()
  if (JSON.stringify(declared) !== JSON.stringify(actual)) findings.push('root manifest inventory mismatch')
  return findings
}

async function externalIntegrityFindings(files) {
  const findings = []
  const current = JSON.parse(files.get('lineage.json'))
  const previousPaths = [
    ['original DEV/CAL', path.resolve('src/test/resources/search-v3-evaluation/lineage.json')],
    ['long-form 1.1.0', path.resolve('src/searchEvaluation/resources/search-v3-evaluation/devcal-1.1.0/lineage.json')],
    ['robustness 1.0.0', path.resolve('src/searchEvaluation/resources/search-v3-evaluation/devcal-robustness-1.0.0/lineage.json')],
  ]
  for (const [label, location] of previousPaths) {
    const previous = JSON.parse(await readFile(location, 'utf8'))
    findings.push(...collisionFindings(current, previous, label))
  }
  const sealed = JSON.parse(await readFile(SEALED_MANIFEST, 'utf8'))
  if (sealed.combinedSha256 !== SEALED_FINAL_SHA256
      || sealed.opened !== false || sealed.searchExecuted !== false) {
    findings.push('SEALED FINAL manifest metadata changed')
  }
  return findings
}

async function listFiles(root, current = '') {
  const directory = path.join(root, current)
  const entries = await readdir(directory, { withFileTypes: true }).catch(() => [])
  const result = []
  for (const entry of entries) {
    const relative = path.posix.join(current.replaceAll('\\', '/'), entry.name)
    if (entry.isDirectory()) result.push(...await listFiles(root, relative))
    else result.push(relative)
  }
  return result.sort()
}

async function check(files) {
  const findings = [...validateInMemory(files), ...await externalIntegrityFindings(files)]
  for (const [relative, expected] of files) {
    const actual = await readFile(path.join(OUTPUT_ROOT, relative), 'utf8').catch(() => null)
    if (actual !== expected) findings.push(`content mismatch: ${relative}`)
  }
  const actualFiles = await listFiles(OUTPUT_ROOT)
  if (JSON.stringify(actualFiles) !== JSON.stringify([...files.keys()].sort())) {
    findings.push('materialized file inventory mismatch')
  }
  if (findings.length > 0) {
    findings.forEach((value) => console.error(`FAIL ${value}`))
    process.exitCode = 1
    return
  }
  const manifest = JSON.parse(files.get('manifest.json'))
  console.log(`PASS dataset=${DATASET_VERSION}`)
  console.log(`PASS files=${files.size}`)
  console.log(`PASS bundles=${manifest.counts.userBundles}`)
  console.log(`PASS queries=${manifest.counts.queries}`)
  console.log(`PASS typedObservations=${manifest.counts.typedObservations}`)
  console.log(`PASS combinedSha256=${manifest.combinedSha256}`)
  console.log(`PASS sealedFinal=${SEALED_FINAL_SHA256} opened=false searchExecuted=false`)
}

async function materialize(files) {
  if (existsSync(OUTPUT_ROOT)) {
    const frozenManifest = path.join(OUTPUT_ROOT, 'manifest.json')
    const status = existsSync(frozenManifest)
      ? JSON.parse(readFileSync(frozenManifest, 'utf8')).status
      : 'existing directory without manifest'
    throw new Error(`Refusing to overwrite ${OUTPUT_ROOT} (${status}); use --check or a new dataset version`)
  }
  const findings = [...validateInMemory(files), ...await externalIntegrityFindings(files)]
  if (findings.length > 0) throw new Error(`Refusing to materialize invalid stress set:\n${findings.join('\n')}`)
  for (const [relative, content] of files) {
    const destination = path.join(OUTPUT_ROOT, relative)
    await mkdir(path.dirname(destination), { recursive: true })
    await writeFile(destination, content, 'utf8')
  }
  console.log(`Materialized ${DATASET_VERSION} at ${OUTPUT_ROOT}`)
}

const files = buildArtifacts()
if (process.argv.includes('--check')) await check(files)
else await materialize(files)
