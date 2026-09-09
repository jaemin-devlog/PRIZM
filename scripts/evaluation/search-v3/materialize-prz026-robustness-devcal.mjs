import { createHash } from 'node:crypto'
import { mkdir, readFile, readdir, writeFile } from 'node:fs/promises'
import path from 'node:path'

const DATASET_VERSION = 'search-v3-fresh-devcal-robustness-1.0.0'
const PREVIOUS_VERSION = 'search-v3-fresh-devcal-1.1.0'
const SCHEMA_VERSION = '1.0.0'
const FROZEN_AT = '2026-08-30T22:26:53.1683097+09:00'
const BASE_REVISION = '01d9ae2f90eff691d96041579e42a02aa04a3486'
const OUTPUT_ROOT = path.resolve(
  'src/searchEvaluation/resources/search-v3-evaluation/devcal-robustness-1.0.0',
)

function q(text, language, categories) {
  return { text, language, categories }
}

function s(id, title, before, fact, after, evidenceQuery, metadata = {}) {
  return {
    id,
    title,
    blocks: [before, fact, after],
    fact: {
      anchor: fact,
      actor: metadata.actor ?? 'SELF',
      completionState: metadata.completionState ?? 'COMPLETED',
      aspects: metadata.aspects ?? [id.toLowerCase()],
      numerics: metadata.numerics ?? [],
    },
    evidenceQuery,
  }
}

const bundles = [
  {
    userBundleId: 'SV3-RB-U201',
    split: 'DEV',
    professionGroup: 'FRONTEND_MOBILE',
    profession: 'mobile product engineer',
    language: 'KO',
    templateFamilyId: 'SV3-RB-TEMPLATE-MOBILE-NARRATIVE-201',
    generatorSeedId: 'SV3-RB-SEED-MOBILE-201-7F31',
    document: {
      title: '합성 모바일 현장 서비스 포트폴리오',
      fileName: 'sv3-rb-u201-mobile-field-portfolio-v01.txt',
      documentType: 'PORTFOLIO',
      documentStructure: 'LONG_PORTFOLIO',
    },
    sections: [
      s('P01', '현장 기록 동기화',
        '산간 지역 점검자는 연결이 끊긴 상태에서 여러 설비를 순회했다. 기존 화면은 전송 순서와 서버 도착 순서가 다르면 같은 기록을 두 번 보이거나 최근 메모를 덮어썼다. 연결 단절, 앱 강제 종료, 장시간 대기와 재로그인을 포함한 순회 흐름으로 문제를 재현했다.',
        '오프라인 상태에서 작성한 점검 기록 420건을 36시간 동안 재연결 시험했고, 충돌 해결 규칙을 적용한 뒤 중복 저장 비율을 0.8% 아래로 낮춘 버전을 현장 앱에 배포했다.',
        '배포 뒤에는 동기화 상태를 대기, 전송 중, 확인 완료로 나눠 표시했다. 운영 담당자와 주간 로그를 검토하면서 오류를 기록 생성, 전송, 서버 반영 단계로 분리했고, 네트워크 조건이 다른 세 현장에서도 같은 절차를 사용했다.',
        q('연결이 불안정한 현장 앱에서 기록 중복을 줄이고 실제 배포한 근거가 있나요?', 'KO',
          ['semantic_paraphrase', 'completed_production', 'korean']),
        { completionState: 'PRODUCTION', aspects: ['offline_sync_reliability'] }),
      s('P02', '접근 가능한 검사 흐름',
        '접근성 검토는 색상 대비만 확인하는 방식에서 벗어나 실제 과업 순서로 진행했다. 라벨이 비어 있는 입력, 전환 뒤 사라지는 초점, 아이콘만으로 표현된 위험 상태를 재현 단계와 함께 정리했다.',
        '스크린 리더 사용자가 설비 선택부터 점검 제출까지 키보드와 음성 안내만으로 완료하도록 초점 순서와 오류 메시지를 고쳤고, 18개 핵심 과업의 접근성 검증을 모두 완료했다.',
        '검증표에는 기기, 운영체제, 보조 기술과 통과 기준을 남겼다. 수정 뒤 동일한 과업을 다시 수행했고, 새 컴포넌트가 문제를 되살리지 않도록 배포 전 확인 항목에 초점 이동과 오류 안내를 포함했다.',
        q('보조 기술만으로 핵심 점검 과업을 끝낼 수 있게 검증한 경험이 있나요?', 'KO',
          ['abstract_competency', 'semantic_paraphrase', 'korean']),
        { aspects: ['accessible_task_flow'] }),
      s('P03', '목록 렌더링 안정화',
        '현장 목록은 필터를 바꿀 때마다 전체 행을 다시 만들고 이미지와 상태 배지를 동시에 계산했다. 프로파일 기록을 화면 구성, 데이터 변환, 이미지 처리로 나눠 반복 정렬과 보이지 않는 행 렌더링이 가장 큰 지연임을 확인했다.',
        '저사양 기기에서 2,000개 점검 항목을 여는 초기 화면 시간을 4.6초에서 1.9초로 줄였고, 측정 기준과 회귀 테스트를 릴리스 체크리스트에 반영했다.',
        '화면에 보이는 범위를 우선 처리하고 안정적인 행 식별자와 변환 결과 재사용을 적용했다. 세 종류 기기에서 최초 진입과 필터 변경을 반복해 평균뿐 아니라 사용한 데이터 크기와 느린 구간도 함께 기록했다.',
        q('대규모 모바일 목록의 첫 화면 지연을 절반 이하로 줄였다는 근거를 보여 주세요.', 'KO',
          ['numeric_quantity', 'semantic_paraphrase', 'korean']),
        { aspects: ['mobile_rendering_performance'] }),
      s('P04', '릴리스 관찰 가능성',
        '앱 충돌률만 낮으면 정상이라고 판단하던 방식은 데이터 동기화 지연을 놓쳤다. 릴리스 단계를 내부, 제한 현장, 전체 현장으로 나누고 각 단계에서 확인할 신호와 책임자를 명시했다.',
        '단계적 배포에서 오류율과 동기화 지연을 함께 관찰하는 대시보드와 중단 기준을 운영했고, 두 차례의 위험 신호에서 전체 배포 전에 롤아웃을 멈추고 원인을 수정했다.',
        '중단 뒤에는 변경 묶음, 영향을 받은 기기와 네트워크 조건을 비교했다. 회고에 판단자와 재개 조건을 남겨 다음 릴리스가 개인 기억에 의존하지 않게 했고 사용자 공지 절차도 체크리스트에 연결했다.',
        q('위험 신호를 근거로 모바일 배포를 중단하고 복구한 운영 경험이 있나요?', 'KO',
          ['abstract_competency', 'completed_production', 'korean']),
        { aspects: ['release_observability'] }),
    ],
  },
  {
    userBundleId: 'SV3-RB-U202',
    split: 'DEV',
    professionGroup: 'DESIGN_PRODUCT',
    profession: 'service designer and product strategist',
    language: 'EN',
    templateFamilyId: 'SV3-RB-TEMPLATE-DESIGN-CASEBOOK-202',
    generatorSeedId: 'SV3-RB-SEED-DESIGN-202-4A86',
    document: {
      title: 'Synthetic Public Service Design Casebook',
      fileName: 'sv3-rb-u202-service-design-casebook-v01.txt',
      documentType: 'PORTFOLIO',
      documentStructure: 'LONG_PORTFOLIO',
    },
    sections: [
      s('P01', 'Appointment recovery research',
        'The service team had separate reports for call volume, office queues, and missed visits, but those reports did not explain why residents repeated information. I prepared a neutral observation guide and recorded where people paused, changed channels, or asked another person for help.',
        'I observed 27 residents as they tried to reschedule missed appointments, mapped the handoffs between the call center and local offices, and turned five recurring breakdowns into a shared recovery journey.',
        'The journey map separated evidence from interpretation and linked each breakdown to its source session. Policy, operations, and support staff corrected two assumptions and agreed on the moments that required one consistent recovery message.',
        q('What evidence shows the candidate converted field observation into a cross-channel recovery model?', 'EN',
          ['semantic_paraphrase', 'abstract_competency', 'english']),
        { aspects: ['service_research_synthesis'] }),
      s('P02', 'Prototype decision record',
        'Three concepts used different combinations of reminders, confirmation language, and contact choices. Sessions used the same tasks and success definition so the team compared observed behavior rather than presentation preference.',
        'A moderated prototype with 16 participants raised successful rescheduling from 56% to 81%, and I documented the evidence, open risks, and rationale before the service owner approved the revised flow.',
        'The decision record included concepts that were not selected and why. It also named unresolved translation and assisted-service questions, preventing the prototype result from being presented as proof that every resident could complete the service unaided.',
        q('Did a tested design improve appointment completion to at least eighty percent before approval?', 'EN',
          ['numeric_quantity', 'semantic_paraphrase', 'english']),
        { aspects: ['prototype_outcome'] }),
      s('P03', 'Policy and operation alignment',
        'Earlier workshops produced long idea lists but left ownership unclear. For each decision session I circulated the disputed question, relevant evidence, legal constraints, and decision maker, then captured alternatives and consequences.',
        'I facilitated four decision sessions where policy, call-center, accessibility, and local-office representatives resolved ownership conflicts and agreed on one escalation rule for urgent appointment recovery.',
        'The resulting rule specified when the call center could resolve a case, when a local office intervened, and what information followed the resident. A six-week review checked whether teams used the same rule and recorded exceptions explicitly.',
        q('Is there evidence of aligning several departments around a single operational escalation rule?', 'EN',
          ['job_requirement', 'abstract_competency', 'english']),
        { aspects: ['cross_functional_alignment'] }),
      s('P04', 'Measured service rollout',
        'The rollout paired each office with a support contact and recorded training, local constraints, and activation date. A comparison period was selected before launch, and offices labeled repeat contacts with the same rule used in the baseline.',
        'The revised recovery service launched in six offices, and the team tracked repeat contacts for eight weeks before confirming a 22% reduction without changing the original measurement definition.',
        'Weekly reviews looked for demand moving to another channel rather than celebrating a single lower number. The final note separated observed reduction, seasonal uncertainty, and qualitative feedback and retained office-level counts for audit.',
        q('Was the redesigned recovery service launched across multiple offices with a sustained reduction in repeat contacts?', 'EN',
          ['completed_production', 'numeric_quantity', 'english']),
        { completionState: 'PRODUCTION', aspects: ['service_rollout_measurement'] }),
    ],
  },
  {
    userBundleId: 'SV3-RB-U203',
    split: 'DEV',
    professionGroup: 'DATA_AI_INFRA',
    profession: 'data platform and reliability engineer',
    language: 'KO_EN_MIXED',
    templateFamilyId: 'SV3-RB-TEMPLATE-DATA-OPERATIONS-203',
    generatorSeedId: 'SV3-RB-SEED-DATA-203-C912',
    document: {
      title: 'Synthetic Data Platform 운영 기록',
      fileName: 'sv3-rb-u203-data-platform-record-v01.txt',
      documentType: 'CAREER_REVIEW',
      documentStructure: 'CAREER_DESCRIPTION',
    },
    sections: [
      s('P01', 'Schema change safety',
        '여러 팀이 같은 event를 소비했지만 변경 요청은 서로 다른 문서에서 관리됐다. 작은 field rename도 지연 batch와 dashboard 오류로 이어질 수 있어 source, consumer, owner와 적용 시간을 하나의 change record로 모았다.',
        '본인은 producer schema 변경을 shadow validation으로 먼저 확인하고, 38개 downstream dataset의 compatibility report를 통과한 변경만 production registry에 반영하는 절차를 운영했다.',
        '검증 실패는 차단 사유와 영향을 받는 consumer를 보여 주었다. emergency override에는 별도 승인과 만료 시간을 요구했고, 적용 뒤 실제 payload sample과 contract 결과를 비교했다.',
        q('How did the candidate prevent incompatible event schemas from reaching production consumers?', 'EN',
          ['semantic_paraphrase', 'completed_production', 'english']),
        { completionState: 'PRODUCTION', aspects: ['schema_change_safety'] }),
      s('P02', 'Batch recovery practice',
        '사고 시 scheduler, storage, transformation 로그가 서로 다른 시간대를 사용했다. 공통 timeline을 만들고 입력 도착, checkpoint 생성, task retry와 output publish를 같은 시각 기준으로 맞췄다.',
        '새벽 집계 지연 사고에서 last safe checkpoint부터 1,700만 건을 재처리했고, 중복 key와 누락 partition 검사를 완료한 뒤 오전 보고 전에 정상 결과를 복구했다.',
        '복구 후 retry 성공만 확인하지 않고 row count, key uniqueness, partition completeness를 비교했다. 재처리 범위와 비용을 incident record에 남겨 다음 대응자가 같은 계산을 반복하지 않게 했다.',
        q('대규모 배치 지연 뒤 안전한 지점부터 데이터를 검증하며 복구한 근거가 있나요?', 'KO',
          ['abstract_competency', 'numeric_quantity', 'korean']),
        { aspects: ['batch_recovery'] }),
      s('P03', 'Model monitoring boundary',
        '전체 평균 score는 신규 사용자와 장기 사용자의 변화를 상쇄했다. 학습 데이터와 serving event의 공통 segment key를 선택하고 missing 값과 sample size가 작은 구간을 별도로 표시했다.',
        '추천 score distribution drift를 segment별로 측정하는 monitor를 배포하고, 두 개 segment에서 기준을 넘었을 때 model owner가 원인 분석을 끝낼 때까지 자동 promotion을 중단했다.',
        'alert는 곧바로 모델 실패를 선언하지 않고 traffic composition, feature freshness와 upstream event를 차례로 확인했다. promotion 재개에는 owner 승인과 검증 결과를 요구했다.',
        q('Was automatic model promotion stopped when segment-level drift crossed the monitoring boundary?', 'EN',
          ['completion_state', 'semantic_paraphrase', 'english']),
        { completionState: 'PRODUCTION', aspects: ['model_monitoring_control'] }),
      s('P04', 'On-call learning loop',
        '회고 문서는 팀마다 표현이 달라 같은 유형의 문제를 찾기 어려웠다. trigger, detection gap, containment, recovery와 prevention 항목을 공통으로 만들되 관련 원인을 함께 기록했다.',
        '분기 동안 발생한 11건의 data incident를 공통 taxonomy로 다시 분류하고, 반복 원인 세 가지에 owner와 due date가 있는 preventive action을 연결해 모두 완료했다.',
        'action review에서는 문서 작성 자체를 완료로 보지 않고 alert, test, ownership 또는 운영 절차가 실제로 바뀌었는지 확인했다. 기한 변경도 이유와 새 책임자를 공개했다.',
        q('반복 데이터 사고를 분류해 예방 조치를 끝까지 완료한 경험이 있나요?', 'KO',
          ['abstract_competency', 'completed_production', 'korean']),
        { aspects: ['incident_prevention_loop'] }),
    ],
  },
  {
    userBundleId: 'SV3-RB-U204',
    split: 'CALIBRATION',
    professionGroup: 'FRONTEND_MOBILE',
    profession: 'frontend platform engineer',
    language: 'EN',
    templateFamilyId: 'SV3-RB-TEMPLATE-FRONTEND-DELIVERY-204',
    generatorSeedId: 'SV3-RB-SEED-FRONTEND-204-2D57',
    document: {
      title: 'Synthetic Multi-Region Web Delivery Portfolio',
      fileName: 'sv3-rb-u204-frontend-delivery-portfolio-v01.txt',
      documentType: 'PORTFOLIO',
      documentStructure: 'LONG_PORTFOLIO',
    },
    sections: [
      s('P01', 'Resilient form submission',
        'Applicants in shared facilities often lost connectivity near the final attachment step. The previous form restarted the session after an error and did not explain whether information had reached the server, creating duplicate attempts.',
        'I shipped a resumable application form that retained encrypted local drafts through intermittent connectivity and successfully recovered 96% of interrupted submissions during the staged rollout.',
        'The implementation separated local draft state, uploaded attachments, and confirmed server state. Recovery tests covered browser restarts, expired sessions, attachment retries, and switching networks.',
        q('What evidence shows that interrupted web applications could resume without losing submitted work?', 'EN',
          ['semantic_paraphrase', 'completed_production', 'english']),
        { completionState: 'PRODUCTION', aspects: ['resumable_form_delivery'] }),
      s('P02', 'Internationalization release',
        'Mirroring individual pages had produced inconsistent icon direction, truncated labels, and controls whose visual order did not match keyboard order. I classified shared component behavior instead of applying one global transform.',
        'The team released right-to-left layout support for Arabic and Hebrew across 34 shared components, with visual regression and keyboard-navigation checks passing in both language modes.',
        'Review fixtures included long translations, mixed numerals, embedded English product names, validation errors, and narrow viewports. Release notes identified content constraints that layout code could not enforce.',
        q('Was bidirectional layout support released and verified across the shared component library?', 'EN',
          ['completed_production', 'job_requirement', 'english']),
        { completionState: 'PRODUCTION', aspects: ['bidirectional_component_release'] }),
      s('P03', 'Checkout performance budget',
        'The checkout accumulated third-party scripts, repeated state conversion, and synchronous price recalculation. Measurements used field data and a controlled device profile tagged by route, connection class, and promotions.',
        'I introduced a checkout performance budget and reduced p75 interaction latency from 310 milliseconds to 170 milliseconds on mid-range devices without removing validation or analytics.',
        'Work was divided into scheduling, component updates, and script ownership. The budget ran in pull requests and release monitoring, and exceptions required an owner, measured impact, and expiry date.',
        q('Did the candidate bring typical checkout interaction latency below two hundred milliseconds?', 'EN',
          ['numeric_quantity', 'semantic_paraphrase', 'english']),
        { aspects: ['checkout_interaction_performance'] }),
      s('P04', 'Frontend incident containment',
        'A global rollback would have removed unrelated fixes and interrupted unaffected regions. The playbook identified feature and asset boundaries that could be disabled independently and required cache and origin version checks.',
        'During a localization release incident, I disabled only the affected locale bundle, preserved checkout for other regions, and restored the corrected bundle within 43 minutes after source-map verification.',
        'The review linked the malformed build to a missing validation step and added fixture compilation before publication. It recorded containment, communication, cache purge order, and evidence used to reopen the locale.',
        q('Is there evidence of limiting a frontend incident to one locale while keeping other regions available?', 'EN',
          ['abstract_competency', 'semantic_paraphrase', 'english']),
        { aspects: ['frontend_incident_containment'] }),
    ],
  },
  {
    userBundleId: 'SV3-RB-U205',
    split: 'CALIBRATION',
    professionGroup: 'MARKETING_SALES',
    profession: 'regional growth and partner marketing manager',
    language: 'KO',
    templateFamilyId: 'SV3-RB-TEMPLATE-MARKETING-REVIEW-205',
    generatorSeedId: 'SV3-RB-SEED-MARKETING-205-8B40',
    document: {
      title: '합성 지역 파트너 마케팅 경력기술서',
      fileName: 'sv3-rb-u205-partner-marketing-review-v01.txt',
      documentType: 'CAREER_REVIEW',
      documentStructure: 'CAREER_DESCRIPTION',
    },
    sections: [
      s('P01', '파트너 수요 프로그램',
        '이전 보고는 다운로드, 행사 등록과 상담 요청을 한 숫자로 합쳐 실제 영업 연결을 판단하기 어려웠다. 파트너와 영업 운영 담당자가 함께 쓸 단계 정의와 채널별 검증 책임자를 시작 전에 합의했다.',
        '지역 파트너 14곳과 공동 캠페인을 운영해 검증된 상담 요청 1,260건을 만들었고, 중복과 내부 테스트를 제외한 정의를 CRM 보고서에 동일하게 적용했다.',
        '주간 검토에서는 거절 사유, 중복 계정, 후속 연락 여부를 함께 확인했다. 파트너별 보고서가 같은 기준을 쓰도록 제외 조건을 배포하고 정의 변경 시점을 표시했다.',
        q('공동 파트너 캠페인으로 천 건 이상의 유효 상담 요청을 만든 근거가 있나요?', 'KO',
          ['numeric_quantity', 'semantic_paraphrase', 'korean']),
        { aspects: ['partner_demand_program'] }),
      s('P02', '메시지 현지화 운영',
        '지역별 캠페인은 같은 제품을 설명하면서도 문제 상황과 구매 결정자가 달랐다. 번역 검토만으로는 고객이 사용하지 않는 단어가 반복되고 채널마다 혜택을 다르게 설명했다.',
        '영어 원문을 그대로 번역하지 않고 6개 지역의 고객 인터뷰와 검색 표현을 반영한 메시지 체계를 만들었으며, 승인된 표현을 광고·행사·영업 자료에 공통 적용했다.',
        '메시지 체계에는 핵심 주장, 허용 근거, 피해야 할 과장 표현과 지역별 예시를 포함했다. 현지 변경 제안에는 인터뷰나 검색 자료를 연결하고 승인 뒤 중앙 문서와 캠페인 자산을 함께 갱신했다.',
        q('여러 지역의 실제 고객 언어를 근거로 마케팅 메시지를 표준화했나요?', 'KO',
          ['abstract_competency', 'job_requirement', 'korean']),
        { aspects: ['evidence_based_localization'] }),
      s('P03', '예산 재배분 실험',
        '마지막 클릭 중심 보고는 긴 검토 기간을 가진 파트너 채널을 과소평가했다. 실험 전에 대상 지역, 기간, 상담 자격 기준과 기존 영업 활동을 통제할 방법을 정했다.',
        '채널별 증분 상담 비용을 비교하는 8주 실험을 완료하고, 성과가 낮은 두 채널의 예산 18%를 파트너 웨비나와 검색 캠페인으로 재배분했다.',
        '결과 검토에서는 단기 상담 수뿐 아니라 후속 미팅과 파이프라인 이동을 확인했다. 재배분 결정과 불확실성을 기록하고 다음 분기에 같은 차이가 유지되는지 재검토할 날짜를 정했다.',
        q('실험 결과를 근거로 낮은 성과 채널의 예산을 실제 재배치한 경험이 있나요?', 'KO',
          ['completed_production', 'semantic_paraphrase', 'korean']),
        { aspects: ['evidence_based_budget_shift'] }),
      s('P04', '영업 인계 품질',
        '영업 담당자는 연락처는 받았지만 고객이 참여한 행사, 관심 제품, 동의한 후속 연락과 파트너 역할을 다시 확인해야 했다. 실제 반송 사례에서 누락 유형을 분류했다.',
        '마케팅에서 영업으로 넘긴 상담 요청의 필수 맥락을 표준화하고 10주 동안 누락률을 31%에서 7%로 낮춰 지역별 인계 검토에 정착시켰다.',
        '대시보드는 누락 필드 수가 아니라 영업이 후속 행동을 시작할 수 있는지를 기준으로 삼았다. 지역별 예외를 남기고 개선 뒤에도 매주 표본을 확인했다. 검토 결과는 마케팅과 영업 운영 담당자가 함께 확인해 기준 변경과 단순 입력 실수를 구분했다.',
        q('영업 인계에 필요한 맥락 누락을 10% 아래로 낮춘 근거가 있나요?', 'KO',
          ['numeric_quantity', 'abstract_competency', 'korean']),
        { aspects: ['handoff_quality'] }),
    ],
  },
  {
    userBundleId: 'SV3-RB-U206',
    split: 'CALIBRATION',
    professionGroup: 'NON_DEVELOPMENT_GENERAL',
    profession: 'community learning operations coordinator',
    language: 'KO_EN_MIXED',
    templateFamilyId: 'SV3-RB-TEMPLATE-LEARNING-OPERATIONS-206',
    generatorSeedId: 'SV3-RB-SEED-LEARNING-206-5E23',
    document: {
      title: 'Synthetic Community Learning 운영 Portfolio',
      fileName: 'sv3-rb-u206-learning-operations-portfolio-v01.txt',
      documentType: 'PORTFOLIO',
      documentStructure: 'LONG_PORTFOLIO',
    },
    sections: [
      s('P01', 'Volunteer onboarding',
        '기존 오리엔테이션은 규정을 읽는 데 집중해 갈등이나 안전 우려가 생겼을 때 대응 순서를 확인하기 어려웠다. 설명, 시범, 역할 연습과 관찰 피드백을 연결했다.',
        '신규 facilitator 48명을 대상으로 역할 연습과 safety scenario를 포함한 onboarding을 운영했고, 모든 참가자의 observation checklist와 후속 코칭 기록을 완료했다.',
        '체크리스트 제출만으로 준비 완료를 선언하지 않았다. 질문 전달, 경계 설정, 긴급 연락과 수업 종료 절차를 관찰했고 보완이 필요한 참가자는 다음 세션 전에 재연습했다.',
        q('실제 상황 연습과 관찰을 포함해 수십 명의 진행자를 준비시킨 근거가 있나요?', 'KO',
          ['abstract_competency', 'numeric_quantity', 'korean']),
        { aspects: ['facilitator_onboarding'] }),
      s('P02', 'Attendance recovery',
        'Staff had sent the same reminder to every learner and marked a missed session as disengagement. Interviews showed that shift work, transport changes, and unclear rescheduling created different barriers.',
        'I redesigned the reminder and follow-up sequence for evening classes, and verified over twelve weeks that missed sessions fell from 24% to 13% without excluding learners who needed schedule changes.',
        'The sequence confirmed preferred contact, offered a rescheduling step, and routed repeated barriers to a coordinator. Weekly review separated cancellations, rescheduled attendance, and no contact.',
        q('Did the revised class follow-up process reduce missed attendance while retaining schedule-change cases?', 'EN',
          ['numeric_quantity', 'semantic_paraphrase', 'english']),
        { aspects: ['attendance_recovery'] }),
      s('P03', 'Safeguarding escalation',
        '각 교육장은 비슷한 상황을 다른 이름으로 부르고 연락 순서도 달랐다. 긴급 상황, 기록이 필요한 우려, 일반 문의를 구분하고 카드에는 관찰 사실과 연락 경로를 담았다.',
        '세 교육장에서 같은 safeguarding escalation card를 사용하도록 전환하고, 9건의 우려 사례가 지정된 담당자에게 기록과 함께 전달됐는지 월별로 확인했다.',
        '월별 검토는 사례 내용을 불필요하게 공유하지 않고 접수 시각, 담당자 확인, 필요한 조치와 종료 여부만 확인했다. 누락 시 카드 위치와 교대 인계를 점검했다.',
        q('여러 교육장에서 보호 우려를 같은 절차로 기록하고 담당자에게 인계했나요?', 'KO',
          ['completed_production', 'abstract_competency', 'korean']),
        { completionState: 'PRODUCTION', aspects: ['safeguarding_escalation'] }),
      s('P04', 'Resource inventory',
        'Inventory sheets used different item names and were updated after the fact, so active use could not be distinguished from loss. I reconciled labels and agreed on the minimum transfer information.',
        'A shared lending process for 620 learning kits reduced untraceable items from 46 to 8 in one term, with each transfer retaining the kit ID, location, responsible person, and return condition.',
        'The process supported batch movement but preserved individual kit identity. Term-end review separated damaged, delayed, and untraceable items and linked recurring loss points to storage and handover timing.',
        q('What evidence shows that accountable transfers sharply reduced missing learning kits?', 'EN',
          ['semantic_paraphrase', 'numeric_quantity', 'english']),
        { aspects: ['inventory_accountability'] }),
    ],
  },
]

function sha256(value) {
  return createHash('sha256').update(value).digest('hex')
}

function stableJson(value) {
  return JSON.stringify(value, null, 2) + '\n'
}

function normalize(value) {
  return value.normalize('NFKC').toLowerCase().replace(/[^\p{L}\p{N}]+/gu, ' ').trim()
}

function codePointOffset(value, utf16Offset) {
  return Array.from(value.slice(0, utf16Offset)).length
}

function lineAt(value, utf16Offset) {
  return value.slice(0, utf16Offset).split('\n').length
}

function sourceSpan(bundle, document, id, start, end, parentId) {
  const text = document.source.slice(start, end)
  return {
    spanId: id,
    parentId,
    documentId: document.documentId,
    versionId: document.versionId,
    sourcePath: 'documents/' + bundle.document.fileName,
    sourceType: 'TXT_TEXT',
    page: null,
    charStart: codePointOffset(document.source, start),
    charEnd: codePointOffset(document.source, end),
    lineStart: lineAt(document.source, start),
    lineEnd: lineAt(document.source, Math.max(start, end - 1)),
    text,
    textSha256: sha256(text),
  }
}

function materializeBundle(bundle) {
  const rendered = bundle.sections.map((value) => ({
    section: value,
    text: value.title + '\n\n' + value.blocks.join('\n\n'),
  }))
  const source = bundle.document.title + '\n\n'
    + rendered.map((value) => value.text).join('\n\n') + '\n'
  const documentId = bundle.userBundleId + '-D01'
  const versionId = documentId + '-V01'
  let cursor = bundle.document.title.length + 2
  const sections = rendered.map(({ section, text }) => {
    const start = source.indexOf(text, cursor)
    if (start < 0) throw new Error('Cannot locate section ' + bundle.userBundleId + '-' + section.id)
    const end = start + text.length
    cursor = end
    return { ...section, start, end }
  })
  const document = { documentId, versionId, source, sections }
  const parents = []
  const evidenceGroups = []
  const evidenceUnits = []
  const queries = []
  for (const value of sections) {
    const parentId = bundle.userBundleId + '-' + value.id
    const unitId = parentId + '-E01'
    const groupId = bundle.userBundleId + '-G-' + value.id + '-E01'
    const sourceFactId = 'SV3-RB-FACT-' + bundle.userBundleId.slice(7) + '-' + value.id + '-E01'
    parents.push({
      parentId,
      userBundleId: bundle.userBundleId,
      documentId,
      versionId,
      label: value.title,
      sourceSpan: sourceSpan(bundle, document, parentId + '-PS01', value.start, value.end, parentId),
    })
    const start = source.indexOf(value.fact.anchor)
    if (start < value.start || start >= value.end || start !== source.lastIndexOf(value.fact.anchor)) {
      throw new Error('Fact anchor must be unique in parent: ' + unitId)
    }
    evidenceGroups.push({
      groupId,
      userBundleId: bundle.userBundleId,
      sourceFactId,
      description: value.fact.aspects.join(', '),
      evidenceUnitIds: [unitId],
    })
    evidenceUnits.push({
      evidenceUnitId: unitId,
      userBundleId: bundle.userBundleId,
      parentId,
      groupId,
      documentId,
      versionId,
      sourceFactId,
      sourceSpans: [sourceSpan(
        bundle, document, unitId + '-S01', start, start + value.fact.anchor.length, parentId,
      )],
      primarySpanId: unitId + '-S01',
      contextSpanIds: [],
      actor: value.fact.actor,
      completionState: value.fact.completionState,
      aspects: value.fact.aspects,
      entities: [],
      numerics: value.fact.numerics,
      dates: [],
    })
    const queryId = bundle.userBundleId + '-Q' + value.id.slice(1).padStart(2, '0')
    const aspectId = value.fact.aspects[0]
    queries.push({
      queryId,
      userBundleId: bundle.userBundleId,
      questionGroupId: queryId + '-FAMILY',
      query: value.evidenceQuery.text,
      normalizedQuery: normalize(value.evidenceQuery.text),
      categories: value.evidenceQuery.categories,
      language: value.evidenceQuery.language,
      answerability: 'SUPPORTED',
      aspectExpression: {
        operator: 'ANY',
        requiredAspectIds: [aspectId],
        minShouldMatch: 1,
      },
      aspects: [{
        aspectId,
        required: true,
        answerability: 'SUPPORTED',
        expectedEvidence: [{ evidenceUnitId: unitId, supportRelation: 'DIRECT_SUPPORT' }],
        requiredEvidenceGroupIds: [groupId],
        minEvidenceGroups: 1,
        constraints: {
          entities: [],
          numerics: [],
          dates: [],
          actors: [],
          completionStates: [],
          polarity: 'POSITIVE',
        },
      }],
      safetyExclusions: [],
    })
  }
  return { bundle, document, parents, evidenceGroups, evidenceUnits, queries }
}

function countBy(values, selector) {
  const result = {}
  for (const value of values) {
    const key = selector(value)
    result[key] = (result[key] ?? 0) + 1
  }
  return Object.fromEntries(Object.entries(result).sort(([a], [b]) => a.localeCompare(b)))
}

function buildArtifacts() {
  const materialized = bundles.map(materializeBundle)
  const lengths = materialized.map((value) => Array.from(value.document.source).length)
  if (materialized.length !== 6 || lengths.some((value) => value < 1200)) {
    throw new Error('Expected six documents with at least 1,200 code points: ' + lengths.join(', '))
  }
  if (materialized.flatMap((value) => value.queries).length !== 24) {
    throw new Error('Robustness suite requires exactly 24 DIRECT-support queries')
  }
  const frontend = materialized.filter((value) => value.bundle.professionGroup === 'FRONTEND_MOBILE')
  if (frontend.length !== 2 || frontend.flatMap((value) => value.queries).length !== 8) {
    throw new Error('Fresh FRONTEND_MOBILE replication requires two bundles and eight queries')
  }
  const developmentCount = materialized.filter((value) =>
    ['BACKEND', 'FRONTEND_MOBILE', 'DATA_AI_INFRA'].includes(value.bundle.professionGroup)).length
  if (developmentCount > materialized.length / 2) {
    throw new Error('Development profession documents must not exceed half of the suite')
  }

  const files = new Map()
  const splitHashes = {}
  for (const split of ['DEV', 'CALIBRATION']) {
    const splitDirectory = split === 'DEV' ? 'dev' : 'calibration'
    const values = materialized.filter((value) => value.bundle.split === split)
    if (values.length !== 3) throw new Error('Each split requires exactly three bundles')
    const corpus = {
      artifactType: 'CORPUS',
      schemaVersion: SCHEMA_VERSION,
      datasetVersion: DATASET_VERSION,
      split,
      userBundles: values.map(({ bundle, document }) => ({
        userBundleId: bundle.userBundleId,
        split,
        professionGroup: bundle.professionGroup,
        profession: bundle.profession,
        languageProfile: bundle.language,
        documentFamilyId: bundle.userBundleId + '-DOC-FAMILY',
        templateFamilyId: bundle.templateFamilyId,
        documents: [{
          documentId: document.documentId,
          logicalDocumentId: document.documentId + '-LOGICAL',
          versionLineageId: document.documentId + '-LINEAGE',
          versionId: document.versionId,
          versionNumber: 1,
          active: true,
          title: bundle.document.title,
          documentType: bundle.document.documentType,
          documentStructure: bundle.document.documentStructure,
          fileType: 'TXT',
          language: bundle.language,
          contentPath: 'documents/' + bundle.document.fileName,
          contentSha256: sha256(document.source),
          supportScope: 'SUPPORTED_BY_CURRENT',
          visibility: 'OWNER_ONLY',
          provenance: {
            classification: 'SYNTHETIC',
            license: 'Apache-2.0',
            generatorName: 'prizm-prz026-robustness-materializer',
            generatorRevision: '1.0.0',
            generatorSeedId: bundle.generatorSeedId,
          },
        }],
      })),
    }
    const questions = {
      artifactType: 'QUESTIONS',
      schemaVersion: SCHEMA_VERSION,
      datasetVersion: DATASET_VERSION,
      split,
      queries: values.flatMap((value) => value.queries),
    }
    const gold = {
      artifactType: 'GOLD_EVIDENCE',
      schemaVersion: SCHEMA_VERSION,
      datasetVersion: DATASET_VERSION,
      split,
      parents: values.flatMap((value) => value.parents),
      evidenceGroups: values.flatMap((value) => value.evidenceGroups),
      evidenceUnits: values.flatMap((value) => value.evidenceUnits),
    }
    for (const value of values) {
      files.set(splitDirectory + '/documents/' + value.bundle.document.fileName, value.document.source)
    }
    files.set(splitDirectory + '/corpus.json', stableJson(corpus))
    files.set(splitDirectory + '/questions.json', stableJson(questions))
    files.set(splitDirectory + '/gold-evidence.json', stableJson(gold))
    const entries = [...files.entries()]
      .filter(([file]) => file.startsWith(splitDirectory + '/'))
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([file, content]) => ({
        path: file.slice(splitDirectory.length + 1),
        bytes: Buffer.byteLength(content),
        sha256: sha256(content),
      }))
    const combinedSha256 = sha256(entries.map((value) => value.path + ':' + value.sha256).join('\n'))
    splitHashes[split] = combinedSha256
    files.set(splitDirectory + '/manifest.json', stableJson({
      artifactType: 'MANIFEST',
      schemaVersion: SCHEMA_VERSION,
      datasetVersion: DATASET_VERSION,
      split,
      status: 'DEV_CAL_ROBUSTNESS_FROZEN',
      mutable: false,
      frozenAt: FROZEN_AT,
      previousVersion: PREVIOUS_VERSION,
      materializationBaseRevision: BASE_REVISION,
      counts: {
        userBundles: values.length,
        documents: values.length,
        queries: questions.queries.length,
        evidenceParents: gold.parents.length,
        evidenceGroups: gold.evidenceGroups.length,
        evidenceUnits: gold.evidenceUnits.length,
      },
      distributions: {
        profession: countBy(values, (value) => value.bundle.professionGroup),
        documentLanguage: countBy(values, (value) => value.bundle.language),
        queryLanguage: countBy(questions.queries, (value) => value.language),
        answerability: countBy(questions.queries, (value) => value.answerability),
        category: countBy(questions.queries.flatMap((value) => value.categories), (value) => value),
      },
      files: entries,
      combinedSha256,
    }))
  }

  const lineage = {
    artifactType: 'LINEAGE',
    schemaVersion: SCHEMA_VERSION,
    datasetVersion: DATASET_VERSION,
    previousVersion: PREVIOUS_VERSION,
    changeReason: 'Replicate B3 retrieval-passage ranking and cost on independent DEV/CAL lineages.',
    sealedFinalPolicy: 'PRZ-025 SEALED_FINAL_TEST remains byte-identical, unopened, and unsearched.',
    generator: 'scripts/evaluation/search-v3/materialize-prz026-robustness-devcal.mjs',
    materializationBaseRevision: BASE_REVISION,
    bundles: materialized.map(({ bundle, document, evidenceUnits, queries }) => ({
      userBundleId: bundle.userBundleId,
      split: bundle.split,
      documentFamilyId: bundle.userBundleId + '-DOC-FAMILY',
      templateFamilyId: bundle.templateFamilyId,
      generatorSeedId: bundle.generatorSeedId,
      logicalDocumentIds: [document.documentId + '-LOGICAL'],
      versionLineageIds: [document.documentId + '-LINEAGE'],
      sourceFactIds: evidenceUnits.map((value) => value.sourceFactId),
      sourceFactSignatures: evidenceUnits.map((value) => sha256(normalize(value.sourceSpans[0].text))),
      questionGroupIds: queries.map((value) => value.questionGroupId),
      normalizedQueries: queries.map((value) => value.normalizedQuery),
    })),
  }
  files.set('lineage.json', stableJson(lineage))
  files.set('README.md',
    '# PRZ-026 Retrieval Passage Robustness DEV/CAL\n\n'
    + '- dataset: ' + DATASET_VERSION + '\n'
    + '- previous dataset: ' + PREVIOUS_VERSION + '\n'
    + '- scope: synthetic DEV/CAL only; no personal data\n'
    + '- purpose: independent B2/B3 paired robustness replication\n'
    + '- B3 policy revision: ' + BASE_REVISION + '\n'
    + '- SEALED FINAL: not copied, not opened, not searched\n\n'
    + 'Run node scripts/evaluation/search-v3/materialize-prz026-robustness-devcal.mjs --check '
    + 'to verify byte-for-byte materialization.\n')

  const manifestInputs = [...files.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([file, content]) => ({ path: file, bytes: Buffer.byteLength(content), sha256: sha256(content) }))
  const combinedSha256 = sha256(manifestInputs.map((value) => value.path + ':' + value.sha256).join('\n'))
  files.set('manifest.json', stableJson({
    artifactType: 'MANIFEST',
    schemaVersion: SCHEMA_VERSION,
    datasetVersion: DATASET_VERSION,
    split: 'DEV_CAL_ONLY',
    status: 'DEV_CAL_ROBUSTNESS_FROZEN',
    mutable: false,
    opened: true,
    executionPolicy: 'DEV_CAL_EVALUATION_ALLOWED',
    executionStateRecord: 'ignored report plus PRZ-026 evidence.md',
    frozenAt: FROZEN_AT,
    previousVersion: PREVIOUS_VERSION,
    materializationBaseRevision: BASE_REVISION,
    b3PolicyRevision: BASE_REVISION,
    splitCombinedSha256: splitHashes,
    counts: {
      userBundles: materialized.length,
      documents: materialized.length,
      queries: materialized.flatMap((value) => value.queries).length,
      directQueries: materialized.flatMap((value) => value.queries).length,
      evidenceParents: materialized.flatMap((value) => value.parents).length,
      evidenceGroups: materialized.flatMap((value) => value.evidenceGroups).length,
      evidenceUnits: materialized.flatMap((value) => value.evidenceUnits).length,
    },
    distributions: {
      split: countBy(materialized, (value) => value.bundle.split),
      profession: countBy(materialized, (value) => value.bundle.professionGroup),
      documentLanguage: countBy(materialized, (value) => value.bundle.language),
      documentCodePointLength: Object.fromEntries(materialized.map((value) => [
        value.document.documentId, Array.from(value.document.source).length,
      ])),
      queryLanguage: countBy(materialized.flatMap((value) => value.queries), (value) => value.language),
      answerability: countBy(materialized.flatMap((value) => value.queries), (value) => value.answerability),
      category: countBy(materialized.flatMap((value) => value.queries)
        .flatMap((value) => value.categories), (value) => value),
    },
    files: manifestInputs,
    combinedSha256,
  }))
  return files
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

function collisionFindings(current, previous, prefix) {
  const findings = []
  for (const field of ['userBundleId', 'documentFamilyId', 'templateFamilyId', 'generatorSeedId']) {
    const known = new Set(previous.bundles.map((value) => value[field]))
    if (current.bundles.some((value) => known.has(value[field]))) {
      findings.push(prefix + ' lineage collision: ' + field)
    }
  }
  for (const field of [
    'logicalDocumentIds', 'versionLineageIds', 'sourceFactIds',
    'sourceFactSignatures', 'questionGroupIds', 'normalizedQueries',
  ]) {
    const known = new Set(previous.bundles.flatMap((value) => value[field]))
    if (current.bundles.flatMap((value) => value[field]).some((value) => known.has(value))) {
      findings.push(prefix + ' leakage collision: ' + field)
    }
  }
  return findings
}

async function check(files) {
  const findings = []
  for (const [relative, expected] of files) {
    const actual = await readFile(path.join(OUTPUT_ROOT, relative), 'utf8').catch(() => null)
    if (actual !== expected) findings.push('content mismatch: ' + relative)
  }
  if (JSON.stringify(await listFiles(OUTPUT_ROOT)) !== JSON.stringify([...files.keys()].sort())) {
    findings.push('file inventory mismatch')
  }
  const lineage = JSON.parse(files.get('lineage.json'))
  for (const field of ['userBundleId', 'documentFamilyId', 'templateFamilyId', 'generatorSeedId']) {
    const values = lineage.bundles.map((value) => value[field])
    if (new Set(values).size !== values.length) findings.push('suite lineage duplicate: ' + field)
  }
  for (const field of [
    'logicalDocumentIds', 'versionLineageIds', 'sourceFactIds',
    'sourceFactSignatures', 'questionGroupIds', 'normalizedQueries',
  ]) {
    const values = lineage.bundles.flatMap((value) => value[field])
    if (new Set(values).size !== values.length) findings.push('suite leakage duplicate: ' + field)
  }
  const original = JSON.parse(await readFile(
    path.resolve('src/test/resources/search-v3-evaluation/lineage.json'), 'utf8'))
  const longForm = JSON.parse(await readFile(
    path.resolve('src/searchEvaluation/resources/search-v3-evaluation/devcal-1.1.0/lineage.json'),
    'utf8',
  ))
  findings.push(...collisionFindings(lineage, original, 'original-seed'))
  findings.push(...collisionFindings(lineage, longForm, 'devcal-1.1.0'))
  const sealed = JSON.parse(await readFile(
    path.resolve('src/test/resources/search-v3-evaluation/sealed-final/manifest.json'), 'utf8'))
  if (sealed.combinedSha256 !== 'e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383'
      || sealed.opened !== false || sealed.searchExecuted !== false) {
    findings.push('PRZ-025 SEALED FINAL metadata changed')
  }
  if (findings.length > 0) {
    findings.forEach((value) => console.error('FAIL ' + value))
    process.exitCode = 1
    return
  }
  const manifest = JSON.parse(files.get('manifest.json'))
  console.log('PASS dataset=' + DATASET_VERSION)
  console.log('PASS files=' + files.size)
  console.log('PASS bundles=' + manifest.counts.userBundles)
  console.log('PASS queries=' + manifest.counts.queries)
  console.log('PASS combinedSha256=' + manifest.combinedSha256)
}

async function materialize(files) {
  for (const [relative, content] of files) {
    const destination = path.join(OUTPUT_ROOT, relative)
    await mkdir(path.dirname(destination), { recursive: true })
    await writeFile(destination, content, 'utf8')
  }
  console.log('Materialized ' + DATASET_VERSION + ' at ' + OUTPUT_ROOT)
}

const files = buildArtifacts()
if (process.argv.includes('--check')) await check(files)
else await materialize(files)
