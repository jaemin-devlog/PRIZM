import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, relative, resolve, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

const SCRIPT_DIR = dirname(fileURLToPath(import.meta.url))
const REPOSITORY_ROOT = resolve(SCRIPT_DIR, '../../..')
const DATASET_ROOT = resolve(REPOSITORY_ROOT, 'src/test/resources/search-v3-evaluation')
const DATASET_VERSION = 'search-v3-fresh-seed-1.0.1'
const SCHEMA_VERSION = '1.0.0'
const GENERATION_SOURCE_REVISION = '14073dd1664fddb009a5bea8823a228e582abf51'
const GENERATOR_NAME = 'prizm-search-v3-seed-materializer'
const GENERATOR_REVISION = '1.0.0'
const SPLIT_DIR = new Map([
  ['DEV', 'dev'],
  ['CALIBRATION', 'calibration'],
  ['SEALED_FINAL_TEST', 'sealed-final'],
])

const sha256 = (value) => createHash('sha256').update(value).digest('hex')
const json = (value) => `${JSON.stringify(value, null, 2)}\n`
const posix = (value) => value.split(sep).join('/')
const normalizedQuery = (value) => value
  .normalize('NFKC')
  .toLocaleLowerCase('und')
  .replace(/[\p{P}\p{S}]+/gu, ' ')
  .replace(/\s+/gu, ' ')
  .trim()

function constraints(overrides = {}) {
  return {
    entities: [],
    numerics: [],
    dates: [],
    actors: [],
    completionStates: [],
    polarity: 'POSITIVE',
    ...overrides,
  }
}

function aspect(aspectId, answerability, expectedEvidence = [], overrides = {}) {
  const directGroups = expectedEvidence
    .filter((entry) => entry.supportRelation === 'DIRECT_SUPPORT')
    .map((entry) => entry.groupId)
    .filter(Boolean)
  return {
    aspectId,
    required: true,
    answerability,
    expectedEvidence: expectedEvidence.map(({ evidenceUnitId, supportRelation }) => ({ evidenceUnitId, supportRelation })),
    requiredEvidenceGroupIds: overrides.requiredEvidenceGroupIds ?? [...new Set(directGroups)],
    minEvidenceGroups: overrides.minEvidenceGroups ?? (directGroups.length > 0 ? 1 : 0),
    constraints: constraints(overrides.constraints),
  }
}

function query(user, number, text, categories, language, answerability, aspects, overrides = {}) {
  const queryId = `${user}-Q${String(number).padStart(2, '0')}`
  return {
    queryId,
    userBundleId: user,
    questionGroupId: overrides.questionGroupId ?? `${queryId}-FAMILY`,
    query: text,
    normalizedQuery: normalizedQuery(text),
    categories,
    language,
    answerability,
    aspectExpression: overrides.aspectExpression ?? {
      operator: 'ALL',
      requiredAspectIds: aspects.map((entry) => entry.aspectId),
      minShouldMatch: aspects.length,
    },
    aspects,
    safetyExclusions: overrides.safetyExclusions ?? [],
  }
}

function document(user, documentNumber, versionNumber, fields) {
  const number = String(documentNumber).padStart(2, '0')
  const version = String(versionNumber).padStart(2, '0')
  const documentId = `${user}-D${number}`
  return {
    documentId,
    logicalDocumentId: `${documentId}-LOGICAL`,
    versionLineageId: `${documentId}-LINEAGE`,
    versionId: `${documentId}-V${version}`,
    versionNumber,
    ...fields,
  }
}

const bundles = [
  {
    userBundleId: 'SV3-U01', split: 'DEV', professionGroup: 'BACKEND', profession: 'backend engineer',
    languageProfile: 'KO', documentFamilyId: 'SV3-U01-DOC-FAMILY', templateFamilyId: 'SV3-TEMPLATE-BACKEND-01',
    generatorSeedId: 'SV3-SEED-BACKEND-8F1C',
    documents: [
      document('SV3-U01', 1, 1, {
        active: true, title: '합성 백엔드 경력 요약', documentType: 'RESUME', documentStructure: 'SHORT_RESUME',
        fileType: 'TXT', language: 'KO', fileName: 'sv3-u01-backend-resume-v01.txt', supportScope: 'SUPPORTED_BY_CURRENT',
        content: [
          '합성 백엔드 경력 요약',
          '',
          '서비스 운영',
          '본인은 내부 결제 게이트웨이 PAY-GW를 설계하고 2024년 1월부터 2024년 12월까지 운영했다.',
          '일일 요청 1,300건을 안정적으로 처리했고 장애 대응 절차를 문서화했다.',
          'Kafka는 검토했지만 운영 환경에 도입하지 않았다.',
          '',
          '운영 원칙',
          '고객 데이터 접근은 최소 권한으로 제한하고 변경 기록을 남겼다.',
          '',
        ].join('\n'),
      }),
      document('SV3-U01', 1, 0, {
        active: false, title: '합성 백엔드 경력 요약 초안', documentType: 'RESUME', documentStructure: 'SHORT_RESUME',
        fileType: 'TXT', language: 'KO', fileName: 'sv3-u01-backend-resume-v00-inactive.txt', supportScope: 'SUPPORTED_BY_CURRENT',
        content: [
          '합성 백엔드 경력 요약 초안',
          '',
          '과거 실험',
          'DormantQueue를 사내 시험 환경에서만 사용했다.',
          '',
        ].join('\n'),
      }),
      document('SV3-U01', 2, 1, {
        active: true, title: '합성 장애 대응 경력기술서', documentType: 'CAREER_REVIEW', documentStructure: 'CAREER_DESCRIPTION',
        fileType: 'TXT', language: 'KO', fileName: 'sv3-u01-backend-career-v01.txt', supportScope: 'SUPPORTED_BY_CURRENT',
        content: [
          '합성 장애 대응 경력기술서',
          '',
          '장애 재발 방지',
          '본인은 반복 장애의 로그와 배포 이력을 대조해 원인을 좁혔다.',
          '재발 방지 체크리스트를 작성하고 월별 복구 훈련을 운영했다.',
          '',
        ].join('\n'),
      }),
    ],
    parents: [
      { parentId: 'SV3-U01-P01', documentId: 'SV3-U01-D01', versionId: 'SV3-U01-D01-V01', label: '서비스 운영', anchor: '서비스 운영\n본인은 내부 결제 게이트웨이 PAY-GW를 설계하고 2024년 1월부터 2024년 12월까지 운영했다.\n일일 요청 1,300건을 안정적으로 처리했고 장애 대응 절차를 문서화했다.\nKafka는 검토했지만 운영 환경에 도입하지 않았다.' },
      { parentId: 'SV3-U01-P02', documentId: 'SV3-U01-D02', versionId: 'SV3-U01-D02-V01', label: '장애 재발 방지', anchor: '장애 재발 방지\n본인은 반복 장애의 로그와 배포 이력을 대조해 원인을 좁혔다.\n재발 방지 체크리스트를 작성하고 월별 복구 훈련을 운영했다.' },
      { parentId: 'SV3-U01-P03', documentId: 'SV3-U01-D01', versionId: 'SV3-U01-D01-V00', label: '과거 실험', anchor: '과거 실험\nDormantQueue를 사내 시험 환경에서만 사용했다.' },
    ],
    units: [
      { id: 'SV3-U01-P01-E01', parentId: 'SV3-U01-P01', groupId: 'SV3-U01-G01', sourceFactId: 'SV3-FACT-U01-PAYGW', groupDescription: 'PAY-GW 설계 및 운영', anchors: ['본인은 내부 결제 게이트웨이 PAY-GW를 설계하고 2024년 1월부터 2024년 12월까지 운영했다.'], actor: 'SELF', state: 'PRODUCTION', aspects: ['paygw_operation'], entities: [{ entityType: 'PRODUCT_IDENTIFIER', canonicalValue: 'PAY-GW', surfaceForms: ['PAY-GW'] }], dates: [{ start: '2024-01-01', end: '2024-12-31', precision: 'MONTH', sourceSurface: '2024년 1월부터 2024년 12월까지' }] },
      { id: 'SV3-U01-P01-E02', parentId: 'SV3-U01-P01', groupId: 'SV3-U01-G02', sourceFactId: 'SV3-FACT-U01-REQUESTS', groupDescription: '일일 요청 처리량', anchors: ['일일 요청 1,300건을 안정적으로 처리했고 장애 대응 절차를 문서화했다.'], actor: 'SELF', state: 'PRODUCTION', aspects: ['request_volume'], numerics: [{ normalizedValue: 1300, unit: 'REQUEST_PER_DAY', semanticType: 'REQUEST_COUNT', sourceSurface: '1,300건', qualifierTokens: ['일일', '요청'] }] },
      { id: 'SV3-U01-P01-E03', parentId: 'SV3-U01-P01', groupId: 'SV3-U01-G03', sourceFactId: 'SV3-FACT-U01-KAFKA-NOT-ADOPTED', groupDescription: 'Kafka 미도입', anchors: ['Kafka는 검토했지만 운영 환경에 도입하지 않았다.'], actor: 'SELF', state: 'ATTEMPTED', aspects: ['kafka_production'], entities: [{ entityType: 'TECHNOLOGY', canonicalValue: 'KAFKA', surfaceForms: ['Kafka'] }] },
      { id: 'SV3-U01-P02-E01', parentId: 'SV3-U01-P02', groupId: 'SV3-U01-G04', sourceFactId: 'SV3-FACT-U01-INCIDENT-PREVENTION', groupDescription: '장애 재발 방지 절차', anchors: ['본인은 반복 장애의 로그와 배포 이력을 대조해 원인을 좁혔다.', '재발 방지 체크리스트를 작성하고 월별 복구 훈련을 운영했다.'], actor: 'SELF', state: 'COMPLETED', aspects: ['incident_prevention'] },
      { id: 'SV3-U01-P03-E01', parentId: 'SV3-U01-P03', groupId: 'SV3-U01-G05', sourceFactId: 'SV3-FACT-U01-DORMANTQUEUE-INACTIVE', groupDescription: '비활성 버전의 DormantQueue 실험', anchors: ['DormantQueue를 사내 시험 환경에서만 사용했다.'], actor: 'SELF', state: 'PROTOTYPE', aspects: ['dormantqueue_experiment'], entities: [{ entityType: 'TECHNOLOGY', canonicalValue: 'DORMANTQUEUE', surfaceForms: ['DormantQueue'] }] },
    ],
    questions: [],
  },
  {
    userBundleId: 'SV3-U02', split: 'CALIBRATION', professionGroup: 'FRONTEND_MOBILE', profession: 'frontend and mobile engineer',
    languageProfile: 'EN', documentFamilyId: 'SV3-U02-DOC-FAMILY', templateFamilyId: 'SV3-TEMPLATE-FRONTEND-02',
    generatorSeedId: 'SV3-SEED-FRONTEND-2A7E',
    documents: [
      document('SV3-U02', 1, 1, {
        active: true, title: 'Synthetic Interface Portfolio', documentType: 'PORTFOLIO', documentStructure: 'LONG_PORTFOLIO',
        fileType: 'TXT', language: 'EN', fileName: 'sv3-u02-interface-portfolio-v01.txt', supportScope: 'SUPPORTED_BY_CURRENT',
        content: [
          'Synthetic Interface Portfolio',
          '',
          'Accessibility release',
          'I led keyboard-navigation and screen-reader improvements for the account workflow.',
          'We shipped the workflow at WCAG 2.1 AA and reduced accessibility audit findings from 18 to 4.',
          '',
          'Prototype boundary',
          'I built a voice-navigation prototype for a mobile research session.',
          'The voice-navigation prototype was not released to production.',
          '',
          'Mobile delivery',
          'I coordinated the iOS and Android release checklist with design and quality teams.',
          '',
        ].join('\n'),
      }),
    ],
    parents: [
      { parentId: 'SV3-U02-P01', documentId: 'SV3-U02-D01', versionId: 'SV3-U02-D01-V01', label: 'Accessibility release', anchor: 'Accessibility release\nI led keyboard-navigation and screen-reader improvements for the account workflow.\nWe shipped the workflow at WCAG 2.1 AA and reduced accessibility audit findings from 18 to 4.' },
      { parentId: 'SV3-U02-P02', documentId: 'SV3-U02-D01', versionId: 'SV3-U02-D01-V01', label: 'Prototype boundary', anchor: 'Prototype boundary\nI built a voice-navigation prototype for a mobile research session.\nThe voice-navigation prototype was not released to production.' },
      { parentId: 'SV3-U02-P03', documentId: 'SV3-U02-D01', versionId: 'SV3-U02-D01-V01', label: 'Mobile delivery', anchor: 'Mobile delivery\nI coordinated the iOS and Android release checklist with design and quality teams.' },
    ],
    units: [
      { id: 'SV3-U02-P01-E01', parentId: 'SV3-U02-P01', groupId: 'SV3-U02-G01', sourceFactId: 'SV3-FACT-U02-A11Y', groupDescription: '접근성 개선 출시', anchors: ['I led keyboard-navigation and screen-reader improvements for the account workflow.', 'We shipped the workflow at WCAG 2.1 AA and reduced accessibility audit findings from 18 to 4.'], actor: 'SELF', state: 'PRODUCTION', aspects: ['accessibility_release'], entities: [{ entityType: 'STANDARD', canonicalValue: 'WCAG_2_1_AA', surfaceForms: ['WCAG 2.1 AA'] }], numerics: [{ normalizedValue: 18, unit: 'FINDING', semanticType: 'AUDIT_FINDING_BEFORE', sourceSurface: '18', qualifierTokens: ['findings'] }, { normalizedValue: 4, unit: 'FINDING', semanticType: 'AUDIT_FINDING_AFTER', sourceSurface: '4', qualifierTokens: ['findings'] }] },
      { id: 'SV3-U02-P02-E01', parentId: 'SV3-U02-P02', groupId: 'SV3-U02-G02', sourceFactId: 'SV3-FACT-U02-VOICE-PROTOTYPE', groupDescription: '음성 탐색 prototype', anchors: ['I built a voice-navigation prototype for a mobile research session.'], actor: 'SELF', state: 'PROTOTYPE', aspects: ['voice_navigation_prototype'], entities: [{ entityType: 'FEATURE', canonicalValue: 'VOICE_NAVIGATION', surfaceForms: ['voice-navigation'] }] },
      { id: 'SV3-U02-P02-E02', parentId: 'SV3-U02-P02', groupId: 'SV3-U02-G03', sourceFactId: 'SV3-FACT-U02-VOICE-NOT-PROD', groupDescription: '음성 탐색 production 미출시', anchors: ['The voice-navigation prototype was not released to production.'], actor: 'SELF', state: 'PROTOTYPE', aspects: ['voice_navigation_production'], entities: [{ entityType: 'FEATURE', canonicalValue: 'VOICE_NAVIGATION', surfaceForms: ['voice-navigation'] }] },
      { id: 'SV3-U02-P03-E01', parentId: 'SV3-U02-P03', groupId: 'SV3-U02-G04', sourceFactId: 'SV3-FACT-U02-MOBILE-RELEASE', groupDescription: 'iOS/Android 출시 조율', anchors: ['I coordinated the iOS and Android release checklist with design and quality teams.'], actor: 'SELF', state: 'COMPLETED', aspects: ['mobile_release'], entities: [{ entityType: 'PLATFORM', canonicalValue: 'IOS_ANDROID', surfaceForms: ['iOS', 'Android'] }] },
    ],
    questions: [],
  },
  {
    userBundleId: 'SV3-U03', split: 'CALIBRATION', professionGroup: 'DATA_AI_INFRA', profession: 'data and infrastructure specialist',
    languageProfile: 'KO_EN_MIXED', documentFamilyId: 'SV3-U03-DOC-FAMILY', templateFamilyId: 'SV3-TEMPLATE-DATA-03',
    generatorSeedId: 'SV3-SEED-DATA-4D9B',
    documents: [
      document('SV3-U03', 1, 1, {
        active: true, title: 'Data & Infra 운영표', documentType: 'PROJECT_REPORT', documentStructure: 'TABLE_LIKE',
        fileType: 'TXT', language: 'KO_EN_MIXED', fileName: 'sv3-u03-data-infra-table-v01.txt', supportScope: 'SUPPORTED_BY_CURRENT',
        content: [
          'Data & Infra 운영표',
          '',
          '항목 | 기간 | 결과',
          'pipeline | 2023-03~2024-02 | 월 2,400,000 records 검증과 품질 경보 운영',
          'incident | 2024-01 | on-call runbook 작성 및 복구 훈련 완료',
          '',
          '모델 상태',
          '추천 모델은 offline prototype까지 검증했고 production 배포는 하지 않았다.',
          '',
        ].join('\n'),
      }),
      document('SV3-U03', 2, 1, {
        active: true, title: 'Synthetic Training Record', documentType: 'COURSE_COMPLETION', documentStructure: 'CERTIFICATION_TRAINING',
        fileType: 'TXT', language: 'KO_EN_MIXED', fileName: 'sv3-u03-training-record-v01.txt', supportScope: 'SUPPORTED_BY_CURRENT',
        content: [
          'Synthetic Training Record',
          '',
          '수료 내역',
          '2024년 5월 Kubernetes Administration 과정을 32시간 이수했다.',
          '교육 실습에서 cluster backup과 restore 절차를 수행했다.',
          '',
        ].join('\n'),
      }),
    ],
    parents: [
      { parentId: 'SV3-U03-P01', documentId: 'SV3-U03-D01', versionId: 'SV3-U03-D01-V01', label: '운영표', anchor: '항목 | 기간 | 결과\npipeline | 2023-03~2024-02 | 월 2,400,000 records 검증과 품질 경보 운영\nincident | 2024-01 | on-call runbook 작성 및 복구 훈련 완료' },
      { parentId: 'SV3-U03-P02', documentId: 'SV3-U03-D01', versionId: 'SV3-U03-D01-V01', label: '모델 상태', anchor: '모델 상태\n추천 모델은 offline prototype까지 검증했고 production 배포는 하지 않았다.' },
      { parentId: 'SV3-U03-P03', documentId: 'SV3-U03-D02', versionId: 'SV3-U03-D02-V01', label: '수료 내역', anchor: '수료 내역\n2024년 5월 Kubernetes Administration 과정을 32시간 이수했다.\n교육 실습에서 cluster backup과 restore 절차를 수행했다.' },
    ],
    units: [
      { id: 'SV3-U03-P01-E01', parentId: 'SV3-U03-P01', groupId: 'SV3-U03-G01', sourceFactId: 'SV3-FACT-U03-PIPELINE', groupDescription: 'pipeline 운영 기간과 처리량', anchors: ['pipeline | 2023-03~2024-02 | 월 2,400,000 records 검증과 품질 경보 운영'], actor: 'SELF', state: 'PRODUCTION', aspects: ['pipeline_operation'], entities: [{ entityType: 'SYSTEM', canonicalValue: 'PIPELINE', surfaceForms: ['pipeline'] }], numerics: [{ normalizedValue: 2400000, unit: 'RECORD_PER_MONTH', semanticType: 'DATA_RECORD_COUNT', sourceSurface: '2,400,000', qualifierTokens: ['월', 'records'] }], dates: [{ start: '2023-03-01', end: '2024-02-29', precision: 'MONTH', sourceSurface: '2023-03~2024-02' }] },
      { id: 'SV3-U03-P01-E02', parentId: 'SV3-U03-P01', groupId: 'SV3-U03-G02', sourceFactId: 'SV3-FACT-U03-INCIDENT', groupDescription: 'on-call 복구 훈련', anchors: ['incident | 2024-01 | on-call runbook 작성 및 복구 훈련 완료'], actor: 'SELF', state: 'COMPLETED', aspects: ['incident_response'], dates: [{ start: '2024-01-01', end: '2024-01-31', precision: 'MONTH', sourceSurface: '2024-01' }] },
      { id: 'SV3-U03-P02-E01', parentId: 'SV3-U03-P02', groupId: 'SV3-U03-G03', sourceFactId: 'SV3-FACT-U03-MODEL-NOT-PROD', groupDescription: '추천 모델 prototype 및 미배포', anchors: ['추천 모델은 offline prototype까지 검증했고 production 배포는 하지 않았다.'], actor: 'SELF', state: 'PROTOTYPE', aspects: ['model_production'], entities: [{ entityType: 'ARTIFACT', canonicalValue: 'RECOMMENDATION_MODEL', surfaceForms: ['추천 모델'] }] },
      { id: 'SV3-U03-P03-E01', parentId: 'SV3-U03-P03', groupId: 'SV3-U03-G04', sourceFactId: 'SV3-FACT-U03-K8S-TRAINING', groupDescription: 'Kubernetes 과정 수료', anchors: ['2024년 5월 Kubernetes Administration 과정을 32시간 이수했다.', '교육 실습에서 cluster backup과 restore 절차를 수행했다.'], actor: 'SELF', state: 'COMPLETED', aspects: ['kubernetes_training'], entities: [{ entityType: 'COURSE', canonicalValue: 'KUBERNETES_ADMINISTRATION', surfaceForms: ['Kubernetes Administration'] }], numerics: [{ normalizedValue: 32, unit: 'HOUR', semanticType: 'TRAINING_DURATION', sourceSurface: '32시간', qualifierTokens: ['과정', '이수'] }], dates: [{ start: '2024-05-01', end: '2024-05-31', precision: 'MONTH', sourceSurface: '2024년 5월' }] },
    ],
    questions: [],
  },
  {
    userBundleId: 'SV3-U04', split: 'DEV', professionGroup: 'DESIGN_PRODUCT', profession: 'service designer and product researcher',
    languageProfile: 'KO', documentFamilyId: 'SV3-U04-DOC-FAMILY', templateFamilyId: 'SV3-TEMPLATE-DESIGN-04',
    generatorSeedId: 'SV3-SEED-DESIGN-5C2D',
    documents: [
      document('SV3-U04', 1, 1, {
        active: true, title: '합성 서비스 디자인 포트폴리오', documentType: 'PORTFOLIO', documentStructure: 'LONG_PORTFOLIO',
        fileType: 'TXT', language: 'KO', fileName: 'sv3-u04-design-portfolio-v01.txt', supportScope: 'SUPPORTED_BY_CURRENT',
        content: [
          '합성 서비스 디자인 포트폴리오',
          '',
          '사용자 조사',
          '본인은 소상공인 12명을 인터뷰해 주문 취소 과정의 혼란 지점을 분류했다.',
          '관찰 결과를 토대로 단계 명칭과 오류 안내 문구를 다시 설계했다.',
          '',
          '검증 결과',
          '사용성 평가에서 과업 완료율이 62%에서 84%로 높아졌다.',
          '',
          '타 조직 활동',
          '마케팅팀은 별도 랜딩 페이지에서 A/B 테스트를 수행했다.',
          '',
        ].join('\n'),
      }),
    ],
    parents: [
      { parentId: 'SV3-U04-P01', documentId: 'SV3-U04-D01', versionId: 'SV3-U04-D01-V01', label: '사용자 조사', anchor: '사용자 조사\n본인은 소상공인 12명을 인터뷰해 주문 취소 과정의 혼란 지점을 분류했다.\n관찰 결과를 토대로 단계 명칭과 오류 안내 문구를 다시 설계했다.' },
      { parentId: 'SV3-U04-P02', documentId: 'SV3-U04-D01', versionId: 'SV3-U04-D01-V01', label: '검증 결과', anchor: '검증 결과\n사용성 평가에서 과업 완료율이 62%에서 84%로 높아졌다.' },
      { parentId: 'SV3-U04-P03', documentId: 'SV3-U04-D01', versionId: 'SV3-U04-D01-V01', label: '타 조직 활동', anchor: '타 조직 활동\n마케팅팀은 별도 랜딩 페이지에서 A/B 테스트를 수행했다.' },
    ],
    units: [
      { id: 'SV3-U04-P01-E01', parentId: 'SV3-U04-P01', groupId: 'SV3-U04-G01', sourceFactId: 'SV3-FACT-U04-INTERVIEWS', groupDescription: '소상공인 인터뷰', anchors: ['본인은 소상공인 12명을 인터뷰해 주문 취소 과정의 혼란 지점을 분류했다.'], actor: 'SELF', state: 'COMPLETED', aspects: ['user_research'], numerics: [{ normalizedValue: 12, unit: 'PERSON', semanticType: 'INTERVIEW_PARTICIPANT_COUNT', sourceSurface: '12명', qualifierTokens: ['소상공인', '인터뷰'] }] },
      { id: 'SV3-U04-P01-E02', parentId: 'SV3-U04-P01', groupId: 'SV3-U04-G02', sourceFactId: 'SV3-FACT-U04-REDESIGN', groupDescription: '조사 기반 인터페이스 재설계', anchors: ['관찰 결과를 토대로 단계 명칭과 오류 안내 문구를 다시 설계했다.'], actor: 'SELF', state: 'COMPLETED', aspects: ['design_decision'] },
      { id: 'SV3-U04-P02-E01', parentId: 'SV3-U04-P02', groupId: 'SV3-U04-G03', sourceFactId: 'SV3-FACT-U04-USABILITY', groupDescription: '과업 완료율 개선', anchors: ['사용성 평가에서 과업 완료율이 62%에서 84%로 높아졌다.'], actor: 'SELF', state: 'COMPLETED', aspects: ['usability_outcome'], numerics: [{ normalizedValue: 62, unit: 'PERCENT', semanticType: 'TASK_COMPLETION_RATE_BEFORE', sourceSurface: '62%', qualifierTokens: ['과업', '완료율'] }, { normalizedValue: 84, unit: 'PERCENT', semanticType: 'TASK_COMPLETION_RATE_AFTER', sourceSurface: '84%', qualifierTokens: ['과업', '완료율'] }] },
      { id: 'SV3-U04-P03-E01', parentId: 'SV3-U04-P03', groupId: 'SV3-U04-G04', sourceFactId: 'SV3-FACT-U04-OTHER-ACTOR-AB', groupDescription: '마케팅팀의 A/B 테스트', anchors: ['마케팅팀은 별도 랜딩 페이지에서 A/B 테스트를 수행했다.'], actor: 'OTHER', state: 'COMPLETED', aspects: ['ab_test'] },
    ],
    questions: [],
  },
  {
    userBundleId: 'SV3-U05', split: 'SEALED_FINAL_TEST', professionGroup: 'PLANNING', profession: 'business planner',
    languageProfile: 'KO', documentFamilyId: 'SV3-U05-DOC-FAMILY', templateFamilyId: 'SV3-TEMPLATE-PLANNING-05',
    generatorSeedId: 'SV3-SEED-PLANNING-3B6A',
    documents: [
      document('SV3-U05', 1, 1, {
        active: true, title: '합성 기획 자기소개서', documentType: 'COVER_LETTER', documentStructure: 'NARRATIVE_SELF_INTRODUCTION',
        fileType: 'TXT', language: 'KO', fileName: 'sv3-u05-planning-narrative-v01.txt', supportScope: 'SUPPORTED_BY_CURRENT',
        content: [
          '합성 기획 자기소개서',
          '',
          '2025년 1분기 실행',
          '본인은 2025년 1월부터 3월까지 고객 문의 분류 기준을 정리하고 운영 부서와 적용했다.',
          '주간 회고를 열어 누락 유형을 확인하고 다음 주 업무 우선순위에 반영했다.',
          '',
          '향후 계획',
          '2025년 3분기에는 파트너 교육 프로그램을 추진할 계획이며 아직 실행하지 않았다.',
          '',
        ].join('\n'),
      }),
    ],
    parents: [
      { parentId: 'SV3-U05-P01', documentId: 'SV3-U05-D01', versionId: 'SV3-U05-D01-V01', label: '2025년 1분기 실행', anchor: '2025년 1분기 실행\n본인은 2025년 1월부터 3월까지 고객 문의 분류 기준을 정리하고 운영 부서와 적용했다.\n주간 회고를 열어 누락 유형을 확인하고 다음 주 업무 우선순위에 반영했다.' },
      { parentId: 'SV3-U05-P02', documentId: 'SV3-U05-D01', versionId: 'SV3-U05-D01-V01', label: '향후 계획', anchor: '향후 계획\n2025년 3분기에는 파트너 교육 프로그램을 추진할 계획이며 아직 실행하지 않았다.' },
    ],
    units: [
      { id: 'SV3-U05-P01-E01', parentId: 'SV3-U05-P01', groupId: 'SV3-U05-G01', sourceFactId: 'SV3-FACT-U05-CLASSIFICATION', groupDescription: '고객 문의 분류 기준 적용', anchors: ['본인은 2025년 1월부터 3월까지 고객 문의 분류 기준을 정리하고 운영 부서와 적용했다.'], actor: 'SELF', state: 'COMPLETED', aspects: ['planning_execution'], dates: [{ start: '2025-01-01', end: '2025-03-31', precision: 'MONTH', sourceSurface: '2025년 1월부터 3월까지' }] },
      { id: 'SV3-U05-P01-E02', parentId: 'SV3-U05-P01', groupId: 'SV3-U05-G02', sourceFactId: 'SV3-FACT-U05-RETROSPECTIVE', groupDescription: '주간 회고와 우선순위 반영', anchors: ['주간 회고를 열어 누락 유형을 확인하고 다음 주 업무 우선순위에 반영했다.'], actor: 'SELF', state: 'COMPLETED', aspects: ['planning_feedback_loop'] },
      { id: 'SV3-U05-P02-E01', parentId: 'SV3-U05-P02', groupId: 'SV3-U05-G03', sourceFactId: 'SV3-FACT-U05-PLANNED-TRAINING', groupDescription: '미실행 파트너 교육 계획', anchors: ['2025년 3분기에는 파트너 교육 프로그램을 추진할 계획이며 아직 실행하지 않았다.'], actor: 'SELF', state: 'PLANNED', aspects: ['partner_training'], dates: [{ start: '2025-07-01', end: '2025-09-30', precision: 'QUARTER', sourceSurface: '2025년 3분기' }] },
    ],
    questions: [],
  },
  {
    userBundleId: 'SV3-U06', split: 'DEV', professionGroup: 'MARKETING_SALES', profession: 'growth marketer and sales operations specialist',
    languageProfile: 'KO_EN_MIXED', documentFamilyId: 'SV3-U06-DOC-FAMILY', templateFamilyId: 'SV3-TEMPLATE-GROWTH-06',
    generatorSeedId: 'SV3-SEED-GROWTH-7E3F',
    documents: [
      document('SV3-U06', 1, 1, {
        active: true, title: 'Synthetic Growth & Sales Career Description', documentType: 'CAREER_REVIEW', documentStructure: 'CAREER_DESCRIPTION',
        fileType: 'TXT', language: 'KO_EN_MIXED', fileName: 'sv3-u06-growth-career-v01.txt', supportScope: 'SUPPORTED_BY_CURRENT',
        content: [
          'Synthetic Growth & Sales Career Description',
          '',
          'Campaign execution',
          'I launched a Korean-English onboarding campaign across email and webinar channels.',
          'The campaign generated 1,300 qualified leads, not 1,300 paid customers.',
          '',
          'Sales operations',
          'I standardized CRM lead-stage definitions and trained the regional sales coordinators.',
          '',
          'Actor boundary',
          'The partner sales team closed 40 enterprise accounts; I only prepared the briefing materials.',
          '',
        ].join('\n'),
      }),
    ],
    parents: [
      { parentId: 'SV3-U06-P01', documentId: 'SV3-U06-D01', versionId: 'SV3-U06-D01-V01', label: 'Campaign execution', anchor: 'Campaign execution\nI launched a Korean-English onboarding campaign across email and webinar channels.\nThe campaign generated 1,300 qualified leads, not 1,300 paid customers.' },
      { parentId: 'SV3-U06-P02', documentId: 'SV3-U06-D01', versionId: 'SV3-U06-D01-V01', label: 'Sales operations', anchor: 'Sales operations\nI standardized CRM lead-stage definitions and trained the regional sales coordinators.' },
      { parentId: 'SV3-U06-P03', documentId: 'SV3-U06-D01', versionId: 'SV3-U06-D01-V01', label: 'Actor boundary', anchor: 'Actor boundary\nThe partner sales team closed 40 enterprise accounts; I only prepared the briefing materials.' },
    ],
    units: [
      { id: 'SV3-U06-P01-E01', parentId: 'SV3-U06-P01', groupId: 'SV3-U06-G01', sourceFactId: 'SV3-FACT-U06-CAMPAIGN', groupDescription: 'email/webinar 캠페인 실행', anchors: ['I launched a Korean-English onboarding campaign across email and webinar channels.'], actor: 'SELF', state: 'PRODUCTION', aspects: ['campaign_execution'], entities: [{ entityType: 'CHANNEL', canonicalValue: 'EMAIL_WEBINAR', surfaceForms: ['email', 'webinar'] }] },
      { id: 'SV3-U06-P01-E02', parentId: 'SV3-U06-P01', groupId: 'SV3-U06-G02', sourceFactId: 'SV3-FACT-U06-LEADS', groupDescription: 'qualified leads 1,300건', anchors: ['The campaign generated 1,300 qualified leads, not 1,300 paid customers.'], actor: 'SELF', state: 'COMPLETED', aspects: ['qualified_leads'], numerics: [{ normalizedValue: 1300, unit: 'LEAD', semanticType: 'QUALIFIED_LEAD_COUNT', sourceSurface: '1,300 qualified leads', qualifierTokens: ['qualified', 'leads'] }, { normalizedValue: 1300, unit: 'CUSTOMER', semanticType: 'PAID_CUSTOMER_COUNT_NEGATED', sourceSurface: '1,300 paid customers', qualifierTokens: ['paid', 'customers'] }] },
      { id: 'SV3-U06-P02-E01', parentId: 'SV3-U06-P02', groupId: 'SV3-U06-G03', sourceFactId: 'SV3-FACT-U06-CRM', groupDescription: 'CRM 단계 표준화 및 교육', anchors: ['I standardized CRM lead-stage definitions and trained the regional sales coordinators.'], actor: 'SELF', state: 'COMPLETED', aspects: ['sales_operations'], entities: [{ entityType: 'SYSTEM', canonicalValue: 'CRM', surfaceForms: ['CRM'] }] },
      { id: 'SV3-U06-P03-E01', parentId: 'SV3-U06-P03', groupId: 'SV3-U06-G04', sourceFactId: 'SV3-FACT-U06-OTHER-ACTOR-SALES', groupDescription: '파트너 영업팀의 계약 성과', anchors: ['The partner sales team closed 40 enterprise accounts; I only prepared the briefing materials.'], actor: 'OTHER', state: 'COMPLETED', aspects: ['enterprise_accounts'], numerics: [{ normalizedValue: 40, unit: 'ACCOUNT', semanticType: 'ENTERPRISE_ACCOUNT_COUNT', sourceSurface: '40 enterprise accounts', qualifierTokens: ['partner', 'sales', 'team'] }] },
    ],
    questions: [],
  },
  {
    userBundleId: 'SV3-U07', split: 'SEALED_FINAL_TEST', professionGroup: 'NON_DEVELOPMENT_GENERAL', profession: 'customer operations coordinator',
    languageProfile: 'KO', documentFamilyId: 'SV3-U07-DOC-FAMILY', templateFamilyId: 'SV3-TEMPLATE-OPERATIONS-07',
    generatorSeedId: 'SV3-SEED-OPERATIONS-9A4C',
    documents: [
      document('SV3-U07', 1, 1, {
        active: true, title: '합성 고객 운영 이력서', documentType: 'RESUME', documentStructure: 'SHORT_RESUME',
        fileType: 'TXT', language: 'KO', fileName: 'sv3-u07-operations-resume-v01.txt', supportScope: 'SUPPORTED_BY_CURRENT',
        content: [
          '합성 고객 운영 이력서',
          '',
          '고객 운영',
          '2022년 5월부터 2023년 11월까지 교대 근무 인수인계표를 관리했다.',
          '본인은 월 평균 문의 480건을 분류하고 긴급 건을 당일 담당자에게 전달했다.',
          '',
          '자격 경계',
          '지게차 운전 자격은 보유하지 않았다.',
          '',
        ].join('\n'),
      }),
      document('SV3-U07', 2, 1, {
        active: true, title: '합성 교육 및 수료 기록', documentType: 'CERTIFICATE', documentStructure: 'CERTIFICATION_TRAINING',
        fileType: 'TXT', language: 'KO', fileName: 'sv3-u07-operations-training-v01.txt', supportScope: 'SUPPORTED_BY_CURRENT',
        content: [
          '합성 교육 및 수료 기록',
          '',
          '수료 내역',
          '2023년 6월 응급 대응 기본 교육을 수료했다.',
          '현장 모의 훈련에서 신고, 대피 안내, 인원 확인 절차를 수행했다.',
          '',
        ].join('\n'),
      }),
    ],
    parents: [
      { parentId: 'SV3-U07-P01', documentId: 'SV3-U07-D01', versionId: 'SV3-U07-D01-V01', label: '고객 운영', anchor: '고객 운영\n2022년 5월부터 2023년 11월까지 교대 근무 인수인계표를 관리했다.\n본인은 월 평균 문의 480건을 분류하고 긴급 건을 당일 담당자에게 전달했다.' },
      { parentId: 'SV3-U07-P02', documentId: 'SV3-U07-D01', versionId: 'SV3-U07-D01-V01', label: '자격 경계', anchor: '자격 경계\n지게차 운전 자격은 보유하지 않았다.' },
      { parentId: 'SV3-U07-P03', documentId: 'SV3-U07-D02', versionId: 'SV3-U07-D02-V01', label: '수료 내역', anchor: '수료 내역\n2023년 6월 응급 대응 기본 교육을 수료했다.\n현장 모의 훈련에서 신고, 대피 안내, 인원 확인 절차를 수행했다.' },
    ],
    units: [
      { id: 'SV3-U07-P01-E01', parentId: 'SV3-U07-P01', groupId: 'SV3-U07-G01', sourceFactId: 'SV3-FACT-U07-SHIFT-HANDOFF', groupDescription: '교대 인수인계표 관리', anchors: ['2022년 5월부터 2023년 11월까지 교대 근무 인수인계표를 관리했다.'], actor: 'SELF', state: 'COMPLETED', aspects: ['shift_handoff'], dates: [{ start: '2022-05-01', end: '2023-11-30', precision: 'MONTH', sourceSurface: '2022년 5월부터 2023년 11월까지' }] },
      { id: 'SV3-U07-P01-E02', parentId: 'SV3-U07-P01', groupId: 'SV3-U07-G02', sourceFactId: 'SV3-FACT-U07-INQUIRIES', groupDescription: '월평균 문의 분류', anchors: ['본인은 월 평균 문의 480건을 분류하고 긴급 건을 당일 담당자에게 전달했다.'], actor: 'SELF', state: 'PRODUCTION', aspects: ['customer_operations'], numerics: [{ normalizedValue: 480, unit: 'INQUIRY_PER_MONTH', semanticType: 'CUSTOMER_INQUIRY_COUNT', sourceSurface: '480건', qualifierTokens: ['월', '평균', '문의'] }] },
      { id: 'SV3-U07-P02-E01', parentId: 'SV3-U07-P02', groupId: 'SV3-U07-G03', sourceFactId: 'SV3-FACT-U07-NO-FORKLIFT', groupDescription: '지게차 자격 미보유', anchors: ['지게차 운전 자격은 보유하지 않았다.'], actor: 'SELF', state: 'NOT_APPLICABLE', aspects: ['forklift_certificate'], entities: [{ entityType: 'CERTIFICATION', canonicalValue: 'FORKLIFT_LICENSE', surfaceForms: ['지게차 운전 자격'] }] },
      { id: 'SV3-U07-P03-E01', parentId: 'SV3-U07-P03', groupId: 'SV3-U07-G04', sourceFactId: 'SV3-FACT-U07-EMERGENCY-TRAINING', groupDescription: '응급 대응 교육 수료와 실습', anchors: ['2023년 6월 응급 대응 기본 교육을 수료했다.', '현장 모의 훈련에서 신고, 대피 안내, 인원 확인 절차를 수행했다.'], actor: 'SELF', state: 'COMPLETED', aspects: ['emergency_training'], entities: [{ entityType: 'COURSE', canonicalValue: 'EMERGENCY_RESPONSE_BASIC', surfaceForms: ['응급 대응 기본 교육'] }], dates: [{ start: '2023-06-01', end: '2023-06-30', precision: 'MONTH', sourceSurface: '2023년 6월' }] },
    ],
    questions: [],
  },
]

const ref = (evidenceUnitId, supportRelation, groupId) => ({ evidenceUnitId, supportRelation, groupId })
const entity = (entityType, canonicalValue, surfaceForms) => ({ entityType, canonicalValue, surfaceForms })
const numeric = (operator, value, unit, semanticType, qualifierTokens, upperValue) => ({ operator, value, ...(upperValue === undefined ? {} : { upperValue }), unit, semanticType, qualifierTokens })
const date = (operator, start, end, precision) => ({ operator, start, end, precision })

bundles[0].questions = [
  query('SV3-U01', 1, 'PAY GW 운영 경험이 있나요?', ['literal_identifier', 'typo_format_variation', 'korean_english_mixed'], 'KO_EN_MIXED', 'SUPPORTED', [
    aspect('paygw_operation', 'SUPPORTED', [ref('SV3-U01-P01-E01', 'DIRECT_SUPPORT', 'SV3-U01-G01')], { constraints: { entities: [entity('PRODUCT_IDENTIFIER', 'PAY-GW', ['PAY-GW'])], actors: ['SELF'], completionStates: ['PRODUCTION'] } }),
  ], { safetyExclusions: [{ evidenceUnitId: 'SV3-U02-P01-E01', reason: 'OWNER_LEAKAGE' }] }),
  query('SV3-U01', 2, '하루 1,000건 이상 요청을 처리한 경험이 있나요?', ['numeric_quantity', 'numeric_range_comparison', 'korean'], 'KO', 'SUPPORTED', [
    aspect('request_volume', 'SUPPORTED', [ref('SV3-U01-P01-E02', 'DIRECT_SUPPORT', 'SV3-U01-G02')], { constraints: { numerics: [numeric('GTE', 1000, 'REQUEST_PER_DAY', 'REQUEST_COUNT', ['요청'])], actors: ['SELF'] } }),
  ]),
  query('SV3-U01', 3, 'Kafka를 운영 환경에 도입했나요?', ['hard_negative', 'negation', 'completion_state', 'completed_production', 'korean_english_mixed'], 'KO_EN_MIXED', 'NOT_SUPPORTED', [
    aspect('kafka_production', 'NOT_SUPPORTED', [ref('SV3-U01-P01-E03', 'CONTRADICTS', 'SV3-U01-G03')], { constraints: { entities: [entity('TECHNOLOGY', 'KAFKA', ['Kafka'])], actors: ['SELF'], completionStates: ['PRODUCTION'] } }),
  ]),
  query('SV3-U01', 4, '장애가 반복되지 않도록 어떤 체계를 만들었나요?', ['semantic_paraphrase', 'abstract_competency', 'multi_evidence', 'korean'], 'KO', 'SUPPORTED', [
    aspect('incident_prevention', 'SUPPORTED', [ref('SV3-U01-P02-E01', 'DIRECT_SUPPORT', 'SV3-U01-G04')], { requiredEvidenceGroupIds: ['SV3-U01-G04'], minEvidenceGroups: 1, constraints: { actors: ['SELF'], completionStates: ['COMPLETED'] } }),
  ]),
  query('SV3-U01', 5, 'DormantQueue 운영 경험이 있나요?', ['no_answer', 'hard_negative', 'completion_state', 'korean_english_mixed'], 'KO_EN_MIXED', 'NOT_SUPPORTED', [
    aspect('dormantqueue_production', 'NOT_SUPPORTED', [ref('SV3-U01-P03-E01', 'RELATED', 'SV3-U01-G05')], { constraints: { entities: [entity('TECHNOLOGY', 'DORMANTQUEUE', ['DormantQueue'])], actors: ['SELF'], completionStates: ['PRODUCTION'] } }),
  ], { safetyExclusions: [{ evidenceUnitId: 'SV3-U01-P03-E01', reason: 'INACTIVE_VERSION_LEAKAGE' }, { evidenceUnitId: 'SV3-U01-P03-E01', reason: 'WRONG_VERSION_LEAKAGE' }] }),
]

bundles[1].questions = [
  query('SV3-U02', 1, 'Did the candidate ship WCAG 2.1 AA improvements?', ['literal_identifier', 'completed_production', 'english'], 'EN', 'SUPPORTED', [
    aspect('accessibility_release', 'SUPPORTED', [ref('SV3-U02-P01-E01', 'DIRECT_SUPPORT', 'SV3-U02-G01')], { constraints: { entities: [entity('STANDARD', 'WCAG_2_1_AA', ['WCAG 2.1 AA'])], actors: ['SELF'], completionStates: ['PRODUCTION'] } }),
  ]),
  query('SV3-U02', 2, 'What evidence shows the account flow became more accessible?', ['semantic_paraphrase', 'abstract_competency', 'english'], 'EN', 'SUPPORTED', [
    aspect('accessibility_release', 'SUPPORTED', [ref('SV3-U02-P01-E01', 'DIRECT_SUPPORT', 'SV3-U02-G01')], { constraints: { actors: ['SELF'] } }),
  ]),
  query('SV3-U02', 3, 'Was voice navigation released to production?', ['hard_negative', 'attempted_prototype', 'completion_state', 'english'], 'EN', 'NOT_SUPPORTED', [
    aspect('voice_navigation_production', 'NOT_SUPPORTED', [ref('SV3-U02-P02-E01', 'RELATED', 'SV3-U02-G02'), ref('SV3-U02-P02-E02', 'CONTRADICTS', 'SV3-U02-G03')], { constraints: { entities: [entity('FEATURE', 'VOICE_NAVIGATION', ['voice-navigation'])], actors: ['SELF'], completionStates: ['PRODUCTION'] } }),
  ]),
  query('SV3-U02', 4, 'Show accessibilty work for screen reader users.', ['typo_format_variation', 'semantic_paraphrase', 'english'], 'EN', 'SUPPORTED', [
    aspect('accessibility_release', 'SUPPORTED', [ref('SV3-U02-P01-E01', 'DIRECT_SUPPORT', 'SV3-U02-G01')]),
  ]),
]

bundles[2].questions = [
  query('SV3-U03', 1, '2023년 3월부터 2024년 2월까지 pipeline을 운영했나요?', ['date_range', 'korean_english_mixed'], 'KO_EN_MIXED', 'SUPPORTED', [
    aspect('pipeline_operation', 'SUPPORTED', [ref('SV3-U03-P01-E01', 'DIRECT_SUPPORT', 'SV3-U03-G01')], { constraints: { dates: [date('CONTAINS', '2023-03-01', '2024-02-29', 'MONTH')], actors: ['SELF'], completionStates: ['PRODUCTION'] } }),
  ]),
  query('SV3-U03', 2, '월 200만 records 이상을 검증한 경험이 있나요?', ['numeric_quantity', 'numeric_range_comparison', 'korean_english_mixed'], 'KO_EN_MIXED', 'SUPPORTED', [
    aspect('pipeline_volume', 'SUPPORTED', [ref('SV3-U03-P01-E01', 'DIRECT_SUPPORT', 'SV3-U03-G01')], { constraints: { numerics: [numeric('GTE', 2000000, 'RECORD_PER_MONTH', 'DATA_RECORD_COUNT', ['records'])], actors: ['SELF'] } }),
  ]),
  query('SV3-U03', 3, '운영 pipeline과 Kubernetes 교육을 모두 갖췄나요?', ['multi_evidence', 'multi_aspect', 'job_requirement', 'korean_english_mixed'], 'KO_EN_MIXED', 'SUPPORTED', [
    aspect('pipeline_operation', 'SUPPORTED', [ref('SV3-U03-P01-E01', 'DIRECT_SUPPORT', 'SV3-U03-G01')]),
    aspect('kubernetes_training', 'SUPPORTED', [ref('SV3-U03-P03-E01', 'DIRECT_SUPPORT', 'SV3-U03-G04')]),
  ]),
  query('SV3-U03', 4, '추천 모델을 production에 배포했나요?', ['hard_negative', 'attempted_prototype', 'completion_state', 'korean_english_mixed'], 'KO_EN_MIXED', 'NOT_SUPPORTED', [
    aspect('model_production', 'NOT_SUPPORTED', [ref('SV3-U03-P02-E01', 'CONTRADICTS', 'SV3-U03-G03')], { constraints: { entities: [entity('ARTIFACT', 'RECOMMENDATION_MODEL', ['추천 모델'])], actors: ['SELF'], completionStates: ['PRODUCTION'] } }),
  ]),
]

bundles[3].questions = [
  query('SV3-U04', 1, '사용자 문제를 발견하고 디자인 결정으로 연결한 근거는?', ['semantic_paraphrase', 'abstract_competency', 'multi_evidence', 'korean'], 'KO', 'SUPPORTED', [
    aspect('research_to_design', 'SUPPORTED', [ref('SV3-U04-P01-E01', 'DIRECT_SUPPORT', 'SV3-U04-G01'), ref('SV3-U04-P01-E02', 'DIRECT_SUPPORT', 'SV3-U04-G02')], { requiredEvidenceGroupIds: ['SV3-U04-G01', 'SV3-U04-G02'], minEvidenceGroups: 2, constraints: { actors: ['SELF'] } }),
  ]),
  query('SV3-U04', 2, '본인이 A/B 테스트를 수행했나요?', ['hard_negative', 'other_actor', 'korean_english_mixed'], 'KO_EN_MIXED', 'NOT_SUPPORTED', [
    aspect('ab_test_self', 'NOT_SUPPORTED', [ref('SV3-U04-P03-E01', 'INSUFFICIENT', 'SV3-U04-G04')], { constraints: { actors: ['SELF'], completionStates: ['COMPLETED'] } }),
  ]),
  query('SV3-U04', 3, '본인이 사용자 인터뷰와 A/B 테스트를 모두 했나요?', ['multi_aspect', 'other_actor', 'korean_english_mixed'], 'KO_EN_MIXED', 'PARTIALLY_SUPPORTED', [
    aspect('user_research', 'SUPPORTED', [ref('SV3-U04-P01-E01', 'DIRECT_SUPPORT', 'SV3-U04-G01')], { constraints: { actors: ['SELF'] } }),
    aspect('ab_test_self', 'NOT_SUPPORTED', [ref('SV3-U04-P03-E01', 'INSUFFICIENT', 'SV3-U04-G04')], { constraints: { actors: ['SELF'] } }),
  ]),
  query('SV3-U04', 4, '재설계가 실제 사용성 결과로 이어졌나요?', ['semantic_paraphrase', 'numeric_quantity', 'korean'], 'KO', 'SUPPORTED', [
    aspect('usability_outcome', 'SUPPORTED', [ref('SV3-U04-P02-E01', 'DIRECT_SUPPORT', 'SV3-U04-G03')], { constraints: { numerics: [numeric('GTE', 80, 'PERCENT', 'TASK_COMPLETION_RATE_AFTER', ['과업', '완료율'])] } }),
  ]),
]

bundles[4].questions = [
  query('SV3-U05', 1, '2025년 1분기에 실제로 적용한 기획 업무가 있나요?', ['date_range', 'completed_production', 'korean'], 'KO', 'SUPPORTED', [
    aspect('planning_execution', 'SUPPORTED', [ref('SV3-U05-P01-E01', 'DIRECT_SUPPORT', 'SV3-U05-G01')], { constraints: { dates: [date('CONTAINS', '2025-01-01', '2025-03-31', 'QUARTER')], actors: ['SELF'], completionStates: ['COMPLETED'] } }),
  ]),
  query('SV3-U05', 2, '파트너 교육 프로그램을 이미 운영했나요?', ['hard_negative', 'planned', 'completion_state', 'korean'], 'KO', 'NOT_SUPPORTED', [
    aspect('partner_training_completed', 'NOT_SUPPORTED', [ref('SV3-U05-P02-E01', 'CONTRADICTS', 'SV3-U05-G03')], { constraints: { actors: ['SELF'], completionStates: ['COMPLETED', 'PRODUCTION'] } }),
  ]),
  query('SV3-U05', 3, '계획을 실행하고 피드백으로 우선순위를 조정한 경험은?', ['semantic_paraphrase', 'abstract_competency', 'multi_evidence', 'korean'], 'KO', 'SUPPORTED', [
    aspect('planning_cycle', 'SUPPORTED', [ref('SV3-U05-P01-E01', 'DIRECT_SUPPORT', 'SV3-U05-G01'), ref('SV3-U05-P01-E02', 'DIRECT_SUPPORT', 'SV3-U05-G02')], { requiredEvidenceGroupIds: ['SV3-U05-G01', 'SV3-U05-G02'], minEvidenceGroups: 2, constraints: { actors: ['SELF'] } }),
  ]),
  query('SV3-U05', 4, '2024년에 해외 법인 설립을 완료했나요?', ['no_answer', 'hard_negative', 'date_range', 'korean'], 'KO', 'NOT_SUPPORTED', [
    aspect('foreign_entity_2024', 'NOT_SUPPORTED', []),
  ]),
]

bundles[5].questions = [
  query('SV3-U06', 1, 'Did the campaign generate at least 1,000 qualified leads?', ['numeric_quantity', 'numeric_range_comparison', 'english'], 'EN', 'SUPPORTED', [
    aspect('qualified_leads', 'SUPPORTED', [ref('SV3-U06-P01-E02', 'DIRECT_SUPPORT', 'SV3-U06-G02')], { constraints: { numerics: [numeric('GTE', 1000, 'LEAD', 'QUALIFIED_LEAD_COUNT', ['qualified', 'leads'])], actors: ['SELF'] } }),
  ]),
  query('SV3-U06', 2, 'Did the campaign acquire 1,000 paid customers?', ['hard_negative', 'negation', 'numeric_quantity', 'english'], 'EN', 'NOT_SUPPORTED', [
    aspect('paid_customers', 'NOT_SUPPORTED', [ref('SV3-U06-P01-E02', 'CONTRADICTS', 'SV3-U06-G02')], { constraints: { numerics: [numeric('GTE', 1000, 'CUSTOMER', 'PAID_CUSTOMER_COUNT', ['paid', 'customers'])], actors: ['SELF'] } }),
  ]),
  query('SV3-U06', 3, 'Did this person close 40 enterprise accounts?', ['hard_negative', 'other_actor', 'numeric_quantity', 'english'], 'EN', 'NOT_SUPPORTED', [
    aspect('enterprise_accounts_self', 'NOT_SUPPORTED', [ref('SV3-U06-P03-E01', 'INSUFFICIENT', 'SV3-U06-G04')], { constraints: { numerics: [numeric('EQ', 40, 'ACCOUNT', 'ENTERPRISE_ACCOUNT_COUNT', ['enterprise', 'accounts'])], actors: ['SELF'] } }),
  ]),
  query('SV3-U06', 4, 'email/webinar campaign과 CRM 운영 경험을 모두 보여 주세요.', ['multi_evidence', 'multi_aspect', 'job_requirement', 'korean_english_mixed'], 'KO_EN_MIXED', 'SUPPORTED', [
    aspect('campaign_execution', 'SUPPORTED', [ref('SV3-U06-P01-E01', 'DIRECT_SUPPORT', 'SV3-U06-G01')]),
    aspect('sales_operations', 'SUPPORTED', [ref('SV3-U06-P02-E01', 'DIRECT_SUPPORT', 'SV3-U06-G03')]),
  ]),
]

bundles[6].questions = [
  query('SV3-U07', 1, '응급 대응 기본 교육을 수료했나요?', ['literal_identifier', 'completed_production', 'korean'], 'KO', 'SUPPORTED', [
    aspect('emergency_training', 'SUPPORTED', [ref('SV3-U07-P03-E01', 'DIRECT_SUPPORT', 'SV3-U07-G04')], { constraints: { entities: [entity('COURSE', 'EMERGENCY_RESPONSE_BASIC', ['응급 대응 기본 교육'])], actors: ['SELF'], completionStates: ['COMPLETED'] } }),
  ]),
  query('SV3-U07', 2, '2022년 5월부터 2023년 11월까지 교대 운영을 맡았나요?', ['date_range', 'korean'], 'KO', 'SUPPORTED', [
    aspect('shift_handoff', 'SUPPORTED', [ref('SV3-U07-P01-E01', 'DIRECT_SUPPORT', 'SV3-U07-G01')], { constraints: { dates: [date('CONTAINS', '2022-05-01', '2023-11-30', 'MONTH')], actors: ['SELF'] } }),
  ]),
  query('SV3-U07', 3, '지게차 운전 자격증을 보유했나요?', ['hard_negative', 'negation', 'korean'], 'KO', 'NOT_SUPPORTED', [
    aspect('forklift_certificate', 'NOT_SUPPORTED', [ref('SV3-U07-P02-E01', 'CONTRADICTS', 'SV3-U07-G03')], { constraints: { entities: [entity('CERTIFICATION', 'FORKLIFT_LICENSE', ['지게차 운전 자격'])], actors: ['SELF'] } }),
  ]),
  query('SV3-U07', 4, '고객 문의 운영과 현장 응급 대응을 모두 수행할 수 있는 근거는?', ['multi_evidence', 'multi_aspect', 'job_requirement', 'korean'], 'KO', 'SUPPORTED', [
    aspect('customer_operations', 'SUPPORTED', [ref('SV3-U07-P01-E02', 'DIRECT_SUPPORT', 'SV3-U07-G02')]),
    aspect('emergency_training', 'SUPPORTED', [ref('SV3-U07-P03-E01', 'DIRECT_SUPPORT', 'SV3-U07-G04')]),
  ], { safetyExclusions: [{ evidenceUnitId: 'SV3-U06-P02-E01', reason: 'UNAUTHORIZED_SOURCE_EXPOSURE' }] }),
]

function sourceDocument(bundle, documentId, versionId) {
  const found = bundle.documents.find((entry) => entry.documentId === documentId && entry.versionId === versionId)
  if (!found) throw new Error(`Missing source document ${documentId}/${versionId}`)
  return found
}

function locate(content, anchor, label) {
  const first = content.indexOf(anchor)
  const last = content.lastIndexOf(anchor)
  if (first < 0) throw new Error(`${label}: anchor not found: ${anchor}`)
  if (first !== last) throw new Error(`${label}: anchor is not unique: ${anchor}`)
  const before = content.slice(0, first)
  const inclusive = content.slice(0, first + anchor.length)
  return {
    charStart: Array.from(before).length,
    charEnd: Array.from(inclusive).length,
    lineStart: before.split('\n').length,
    lineEnd: inclusive.split('\n').length,
  }
}

function materializeSpan(bundle, parentId, document, spanId, anchor) {
  const position = locate(document.content, anchor, spanId)
  return {
    spanId,
    parentId,
    documentId: document.documentId,
    versionId: document.versionId,
    sourcePath: `documents/${document.fileName}`,
    sourceType: 'TXT_TEXT',
    page: null,
    ...position,
    text: anchor,
    textSha256: sha256(Buffer.from(anchor, 'utf8')),
  }
}

function writeFile(path, value) {
  mkdirSync(dirname(path), { recursive: true })
  writeFileSync(path, value)
}

function countBy(values) {
  return Object.fromEntries([...new Set(values)].sort().map((value) => [value, values.filter((candidate) => candidate === value).length]))
}

function manifestFile(path, base) {
  const bytes = readFileSync(path)
  return { path: posix(relative(base, path)), bytes: bytes.length, sha256: sha256(bytes) }
}

function combinedHash(files) {
  const record = [...files]
    .sort((left, right) => left.path.localeCompare(right.path))
    .map((entry) => `${entry.path}\0${entry.sha256}\n`)
    .join('')
  return sha256(Buffer.from(record, 'utf8'))
}

function buildSplit(bundleSet, split) {
  const splitDirectory = resolve(DATASET_ROOT, SPLIT_DIR.get(split))
  const corpusBundles = []
  const parents = []
  const evidenceUnits = []
  const groupMap = new Map()
  const questions = []

  for (const bundle of bundleSet) {
    const documents = []
    for (const source of bundle.documents) {
      const contentPath = resolve(splitDirectory, 'documents', source.fileName)
      writeFile(contentPath, source.content)
      documents.push({
        documentId: source.documentId,
        logicalDocumentId: source.logicalDocumentId,
        versionLineageId: source.versionLineageId,
        versionId: source.versionId,
        versionNumber: source.versionNumber,
        active: source.active,
        title: source.title,
        documentType: source.documentType,
        documentStructure: source.documentStructure,
        fileType: source.fileType,
        language: source.language,
        contentPath: `documents/${source.fileName}`,
        contentSha256: sha256(Buffer.from(source.content, 'utf8')),
        supportScope: source.supportScope,
        visibility: 'OWNER_ONLY',
        provenance: {
          classification: 'SYNTHETIC',
          license: 'Apache-2.0',
          generatorName: GENERATOR_NAME,
          generatorRevision: GENERATOR_REVISION,
          generatorSeedId: bundle.generatorSeedId,
        },
      })
    }
    corpusBundles.push({
      userBundleId: bundle.userBundleId,
      split: bundle.split,
      professionGroup: bundle.professionGroup,
      profession: bundle.profession,
      languageProfile: bundle.languageProfile,
      documentFamilyId: bundle.documentFamilyId,
      templateFamilyId: bundle.templateFamilyId,
      documents,
    })

    for (const rawParent of bundle.parents) {
      const document = sourceDocument(bundle, rawParent.documentId, rawParent.versionId)
      parents.push({
        parentId: rawParent.parentId,
        userBundleId: bundle.userBundleId,
        documentId: rawParent.documentId,
        versionId: rawParent.versionId,
        label: rawParent.label,
        sourceSpan: materializeSpan(bundle, rawParent.parentId, document, `${rawParent.parentId}-PS01`, rawParent.anchor),
      })
    }

    for (const rawUnit of bundle.units) {
      const parent = bundle.parents.find((entry) => entry.parentId === rawUnit.parentId)
      if (!parent) throw new Error(`Missing parent for ${rawUnit.id}`)
      const document = sourceDocument(bundle, parent.documentId, parent.versionId)
      const spans = rawUnit.anchors.map((anchor, index) => materializeSpan(
        bundle,
        rawUnit.parentId,
        document,
        `${rawUnit.id}-S${String(index + 1).padStart(2, '0')}`,
        anchor,
      ))
      const unit = {
        evidenceUnitId: rawUnit.id,
        userBundleId: bundle.userBundleId,
        parentId: rawUnit.parentId,
        groupId: rawUnit.groupId,
        documentId: parent.documentId,
        versionId: parent.versionId,
        sourceFactId: rawUnit.sourceFactId,
        sourceSpans: spans,
        primarySpanId: spans[0].spanId,
        contextSpanIds: spans.slice(1).map((entry) => entry.spanId),
        actor: rawUnit.actor,
        completionState: rawUnit.state,
        aspects: rawUnit.aspects,
        entities: rawUnit.entities ?? [],
        numerics: rawUnit.numerics ?? [],
        dates: rawUnit.dates ?? [],
      }
      evidenceUnits.push(unit)
      const existing = groupMap.get(rawUnit.groupId)
      if (existing) {
        existing.evidenceUnitIds.push(rawUnit.id)
      } else {
        groupMap.set(rawUnit.groupId, {
          groupId: rawUnit.groupId,
          userBundleId: bundle.userBundleId,
          sourceFactId: rawUnit.sourceFactId,
          description: rawUnit.groupDescription,
          evidenceUnitIds: [rawUnit.id],
        })
      }
    }
    questions.push(...bundle.questions)
  }

  const corpus = { artifactType: 'CORPUS', schemaVersion: SCHEMA_VERSION, datasetVersion: DATASET_VERSION, split, userBundles: corpusBundles }
  const gold = { artifactType: 'GOLD_EVIDENCE', schemaVersion: SCHEMA_VERSION, datasetVersion: DATASET_VERSION, split, parents, evidenceGroups: [...groupMap.values()], evidenceUnits }
  const questionArtifact = { artifactType: 'QUESTIONS', schemaVersion: SCHEMA_VERSION, datasetVersion: DATASET_VERSION, split, queries: questions }
  writeFile(resolve(splitDirectory, 'corpus.json'), json(corpus))
  writeFile(resolve(splitDirectory, 'gold-evidence.json'), json(gold))
  writeFile(resolve(splitDirectory, 'questions.json'), json(questionArtifact))
  return { corpus, gold, questions: questionArtifact }
}

function distributions(allBundles, allQuestions, allDocuments) {
  return {
    split: countBy(allBundles.map((entry) => entry.split)),
    profession: countBy(allBundles.map((entry) => entry.professionGroup)),
    documentLanguage: countBy(allDocuments.map((entry) => entry.language)),
    documentFileType: countBy(allDocuments.map((entry) => entry.fileType)),
    documentStructure: countBy(allDocuments.map((entry) => entry.documentStructure)),
    queryLanguage: countBy(allQuestions.map((entry) => entry.language)),
    answerability: countBy(allQuestions.map((entry) => entry.answerability)),
    category: countBy(allQuestions.flatMap((entry) => entry.categories)),
  }
}

function artifactCounts(allBundles, allQuestions, allDocuments, allParents, allGroups, allUnits) {
  return {
    userBundles: allBundles.length,
    logicalDocuments: new Set(allDocuments.map((entry) => entry.logicalDocumentId)).size,
    documentVersions: allDocuments.length,
    activeDocumentVersions: allDocuments.filter((entry) => entry.active).length,
    inactiveDocumentVersions: allDocuments.filter((entry) => !entry.active).length,
    queries: allQuestions.length,
    evidenceParents: allParents.length,
    evidenceGroups: allGroups.length,
    evidenceUnits: allUnits.length,
  }
}

function writeManifest(path, manifest) {
  writeFile(path, json(manifest))
}

function materialize(sealedAt, supersededVersion) {
  const sealedManifest = resolve(DATASET_ROOT, 'sealed-final/manifest.json')
  if (existsSync(sealedManifest)) {
    const existing = JSON.parse(readFileSync(sealedManifest, 'utf8'))
    const approvedPrecommitSupersession = existing.datasetVersion === supersededVersion
      && existing.datasetVersion !== DATASET_VERSION
      && existing.opened === false
      && existing.searchExecuted === false
    if (!approvedPrecommitSupersession) {
      throw new Error('Refusing to overwrite sealed-final/manifest.json; create a new dataset version')
    }
  }

  const splitArtifacts = new Map()
  for (const split of SPLIT_DIR.keys()) {
    splitArtifacts.set(split, buildSplit(bundles.filter((entry) => entry.split === split), split))
  }

  const lineage = {
    artifactType: 'LINEAGE',
    schemaVersion: SCHEMA_VERSION,
    datasetVersion: DATASET_VERSION,
    bundles: bundles.map((bundle) => ({
      userBundleId: bundle.userBundleId,
      split: bundle.split,
      documentFamilyId: bundle.documentFamilyId,
      templateFamilyId: bundle.templateFamilyId,
      generatorName: GENERATOR_NAME,
      generatorRevision: GENERATOR_REVISION,
      generatorSeedId: bundle.generatorSeedId,
      logicalDocumentIds: [...new Set(bundle.documents.map((entry) => entry.logicalDocumentId))],
      versionLineageIds: [...new Set(bundle.documents.map((entry) => entry.versionLineageId))],
      sourceFactIds: bundle.units.map((entry) => entry.sourceFactId),
      sourceFactSignatures: bundle.units.map((entry) => sha256(Buffer.from(normalizedQuery(entry.anchors.join(' ')), 'utf8'))),
      questionGroupIds: bundle.questions.map((entry) => entry.questionGroupId),
      normalizedQueries: bundle.questions.map((entry) => entry.normalizedQuery),
    })),
  }
  writeFile(resolve(DATASET_ROOT, 'lineage.json'), json(lineage))

  for (const [split, directory] of SPLIT_DIR) {
    const splitRoot = resolve(DATASET_ROOT, directory)
    const schemaPaths = [
      resolve(DATASET_ROOT, 'schema/search-v3-benchmark.schema.json'),
      resolve(DATASET_ROOT, 'schema/search-v3-prediction.schema.json'),
      resolve(DATASET_ROOT, 'lineage.json'),
    ]
    const splitFiles = [
      ...schemaPaths,
      resolve(splitRoot, 'corpus.json'),
      resolve(splitRoot, 'gold-evidence.json'),
      resolve(splitRoot, 'questions.json'),
      ...splitArtifacts.get(split).corpus.userBundles.flatMap((bundle) => bundle.documents.map((documentEntry) => resolve(splitRoot, documentEntry.contentPath))),
    ].map((path) => manifestFile(path, DATASET_ROOT))
    const artifacts = splitArtifacts.get(split)
    const splitBundles = artifacts.corpus.userBundles
    const splitDocuments = splitBundles.flatMap((entry) => entry.documents)
    const manifest = {
      artifactType: 'MANIFEST', schemaVersion: SCHEMA_VERSION, datasetVersion: DATASET_VERSION, split,
      status: split === 'SEALED_FINAL_TEST' ? 'SEALED' : 'MATERIALIZED',
      mutable: split !== 'SEALED_FINAL_TEST', opened: false, searchExecuted: false,
      sealedAt: split === 'SEALED_FINAL_TEST' ? sealedAt : null,
      generationSourceRevision: GENERATION_SOURCE_REVISION,
      counts: artifactCounts(splitBundles, artifacts.questions.queries, splitDocuments, artifacts.gold.parents, artifacts.gold.evidenceGroups, artifacts.gold.evidenceUnits),
      distributions: distributions(splitBundles, artifacts.questions.queries, splitDocuments),
      files: splitFiles.sort((left, right) => left.path.localeCompare(right.path)),
      combinedSha256: combinedHash(splitFiles),
    }
    writeManifest(resolve(splitRoot, 'manifest.json'), manifest)
  }

  const allCorpus = [...splitArtifacts.values()].flatMap((entry) => entry.corpus.userBundles)
  const allQuestions = [...splitArtifacts.values()].flatMap((entry) => entry.questions.queries)
  const allDocuments = allCorpus.flatMap((entry) => entry.documents)
  const allParents = [...splitArtifacts.values()].flatMap((entry) => entry.gold.parents)
  const allGroups = [...splitArtifacts.values()].flatMap((entry) => entry.gold.evidenceGroups)
  const allUnits = [...splitArtifacts.values()].flatMap((entry) => entry.gold.evidenceUnits)
  const allFiles = [
    resolve(DATASET_ROOT, 'README.md'),
    resolve(DATASET_ROOT, 'schema/search-v3-benchmark.schema.json'),
    resolve(DATASET_ROOT, 'schema/search-v3-prediction.schema.json'),
    resolve(DATASET_ROOT, 'lineage.json'),
    ...[...SPLIT_DIR.values()].flatMap((directory) => {
      const artifact = splitArtifacts.get([...SPLIT_DIR].find(([, value]) => value === directory)[0])
      const root = resolve(DATASET_ROOT, directory)
      return [
        resolve(root, 'corpus.json'), resolve(root, 'gold-evidence.json'), resolve(root, 'questions.json'), resolve(root, 'manifest.json'),
        ...artifact.corpus.userBundles.flatMap((bundle) => bundle.documents.map((entry) => resolve(root, entry.contentPath))),
      ]
    }),
  ].map((path) => manifestFile(path, DATASET_ROOT))
  const overallManifest = {
    artifactType: 'MANIFEST', schemaVersion: SCHEMA_VERSION, datasetVersion: DATASET_VERSION, split: 'ALL',
    status: 'FRESH_BENCHMARK_SEED_FROZEN', mutable: false, opened: false, searchExecuted: false,
    sealedAt, generationSourceRevision: GENERATION_SOURCE_REVISION,
    counts: artifactCounts(allCorpus, allQuestions, allDocuments, allParents, allGroups, allUnits),
    distributions: distributions(allCorpus, allQuestions, allDocuments),
    files: allFiles.sort((left, right) => left.path.localeCompare(right.path)),
    combinedSha256: combinedHash(allFiles),
  }
  writeManifest(resolve(DATASET_ROOT, 'manifest.json'), overallManifest)
  return overallManifest
}

const sealedAtArg = process.argv.find((value) => value.startsWith('--sealed-at='))
const supersedeArg = process.argv.find((value) => value.startsWith('--supersede-unopened-precommit-seed='))
if (!process.argv.includes('--initialize-sealed-final') || !sealedAtArg) {
  throw new Error('Usage: node materialize-search-v3-seed.mjs --initialize-sealed-final --sealed-at=<ISO-8601> [--supersede-unopened-precommit-seed=<old-version>]')
}
const sealedAt = sealedAtArg.slice('--sealed-at='.length)
if (!Number.isFinite(Date.parse(sealedAt))) throw new Error(`Invalid --sealed-at value: ${sealedAt}`)
const supersededVersion = supersedeArg?.slice('--supersede-unopened-precommit-seed='.length)
const result = materialize(sealedAt, supersededVersion)
console.log(JSON.stringify({
  status: result.status,
  counts: result.counts,
  combinedSha256: result.combinedSha256,
  sealedAt: result.sealedAt,
}, null, 2))
