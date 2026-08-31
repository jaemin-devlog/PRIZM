import { createHash } from 'node:crypto'
import { existsSync, readFileSync } from 'node:fs'
import { mkdir, readFile, readdir, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const DATASET_VERSION = 'search-v3-typed-constraints-stress-1.1.0'
const SCHEMA_VERSION = '1.0.0'
const TYPED_SCHEMA_VERSION = '1.1.0'
const CONTRACT_VERSION = '1.1.0'
const STATUS = 'INPUT_FROZEN'
const FROZEN_AT = '2026-08-31T20:41:06+09:00'
const INPUT_BASELINE_REVISION = 'd195f3bd8645bef88964ecf033a5815626d1004c'
const GENERATOR_REVISION = 'prz028-typed-capability-stress-v1'
const GENERATOR_NAME = 'materialize-prz028-typed-capability-stress'
const SEALED_FINAL_SHA256 = 'e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383'
const PRECOMMIT_CORRECTION_FROM_SHA256 = '1cd08b74de10698f6313ac722aa6838b3ea5dcf5d6e4649251358464b1f1ab3f'
const OUTPUT_ROOT = path.resolve(
  'src/searchEvaluation/resources/search-v3-evaluation/typed-constraints-stress-1.1.0',
)
const SEALED_MANIFEST = path.resolve(
  'src/test/resources/search-v3-evaluation/sealed-final/manifest.json',
)
const GENERATOR_PATH = fileURLToPath(import.meta.url)

const PRIMARY_FAMILIES = [
  'quantity_wrong_value',
  'qualifier_mismatch',
  'date',
  'identifier_number',
  'percentage_direction',
  'range_boundary',
]
const MATCH_STATES = ['SATISFIED', 'CONTRADICTED', 'UNKNOWN']
const EXPECTED_REASONS = [
  'MATCHED',
  'VALUE_MISMATCH',
  'DIRECTION_MISMATCH',
  'QUALIFIER_MISMATCH',
  'UNIT_MISMATCH',
  'NO_MATCHING_OBSERVATION',
  'AMBIGUOUS_OBSERVATION',
]
const TYPED_KINDS = ['QUANTITY', 'DATE', 'IDENTIFIER_NUMBER']
const LANGUAGE_CATEGORY = { KO: 'korean', EN: 'english', KO_EN_MIXED: 'korean_english_mixed' }
const FORBIDDEN_RUNTIME_KEYS = new Set([
  'chunkId', 'expectedChunkId', 'runtimeChunkId', 'runtimeParentId',
  'databaseParentId', 'dbChunkId', 'retrievalPassageId',
])

const satisfied = () => ({ state: 'SATISFIED', reason: 'MATCHED' })
const valueMismatch = () => ({ state: 'CONTRADICTED', reason: 'VALUE_MISMATCH' })
const directionMismatch = () => ({ state: 'CONTRADICTED', reason: 'DIRECTION_MISMATCH' })
const qualifierMismatch = () => ({ state: 'UNKNOWN', reason: 'QUALIFIER_MISMATCH' })
const unitMismatch = () => ({ state: 'UNKNOWN', reason: 'UNIT_MISMATCH' })
const noObservation = () => ({ state: 'UNKNOWN', reason: 'NO_MATCHING_OBSERVATION' })

const quantity = (surface, value, normalizedUnit, qualifier, options = {}) => ({
  kind: 'QUANTITY', surface, value, normalizedUnit, qualifier,
  direction: options.direction ?? 'NONE',
  ...(options.directionSurface === undefined ? {} : { directionSurface: options.directionSurface }),
})
const dateObservation = (surface, start, end, qualifier) => ({
  kind: 'DATE', surface, start, end, precision: 'FULL_DATE', qualifier,
})
const identifierNumber = (surface, identifier, numberSurface) => ({
  kind: 'IDENTIFIER_NUMBER', surface, identifier, numberSurface,
  normalizedSegments: numberSurface.split('.').map((value) => Number(value)),
})

const qQuantity = (surface, operator, value, normalizedUnit, qualifier, options = {}) => ({
  kind: 'QUANTITY', surface, operator, value,
  ...(options.upperValue === undefined ? {} : { upperValue: options.upperValue }),
  normalizedUnit, qualifier, direction: options.direction ?? 'NONE',
  ...(options.directionSurface === undefined ? {} : { directionSurface: options.directionSurface }),
})
const qDate = (surface, operator, value, qualifier) => ({
  kind: 'DATE', surface, operator, value, precision: 'FULL_DATE', qualifier,
})
const qIdentifierNumber = (surface, identifier, numberSurface) => ({
  kind: 'IDENTIFIER_NUMBER', surface, identifier, numberSurface,
  normalizedSegments: numberSurface.split('.').map((value) => Number(value)),
})

const bundles = [
  {
    id: 'SV3-U41', split: 'DEV', professionGroup: 'NON_DEVELOPMENT_GENERAL',
    profession: 'Parcel claims coordinator', language: 'KO',
    title: '소포 보상 처리 기록', documentType: 'CAREER_REVIEW',
    documentStructure: 'CAREER_DESCRIPTION',
    template: 'SV3-TC11-TEMPLATE-PARCEL-41', seed: 'SV3-TC11-SEED-PARCEL-41-H2K7',
    facts: [
      {
        key: 'P01', heading: '1분기 보상 처리',
        text: '1분기에는 파손 소포 보상 요청 420건을 종결하고 처리 사유를 기록했다.',
        observations: [quantity('420건', 420, '건', '파손 소포 보상 요청')],
      },
      {
        key: 'P02', heading: '2분기 보상 처리',
        text: '2분기에는 파손 소포 보상 요청 680건을 종결하고 회수 절차를 안내했다.',
        observations: [quantity('680건', 680, '건', '파손 소포 보상 요청')],
      },
      {
        key: 'P03', heading: '배송 라벨 정정',
        text: '같은 분기에는 배송 라벨 680건을 재발행해 주소 정정 요청을 반영했다.',
        observations: [quantity('680건', 680, '건', '배송 라벨')],
      },
    ],
    queries: [
      {
        key: 'Q01', text: '파손 소포 보상 요청을 600건 이상 종결한 경험이 있나요?',
        answerability: 'SUPPORTED', direct: 'P02', primaryFamily: 'quantity_wrong_value',
        categories: ['numeric_quantity', 'numeric_range_comparison'],
        constraint: qQuantity('600건 이상', 'GTE', 600, '건', '파손 소포 보상 요청'),
        states: { P01: valueMismatch(), P02: satisfied(), P03: qualifierMismatch() },
      },
      {
        key: 'Q02', text: '파손 소포 보상 요청을 정확히 680건 종결한 기간이 있나요?',
        answerability: 'SUPPORTED', direct: 'P02', primaryFamily: 'qualifier_mismatch',
        categories: ['numeric_quantity'],
        constraint: qQuantity('680건', 'EQ', 680, '건', '파손 소포 보상 요청'),
        states: { P01: valueMismatch(), P02: satisfied(), P03: qualifierMismatch() },
      },
      {
        key: 'Q03', text: '파손 소포 보상 요청을 420건 이하 종결한 기간이 있나요?',
        answerability: 'SUPPORTED', direct: 'P01', primaryFamily: 'range_boundary',
        categories: ['numeric_quantity', 'numeric_range_comparison'],
        constraint: qQuantity('420건 이하', 'LTE', 420, '건', '파손 소포 보상 요청'),
        states: { P01: satisfied(), P02: valueMismatch(), P03: qualifierMismatch() },
      },
      {
        key: 'Q04', text: '파손 소포 보상 요청을 700건 초과 종결한 기간이 있나요?',
        answerability: 'NOT_SUPPORTED', diagnostic: 'P02', primaryFamily: 'quantity_wrong_value',
        categories: ['numeric_quantity', 'numeric_range_comparison', 'hard_negative'],
        constraint: qQuantity('700건 초과', 'GT', 700, '건', '파손 소포 보상 요청'),
        states: { P01: valueMismatch(), P02: valueMismatch(), P03: qualifierMismatch() },
      },
    ],
  },
  {
    id: 'SV3-U42', split: 'DEV', professionGroup: 'DESIGN_PRODUCT',
    profession: 'Exhibition experience designer', language: 'EN',
    title: 'Exhibition wayfinding case notes', documentType: 'PORTFOLIO',
    documentStructure: 'LONG_PORTFOLIO',
    template: 'SV3-TC11-TEMPLATE-EXHIBIT-42', seed: 'SV3-TC11-SEED-EXHIBIT-42-J5M8',
    facts: [
      {
        key: 'P01', heading: 'Permanent exhibit outcome',
        text: 'For the permanent exhibit, the wayfinding error rate decreased by 38% after the floor markers were revised.',
        observations: [quantity('38%', 38, '%', 'wayfinding error rate', { direction: 'DECREASE', directionSurface: 'decreased' })],
      },
      {
        key: 'P02', heading: 'Temporary installation outcome',
        text: 'During a temporary installation, the wayfinding error rate increased by 38% when directional signs were removed.',
        observations: [quantity('38%', 38, '%', 'wayfinding error rate', { direction: 'INCREASE', directionSurface: 'increased' })],
      },
      {
        key: 'P03', heading: 'Fabrication review',
        text: 'In the fabrication review, the installation defect rate decreased by 38% after a checklist change.',
        observations: [quantity('38%', 38, '%', 'installation defect rate', { direction: 'DECREASE', directionSurface: 'decreased' })],
      },
      {
        key: 'P04', heading: 'Primary gallery timeline',
        text: 'The primary gallery redesign continued for 10 months from research through handoff.',
        observations: [quantity('10 months', 10, 'months', 'primary gallery redesign')],
      },
      {
        key: 'P05', heading: 'Earlier gallery concept',
        text: 'An earlier primary gallery redesign concept ran for 6 months before it was discontinued.',
        observations: [quantity('6 months', 6, 'months', 'primary gallery redesign')],
      },
    ],
    queries: [
      {
        key: 'Q01', text: 'Did the permanent exhibit decrease the wayfinding error rate by at least 38%?',
        answerability: 'SUPPORTED', direct: 'P01', primaryFamily: 'percentage_direction',
        categories: ['numeric_quantity', 'numeric_range_comparison'],
        constraint: qQuantity('at least 38%', 'GTE', 38, '%', 'wayfinding error rate', { direction: 'DECREASE', directionSurface: 'decrease' }),
        states: { P01: satisfied(), P02: directionMismatch(), P03: qualifierMismatch(), P04: unitMismatch(), P05: unitMismatch() },
      },
      {
        key: 'Q02', text: 'Did any installation increase the wayfinding error rate by more than 30%?',
        answerability: 'SUPPORTED', direct: 'P02', primaryFamily: 'percentage_direction',
        categories: ['numeric_quantity', 'numeric_range_comparison'],
        constraint: qQuantity('more than 30%', 'GT', 30, '%', 'wayfinding error rate', { direction: 'INCREASE', directionSurface: 'increase' }),
        states: { P01: directionMismatch(), P02: satisfied(), P03: qualifierMismatch(), P04: unitMismatch(), P05: unitMismatch() },
      },
      {
        key: 'Q03', text: 'Did the primary gallery redesign last between 9 and 11 months?',
        answerability: 'SUPPORTED', direct: 'P04', primaryFamily: 'range_boundary',
        categories: ['numeric_quantity', 'numeric_range_comparison'],
        constraint: qQuantity('between 9 and 11 months', 'RANGE', 9, 'months', 'primary gallery redesign', { upperValue: 11 }),
        states: { P01: unitMismatch(), P02: unitMismatch(), P03: unitMismatch(), P04: satisfied(), P05: valueMismatch() },
      },
      {
        key: 'Q04', text: 'Did the portfolio document a 38% decrease in fabrication scrap?',
        answerability: 'NOT_SUPPORTED', diagnostic: 'P03', primaryFamily: 'qualifier_mismatch',
        categories: ['numeric_quantity', 'hard_negative'],
        constraint: qQuantity('38% decrease', 'EQ', 38, '%', 'fabrication scrap', { direction: 'DECREASE', directionSurface: 'decrease' }),
        states: { P01: qualifierMismatch(), P02: qualifierMismatch(), P03: qualifierMismatch(), P04: unitMismatch(), P05: unitMismatch() },
      },
    ],
  },
  {
    id: 'SV3-U43', split: 'DEV', professionGroup: 'PLANNING',
    profession: 'Transit permit planner', language: 'KO_EN_MIXED',
    title: 'Transit permit 승인 기록', documentType: 'PROJECT_REPORT',
    documentStructure: 'PROJECT_DESCRIPTION',
    template: 'SV3-TC11-TEMPLATE-PERMIT-43', seed: 'SV3-TC11-SEED-PERMIT-43-L8Q4',
    facts: [
      {
        key: 'P01', heading: 'Current permit review',
        text: '승인된 permit review 시작일은 2026-02-15이며 이후 주민 공지 절차로 이어졌다.',
        observations: [dateObservation('2026-02-15', '2026-02-15', '2026-02-15', 'permit review 시작일')],
      },
      {
        key: 'P02', heading: 'Earlier approved cycle',
        text: '이전 승인 주기의 permit review 시작일은 2025-11-20이었고 해당 주기는 연말에 종료됐다.',
        observations: [dateObservation('2025-11-20', '2025-11-20', '2025-11-20', '이전 승인 주기의 permit review 시작일')],
      },
      {
        key: 'P03', heading: 'Signed specification',
        text: '서명된 운영 명세는 TransitForm 4.2를 제출 양식의 기준 version으로 지정했다.',
        observations: [identifierNumber('TransitForm 4.2', 'TransitForm', '4.2')],
      },
      {
        key: 'P04', heading: 'Superseded draft',
        text: '폐기된 초안에는 TransitForm 4.0이 남아 있었지만 승인 명세에는 반영되지 않았다.',
        observations: [identifierNumber('TransitForm 4.0', 'TransitForm', '4.0')],
      },
    ],
    queries: [
      {
        key: 'Q01', text: 'permit review 시작일이 2026-01-31 이후였나요?',
        answerability: 'SUPPORTED', direct: 'P01', primaryFamily: 'date',
        categories: ['date_range'],
        constraint: qDate('2026-01-31 이후', 'GTE', '2026-01-31', 'permit review 시작일'),
        states: { P01: satisfied(), P02: valueMismatch(), P03: noObservation(), P04: noObservation() },
      },
      {
        key: 'Q02', text: 'permit review 시작일이 2025-01-01 이전이었나요?',
        answerability: 'NOT_SUPPORTED', diagnostic: 'P02', primaryFamily: 'date',
        categories: ['date_range', 'hard_negative'],
        constraint: qDate('2025-01-01 이전', 'LT', '2025-01-01', 'permit review 시작일'),
        states: { P01: valueMismatch(), P02: valueMismatch(), P03: noObservation(), P04: noObservation() },
      },
      {
        key: 'Q03', text: '승인된 운영 명세가 TransitForm 4.2를 요구했나요?',
        answerability: 'SUPPORTED', direct: 'P03', primaryFamily: 'identifier_number',
        categories: ['literal_identifier'],
        constraint: qIdentifierNumber('TransitForm 4.2', 'TransitForm', '4.2'),
        states: { P01: noObservation(), P02: noObservation(), P03: satisfied(), P04: valueMismatch() },
      },
      {
        key: 'Q04', text: '승인된 운영 명세가 TransitForm 4.5를 요구했나요?',
        answerability: 'NOT_SUPPORTED', diagnostic: 'P03', primaryFamily: 'identifier_number',
        categories: ['literal_identifier', 'hard_negative'],
        constraint: qIdentifierNumber('TransitForm 4.5', 'TransitForm', '4.5'),
        states: { P01: noObservation(), P02: noObservation(), P03: valueMismatch(), P04: valueMismatch() },
      },
    ],
  },
  {
    id: 'SV3-U44', split: 'CALIBRATION', professionGroup: 'MARKETING_SALES',
    profession: 'Membership operations manager', language: 'EN',
    title: 'Membership renewal operations record', documentType: 'CAREER_REVIEW',
    documentStructure: 'CAREER_DESCRIPTION',
    template: 'SV3-TC11-TEMPLATE-RENEWAL-44', seed: 'SV3-TC11-SEED-RENEWAL-44-N3R9',
    facts: [
      {
        key: 'P01', heading: 'Annual renewal cycle',
        text: 'The annual membership cycle recorded 1,850 paid member renewals after payment reconciliation.',
        observations: [quantity('1,850', 1850, 'count', 'paid member renewals')],
      },
      {
        key: 'P02', heading: 'Prior renewal cycle',
        text: 'The prior membership cycle recorded 1,240 paid member renewals before the reminder sequence changed.',
        observations: [quantity('1,240', 1240, 'count', 'paid member renewals')],
      },
      {
        key: 'P03', heading: 'Lecture registrations',
        text: 'The public lecture series recorded 1,850 event registrations in the same reporting period.',
        observations: [quantity('1,850', 1850, 'count', 'event registrations')],
      },
    ],
    queries: [
      {
        key: 'Q01', text: 'Did the membership cycle record at least 1,800 member renewals?',
        answerability: 'SUPPORTED', direct: 'P01', primaryFamily: 'quantity_wrong_value',
        categories: ['numeric_quantity', 'numeric_range_comparison'],
        constraint: qQuantity('at least 1,800', 'GTE', 1800, 'count', 'member renewals'),
        states: { P01: satisfied(), P02: valueMismatch(), P03: qualifierMismatch() },
      },
      {
        key: 'Q02', text: 'Did the cycle record exactly 1,850 member renewals?',
        answerability: 'SUPPORTED', direct: 'P01', primaryFamily: 'qualifier_mismatch',
        categories: ['numeric_quantity'],
        constraint: qQuantity('exactly 1,850', 'EQ', 1850, 'count', 'member renewals'),
        states: { P01: satisfied(), P02: valueMismatch(), P03: qualifierMismatch() },
      },
      {
        key: 'Q03', text: 'Was there a cycle with no more than 1,240 member renewals?',
        answerability: 'SUPPORTED', direct: 'P02', primaryFamily: 'range_boundary',
        categories: ['numeric_quantity', 'numeric_range_comparison'],
        constraint: qQuantity('no more than 1,240', 'LTE', 1240, 'count', 'member renewals'),
        states: { P01: valueMismatch(), P02: satisfied(), P03: qualifierMismatch() },
      },
      {
        key: 'Q04', text: 'Did a cycle exceed 2,000 member renewals?',
        answerability: 'NOT_SUPPORTED', diagnostic: 'P01', primaryFamily: 'quantity_wrong_value',
        categories: ['numeric_quantity', 'numeric_range_comparison', 'hard_negative'],
        constraint: qQuantity('exceed 2,000', 'GT', 2000, 'count', 'member renewals'),
        states: { P01: valueMismatch(), P02: valueMismatch(), P03: qualifierMismatch() },
      },
    ],
  },
  {
    id: 'SV3-U45', split: 'CALIBRATION', professionGroup: 'DATA_AI_INFRA',
    profession: 'Facilities energy analyst', language: 'KO',
    title: '시설 에너지 검증 기록', documentType: 'PROJECT_REPORT',
    documentStructure: 'TABLE_LIKE',
    template: 'SV3-TC11-TEMPLATE-ENERGY-45', seed: 'SV3-TC11-SEED-ENERGY-45-P6S2',
    facts: [
      {
        key: 'P01', heading: '서관 최적화',
        text: '서관 최적화에서는 냉각 에너지 사용량이 27% 감소했고 월별 계측값을 검토했다.',
        observations: [quantity('27%', 27, '%', '냉각 에너지 사용량', { direction: 'DECREASE', directionSurface: '감소' })],
      },
      {
        key: 'P02', heading: '동관 초기 조정',
        text: '동관의 초기 조정 기간에는 냉각 에너지 사용량이 27% 증가해 제어값을 되돌렸다.',
        observations: [quantity('27%', 27, '%', '냉각 에너지 사용량', { direction: 'INCREASE', directionSurface: '증가' })],
      },
      {
        key: 'P03', heading: '정비 알림 점검',
        text: '설비 점검 절차를 바꾼 뒤 정비 알림 누락률이 27% 감소했다.',
        observations: [quantity('27%', 27, '%', '정비 알림 누락률', { direction: 'DECREASE', directionSurface: '감소' })],
      },
      {
        key: 'P04', heading: '주요 검증 기간',
        text: '주요 냉각 최적화 검증은 18개월 동안 운영 데이터와 계절별 부하를 확인했다.',
        observations: [quantity('18개월', 18, '개월', '냉각 최적화 검증')],
      },
      {
        key: 'P05', heading: '초기 검증 기간',
        text: '초기 냉각 최적화 검증은 12개월 동안 제한된 구역만 관찰했다.',
        observations: [quantity('12개월', 12, '개월', '냉각 최적화 검증')],
      },
    ],
    queries: [
      {
        key: 'Q01', text: '냉각 에너지 사용량을 27% 이상 감소시킨 사례가 있나요?',
        answerability: 'SUPPORTED', direct: 'P01', primaryFamily: 'percentage_direction',
        categories: ['numeric_quantity', 'numeric_range_comparison'],
        constraint: qQuantity('27% 이상 감소', 'GTE', 27, '%', '냉각 에너지 사용량', { direction: 'DECREASE', directionSurface: '감소' }),
        states: { P01: satisfied(), P02: directionMismatch(), P03: qualifierMismatch(), P04: unitMismatch(), P05: unitMismatch() },
      },
      {
        key: 'Q02', text: '냉각 에너지 사용량이 20% 초과 증가한 사례가 있나요?',
        answerability: 'SUPPORTED', direct: 'P02', primaryFamily: 'percentage_direction',
        categories: ['numeric_quantity', 'numeric_range_comparison'],
        constraint: qQuantity('20% 초과 증가', 'GT', 20, '%', '냉각 에너지 사용량', { direction: 'INCREASE', directionSurface: '증가' }),
        states: { P01: directionMismatch(), P02: satisfied(), P03: qualifierMismatch(), P04: unitMismatch(), P05: unitMismatch() },
      },
      {
        key: 'Q03', text: '냉각 최적화 검증을 15~20개월 동안 진행했나요?',
        answerability: 'SUPPORTED', direct: 'P04', primaryFamily: 'range_boundary',
        categories: ['numeric_quantity', 'numeric_range_comparison'],
        constraint: qQuantity('15~20개월', 'RANGE', 15, '개월', '냉각 최적화 검증', { upperValue: 20 }),
        states: { P01: unitMismatch(), P02: unitMismatch(), P03: unitMismatch(), P04: satisfied(), P05: valueMismatch() },
      },
      {
        key: 'Q04', text: '냉각수 누수율을 27% 감소시킨 기록이 있나요?',
        answerability: 'NOT_SUPPORTED', diagnostic: 'P03', primaryFamily: 'qualifier_mismatch',
        categories: ['numeric_quantity', 'hard_negative'],
        constraint: qQuantity('27% 감소', 'EQ', 27, '%', '냉각수 누수율', { direction: 'DECREASE', directionSurface: '감소' }),
        states: { P01: qualifierMismatch(), P02: qualifierMismatch(), P03: qualifierMismatch(), P04: unitMismatch(), P05: unitMismatch() },
      },
    ],
  },
  {
    id: 'SV3-U46', split: 'CALIBRATION', professionGroup: 'FRONTEND_MOBILE',
    profession: 'Kiosk platform engineer', language: 'KO_EN_MIXED',
    title: 'Kiosk client 운영 기록', documentType: 'PROJECT_REPORT',
    documentStructure: 'PROJECT_DESCRIPTION',
    template: 'SV3-TC11-TEMPLATE-KIOSK-46', seed: 'SV3-TC11-SEED-KIOSK-46-T4V7',
    facts: [
      {
        key: 'P01', heading: 'Active kiosk client',
        text: 'The active kiosk client uses KioskLink 8.4 for signed device messages in production.',
        observations: [identifierNumber('KioskLink 8.4', 'KioskLink', '8.4')],
      },
      {
        key: 'P02', heading: 'Retired kiosk client',
        text: '폐기된 kiosk client는 KioskLink 8.1을 사용했고 현재 배포 대상에서 제외됐다.',
        observations: [identifierNumber('KioskLink 8.1', 'KioskLink', '8.1')],
      },
      {
        key: 'P03', heading: 'Current maintenance window',
        text: '승인된 maintenance window 시작일은 2026-09-10이며 현장 장비 점검이 함께 진행됐다.',
        observations: [dateObservation('2026-09-10', '2026-09-10', '2026-09-10', 'maintenance window 시작일')],
      },
      {
        key: 'P04', heading: 'Earlier maintenance window',
        text: '이전 승인 주기의 maintenance window 시작일은 2025-12-15였고 당일 복구 훈련을 마쳤다.',
        observations: [dateObservation('2025-12-15', '2025-12-15', '2025-12-15', '이전 승인 주기의 maintenance window 시작일')],
      },
    ],
    queries: [
      {
        key: 'Q01', text: 'production kiosk client가 KioskLink 8.4를 사용하나요?',
        answerability: 'SUPPORTED', direct: 'P01', primaryFamily: 'identifier_number',
        categories: ['literal_identifier'],
        constraint: qIdentifierNumber('KioskLink 8.4', 'KioskLink', '8.4'),
        states: { P01: satisfied(), P02: valueMismatch(), P03: noObservation(), P04: noObservation() },
      },
      {
        key: 'Q02', text: 'production kiosk client가 KioskLink 8.7을 사용하나요?',
        answerability: 'NOT_SUPPORTED', diagnostic: 'P01', primaryFamily: 'identifier_number',
        categories: ['literal_identifier', 'hard_negative'],
        constraint: qIdentifierNumber('KioskLink 8.7', 'KioskLink', '8.7'),
        states: { P01: valueMismatch(), P02: valueMismatch(), P03: noObservation(), P04: noObservation() },
      },
      {
        key: 'Q03', text: 'maintenance window 시작일이 2026-06-30 이후였나요?',
        answerability: 'SUPPORTED', direct: 'P03', primaryFamily: 'date',
        categories: ['date_range'],
        constraint: qDate('2026-06-30 이후', 'GTE', '2026-06-30', 'maintenance window 시작일'),
        states: { P01: noObservation(), P02: noObservation(), P03: satisfied(), P04: valueMismatch() },
      },
      {
        key: 'Q04', text: 'maintenance window 시작일이 2025-01-01 이전이었나요?',
        answerability: 'NOT_SUPPORTED', diagnostic: 'P04', primaryFamily: 'date',
        categories: ['date_range', 'hard_negative'],
        constraint: qDate('2025-01-01 이전', 'LT', '2025-01-01', 'maintenance window 시작일'),
        states: { P01: noObservation(), P02: noObservation(), P03: valueMismatch(), P04: valueMismatch() },
      },
    ],
  },
]

const TYPED_STRESS_SCHEMA = {
  $schema: 'https://json-schema.org/draft/2020-12/schema',
  $id: 'https://github.com/jaemin-devlog/PRIZM/search-v3-typed-stress-1.1.0.schema.json',
  title: 'PRIZM Search V3 Typed Constraint Capability Stress Contract',
  contractVersion: CONTRACT_VERSION,
  description: 'Formal artifact shape. The dependency-free deterministic materializer validator is authoritative; no external JSON Schema engine result is claimed.',
  oneOf: [
    { $ref: '#/$defs/corpusArtifact' },
    { $ref: '#/$defs/goldArtifact' },
    { $ref: '#/$defs/questionsArtifact' },
    { $ref: '#/$defs/typedAnnotationsArtifact' },
    { $ref: '#/$defs/lineageArtifact' },
    { $ref: '#/$defs/manifestArtifact' },
  ],
  $defs: {
    split: { enum: ['DEV', 'CALIBRATION'] },
    matchState: { enum: MATCH_STATES },
    expectedReason: { enum: EXPECTED_REASONS },
    typedKind: { enum: TYPED_KINDS },
    primaryFamily: { enum: PRIMARY_FAMILIES },
    expectedEvidenceState: {
      type: 'object',
      required: ['evidenceUnitId', 'state', 'reason'],
      properties: {
        evidenceUnitId: { type: 'string' },
        state: { $ref: '#/$defs/matchState' },
        reason: { $ref: '#/$defs/expectedReason' },
      },
      additionalProperties: false,
    },
    observation: {
      type: 'object',
      required: [
        'observationId', 'evidenceUnitId', 'sourceSpanId', 'kind',
        'sourceSurface', 'charStart', 'charEnd',
      ],
      properties: {
        observationId: { type: 'string' },
        evidenceUnitId: { type: 'string' },
        sourceSpanId: { type: 'string' },
        kind: { $ref: '#/$defs/typedKind' },
        sourceSurface: { type: 'string', minLength: 1 },
        charStart: { type: 'integer', minimum: 0 },
        charEnd: { type: 'integer', minimum: 1 },
        value: { type: 'number' },
        normalizedUnit: { type: 'string' },
        qualifier: { type: 'string' },
        qualifierCharStart: { type: 'integer', minimum: 0 },
        qualifierCharEnd: { type: 'integer', minimum: 1 },
        direction: { enum: ['NONE', 'INCREASE', 'DECREASE'] },
        directionSourceSurface: { type: 'string' },
        directionCharStart: { type: 'integer', minimum: 0 },
        directionCharEnd: { type: 'integer', minimum: 1 },
        start: { type: 'string', format: 'date' },
        end: { type: 'string', format: 'date' },
        precision: { enum: ['FULL_DATE'] },
        identifier: { type: 'string' },
        numberSurface: { type: 'string' },
        normalizedSegments: { type: 'array', items: { type: 'integer', minimum: 0 } },
      },
      additionalProperties: false,
    },
    queryConstraint: {
      type: 'object',
      required: ['constraintId', 'kind', 'surface', 'queryCharStart', 'queryCharEnd'],
      properties: {
        constraintId: { type: 'string' },
        kind: { $ref: '#/$defs/typedKind' },
        surface: { type: 'string', minLength: 1 },
        queryCharStart: { type: 'integer', minimum: 0 },
        queryCharEnd: { type: 'integer', minimum: 1 },
        operator: { enum: ['EQ', 'GTE', 'LTE', 'GT', 'LT', 'RANGE'] },
        value: { oneOf: [{ type: 'number' }, { type: 'string', format: 'date' }] },
        upperValue: { type: 'number' },
        normalizedUnit: { type: 'string' },
        qualifier: { type: 'string' },
        qualifierCharStart: { type: 'integer', minimum: 0 },
        qualifierCharEnd: { type: 'integer', minimum: 1 },
        direction: { enum: ['NONE', 'INCREASE', 'DECREASE'] },
        directionSourceSurface: { type: 'string' },
        directionCharStart: { type: 'integer', minimum: 0 },
        directionCharEnd: { type: 'integer', minimum: 1 },
        precision: { enum: ['FULL_DATE'] },
        identifier: { type: 'string' },
        numberSurface: { type: 'string' },
        normalizedSegments: { type: 'array', items: { type: 'integer', minimum: 0 } },
      },
      additionalProperties: false,
    },
    queryAnnotation: {
      type: 'object',
      required: [
        'queryId', 'userBundleId', 'primaryFamily', 'constraint',
        'expectedEvidenceStates',
      ],
      properties: {
        queryId: { type: 'string' },
        userBundleId: { type: 'string' },
        primaryFamily: { $ref: '#/$defs/primaryFamily' },
        constraint: { $ref: '#/$defs/queryConstraint' },
        expectedEvidenceStates: {
          type: 'array', minItems: 1,
          items: { $ref: '#/$defs/expectedEvidenceState' },
        },
      },
      additionalProperties: false,
    },
    corpusArtifact: {
      type: 'object', required: ['artifactType', 'schemaVersion', 'datasetVersion', 'split', 'userBundles'],
      properties: {
        artifactType: { const: 'CORPUS' }, schemaVersion: { const: SCHEMA_VERSION },
        datasetVersion: { const: DATASET_VERSION }, split: { $ref: '#/$defs/split' },
        userBundles: { type: 'array', minItems: 1 },
      },
      additionalProperties: false,
    },
    goldArtifact: {
      type: 'object', required: ['artifactType', 'schemaVersion', 'datasetVersion', 'split', 'parents', 'evidenceGroups', 'evidenceUnits'],
      properties: {
        artifactType: { const: 'GOLD_EVIDENCE' }, schemaVersion: { const: SCHEMA_VERSION },
        datasetVersion: { const: DATASET_VERSION }, split: { $ref: '#/$defs/split' },
        parents: { type: 'array' }, evidenceGroups: { type: 'array' }, evidenceUnits: { type: 'array' },
      },
      additionalProperties: false,
    },
    questionsArtifact: {
      type: 'object', required: ['artifactType', 'schemaVersion', 'datasetVersion', 'split', 'queries'],
      properties: {
        artifactType: { const: 'QUESTIONS' }, schemaVersion: { const: SCHEMA_VERSION },
        datasetVersion: { const: DATASET_VERSION }, split: { $ref: '#/$defs/split' },
        queries: { type: 'array', minItems: 1 },
      },
      additionalProperties: false,
    },
    typedAnnotationsArtifact: {
      type: 'object',
      required: ['artifactType', 'schemaVersion', 'datasetVersion', 'split', 'observations', 'queryAnnotations'],
      properties: {
        artifactType: { const: 'TYPED_ANNOTATIONS' },
        schemaVersion: { const: TYPED_SCHEMA_VERSION }, datasetVersion: { const: DATASET_VERSION },
        split: { $ref: '#/$defs/split' },
        observations: { type: 'array', minItems: 1, items: { $ref: '#/$defs/observation' } },
        queryAnnotations: { type: 'array', minItems: 1, items: { $ref: '#/$defs/queryAnnotation' } },
      },
      additionalProperties: false,
    },
    lineageArtifact: {
      type: 'object',
      required: [
        'artifactType', 'schemaVersion', 'datasetVersion', 'status', 'generator',
        'generatorRevision', 'generatorSourceSha256', 'inputBaselineRevision',
        'sealedFinalPolicy', 'bundles',
      ],
      properties: {
        artifactType: { const: 'LINEAGE' }, schemaVersion: { const: SCHEMA_VERSION },
        datasetVersion: { const: DATASET_VERSION }, status: { const: STATUS },
        generator: { type: 'string' }, generatorRevision: { type: 'string' },
        generatorSourceSha256: { type: 'string', pattern: '^[a-f0-9]{64}$' },
        inputBaselineRevision: { type: 'string', pattern: '^[a-f0-9]{40}$' },
        sealedFinalPolicy: { type: 'string' },
        bundles: { type: 'array', minItems: 6 },
      },
      additionalProperties: false,
    },
    manifestArtifact: {
      type: 'object',
      required: [
        'artifactType', 'schemaVersion', 'datasetVersion', 'split', 'status',
        'mutable', 'opened', 'searchExecuted', 'frozenAt',
        'generationSourceRevision', 'generatorRevision', 'validationMode',
        'counts', 'distributions', 'files', 'combinedSha256',
      ],
      properties: {
        artifactType: { const: 'MANIFEST' }, schemaVersion: { const: SCHEMA_VERSION },
        datasetVersion: { const: DATASET_VERSION },
        split: { enum: ['DEV', 'CALIBRATION', 'ALL'] }, status: { const: STATUS },
        mutable: { const: false }, opened: { const: true }, searchExecuted: { const: false },
        sealedAt: { type: ['string', 'null'] }, frozenAt: { type: 'string', format: 'date-time' },
        generationSourceRevision: { type: 'string', pattern: '^[a-f0-9]{40}$' },
        generatorRevision: { type: 'string' },
        validationMode: { const: 'DETERMINISTIC_CONTRACT_VALIDATOR_NO_EXTERNAL_JSON_SCHEMA_ENGINE' },
        previousDatasets: { type: 'array' }, splitCombinedSha256: { type: 'object' },
        counts: { type: 'object' }, distributions: { type: 'object' },
        files: { type: 'array', minItems: 1 },
        combinedSha256: { type: 'string', pattern: '^[a-f0-9]{64}$' },
      },
      additionalProperties: false,
    },
  },
}

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

function codePoints(value) {
  return Array.from(value)
}

function codePointOffset(value, utf16Offset) {
  return codePoints(value.slice(0, utf16Offset)).length
}

function codePointSlice(value, start, end) {
  return codePoints(value).slice(start, end).join('')
}

function locate(content, surface, label, withinStart = 0, withinEnd = codePoints(content).length) {
  const within = codePointSlice(content, withinStart, withinEnd)
  const firstUtf16 = within.indexOf(surface)
  if (firstUtf16 < 0 || within.indexOf(surface, firstUtf16 + surface.length) >= 0) {
    throw new Error(`${label} must occur exactly once in its allowed source range`)
  }
  const start = withinStart + codePointOffset(within, firstUtf16)
  return [start, start + codePoints(surface).length]
}

function lineAt(content, offset) {
  return codePointSlice(content, 0, offset).split('\n').length
}

function generatorSourceSha256() {
  const normalizedSource = readFileSync(GENERATOR_PATH, 'utf8').replaceAll('\r\n', '\n')
  return sha256(Buffer.from(normalizedSource, 'utf8'))
}

function documentContent(bundle) {
  return `${bundle.title}\n\n${bundle.facts.map((fact) => `${fact.heading}\n${fact.text}`).join('\n\n')}\n`
}

function sourceSpan(bundle, fact, content, kind) {
  const [charStart, charEnd] = locate(content, fact.text, `${bundle.id}/${fact.key}`)
  const parentId = `${bundle.id}-${fact.key}`
  return {
    spanId: kind === 'parent' ? `${parentId}-PS01` : `${parentId}-E01-S01`,
    parentId,
    documentId: `${bundle.id}-D01`, versionId: `${bundle.id}-D01-V01`,
    sourcePath: `documents/${bundle.id.toLocaleLowerCase('und')}-typed-capability-v01.txt`,
    sourceType: 'TXT_TEXT', page: null, charStart, charEnd,
    lineStart: lineAt(content, charStart), lineEnd: lineAt(content, charEnd),
    text: fact.text, textSha256: sha256(Buffer.from(fact.text, 'utf8')),
  }
}

function observationArtifact(bundle, fact, observation, content, index) {
  const evidenceSpan = sourceSpan(bundle, fact, content, 'evidence')
  const [charStart, charEnd] = locate(
    content, observation.surface, `${bundle.id}/${fact.key}/observation`,
    evidenceSpan.charStart, evidenceSpan.charEnd,
  )
  const result = {
    observationId: `SV3-TC11-${bundle.id.slice(4)}-${fact.key}-O${String(index + 1).padStart(2, '0')}`,
    evidenceUnitId: `${bundle.id}-${fact.key}-E01`, sourceSpanId: evidenceSpan.spanId,
    kind: observation.kind, sourceSurface: observation.surface, charStart, charEnd,
  }
  if (observation.kind === 'QUANTITY') {
    Object.assign(result, {
      value: observation.value, normalizedUnit: observation.normalizedUnit,
      qualifier: observation.qualifier, direction: observation.direction,
    })
    ;[result.qualifierCharStart, result.qualifierCharEnd] = locate(
      content, observation.qualifier, `${result.observationId}/qualifier`,
      evidenceSpan.charStart, evidenceSpan.charEnd,
    )
    if (observation.direction !== 'NONE') {
      result.directionSourceSurface = observation.directionSurface
      ;[result.directionCharStart, result.directionCharEnd] = locate(
        content, observation.directionSurface, `${result.observationId}/direction`,
        evidenceSpan.charStart, evidenceSpan.charEnd,
      )
    }
  } else if (observation.kind === 'DATE') {
    Object.assign(result, {
      start: observation.start, end: observation.end, precision: observation.precision,
      qualifier: observation.qualifier,
    })
    ;[result.qualifierCharStart, result.qualifierCharEnd] = locate(
      content, observation.qualifier, `${result.observationId}/qualifier`,
      evidenceSpan.charStart, evidenceSpan.charEnd,
    )
  } else if (observation.kind === 'IDENTIFIER_NUMBER') {
    Object.assign(result, {
      identifier: observation.identifier, numberSurface: observation.numberSurface,
      normalizedSegments: observation.normalizedSegments,
    })
  }
  return result
}

function standardNumeric(observation) {
  return {
    normalizedValue: observation.value, unit: observation.normalizedUnit,
    semanticType: 'QUANTITY', sourceSurface: observation.surface,
    qualifierTokens: observation.qualifier.split(/\s+/u).filter(Boolean),
  }
}

function standardDate(observation) {
  return {
    start: observation.start, end: observation.end, precision: 'DAY',
    sourceSurface: observation.surface,
  }
}

function standardEntity(observation) {
  return {
    entityType: 'IDENTIFIER_NUMBER',
    canonicalValue: `${normalize(observation.identifier).replaceAll(' ', '_')}_${observation.numberSurface.replaceAll('.', '_')}`.toUpperCase(),
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
  } else if (constraint.kind === 'IDENTIFIER_NUMBER') {
    result.entities.push(standardEntity(constraint))
  }
  return result
}

function materializeBundle(bundle) {
  const content = documentContent(bundle)
  const documentId = `${bundle.id}-D01`
  const versionId = `${documentId}-V01`
  const contentPath = `documents/${bundle.id.toLocaleLowerCase('und')}-typed-capability-v01.txt`
  const document = {
    documentId, logicalDocumentId: `${documentId}-TC11-LOGICAL`,
    versionLineageId: `${documentId}-TC11-LINEAGE`, versionId, versionNumber: 1, active: true,
    title: bundle.title, documentType: bundle.documentType,
    documentStructure: bundle.documentStructure, fileType: 'TXT', language: bundle.language,
    contentPath, contentSha256: sha256(Buffer.from(content, 'utf8')),
    supportScope: 'SUPPORTED_BY_CURRENT', visibility: 'PUBLIC_FIXTURE',
    provenance: {
      classification: 'SYNTHETIC', license: 'Apache-2.0', generatorName: GENERATOR_NAME,
      generatorRevision: GENERATOR_REVISION, generatorSeedId: bundle.seed,
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
      groupId, userBundleId: bundle.id,
      sourceFactId: `SV3-TC11-FACT-${bundle.id.slice(4)}-${fact.key}`,
      description: fact.heading, evidenceUnitIds: [evidenceUnitId],
    })
    units.push({
      evidenceUnitId, userBundleId: bundle.id, parentId, groupId, documentId, versionId,
      sourceFactId: `SV3-TC11-FACT-${bundle.id.slice(4)}-${fact.key}`,
      sourceSpans: [evidenceSpan], primarySpanId: evidenceSpan.spanId, contextSpanIds: [],
      actor: 'SELF', completionState: 'COMPLETED', aspects: [`typed_capability_${fact.key.toLocaleLowerCase('und')}`],
      entities: fact.observations.filter((value) => value.kind === 'IDENTIFIER_NUMBER').map(standardEntity),
      numerics: fact.observations.filter((value) => value.kind === 'QUANTITY').map(standardNumeric),
      dates: fact.observations.filter((value) => value.kind === 'DATE').map(standardDate),
    })
    observations.push(...fact.observations.map((value, index) => observationArtifact(bundle, fact, value, content, index)))
  }

  const queries = []
  const typedQueries = []
  for (const definition of bundle.queries) {
    const queryId = `${bundle.id}-${definition.key}`
    const aspectId = `typed_capability_${definition.key.toLocaleLowerCase('und')}`
    const directId = definition.direct ? `${bundle.id}-${definition.direct}-E01` : null
    let expectedEvidence = []
    if (directId) {
      expectedEvidence = [{ evidenceUnitId: directId, supportRelation: 'DIRECT_SUPPORT' }]
    } else if (definition.diagnostic) {
      const diagnostic = definition.states[definition.diagnostic] ?? noObservation()
      expectedEvidence = [{
        evidenceUnitId: `${bundle.id}-${definition.diagnostic}-E01`,
        supportRelation: diagnostic.state === 'CONTRADICTED' ? 'CONTRADICTS' : 'INSUFFICIENT',
      }]
    }
    queries.push({
      queryId, userBundleId: bundle.id, questionGroupId: `${queryId}-TC11-FAMILY`,
      query: definition.text, normalizedQuery: normalize(definition.text),
      categories: [...new Set([...definition.categories, LANGUAGE_CATEGORY[bundle.language]])],
      language: bundle.language, answerability: definition.answerability,
      aspectExpression: { operator: 'ALL', requiredAspectIds: [aspectId], minShouldMatch: 1 },
      aspects: [{
        aspectId, required: true, answerability: definition.answerability,
        expectedEvidence,
        requiredEvidenceGroupIds: directId ? [`${bundle.id}-G${definition.direct.slice(1)}`] : [],
        minEvidenceGroups: directId ? 1 : 0,
        constraints: standardConstraints(definition.constraint),
      }],
      safetyExclusions: [],
    })
    const constraint = { constraintId: `${queryId}-C01`, ...definition.constraint }
    ;[constraint.queryCharStart, constraint.queryCharEnd] = locate(
      definition.text, constraint.surface, `${queryId}/constraint`,
    )
    if (constraint.qualifier) {
      ;[constraint.qualifierCharStart, constraint.qualifierCharEnd] = locate(
        definition.text, constraint.qualifier, `${queryId}/qualifier`,
      )
    }
    if (constraint.direction !== undefined && constraint.direction !== 'NONE') {
      constraint.directionSourceSurface = definition.constraint.directionSurface
      ;[constraint.directionCharStart, constraint.directionCharEnd] = locate(
        definition.text, constraint.directionSurface, `${queryId}/direction`,
      )
    }
    typedQueries.push({
      queryId, userBundleId: bundle.id, primaryFamily: definition.primaryFamily,
      constraint,
      expectedEvidenceStates: bundle.facts.map((fact) => ({
        evidenceUnitId: `${bundle.id}-${fact.key}-E01`,
        ...(definition.states[fact.key] ?? noObservation()),
      })),
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
    typedOperator: countBy(typedQueries.map((value) => value.constraint.operator ?? 'EXACT')),
    primaryFamily: countBy(typedQueries.map((value) => value.primaryFamily)),
    expectedState: countBy(typedQueries.flatMap((value) => value.expectedEvidenceStates.map((entry) => entry.state))),
    expectedReason: countBy(typedQueries.flatMap((value) => value.expectedEvidenceStates.map((entry) => entry.reason))),
  }
}

function buildArtifacts() {
  const files = new Map()
  files.set('typed-stress.schema.json', stableJson(TYPED_STRESS_SCHEMA))
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
        documentFamilyId: `${value.bundle.id}-TC11-DOC-FAMILY`,
        templateFamilyId: value.bundle.template,
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
    const inputs = [...files.keys()].filter((relative) => relative.startsWith(`${directory}/`)).sort()
    const entries = inputs.map((relative) => fileEntry(relative.slice(directory.length + 1), files.get(relative)))
    const manifest = {
      artifactType: 'MANIFEST', schemaVersion: SCHEMA_VERSION, datasetVersion: DATASET_VERSION,
      split, status: STATUS, mutable: false, opened: true, searchExecuted: false,
      sealedAt: null, frozenAt: FROZEN_AT,
      generationSourceRevision: INPUT_BASELINE_REVISION,
      generatorRevision: GENERATOR_REVISION,
      validationMode: 'DETERMINISTIC_CONTRACT_VALIDATOR_NO_EXTERNAL_JSON_SCHEMA_ENGINE',
      counts: {
        userBundles: materialized.length,
        documents: materialized.length,
        queries: questions.queries.length,
        typedQueryAnnotations: typed.queryAnnotations.length,
        evidenceParents: gold.parents.length,
        evidenceGroups: gold.evidenceGroups.length,
        evidenceUnits: gold.evidenceUnits.length,
        typedObservations: typed.observations.length,
      },
      distributions: splitDistributions(materialized),
      files: entries,
      combinedSha256: combinedHash(entries),
    }
    splitHashes[split] = manifest.combinedSha256
    files.set(`${directory}/manifest.json`, stableJson(manifest))
  }

  const lineage = {
    artifactType: 'LINEAGE', schemaVersion: SCHEMA_VERSION, datasetVersion: DATASET_VERSION,
    status: STATUS,
    generator: 'scripts/evaluation/search-v3/materialize-prz028-typed-stress-1.1.0.mjs',
    generatorRevision: GENERATOR_REVISION,
    generatorSourceSha256: generatorSourceSha256(),
    inputBaselineRevision: INPUT_BASELINE_REVISION,
    sealedFinalPolicy: 'SEALED_FINAL_TEST access is limited to manifest hash/flags and unified lineage identifiers. Documents, questions, gold, predictions, and search are never accessed.',
    bundles: allMaterialized.map((value) => ({
      userBundleId: value.bundle.id, split: value.bundle.split,
      documentFamilyId: `${value.bundle.id}-TC11-DOC-FAMILY`,
      templateFamilyId: value.bundle.template,
      generatorSeedId: value.bundle.seed,
      logicalDocumentIds: [value.document.logicalDocumentId],
      versionLineageIds: [value.document.versionLineageId],
      sourceFactIds: value.units.map((unit) => unit.sourceFactId),
      sourceFactSignatures: value.units.map((unit) => sha256(Buffer.from(
        normalize(unit.sourceSpans.map((span) => span.text).join(' ')), 'utf8',
      ))),
      questionGroupIds: value.queries.map((query) => query.questionGroupId),
      normalizedQueries: value.queries.map((query) => query.normalizedQuery),
    })),
  }
  files.set('lineage.json', stableJson(lineage))

  const rootEntries = [...files.keys()].sort().map((relative) => fileEntry(relative, files.get(relative)))
  const allQueries = allMaterialized.flatMap((value) => value.queries)
  const allTyped = allMaterialized.flatMap((value) => value.typedQueries)
  const allUnits = allMaterialized.flatMap((value) => value.units)
  const rootManifest = {
    artifactType: 'MANIFEST', schemaVersion: SCHEMA_VERSION, datasetVersion: DATASET_VERSION,
    split: 'ALL', status: STATUS, mutable: false, opened: true, searchExecuted: false,
    sealedAt: null, frozenAt: FROZEN_AT,
    generationSourceRevision: INPUT_BASELINE_REVISION,
    generatorRevision: GENERATOR_REVISION,
    validationMode: 'DETERMINISTIC_CONTRACT_VALIDATOR_NO_EXTERNAL_JSON_SCHEMA_ENGINE',
    previousDatasets: [
      { datasetVersion: 'search-v3-typed-constraints-stress-1.0.0', status: 'INVALID_INPUT_HISTORICAL' },
      { datasetVersion: 'search-v3-typed-constraints-stress-1.0.1', status: 'HISTORICAL_FROZEN', benchmarkExecuted: true },
      { datasetVersion: 'search-v3-fresh-seed-1.0.1', status: 'FROZEN_INPUT' },
      { datasetVersion: 'search-v3-fresh-devcal-1.1.0', status: 'FROZEN_INPUT' },
      { datasetVersion: 'search-v3-fresh-devcal-robustness-1.0.0', status: 'FROZEN_INPUT' },
    ],
    splitCombinedSha256: splitHashes,
    counts: {
      userBundles: allMaterialized.length,
      documents: allMaterialized.length,
      queries: allQueries.length,
      typedQueryAnnotations: allTyped.length,
      evidenceParents: allUnits.length,
      evidenceGroups: allUnits.length,
      evidenceUnits: allUnits.length,
      typedObservations: allMaterialized.flatMap((value) => value.observations).length,
    },
    distributions: {
      split: countBy(allMaterialized.map((value) => value.bundle.split)),
      ...splitDistributions(allMaterialized),
    },
    files: rootEntries,
    combinedSha256: combinedHash(rootEntries),
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

function validateSpan(span, source, parentSpan, findings) {
  if (!span || !source) {
    findings.push('missing source/span during grounding validation')
    return
  }
  const actual = codePointSlice(source.content, span.charStart, span.charEnd)
  if (actual !== span.text || sha256(Buffer.from(actual, 'utf8')) !== span.textSha256) {
    findings.push(`${span.spanId} source span text/hash mismatch`)
  }
  if (span.lineStart !== lineAt(source.content, span.charStart)
      || span.lineEnd !== lineAt(source.content, span.charEnd)) {
    findings.push(`${span.spanId} line provenance mismatch`)
  }
  if (parentSpan && (span.charStart < parentSpan.charStart || span.charEnd > parentSpan.charEnd)) {
    findings.push(`${span.spanId} escapes parent source span`)
  }
}

function validateSchemaContract(schema, findings) {
  if (schema.$schema !== 'https://json-schema.org/draft/2020-12/schema'
      || schema.contractVersion !== CONTRACT_VERSION) {
    findings.push('typed-stress formal schema metadata mismatch')
  }
  for (const [label, actual, expected] of [
    ['primary family', schema.$defs?.primaryFamily?.enum, PRIMARY_FAMILIES],
    ['match state', schema.$defs?.matchState?.enum, MATCH_STATES],
    ['expected reason', schema.$defs?.expectedReason?.enum, EXPECTED_REASONS],
    ['typed kind', schema.$defs?.typedKind?.enum, TYPED_KINDS],
  ]) {
    if (JSON.stringify(actual) !== JSON.stringify(expected)) findings.push(`${label} schema enum mismatch`)
  }
  const refs = new Set((schema.oneOf ?? []).map((entry) => entry.$ref))
  for (const name of [
    'corpusArtifact', 'goldArtifact', 'questionsArtifact',
    'typedAnnotationsArtifact', 'lineageArtifact', 'manifestArtifact',
  ]) {
    if (!refs.has(`#/$defs/${name}`)) findings.push(`formal schema misses ${name}`)
  }
}

function assertUnique(values, label, findings) {
  if (new Set(values).size !== values.length) findings.push(`duplicate ${label}`)
}

function countsEqual(actual, expected) {
  const keys = [...new Set([...Object.keys(actual), ...Object.keys(expected)])].sort()
  return keys.every((key) => actual[key] === expected[key])
}

function validateManifestFiles(files, manifest, prefix, findings) {
  for (const entry of manifest.files) {
    const relative = prefix ? `${prefix}/${entry.path}` : entry.path
    const content = files.get(relative)
    if (content === undefined || Buffer.byteLength(content, 'utf8') !== entry.bytes
        || sha256(Buffer.from(content, 'utf8')) !== entry.sha256) {
      findings.push(`manifest file mismatch: ${relative}`)
    }
  }
  if (combinedHash(manifest.files) !== manifest.combinedSha256) {
    findings.push(`${prefix || 'root'} combined hash mismatch`)
  }
}

function validateInMemory(files) {
  const findings = []
  const parsed = (relative) => JSON.parse(files.get(relative))
  const schema = parsed('typed-stress.schema.json')
  const lineage = parsed('lineage.json')
  const rootManifest = parsed('manifest.json')
  validateSchemaContract(schema, findings)

  const globalQueryIds = []
  const globalParentIds = []
  const globalGroupIds = []
  const globalUnitIds = []
  const globalObservationIds = []
  const globalProfessionGroups = []
  let totalBundles = 0
  let totalDocuments = 0
  let totalQueries = 0
  let totalUnits = 0

  for (const directory of ['dev', 'calibration']) {
    const expectedSplit = directory === 'dev' ? 'DEV' : 'CALIBRATION'
    const corpus = parsed(`${directory}/corpus.json`)
    const gold = parsed(`${directory}/gold-evidence.json`)
    const questions = parsed(`${directory}/questions.json`)
    const typed = parsed(`${directory}/typed-annotations.json`)
    const manifest = parsed(`${directory}/manifest.json`)
    for (const artifact of [corpus, gold, questions, typed, manifest]) {
      if (artifact.datasetVersion !== DATASET_VERSION || artifact.split !== expectedSplit) {
        findings.push(`${directory} artifact version/split mismatch`)
      }
    }
    if (corpus.artifactType !== 'CORPUS' || corpus.schemaVersion !== SCHEMA_VERSION
        || gold.artifactType !== 'GOLD_EVIDENCE' || gold.schemaVersion !== SCHEMA_VERSION
        || questions.artifactType !== 'QUESTIONS' || questions.schemaVersion !== SCHEMA_VERSION
        || typed.artifactType !== 'TYPED_ANNOTATIONS' || typed.schemaVersion !== TYPED_SCHEMA_VERSION
        || manifest.artifactType !== 'MANIFEST' || manifest.schemaVersion !== SCHEMA_VERSION) {
      findings.push(`${directory} schema-contract artifact envelope mismatch`)
    }
    if (manifest.status !== STATUS || manifest.mutable !== false || manifest.opened !== true
        || manifest.searchExecuted !== false || manifest.validationMode
        !== 'DETERMINISTIC_CONTRACT_VALIDATOR_NO_EXTERNAL_JSON_SCHEMA_ENGINE') {
      findings.push(`${directory} INPUT_FROZEN manifest flags mismatch`)
    }
    if (corpus.userBundles.length !== 3 || questions.queries.length !== 12
        || typed.queryAnnotations.length !== 12 || gold.evidenceUnits.length !== 12
        || typed.observations.length !== 12) {
      findings.push(`${directory} must contain 3 bundles, 12 queries, 12 units, and 12 observations`)
    }
    totalBundles += corpus.userBundles.length
    totalDocuments += corpus.userBundles.flatMap((bundle) => bundle.documents).length
    totalQueries += questions.queries.length
    totalUnits += gold.evidenceUnits.length

    const documentMap = new Map()
    for (const bundle of corpus.userBundles) {
      globalProfessionGroups.push(bundle.professionGroup)
      if (bundle.split !== expectedSplit || bundle.documents.length !== 1) {
        findings.push(`${bundle.userBundleId} split/document count mismatch`)
      }
      for (const document of bundle.documents) {
        const content = files.get(`${directory}/${document.contentPath}`)
        if (content === undefined || sha256(Buffer.from(content, 'utf8')) !== document.contentSha256) {
          findings.push(`${document.versionId} document content/hash mismatch`)
        }
        if (document.provenance?.classification !== 'SYNTHETIC'
            || document.provenance?.generatorName !== GENERATOR_NAME
            || document.provenance?.generatorRevision !== GENERATOR_REVISION
            || document.visibility !== 'PUBLIC_FIXTURE') {
          findings.push(`${document.versionId} provenance mismatch`)
        }
        documentMap.set(document.versionId, { document, content, userBundleId: bundle.userBundleId })
      }
    }

    const parentMap = new Map()
    for (const parent of gold.parents) {
      globalParentIds.push(parent.parentId)
      if (parentMap.has(parent.parentId)) findings.push(`duplicate parent ${parent.parentId}`)
      parentMap.set(parent.parentId, parent)
      const source = documentMap.get(parent.versionId)
      if (!source || source.userBundleId !== parent.userBundleId) findings.push(`${parent.parentId} owner/document mismatch`)
      else validateSpan(parent.sourceSpan, source, null, findings)
    }
    const groupMap = new Map()
    for (const group of gold.evidenceGroups) {
      globalGroupIds.push(group.groupId)
      if (groupMap.has(group.groupId)) findings.push(`duplicate evidence group ${group.groupId}`)
      groupMap.set(group.groupId, group)
    }
    const unitMap = new Map()
    for (const unit of gold.evidenceUnits) {
      globalUnitIds.push(unit.evidenceUnitId)
      if (unitMap.has(unit.evidenceUnitId)) findings.push(`duplicate evidence unit ${unit.evidenceUnitId}`)
      unitMap.set(unit.evidenceUnitId, unit)
      const source = documentMap.get(unit.versionId)
      const parent = parentMap.get(unit.parentId)
      const group = groupMap.get(unit.groupId)
      if (!source || !parent || !group || source.userBundleId !== unit.userBundleId
          || parent.userBundleId !== unit.userBundleId || group.userBundleId !== unit.userBundleId
          || group.sourceFactId !== unit.sourceFactId
          || JSON.stringify(group.evidenceUnitIds) !== JSON.stringify([unit.evidenceUnitId])) {
        findings.push(`${unit.evidenceUnitId} Gold graph mismatch`)
        continue
      }
      if (unit.sourceSpans.length !== 1 || unit.primarySpanId !== unit.sourceSpans[0].spanId) {
        findings.push(`${unit.evidenceUnitId} primary source span mismatch`)
      }
      for (const span of unit.sourceSpans) validateSpan(span, source, parent.sourceSpan, findings)
    }

    const observationMap = new Map()
    for (const observation of typed.observations) {
      globalObservationIds.push(observation.observationId)
      if (observationMap.has(observation.observationId)) findings.push(`duplicate observation ${observation.observationId}`)
      observationMap.set(observation.observationId, observation)
      const unit = unitMap.get(observation.evidenceUnitId)
      const span = unit?.sourceSpans.find((entry) => entry.spanId === observation.sourceSpanId)
      const source = unit ? documentMap.get(unit.versionId) : null
      if (!unit || !span || !source || !TYPED_KINDS.includes(observation.kind)) {
        findings.push(`${observation.observationId} observation graph/kind mismatch`)
        continue
      }
      if (observation.charStart < span.charStart || observation.charEnd > span.charEnd
          || codePointSlice(source.content, observation.charStart, observation.charEnd) !== observation.sourceSurface) {
        findings.push(`${observation.observationId} source surface/span mismatch`)
      }
      if (observation.kind === 'QUANTITY' || observation.kind === 'DATE') {
        if (!observation.qualifier
            || codePointSlice(source.content, observation.qualifierCharStart, observation.qualifierCharEnd)
            !== observation.qualifier
            || observation.qualifierCharStart < span.charStart || observation.qualifierCharEnd > span.charEnd) {
          findings.push(`${observation.observationId} qualifier grounding mismatch`)
        }
      }
      if (observation.kind === 'QUANTITY' && observation.direction !== 'NONE') {
        if (!observation.directionSourceSurface
            || codePointSlice(source.content, observation.directionCharStart, observation.directionCharEnd)
            !== observation.directionSourceSurface) {
          findings.push(`${observation.observationId} direction grounding mismatch`)
        }
      }
      if (observation.kind === 'IDENTIFIER_NUMBER'
          && (!observation.identifier || !observation.numberSurface
            || !Array.isArray(observation.normalizedSegments))) {
        findings.push(`${observation.observationId} identifier-number shape mismatch`)
      }
    }
    if (observationMap.size !== unitMap.size
        || [...unitMap.keys()].some((unitId) => ![...observationMap.values()].some((entry) => entry.evidenceUnitId === unitId))) {
      findings.push(`${directory} requires exactly one grounded observation per Evidence Unit`)
    }

    const annotationMap = new Map(typed.queryAnnotations.map((entry) => [entry.queryId, entry]))
    const familyCounts = {}
    const languageCounts = {}
    const answerabilityCounts = {}
    const kindCounts = {}
    const stateCounts = {}
    for (const query of questions.queries) {
      globalQueryIds.push(query.queryId)
      languageCounts[query.language] = (languageCounts[query.language] ?? 0) + 1
      answerabilityCounts[query.answerability] = (answerabilityCounts[query.answerability] ?? 0) + 1
      if (query.normalizedQuery !== normalize(query.query)
          || !query.categories.includes(LANGUAGE_CATEGORY[query.language])) {
        findings.push(`${query.queryId} normalized query/language category mismatch`)
      }
      const annotation = annotationMap.get(query.queryId)
      if (!annotation || annotation.userBundleId !== query.userBundleId
          || !PRIMARY_FAMILIES.includes(annotation.primaryFamily)) {
        findings.push(`${query.queryId} missing/invalid typed annotation`)
        continue
      }
      familyCounts[annotation.primaryFamily] = (familyCounts[annotation.primaryFamily] ?? 0) + 1
      kindCounts[annotation.constraint.kind] = (kindCounts[annotation.constraint.kind] ?? 0) + 1
      const constraint = annotation.constraint
      if (!TYPED_KINDS.includes(constraint.kind)
          || codePointSlice(query.query, constraint.queryCharStart, constraint.queryCharEnd) !== constraint.surface) {
        findings.push(`${query.queryId} constraint surface/kind mismatch`)
      }
      if (constraint.qualifier
          && codePointSlice(query.query, constraint.qualifierCharStart, constraint.qualifierCharEnd)
          !== constraint.qualifier) {
        findings.push(`${query.queryId} query qualifier grounding mismatch`)
      }
      if (constraint.direction !== undefined && constraint.direction !== 'NONE'
          && codePointSlice(query.query, constraint.directionCharStart, constraint.directionCharEnd)
          !== constraint.directionSourceSurface) {
        findings.push(`${query.queryId} query direction grounding mismatch`)
      }
      const bundleUnitIds = [...unitMap.values()]
        .filter((unit) => unit.userBundleId === query.userBundleId)
        .map((unit) => unit.evidenceUnitId).sort()
      const stateUnitIds = annotation.expectedEvidenceStates.map((entry) => entry.evidenceUnitId).sort()
      if (JSON.stringify(bundleUnitIds) !== JSON.stringify(stateUnitIds)) {
        findings.push(`${query.queryId} must freeze state+reason for every bundle Evidence Unit exactly once`)
      }
      let qualifierMismatchCount = 0
      let contradictedCount = 0
      for (const expected of annotation.expectedEvidenceStates) {
        stateCounts[expected.state] = (stateCounts[expected.state] ?? 0) + 1
        if (!MATCH_STATES.includes(expected.state) || !EXPECTED_REASONS.includes(expected.reason)) {
          findings.push(`${query.queryId}/${expected.evidenceUnitId} invalid state/reason enum`)
        }
        if ((expected.state === 'SATISFIED' && expected.reason !== 'MATCHED')
            || (expected.state === 'CONTRADICTED'
              && !['VALUE_MISMATCH', 'DIRECTION_MISMATCH'].includes(expected.reason))
            || (expected.state === 'UNKNOWN'
              && ['MATCHED', 'VALUE_MISMATCH', 'DIRECTION_MISMATCH'].includes(expected.reason))) {
          findings.push(`${query.queryId}/${expected.evidenceUnitId} invalid state/reason pairing`)
        }
        if (expected.reason === 'QUALIFIER_MISMATCH') {
          qualifierMismatchCount += 1
          if (expected.state !== 'UNKNOWN') findings.push(`${query.queryId} qualifier mismatch must be UNKNOWN and never SATISFIED`)
        }
        if (expected.state === 'CONTRADICTED') contradictedCount += 1
      }
      const satisfiedStates = annotation.expectedEvidenceStates.filter((entry) => entry.state === 'SATISFIED')
      const direct = query.aspects.flatMap((aspect) => aspect.expectedEvidence)
        .filter((entry) => entry.supportRelation === 'DIRECT_SUPPORT')
      if (query.answerability === 'SUPPORTED' && (satisfiedStates.length !== 1 || direct.length !== 1
          || satisfiedStates[0].evidenceUnitId !== direct[0].evidenceUnitId)) {
        findings.push(`${query.queryId} SUPPORTED requires exactly one aligned SATISFIED/DIRECT_SUPPORT`)
      }
      if (query.answerability === 'NOT_SUPPORTED' && (satisfiedStates.length !== 0 || direct.length !== 0)) {
        findings.push(`${query.queryId} NOT_SUPPORTED cannot have SATISFIED/DIRECT_SUPPORT`)
      }
      if (annotation.primaryFamily === 'qualifier_mismatch' && qualifierMismatchCount < 1) {
        findings.push(`${query.queryId} qualifier_mismatch family lacks UNKNOWN+QUALIFIER_MISMATCH evidence`)
      }
      if (annotation.primaryFamily !== 'qualifier_mismatch' && contradictedCount < 1) {
        findings.push(`${query.queryId} same-target wrong condition must be CONTRADICTED`)
      }
    }
    if (annotationMap.size !== questions.queries.length) findings.push(`${directory} query/annotation cardinality mismatch`)
    if (!countsEqual(familyCounts, Object.fromEntries(PRIMARY_FAMILIES.map((family) => [family, 2])))) {
      findings.push(`${directory} primary families must each have exactly 2 queries`)
    }
    if (!countsEqual(languageCounts, { EN: 4, KO: 4, KO_EN_MIXED: 4 })) {
      findings.push(`${directory} query language distribution must be EN/KO/MIXED=4/4/4`)
    }
    if (!countsEqual(answerabilityCounts, { NOT_SUPPORTED: 4, SUPPORTED: 8 })) {
      findings.push(`${directory} answerability distribution must be SUPPORTED=8/NOT_SUPPORTED=4`)
    }
    if (!countsEqual(kindCounts, { DATE: 2, IDENTIFIER_NUMBER: 2, QUANTITY: 8 })) {
      findings.push(`${directory} typed kind distribution must be QUANTITY=8/DATE=2/IDENTIFIER_NUMBER=2`)
    }
    if (!countsEqual(stateCounts, { CONTRADICTED: 14, SATISFIED: 8, UNKNOWN: 26 })) {
      findings.push(`${directory} expected state distribution must be SAT=8/CONTR=14/UNKNOWN=26`)
    }
    if (new Set(corpus.userBundles.map((bundle) => bundle.languageProfile)).size !== 3
        || new Set(corpus.userBundles.map((bundle) => bundle.professionGroup)).size !== 3) {
      findings.push(`${directory} must contain three distinct language and profession groups`)
    }

    validateManifestFiles(files, manifest, directory, findings)
    const declared = manifest.files.map((entry) => entry.path).sort()
    const actual = [...files.keys()]
      .filter((relative) => relative.startsWith(`${directory}/`) && relative !== `${directory}/manifest.json`)
      .map((relative) => relative.slice(directory.length + 1)).sort()
    if (JSON.stringify(declared) !== JSON.stringify(actual)) findings.push(`${directory} split manifest inventory mismatch`)
    if (JSON.stringify(manifest.counts) !== JSON.stringify({
      userBundles: 3, documents: 3, queries: 12, typedQueryAnnotations: 12,
      evidenceParents: 12, evidenceGroups: 12, evidenceUnits: 12, typedObservations: 12,
    })) findings.push(`${directory} manifest counts mismatch`)
  }

  assertUnique(globalQueryIds, 'query ID', findings)
  assertUnique(globalParentIds, 'parent ID', findings)
  assertUnique(globalGroupIds, 'group ID', findings)
  assertUnique(globalUnitIds, 'evidence unit ID', findings)
  assertUnique(globalObservationIds, 'observation ID', findings)
  if (totalBundles !== 6 || totalDocuments !== 6 || totalQueries !== 24 || totalUnits !== 24) {
    findings.push('root counts must be 6 bundles/documents and 24 queries/units')
  }
  if (new Set(globalProfessionGroups).size !== 6 || globalProfessionGroups.includes('BACKEND')) {
    findings.push('capability stress must use six distinct non-backend profession groups')
  }

  const lineageSingles = ['userBundleId', 'documentFamilyId', 'templateFamilyId', 'generatorSeedId']
  const lineageArrays = [
    'logicalDocumentIds', 'versionLineageIds', 'sourceFactIds',
    'sourceFactSignatures', 'questionGroupIds', 'normalizedQueries',
  ]
  if (lineage.status !== STATUS || lineage.generatorSourceSha256 !== generatorSourceSha256()
      || lineage.inputBaselineRevision !== INPUT_BASELINE_REVISION || lineage.bundles.length !== 6) {
    findings.push('lineage freeze/generator metadata mismatch')
  }
  for (const field of lineageSingles) assertUnique(lineage.bundles.map((entry) => entry[field]), `lineage ${field}`, findings)
  for (const field of lineageArrays) assertUnique(lineage.bundles.flatMap((entry) => entry[field]), `lineage ${field}`, findings)

  detectForbiddenRuntimeKeys({ lineage, splits: ['dev', 'calibration'].map((directory) => ({
    corpus: parsed(`${directory}/corpus.json`), gold: parsed(`${directory}/gold-evidence.json`),
    questions: parsed(`${directory}/questions.json`), typed: parsed(`${directory}/typed-annotations.json`),
  })) }, 'typed-stress-1.1.0', findings)

  if (rootManifest.status !== STATUS || rootManifest.mutable !== false || rootManifest.opened !== true
      || rootManifest.searchExecuted !== false || rootManifest.generationSourceRevision !== INPUT_BASELINE_REVISION
      || rootManifest.validationMode !== 'DETERMINISTIC_CONTRACT_VALIDATOR_NO_EXTERNAL_JSON_SCHEMA_ENGINE') {
    findings.push('root INPUT_FROZEN manifest flags mismatch')
  }
  validateManifestFiles(files, rootManifest, '', findings)
  const rootDeclared = rootManifest.files.map((entry) => entry.path).sort()
  const rootActual = [...files.keys()].filter((relative) => relative !== 'manifest.json').sort()
  if (JSON.stringify(rootDeclared) !== JSON.stringify(rootActual)) findings.push('root manifest inventory mismatch')
  if (JSON.stringify(rootManifest.counts) !== JSON.stringify({
    userBundles: 6, documents: 6, queries: 24, typedQueryAnnotations: 24,
    evidenceParents: 24, evidenceGroups: 24, evidenceUnits: 24, typedObservations: 24,
  })) findings.push('root manifest counts mismatch')
  return findings
}

function collisionFindings(current, previous, label) {
  const findings = []
  const priorBundles = previous.bundles ?? []
  for (const field of ['userBundleId', 'documentFamilyId', 'templateFamilyId', 'generatorSeedId']) {
    const known = new Set(priorBundles.map((entry) => entry[field]).filter(Boolean))
    if (current.bundles.some((entry) => known.has(entry[field]))) findings.push(`${label} collision: ${field}`)
  }
  for (const field of [
    'logicalDocumentIds', 'versionLineageIds', 'sourceFactIds',
    'sourceFactSignatures', 'questionGroupIds', 'normalizedQueries',
  ]) {
    const known = new Set(priorBundles.flatMap((entry) => entry[field] ?? []))
    if (current.bundles.flatMap((entry) => entry[field] ?? []).some((value) => known.has(value))) {
      findings.push(`${label} collision: ${field}`)
    }
  }
  if (previous.generator && current.generator === previous.generator) findings.push(`${label} collision: generator path`)
  if (previous.generatorRevision && current.generatorRevision === previous.generatorRevision) {
    findings.push(`${label} collision: generator revision`)
  }
  return findings
}

async function externalIntegrityFindings(files) {
  const findings = []
  const current = JSON.parse(files.get('lineage.json'))
  const previousPaths = [
    ['typed stress 1.0.1', path.resolve('src/searchEvaluation/resources/search-v3-evaluation/typed-constraints-stress-1.0.1/lineage.json')],
    ['typed stress 1.0.0', path.resolve('src/searchEvaluation/resources/search-v3-evaluation/typed-constraints-stress-1.0.0/lineage.json')],
    ['long-form DEV/CAL', path.resolve('src/searchEvaluation/resources/search-v3-evaluation/devcal-1.1.0/lineage.json')],
    ['robustness DEV/CAL', path.resolve('src/searchEvaluation/resources/search-v3-evaluation/devcal-robustness-1.0.0/lineage.json')],
  ]
  for (const [label, location] of previousPaths) {
    const previous = JSON.parse(await readFile(location, 'utf8'))
    findings.push(...collisionFindings(current, previous, label))
  }
  const unifiedLineage = JSON.parse(await readFile(
    path.resolve('src/test/resources/search-v3-evaluation/lineage.json'), 'utf8',
  ))
  const unifiedDevCal = {
    ...unifiedLineage,
    bundles: (unifiedLineage.bundles ?? []).filter((entry) => entry.split !== 'SEALED_FINAL_TEST'),
  }
  const unifiedSealedMetadata = {
    ...unifiedLineage,
    bundles: (unifiedLineage.bundles ?? []).filter((entry) => entry.split === 'SEALED_FINAL_TEST'),
  }
  if (unifiedSealedMetadata.bundles.length === 0) {
    findings.push('unified root lineage lacks SEALED_FINAL_TEST metadata bundle entries')
  }
  findings.push(...collisionFindings(current, unifiedDevCal, 'original DEV/CAL lineage metadata'))
  findings.push(...collisionFindings(current, unifiedSealedMetadata, 'SEALED FINAL lineage metadata'))
  const sealed = JSON.parse(await readFile(SEALED_MANIFEST, 'utf8'))
  const rootManifest = JSON.parse(files.get('manifest.json'))
  if (sealed.combinedSha256 !== SEALED_FINAL_SHA256
      || sealed.opened !== false || sealed.searchExecuted !== false) {
    findings.push('SEALED FINAL manifest metadata changed')
  }
  if (sealed.datasetVersion === DATASET_VERSION || sealed.combinedSha256 === rootManifest.combinedSha256) {
    findings.push('SEALED FINAL metadata collides with Stress 1.1.0')
  }
  if ([...files.keys()].some((relative) => relative.includes('sealed-final'))) {
    findings.push('Stress 1.1.0 must not materialize SEALED FINAL paths')
  }
  return findings
}

async function listFiles(root, current = '') {
  const entries = await readdir(path.join(root, current), { withFileTypes: true }).catch(() => [])
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
    findings.forEach((finding) => console.error(`FAIL ${finding}`))
    process.exitCode = 1
    return
  }
  const manifest = JSON.parse(files.get('manifest.json'))
  console.log(`PASS dataset=${DATASET_VERSION}`)
  console.log(`PASS status=${STATUS}`)
  console.log(`PASS files=${files.size}`)
  console.log(`PASS bundles=${manifest.counts.userBundles}`)
  console.log(`PASS queries=${manifest.counts.queries}`)
  console.log(`PASS states=${JSON.stringify(manifest.distributions.expectedState)}`)
  console.log(`PASS primaryFamilies=${JSON.stringify(manifest.distributions.primaryFamily)}`)
  console.log(`PASS combinedSha256=${manifest.combinedSha256}`)
  console.log('PASS schemaValidation=DETERMINISTIC_CONTRACT_VALIDATOR externalJsonSchemaEngine=NOT_RUN')
  console.log(`PASS sealedFinal=${SEALED_FINAL_SHA256} opened=false searchExecuted=false semanticAccess=0`)
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
  if (findings.length > 0) throw new Error(`Refusing to materialize invalid Stress 1.1.0:\n${findings.join('\n')}`)
  for (const [relative, content] of files) {
    const destination = path.join(OUTPUT_ROOT, relative)
    await mkdir(path.dirname(destination), { recursive: true })
    await writeFile(destination, content, 'utf8')
  }
  console.log(`Materialized ${DATASET_VERSION} at ${OUTPUT_ROOT}`)
}

async function applyPrecommitInputCorrection(files) {
  const manifestPath = path.join(OUTPUT_ROOT, 'manifest.json')
  const existingManifest = JSON.parse(await readFile(manifestPath, 'utf8'))
  if (existingManifest.status !== STATUS
      || existingManifest.combinedSha256 !== PRECOMMIT_CORRECTION_FROM_SHA256) {
    throw new Error('Refusing pre-commit correction: existing INPUT_FROZEN root hash is not the audited predecessor')
  }
  const actualFiles = await listFiles(OUTPUT_ROOT)
  const expectedFiles = [...files.keys()].sort()
  if (JSON.stringify(actualFiles) !== JSON.stringify(expectedFiles)) {
    throw new Error('Refusing pre-commit correction: existing inventory differs from the deterministic 19-file contract')
  }
  for (const entry of existingManifest.files) {
    const content = await readFile(path.join(OUTPUT_ROOT, entry.path))
    if (content.length !== entry.bytes || sha256(content) !== entry.sha256) {
      throw new Error(`Refusing pre-commit correction: predecessor file hash mismatch: ${entry.path}`)
    }
  }
  const findings = [...validateInMemory(files), ...await externalIntegrityFindings(files)]
  if (findings.length > 0) throw new Error(`Refusing invalid pre-commit correction:\n${findings.join('\n')}`)
  for (const [relative, content] of files) {
    await writeFile(path.join(OUTPUT_ROOT, relative), content, 'utf8')
  }
  console.log(`Corrected ${DATASET_VERSION} before input commit without deleting or changing inventory`)
}

const files = buildArtifacts()
if (process.argv.includes('--check')) await check(files)
else if (process.argv.includes('--apply-precommit-contract-correction')) await applyPrecommitInputCorrection(files)
else await materialize(files)
