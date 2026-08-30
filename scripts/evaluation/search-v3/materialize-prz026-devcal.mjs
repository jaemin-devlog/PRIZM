import { createHash } from 'node:crypto'
import { mkdir, readFile, readdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'

const DATASET_VERSION = 'search-v3-fresh-devcal-1.1.0'
const PREVIOUS_VERSION = 'search-v3-fresh-seed-1.0.1'
const SCHEMA_VERSION = '1.0.0'
const FROZEN_AT = '2026-08-30T19:43:14.0659652+09:00'
const MATERIALIZATION_BASE_REVISION = 'a9d093dd48e99a8d19675b3a8caa09c794d2888b'
const OUTPUT_ROOT = path.resolve(
  'src/searchEvaluation/resources/search-v3-evaluation/devcal-1.1.0',
)

const normalizedSignatureText = (value) => value
  .normalize('NFKC')
  .toLocaleLowerCase('und')
  .replace(/[\p{P}\p{S}]+/gu, ' ')
  .replace(/\s+/gu, ' ')
  .trim()

const bundles = [
  {
    userBundleId: 'SV3-LF-U101',
    split: 'DEV',
    professionGroup: 'DESIGN_PRODUCT',
    profession: 'service designer and product researcher',
    language: 'KO',
    templateFamilyId: 'SV3-LF-TEMPLATE-DESIGN-101',
    generatorSeedId: 'SV3-LF-SEED-DESIGN-101-A7C2',
    document: {
      title: '합성 지역 교통 예약 서비스 디자인 포트폴리오',
      documentType: 'PORTFOLIO',
      documentStructure: 'LONG_PORTFOLIO',
      fileName: 'sv3-lf-u101-design-portfolio-v01.txt',
    },
    sections: [
      {
        id: 'P01',
        title: '현장 조사와 문제 구조화',
        blocks: [
          '지역 교통 예약 서비스는 전화, 키오스크, 모바일 화면을 함께 운영하고 있었고 채널마다 취소 규칙을 다르게 안내했다. 나는 프로젝트를 시작할 때 고객 문의 기록과 현장 운영 일지를 함께 읽어 반복적으로 등장하는 혼란을 조사 질문으로 바꾸었다. 인터뷰 전에 참여 동의와 기록 보관 범위를 안내했으며, 결과를 특정 개인의 진술로 일반화하지 않도록 관찰 메모와 행동 로그를 분리했다.',
          '본인은 이동 제약이 있는 이용자 18명과 현장 직원 6명을 인터뷰하고 예약 변경 과정의 단절 지점을 여섯 가지 행동 패턴으로 분류했다.',
          '- 전화 상담을 먼저 찾는 이용자의 이유를 채널 선호가 아니라 실패 복구 경험으로 기록했다.\n- 현장 직원이 임시 메모로 보완하던 규칙을 서비스 정책과 화면 정보의 차이로 표시했다.\n- 영어 안내를 이용한 참여자에게 같은 질문을 반복해 번역 표현이 응답을 유도하지 않는지 확인했다.',
          '조사 자료를 정리할 때 빈도가 높은 문제만 남기지 않았다. 낮은 빈도라도 이동 보조가 필요한 참여자에게 큰 비용을 만드는 문제는 별도 위험으로 표시했고, 제품팀과 운영팀이 같은 근거를 보도록 source note를 연결했다. 이 과정은 솔루션을 먼저 정하지 않고 문제의 행위 조건을 설명하는 데 목적이 있었다.',
        ],
        facts: [
          {
            id: 'E01',
            anchor: '본인은 이동 제약이 있는 이용자 18명과 현장 직원 6명을 인터뷰하고 예약 변경 과정의 단절 지점을 여섯 가지 행동 패턴으로 분류했다.',
            actor: 'SELF',
            completionState: 'COMPLETED',
            aspects: ['field_research', 'problem_structuring'],
            numerics: [
              { normalizedValue: 18, unit: 'USER_COUNT', semanticType: 'INTERVIEW_COUNT', sourceSurface: '이용자 18명' },
              { normalizedValue: 6, unit: 'STAFF_COUNT', semanticType: 'INTERVIEW_COUNT', sourceSurface: '현장 직원 6명' },
            ],
          },
        ],
      },
      {
        id: 'P02',
        title: '예약 변경 흐름 재설계',
        blocks: [
          '나는 조사에서 확인한 문제를 예약 상태, 가능한 행동, 복구 안내의 세 층으로 나누었다. 기존 화면은 정책 문장을 길게 보여 주었지만 사용자는 현재 무엇을 할 수 있는지 먼저 찾았다. 그래서 상태 이름을 일상 언어로 바꾸고 변경 가능 시간과 수수료를 행동 버튼 가까이에 배치한 두 가지 prototype을 만들었다.',
          '첫 prototype은 정보량을 줄이는 데 집중했지만 현장 직원이 예외 상황을 설명하기 어려웠다. 두 번째 prototype은 예외를 숨기지 않고 사용자가 선택한 행동에 필요한 규칙만 단계적으로 보여 주었다. 나는 두 안을 동일한 과업과 순서로 평가해 시각적 선호가 아니라 오류 복구 여부를 비교했다.',
          '재설계한 흐름은 비감독 사용성 평가에서 예약 변경 과업 완료율을 58%에서 86%로 높였고, 잘못된 취소 선택은 세션당 평균 1.7회에서 0.4회로 줄였다.',
          '평가 뒤에는 성공한 화면만 전달하지 않았다. 실패가 남은 보조기기 탐색, 긴 역 이름의 줄바꿈, 환불 지연 안내를 backlog와 research note에 함께 남겼다. 운영 담당자가 정책을 바꿀 때 화면 문구와 도움말이 동시에 갱신되도록 content checklist도 작성했다.',
        ],
        facts: [
          {
            id: 'E01',
            anchor: '재설계한 흐름은 비감독 사용성 평가에서 예약 변경 과업 완료율을 58%에서 86%로 높였고, 잘못된 취소 선택은 세션당 평균 1.7회에서 0.4회로 줄였다.',
            actor: 'SELF',
            completionState: 'COMPLETED',
            aspects: ['usability_outcome'],
            numerics: [
              { normalizedValue: 86, unit: 'PERCENT', semanticType: 'TASK_COMPLETION_RATE', sourceSurface: '86%' },
            ],
          },
        ],
      },
      {
        id: 'P03',
        title: '출시 협업과 역할 경계',
        blocks: [
          '제품팀은 재설계안을 작은 지역부터 적용했고 나는 release note, 상담 스크립트, 오류 관찰 항목을 연결했다. 배포 당일에는 현장 직원에게 답을 대신 주기보다 어떤 화면과 규칙이 충돌했는지 기록하도록 안내했다. 일주일 동안 수집된 사례는 디자인 수정과 정책 설명 보완으로 나누어 처리했다.',
          '마케팅 조직은 새 예약 화면을 소개하는 광고 문구의 A/B test를 별도로 수행했으며, 본인은 그 실험을 설계하거나 집행하지 않고 화면 사실 확인만 지원했다.',
          '역할 경계를 기록한 이유는 같은 출시 안에서 수행된 모든 활동을 개인의 성과로 합치지 않기 위해서였다. 광고 전환율은 마케팅 조직의 지표였고, 나는 예약 과정의 과업 성공과 오류 복구 자료만 디자인 의사결정 근거로 사용했다.',
          '프로젝트 종료 회고에서는 연구 질문, prototype 변경, 운영 정책 수정이 어떤 source note에서 시작됐는지 추적했다. 후속 담당자가 결과 숫자만 보고 원래 문제를 오해하지 않도록 반례와 미해결 조건도 같은 문서에 남겼다.',
        ],
        facts: [
          {
            id: 'E01',
            anchor: '마케팅 조직은 새 예약 화면을 소개하는 광고 문구의 A/B test를 별도로 수행했으며, 본인은 그 실험을 설계하거나 집행하지 않고 화면 사실 확인만 지원했다.',
            actor: 'OTHER',
            completionState: 'COMPLETED',
            aspects: ['other_actor_ab_test'],
          },
        ],
      },
      {
        id: 'P04',
        title: '후속 접근성 연구 계획',
        blocks: [
          '다음 연구에서는 화면 확대와 switch control을 사용하는 참여자를 별도로 모집할 계획을 세웠다. 모집 문안, 보조기기 확인 질문, 원격 세션의 지원 절차까지 초안을 만들었지만 현재 포트폴리오 시점에는 참여자 모집과 본조사를 시작하지 않았다.',
          '접근성 연구는 2027년 상반기 제안 단계이며 production 적용이나 완료 성과로 기록하지 않는다.',
          '계획을 남길 때는 예상 효과를 측정 결과처럼 쓰지 않았다. 필요한 동의 절차와 지원 인력, 연구 환경, 삭제 정책을 먼저 검토해야 하며 실제 참여자 자료가 생기면 별도의 접근 권한과 보관 계약을 적용할 예정이다.',
          '이 문서는 완전한 디자인 시스템이나 모든 교통 상황을 다루는 결과 보고서가 아니다. 확인한 범위와 아직 확인하지 않은 범위를 구분해 다음 실험이 이전 결과를 독립적으로 검증할 수 있도록 한다.',
        ],
        facts: [
          {
            id: 'E01',
            anchor: '접근성 연구는 2027년 상반기 제안 단계이며 production 적용이나 완료 성과로 기록하지 않는다.',
            actor: 'SELF',
            completionState: 'PLANNED',
            aspects: ['planned_accessibility_research'],
          },
        ],
      },
    ],
    queries: [
      {
        id: 'Q01',
        text: '현장 관찰을 사용자 흐름 문제로 구조화한 근거가 있나요?',
        language: 'KO',
        categories: ['semantic_paraphrase', 'abstract_competency', 'korean'],
        answerability: 'SUPPORTED',
        expected: [{ unit: 'P01-E01', relation: 'DIRECT_SUPPORT', aspect: 'research' }],
      },
      {
        id: 'Q02',
        text: '예약 변경 과업 완료율을 80% 이상으로 개선했나요?',
        language: 'KO',
        categories: ['numeric_quantity', 'semantic_paraphrase', 'korean'],
        answerability: 'SUPPORTED',
        expected: [{ unit: 'P02-E01', relation: 'DIRECT_SUPPORT', aspect: 'outcome' }],
      },
      {
        id: 'Q03',
        text: '본인이 광고 문구 A/B 테스트를 직접 수행했나요?',
        language: 'KO_EN_MIXED',
        categories: ['other_actor', 'hard_negative', 'korean_english_mixed'],
        answerability: 'NOT_SUPPORTED',
        expected: [{ unit: 'P03-E01', relation: 'INSUFFICIENT', aspect: 'actor' }],
      },
      {
        id: 'Q04',
        text: '접근성 연구를 이미 production에 적용해 완료했나요?',
        language: 'KO_EN_MIXED',
        categories: ['completion_state', 'negation', 'hard_negative', 'korean_english_mixed'],
        answerability: 'NOT_SUPPORTED',
        expected: [{ unit: 'P04-E01', relation: 'CONTRADICTS', aspect: 'completion' }],
      },
    ],
  },
  {
    userBundleId: 'SV3-LF-U102',
    split: 'DEV',
    professionGroup: 'DATA_AI_INFRA',
    profession: 'data reliability engineer',
    language: 'EN',
    templateFamilyId: 'SV3-LF-TEMPLATE-DATA-102',
    generatorSeedId: 'SV3-LF-SEED-DATA-102-C4E8',
    document: {
      title: 'Synthetic Data Reliability and Applied ML Portfolio',
      documentType: 'PORTFOLIO',
      documentStructure: 'LONG_PORTFOLIO',
      fileName: 'sv3-lf-u102-data-reliability-v01.txt',
    },
    sections: [
      {
        id: 'P01',
        title: 'Production batch reliability',
        blocks: [
          'The reporting platform received files from billing, support, and regional operations. Each source used a different delivery window and correction policy, so a late file could look identical to an empty day. I mapped those states before changing the pipeline and retained the original arrival record beside every normalized batch.',
          'I operated a production validation pipeline that checked 2.4 million records per day and reduced the median late-data detection time from 47 minutes to 11 minutes.',
          '- Freshness checks compared the source delivery promise with actual arrival time.\n- Completeness checks retained the expected partition count and the observed count.\n- Recovery notes identified whether a rerun replaced or supplemented an earlier batch.\n- Consumer notices described which dashboards were affected without claiming that unaffected products were delayed.',
          'The rollout was staged by source family. During the first week I kept the old alert and the new state model side by side, reviewed disagreements each morning, and only retired the old path after the data owners agreed on the meaning of late, missing, and corrected. This reduced ambiguity rather than merely lowering the number of alerts.',
          'A later review found that one regional source legitimately delivered after midnight. I changed the expectation for that source instead of widening the global threshold, then added a test fixture for the boundary. The decision and its owner were recorded so that a future schedule change would not silently restore the false alarm.',
        ],
        facts: [
          {
            id: 'E01',
            anchor: 'I operated a production validation pipeline that checked 2.4 million records per day and reduced the median late-data detection time from 47 minutes to 11 minutes.',
            actor: 'SELF',
            completionState: 'PRODUCTION',
            aspects: ['pipeline_scale', 'detection_latency'],
            numerics: [
              { normalizedValue: 2400000, unit: 'RECORD_PER_DAY', semanticType: 'RECORD_COUNT', sourceSurface: '2.4 million records per day' },
            ],
          },
        ],
      },
      {
        id: 'P02',
        title: 'Recommendation prototype boundary',
        blocks: [
          'A product team asked whether support history could help order knowledge-base suggestions. I built an offline prototype using de-identified topic labels and a fixed evaluation snapshot. The objective was to measure whether ranking changed, not to send recommendations to customers.',
          'The recommendation model remained an offline prototype; it was not deployed to production and never served live customer traffic.',
          'The offline review compared the prototype with a frequency baseline, inspected queries where the two systems disagreed, and documented cases in which sparse history produced unstable suggestions. I did not convert that experiment into a claim about business impact because no customer-facing exposure occurred.',
          'Before any future deployment, the proposal would require data-retention review, an owner-scoped serving path, online safety monitoring, and a rollback plan. Those items are open requirements, not completed implementation.',
        ],
        facts: [
          {
            id: 'E01',
            anchor: 'The recommendation model remained an offline prototype; it was not deployed to production and never served live customer traffic.',
            actor: 'SELF',
            completionState: 'PROTOTYPE',
            aspects: ['model_not_production'],
          },
        ],
      },
      {
        id: 'P03',
        title: 'Incident prevention and ownership',
        blocks: [
          'An upstream schema change once caused a field to be parsed as text rather than a decimal. The batch completed, but a downstream aggregate was wrong. I led the review with the source owner, added type expectations at the ingestion boundary, and made corrected runs distinguishable from the first publication.',
          'I introduced a release checklist that paired schema compatibility, sample reconciliation, consumer notification, and recovery rehearsal before a high-risk source change.',
          'The checklist did not eliminate judgment. A source owner could accept a known difference, but the decision needed a time limit and named consumer. On-call notes linked the alert, the accepted exception, and the replay command so a responder would not have to reconstruct the context from chat history.',
          'After three months the team removed two redundant alerts, kept the boundary validation, and moved a costly full comparison to a daily audit. The change reduced unnecessary pages while preserving the signal that had prevented a repeat of the incident.',
          'This work was shared with analysts and platform engineers in a blameless review. I owned the validation and recovery changes; the source team owned its publishing contract. The evidence keeps those actor boundaries explicit.',
        ],
        facts: [
          {
            id: 'E01',
            anchor: 'I introduced a release checklist that paired schema compatibility, sample reconciliation, consumer notification, and recovery rehearsal before a high-risk source change.',
            actor: 'SELF',
            completionState: 'PRODUCTION',
            aspects: ['incident_prevention'],
          },
        ],
      },
      {
        id: 'P04',
        title: 'Operating record',
        blocks: [
          'Capability | Period | State\nBatch validation | 2024-01 to 2025-06 | production\nRecommendation ranking | 2025-03 | offline prototype\nRecovery rehearsal | quarterly | completed',
          'The table summarizes states already described in the source narrative. It is not a substitute for the evidence above, and the prototype row must not be combined with the production batch row to imply a production recommendation service.',
          'I also maintained a short runbook for checking source availability before escalating to consumers. It listed observable conditions rather than vendor-specific assumptions, which allowed the same process to be used when one source moved to a different delivery mechanism.',
          'The portfolio intentionally includes successful changes, rejected assumptions, and boundaries. Retrieval quality should preserve those distinctions when several semantically similar records appear in one long document.',
        ],
        facts: [],
      },
    ],
    queries: [
      {
        id: 'Q01',
        text: 'Did the candidate validate at least two million records each day in production?',
        language: 'EN',
        categories: ['numeric_quantity', 'completed_production', 'english'],
        answerability: 'SUPPORTED',
        expected: [{ unit: 'P01-E01', relation: 'DIRECT_SUPPORT', aspect: 'scale' }],
      },
      {
        id: 'Q02',
        text: 'Was the recommendation model serving live customers in production?',
        language: 'EN',
        categories: ['completion_state', 'negation', 'hard_negative', 'english'],
        answerability: 'NOT_SUPPORTED',
        expected: [{ unit: 'P02-E01', relation: 'CONTRADICTS', aspect: 'deployment' }],
      },
      {
        id: 'Q03',
        text: 'What evidence shows preventive ownership after a data incident?',
        language: 'EN',
        categories: ['semantic_paraphrase', 'abstract_competency', 'english'],
        answerability: 'SUPPORTED',
        expected: [{ unit: 'P03-E01', relation: 'DIRECT_SUPPORT', aspect: 'prevention' }],
      },
      {
        id: 'Q04',
        text: 'Show both high-volume pipeline operation and a concrete prevention process.',
        language: 'EN',
        categories: ['multi_evidence', 'job_requirement', 'english'],
        answerability: 'SUPPORTED',
        expected: [
          { unit: 'P01-E01', relation: 'DIRECT_SUPPORT', aspect: 'operation' },
          { unit: 'P03-E01', relation: 'DIRECT_SUPPORT', aspect: 'prevention' },
        ],
      },
    ],
  },
  {
    userBundleId: 'SV3-LF-U103',
    split: 'DEV',
    professionGroup: 'MARKETING_SALES',
    profession: 'growth marketing and sales operations lead',
    language: 'KO_EN_MIXED',
    templateFamilyId: 'SV3-LF-TEMPLATE-GROWTH-103',
    generatorSeedId: 'SV3-LF-SEED-GROWTH-103-D5B1',
    document: {
      title: 'Synthetic Growth Marketing & Sales Operations Career Review',
      documentType: 'CAREER_REVIEW',
      documentStructure: 'CAREER_DESCRIPTION',
      fileName: 'sv3-lf-u103-growth-sales-v01.txt',
    },
    sections: [
      {
        id: 'P01',
        title: 'Webinar onboarding campaign',
        blocks: [
          '신규 고객이 제품 설명을 요청한 뒤 실제 상담으로 이어지는 과정이 지역마다 달랐다. 나는 email, webinar, sales handoff의 단계를 하나의 funnel로 정의하고 각 단계에서 필요한 동의와 후속 연락 기준을 정리했다. 단순 발송량보다 누구에게 어떤 약속을 했는지 추적하는 것이 우선이었다.',
          'I launched a bilingual webinar onboarding campaign that generated 820 qualified leads; the number was not a count of paid customers.',
          '- Korean invitation copy explained the practical agenda before registration.\n- English follow-up summarized the recording and a clear unsubscribe path.\n- Sales handoff included the question asked, the region, and the consented contact channel.\n- Weekly review separated registration volume from qualified interest and completed contracts.',
          '캠페인 회고에서는 webinar 참석 여부만으로 관심도를 판단하지 않았다. 질문 내용, 후속 자료 열람, 상담 요청을 함께 보고 lead stage를 수정했다. 또한 지역별 작은 표본을 전체 시장의 선호처럼 해석하지 않도록 confidence note를 남겼다.',
        ],
        facts: [
          {
            id: 'E01',
            anchor: 'I launched a bilingual webinar onboarding campaign that generated 820 qualified leads; the number was not a count of paid customers.',
            actor: 'SELF',
            completionState: 'COMPLETED',
            aspects: ['campaign_leads'],
            numerics: [
              { normalizedValue: 820, unit: 'QUALIFIED_LEAD_COUNT', semanticType: 'LEAD_COUNT', sourceSurface: '820 qualified leads' },
            ],
          },
        ],
      },
      {
        id: 'P02',
        title: 'CRM stage rollout',
        blocks: [
          '기존 CRM은 같은 상태 이름을 지역마다 다르게 사용했다. 어떤 팀은 첫 연락만으로 opportunity를 만들었고 다른 팀은 예산 확인 뒤에 만들었다. 나는 과거 보고서를 다시 쓰기보다 새 기준의 시작일과 변환 규칙을 문서화했다.',
          '본인은 5개 지역의 lead-stage 정의를 표준화하고 coordinator 교육을 마친 뒤 2025년 2분기에 공통 CRM workflow를 운영 상태로 전환했다.',
          'Stage | Required evidence | Owner\nQualified | confirmed problem and contact consent | marketing operations\nOpportunity | budget owner and next meeting | regional sales\nClosed | signed agreement recorded | sales operations',
          '운영 전환 첫 달에는 stage 변경 이유가 비어 있는 기록을 매주 검토했다. 숫자를 맞추기 위해 임의로 stage를 올리지 않았고, 불명확한 record는 원래 상태를 유지한 채 담당자에게 확인했다. 그 결과 dashboard 비교가 가능해졌지만 이전 분기의 수치와 직접 연결할 때는 definition change를 표시했다.',
        ],
        facts: [
          {
            id: 'E01',
            anchor: '본인은 5개 지역의 lead-stage 정의를 표준화하고 coordinator 교육을 마친 뒤 2025년 2분기에 공통 CRM workflow를 운영 상태로 전환했다.',
            actor: 'SELF',
            completionState: 'PRODUCTION',
            aspects: ['crm_rollout'],
            numerics: [
              { normalizedValue: 5, unit: 'REGION_COUNT', semanticType: 'ROLLOUT_SCOPE', sourceSurface: '5개 지역' },
            ],
          },
        ],
      },
      {
        id: 'P03',
        title: 'Partner sales actor boundary',
        blocks: [
          '파트너 프로그램에서는 enablement 자료, demo checklist, objection note를 제공했다. 나는 파트너가 고객에게 과장된 약속을 하지 않도록 지원 범위와 escalation path를 반복해 설명했고, 제품팀이 확인하지 않은 roadmap 항목은 자료에서 제외했다.',
          'The partner sales team closed 32 enterprise accounts, while I prepared enablement material and did not negotiate or sign those contracts.',
          '계약 수는 파트너 영업 조직의 결과다. 내가 수행한 업무는 자료 작성과 운영 피드백 수집이며, 계약 체결을 본인의 직접 성과로 표시하지 않는다. 다만 반복되는 objection을 분류해 다음 교육의 예시로 반영한 과정은 내 활동으로 남긴다.',
          '분기 말 review에서는 파트너별 질문 유형과 지원 요청 시간을 살폈다. 규모가 큰 파트너만 우선하지 않고 새 파트너가 처음 막히는 단계도 기록해 onboarding 자료를 보완했다.',
        ],
        facts: [
          {
            id: 'E01',
            anchor: 'The partner sales team closed 32 enterprise accounts, while I prepared enablement material and did not negotiate or sign those contracts.',
            actor: 'OTHER',
            completionState: 'COMPLETED',
            aspects: ['other_actor_contracts'],
            numerics: [
              { normalizedValue: 32, unit: 'ACCOUNT_COUNT', semanticType: 'CLOSED_ACCOUNT_COUNT', sourceSurface: '32 enterprise accounts' },
            ],
          },
        ],
      },
      {
        id: 'P04',
        title: 'Budget experiment status',
        blocks: [
          '다음 반기에는 channel별 incremental lift를 보기 위한 geo experiment를 제안했다. 실험 지역 격리, 예산 상한, 중단 기준과 기존 영업 활동의 영향을 검토했지만 재무 승인 전에는 campaign을 시작하지 않기로 했다.',
          'Geo budget experiment — planned for 2026 Q4, not launched.',
          '제안서의 forecast는 실제 성과가 아니다. 예상 lead 수와 비용 범위는 의사결정을 위한 가정으로 표시했고, 실행 후에는 pre-registered metric과 실제 데이터를 따로 비교하도록 계획했다.',
          '현재 기록에서 완료된 활동은 webinar campaign과 CRM rollout이다. geo experiment는 후속 후보이며 completed production evidence로 합치지 않는다.',
        ],
        facts: [
          {
            id: 'E01',
            anchor: 'Geo budget experiment — planned for 2026 Q4, not launched.',
            actor: 'SELF',
            completionState: 'PLANNED',
            aspects: ['planned_budget_experiment'],
          },
        ],
      },
    ],
    queries: [
      {
        id: 'Q01',
        text: 'Did the bilingual campaign produce at least 800 qualified leads?',
        language: 'EN',
        categories: ['numeric_quantity', 'semantic_paraphrase', 'english'],
        answerability: 'SUPPORTED',
        expected: [{ unit: 'P01-E01', relation: 'DIRECT_SUPPORT', aspect: 'leads' }],
      },
      {
        id: 'Q02',
        text: '5개 지역에서 CRM stage 기준을 실제 운영으로 전환했나요?',
        language: 'KO_EN_MIXED',
        categories: ['completion_state', 'completed_production', 'korean_english_mixed'],
        answerability: 'SUPPORTED',
        expected: [{ unit: 'P02-E01', relation: 'DIRECT_SUPPORT', aspect: 'crm' }],
      },
      {
        id: 'Q03',
        text: 'Did this person personally close 32 enterprise accounts?',
        language: 'EN',
        categories: ['other_actor', 'hard_negative', 'english'],
        answerability: 'NOT_SUPPORTED',
        expected: [{ unit: 'P03-E01', relation: 'CONTRADICTS', aspect: 'actor' }],
      },
      {
        id: 'Q04',
        text: 'webinar campaign과 geo budget experiment를 모두 이미 실행했나요?',
        language: 'KO_EN_MIXED',
        categories: ['multi_evidence', 'completion_state', 'hard_negative', 'korean_english_mixed'],
        answerability: 'PARTIALLY_SUPPORTED',
        expected: [
          { unit: 'P01-E01', relation: 'DIRECT_SUPPORT', aspect: 'campaign' },
          { unit: 'P04-E01', relation: 'CONTRADICTS', aspect: 'experiment' },
        ],
      },
    ],
  },
  {
    userBundleId: 'SV3-LF-U104',
    split: 'CALIBRATION',
    professionGroup: 'FRONTEND_MOBILE',
    profession: 'mobile product engineer',
    language: 'EN',
    templateFamilyId: 'SV3-LF-TEMPLATE-MOBILE-104',
    generatorSeedId: 'SV3-LF-SEED-MOBILE-104-B9F0',
    document: {
      title: 'Synthetic Mobile Delivery and Accessibility Portfolio',
      documentType: 'PORTFOLIO',
      documentStructure: 'LONG_PORTFOLIO',
      fileName: 'sv3-lf-u104-mobile-portfolio-v01.txt',
    },
    sections: [
      {
        id: 'P01',
        title: 'Offline checkout release',
        blocks: [
          'Field staff used the checkout flow in buildings with unreliable connectivity. Earlier clients retried every request independently, which could show a success message before the server confirmed the order. I mapped the local states, server acknowledgements, and recovery paths before changing the interface.',
          'I shipped an offline-safe checkout queue on iOS and Android and reduced duplicate-order crash sessions by 37% over the following eight weeks.',
          '- Pending orders stayed visible until the server acknowledgement arrived.\n- A retry reused the same operation identifier instead of creating a new order.\n- The UI distinguished a queued action from a completed purchase.\n- Support diagnostics recorded state transitions without including customer-entered notes.',
          'The first release was limited to one workflow and had a remote disable switch. During rollout I reviewed failed acknowledgements with backend and support owners, then expanded only after the reconciliation report matched the order ledger. The evidence does not claim that every offline interaction was solved.',
          'A later operating review showed that users sometimes closed the app while an order was pending. I added a resume notice and a recovery test for that path. The change was documented as a follow-up release rather than silently folded into the original result.',
        ],
        facts: [
          {
            id: 'E01',
            anchor: 'I shipped an offline-safe checkout queue on iOS and Android and reduced duplicate-order crash sessions by 37% over the following eight weeks.',
            actor: 'SELF',
            completionState: 'PRODUCTION',
            aspects: ['offline_checkout'],
            numerics: [
              { normalizedValue: 37, unit: 'PERCENT', semanticType: 'CRASH_SESSION_REDUCTION', sourceSurface: '37%' },
            ],
          },
        ],
      },
      {
        id: 'P02',
        title: 'Accessibility audit and remediation',
        blocks: [
          'The account workflow had labels that visually looked complete but did not expose a consistent accessible name. I paired automated checks with keyboard and screen-reader walkthroughs because either method alone missed important states.',
          'I completed screen-reader and keyboard remediation for the account workflow, reducing verified accessibility audit findings from 12 to 3.',
          'The remediation covered focus order, error announcement, button names, and the return path from a modal. I wrote acceptance examples using observable behavior rather than a particular assistive-technology vendor, then checked the examples on both mobile platforms.',
          'Three findings remained: a third-party date picker, a complex chart description, and an operating-system focus defect. They stayed visible in the release note with owners and workarounds; the reduction metric does not imply zero accessibility issues.',
          'A design review also changed component guidance so that new forms inherited the tested label and error patterns. I did not count documentation changes as remediated findings until the product screen passed the same checks.',
        ],
        facts: [
          {
            id: 'E01',
            anchor: 'I completed screen-reader and keyboard remediation for the account workflow, reducing verified accessibility audit findings from 12 to 3.',
            actor: 'SELF',
            completionState: 'PRODUCTION',
            aspects: ['accessibility_remediation'],
            numerics: [
              { normalizedValue: 3, unit: 'FINDING_COUNT', semanticType: 'REMAINING_FINDINGS', sourceSurface: '3' },
            ],
          },
        ],
      },
      {
        id: 'P03',
        title: 'Voice navigation experiment',
        blocks: [
          'A research session explored whether voice commands could help hands-busy users move through a checklist. I created a narrow prototype with scripted intents and local sample data. The prototype was evaluated in moderated sessions and was never connected to a production account.',
          'Voice navigation remained a moderated prototype and was not released in either mobile application.',
          'Participants liked a short confirmation but struggled when commands contained two actions. The finding changed the research question, not the shipped product. Any future work would need privacy review, recovery for misunderstood commands, and clear visual parity.',
          'The prototype code was archived after the session so it could not be mistaken for an enabled feature. Screenshots in the research note carry a prototype label and the release matrix lists the capability as unavailable.',
        ],
        facts: [
          {
            id: 'E01',
            anchor: 'Voice navigation remained a moderated prototype and was not released in either mobile application.',
            actor: 'SELF',
            completionState: 'PROTOTYPE',
            aspects: ['voice_not_shipped'],
          },
        ],
      },
      {
        id: 'P04',
        title: 'Component migration record',
        blocks: [
          'Component | Platform | State\nCheckout status banner | iOS and Android | production\nAccessible account form | iOS and Android | production\nVoice command control | research build | prototype',
          'The migration table summarizes state but does not merge the prototype with production components. Each row points back to its own source section and release boundary.',
          'During migration I removed duplicated local styles only after visual and accessibility checks passed. Shared code was not treated as a goal by itself; platform-specific behavior remained where it produced clearer focus or navigation.',
          'This portfolio includes several similar words such as release, workflow, test, and mobile. A useful retrieval method must still return the exact source state instead of treating every experiment as completed production work.',
        ],
        facts: [],
      },
    ],
    queries: [
      {
        id: 'Q01',
        text: 'What evidence shows resilient mobile checkout under unreliable connectivity?',
        language: 'EN',
        categories: ['semantic_paraphrase', 'abstract_competency', 'english'],
        answerability: 'SUPPORTED',
        expected: [{ unit: 'P01-E01', relation: 'DIRECT_SUPPORT', aspect: 'offline' }],
      },
      {
        id: 'Q02',
        text: 'Were verified accessibility findings reduced to five or fewer?',
        language: 'EN',
        categories: ['numeric_quantity', 'english'],
        answerability: 'SUPPORTED',
        expected: [{ unit: 'P02-E01', relation: 'DIRECT_SUPPORT', aspect: 'accessibility' }],
      },
      {
        id: 'Q03',
        text: 'Was voice navigation released to production on mobile?',
        language: 'EN',
        categories: ['completion_state', 'negation', 'hard_negative', 'english'],
        answerability: 'NOT_SUPPORTED',
        expected: [{ unit: 'P03-E01', relation: 'CONTRADICTS', aspect: 'voice' }],
      },
      {
        id: 'Q04',
        text: 'Show both shipped offline reliability and completed accessibility remediation.',
        language: 'EN',
        categories: ['multi_evidence', 'job_requirement', 'english'],
        answerability: 'SUPPORTED',
        expected: [
          { unit: 'P01-E01', relation: 'DIRECT_SUPPORT', aspect: 'offline' },
          { unit: 'P02-E01', relation: 'DIRECT_SUPPORT', aspect: 'accessibility' },
        ],
      },
    ],
  },
  {
    userBundleId: 'SV3-LF-U105',
    split: 'CALIBRATION',
    professionGroup: 'PLANNING',
    profession: 'service planning manager',
    language: 'KO',
    templateFamilyId: 'SV3-LF-TEMPLATE-PLANNING-105',
    generatorSeedId: 'SV3-LF-SEED-PLANNING-105-E2A6',
    document: {
      title: '합성 서비스 운영 정책 기획 경력기술서',
      documentType: 'CAREER_REVIEW',
      documentStructure: 'NARRATIVE_SELF_INTRODUCTION',
      fileName: 'sv3-lf-u105-planning-narrative-v01.txt',
    },
    sections: [
      {
        id: 'P01',
        title: '문의 분류 정책 적용',
        blocks: [
          '고객 문의를 처리하는 여러 조직이 같은 표현을 다르게 해석해 우선순위가 자주 바뀌었다. 나는 기존 분류표를 새 이름으로 바꾸는 것보다 어떤 관찰 사실이 어떤 대응으로 이어지는지 먼저 정리했다. 상담 기록, 환불 처리, 운영 장애를 표본으로 읽고 분류가 겹치는 조건을 표시했다.',
          '본인은 2025년 1분기에 480건의 문의 표본을 검토해 공통 분류 정책을 적용하고 운영 회의의 우선순위 기준으로 사용했다.',
          '정책은 긴급도, 고객 영향, 복구 가능성, 담당 조직을 서로 다른 축으로 유지했다. 한 문의가 여러 축을 가질 수 있도록 해 단일 label이 원인을 숨기지 않게 했고, 상담사가 판단을 보류할 수 있는 escalation 항목도 남겼다.',
          '적용 첫 달에는 기존 분류와 새 분류를 함께 기록했다. 나는 불일치 사례를 매주 검토해 정의가 모호한 부분을 고쳤지만, 지표를 좋아 보이게 만들기 위해 과거 분류를 소급 변경하지 않았다. 분기 보고서에는 기준 변경일과 비교할 수 없는 구간을 표시했다.',
          '분류 정책은 회의 자료에만 머물지 않았다. 운영 담당자가 새로운 문의를 등록할 때 선택 가능한 값과 설명을 갱신했고, 담당 조직이 바뀌면 기록이 사라지지 않도록 handoff note를 남기게 했다.',
        ],
        facts: [
          {
            id: 'E01',
            anchor: '본인은 2025년 1분기에 480건의 문의 표본을 검토해 공통 분류 정책을 적용하고 운영 회의의 우선순위 기준으로 사용했다.',
            actor: 'SELF',
            completionState: 'PRODUCTION',
            aspects: ['classification_policy'],
            numerics: [
              { normalizedValue: 480, unit: 'CASE_COUNT', semanticType: 'REVIEW_SAMPLE_COUNT', sourceSurface: '480건' },
            ],
          },
        ],
      },
      {
        id: 'P02',
        title: '부서 간 우선순위 워크숍',
        blocks: [
          '정책만 배포하면 각 조직이 자기 사례를 예외로 둘 가능성이 있었다. 나는 실제 사례를 익명화한 카드와 제한된 자원 시나리오를 준비해 참가자가 같은 기준으로 순서를 정하도록 했다. 답을 맞히는 교육이 아니라 판단 근거의 차이를 드러내는 워크숍이었다.',
          '9개 부서가 참여한 우선순위 워크숍을 운영하고, 합의되지 않은 사례 14건을 후속 정책 검토 목록으로 전환했다.',
          '- 참가자는 첫 판단과 근거를 개별로 기록했다.\n- 소그룹은 고객 영향과 복구 가능성을 분리해 다시 정렬했다.\n- 전체 토론은 다수결보다 합의되지 않은 조건을 남기는 데 집중했다.\n- 회고에서는 진행자의 설명이 특정 답을 유도한 사례도 기록했다.',
          '워크숍 뒤에는 높은 순위를 받은 모든 요청을 즉시 실행하지 않았다. 비용, 법적 검토, 다른 제품 일정과의 관계를 담당자가 확인하도록 했고, 기획 문서에는 결정과 보류 사유를 함께 남겼다. 이 방식은 우선순위가 바뀌었을 때 이전 판단을 숨기지 않게 했다.',
        ],
        facts: [
          {
            id: 'E01',
            anchor: '9개 부서가 참여한 우선순위 워크숍을 운영하고, 합의되지 않은 사례 14건을 후속 정책 검토 목록으로 전환했다.',
            actor: 'SELF',
            completionState: 'COMPLETED',
            aspects: ['cross_functional_prioritization'],
            numerics: [
              { normalizedValue: 9, unit: 'DEPARTMENT_COUNT', semanticType: 'PARTICIPATING_GROUP_COUNT', sourceSurface: '9개 부서' },
            ],
          },
        ],
      },
      {
        id: 'P03',
        title: '해외 운영 확대 제안',
        blocks: [
          '해외 운영 확대를 검토하면서 언어, 시간대, 환불 정책, 데이터 보관 위치를 조사했다. 제안서에는 단계별 선택지와 중단 조건을 넣었고 기존 국내 운영의 숫자를 새로운 지역의 예상 성과로 복사하지 않았다.',
          '해외 법인 연계 운영은 2026년 하반기 계획 단계이며 아직 승인, 개시, production 전환이 이루어지지 않았다.',
          '계획을 준비하는 동안 법무와 재무 담당자에게 검토 질문을 전달했지만 그 활동은 서비스 개시가 아니다. 파트너 후보와의 초기 대화도 계약 체결이나 운영 실적으로 기록하지 않는다.',
          '향후 승인된다면 작은 지역에서 support hour와 환불 흐름을 먼저 검증하고, 기준을 바꾼 경우 새 baseline을 만들 예정이다. 현재 문서는 실행 전 가정과 필요한 의사결정을 남기는 역할만 한다.',
        ],
        facts: [
          {
            id: 'E01',
            anchor: '해외 법인 연계 운영은 2026년 하반기 계획 단계이며 아직 승인, 개시, production 전환이 이루어지지 않았다.',
            actor: 'SELF',
            completionState: 'PLANNED',
            aspects: ['international_plan'],
          },
        ],
      },
      {
        id: 'P04',
        title: '교육 운영의 actor 경계',
        blocks: [
          '새 분류 정책을 설명하는 자료는 내가 작성했지만 전사 교육 일정과 참석 관리는 인재개발팀이 담당했다. 두 활동은 같은 정책 rollout에 포함되지만 담당 actor와 직접 성과가 다르다.',
          '인재개발팀은 전사 교육 12회를 진행했고, 본인은 정책 사례와 질의응답 문서를 제공했지만 교육 세션을 직접 진행하지 않았다.',
          '나는 교육 뒤 수집된 질문을 분류해 정책 문서의 모호한 예시를 보완했다. 교육 횟수는 인재개발팀의 실행 결과이고, 내가 직접 수행한 evidence는 자료 작성과 질문 분석이다.',
          '이 경력기술서는 여러 부서가 참여한 활동을 한 사람의 성과로 합치지 않는다. 검색 결과도 actor가 다른 문장을 직접 근거로 잘못 반환하지 않아야 한다.',
        ],
        facts: [
          {
            id: 'E01',
            anchor: '인재개발팀은 전사 교육 12회를 진행했고, 본인은 정책 사례와 질의응답 문서를 제공했지만 교육 세션을 직접 진행하지 않았다.',
            actor: 'OTHER',
            completionState: 'COMPLETED',
            aspects: ['other_actor_training'],
            numerics: [
              { normalizedValue: 12, unit: 'SESSION_COUNT', semanticType: 'TRAINING_SESSION_COUNT', sourceSurface: '12회' },
            ],
          },
        ],
      },
    ],
    queries: [
      {
        id: 'Q01',
        text: '문의 표본 400건 이상을 검토해 실제 정책에 반영했나요?',
        language: 'KO',
        categories: ['numeric_quantity', 'completed_production', 'korean'],
        answerability: 'SUPPORTED',
        expected: [{ unit: 'P01-E01', relation: 'DIRECT_SUPPORT', aspect: 'policy' }],
      },
      {
        id: 'Q02',
        text: '여러 부서의 판단 차이를 구조화해 우선순위를 조정한 경험이 있나요?',
        language: 'KO',
        categories: ['semantic_paraphrase', 'abstract_competency', 'korean'],
        answerability: 'SUPPORTED',
        expected: [{ unit: 'P02-E01', relation: 'DIRECT_SUPPORT', aspect: 'workshop' }],
      },
      {
        id: 'Q03',
        text: '해외 법인 연계 운영을 이미 production으로 전환했나요?',
        language: 'KO_EN_MIXED',
        categories: ['completion_state', 'negation', 'hard_negative', 'korean_english_mixed'],
        answerability: 'NOT_SUPPORTED',
        expected: [{ unit: 'P03-E01', relation: 'CONTRADICTS', aspect: 'international' }],
      },
      {
        id: 'Q04',
        text: '본인이 전사 교육 12회를 직접 진행했나요?',
        language: 'KO',
        categories: ['other_actor', 'hard_negative', 'numeric_quantity', 'korean'],
        answerability: 'NOT_SUPPORTED',
        expected: [{ unit: 'P04-E01', relation: 'CONTRADICTS', aspect: 'actor' }],
      },
    ],
  },
  {
    userBundleId: 'SV3-LF-U106',
    split: 'CALIBRATION',
    professionGroup: 'NON_DEVELOPMENT_GENERAL',
    profession: 'multi-site operations coordinator',
    language: 'KO_EN_MIXED',
    templateFamilyId: 'SV3-LF-TEMPLATE-OPERATIONS-106',
    generatorSeedId: 'SV3-LF-SEED-OPERATIONS-106-F1D4',
    document: {
      title: 'Synthetic Multi-site Operations Career Description',
      documentType: 'CAREER_REVIEW',
      documentStructure: 'CAREER_DESCRIPTION',
      fileName: 'sv3-lf-u106-operations-v01.txt',
    },
    sections: [
      {
        id: 'P01',
        title: 'Shift handoff across three sites',
        blocks: [
          '세 개 운영 현장은 같은 고객 요청을 서로 다른 spreadsheet와 구두 메모로 넘겼다. 교대가 바뀌면 완료 여부와 다음 행동을 다시 확인해야 했고, 긴급하지 않은 요청이 여러 번 미뤄졌다. 나는 각 현장의 실제 handoff를 관찰하고 공통으로 필요한 상태를 정리했다.',
          '본인은 3개 현장에 공통 shift handoff checklist를 적용해 인수인계 누락 작업을 12주 동안 27% 줄였다.',
          '- 요청의 현재 상태와 다음 행동을 분리했다.\n- 완료라고 표시하려면 확인한 사람과 시간을 남기게 했다.\n- 다음 교대가 판단해야 하는 항목은 질문 형태로 전달했다.\n- 고객의 민감한 내용은 checklist에 복사하지 않고 권한 있는 원문 위치만 연결했다.',
          '도입 첫 주에는 작성 시간이 늘었다는 의견이 있었다. 나는 모든 메모를 요구하지 않고 교대 뒤 다시 물어본 항목을 분석해 필수 field를 줄였다. 주간 review에서는 누락률과 함께 checklist 사용 시간을 살펴 비용을 숨기지 않았다.',
          '현장별 예외는 별도 note로 유지했지만 공통 상태 이름은 바꾸지 않았다. 덕분에 다른 현장을 지원하는 직원도 완료, 대기, 확인 필요를 같은 의미로 읽을 수 있었다.',
        ],
        facts: [
          {
            id: 'E01',
            anchor: '본인은 3개 현장에 공통 shift handoff checklist를 적용해 인수인계 누락 작업을 12주 동안 27% 줄였다.',
            actor: 'SELF',
            completionState: 'PRODUCTION',
            aspects: ['shift_handoff'],
            numerics: [
              { normalizedValue: 27, unit: 'PERCENT', semanticType: 'MISSED_TASK_REDUCTION', sourceSurface: '27%' },
            ],
          },
        ],
      },
      {
        id: 'P02',
        title: 'Emergency response training',
        blocks: [
          '운영 업무에는 경미한 부상, 시설 경보, 대피 안내가 포함됐다. 나는 현장 책임자의 절차를 대신 만들지 않고 실제 상황에서 누구에게 연락하고 어떤 구역을 비워야 하는지 학습했다.',
          'Emergency response fundamentals — completed in 2024-09.',
          '교육은 이론 설명, 역할별 연락, 대피 경로 확인, 모의 상황 회고로 구성됐다. 수료 뒤에는 현장별 연락처와 집결지 변경을 확인하는 월간 점검에 참여했다.',
          '수료 기록은 전문 의료 자격이나 소방 지휘 권한을 뜻하지 않는다. 문서에는 실제 교육 범위와 완료 날짜만 남기며 확인하지 않은 능력을 확대하지 않는다.',
        ],
        facts: [
          {
            id: 'E01',
            anchor: 'Emergency response fundamentals — completed in 2024-09.',
            actor: 'SELF',
            completionState: 'COMPLETED',
            aspects: ['emergency_training'],
          },
        ],
      },
      {
        id: 'P03',
        title: 'Equipment qualification boundary',
        blocks: [
          '일부 현장에는 지게차와 적재 장비가 있었지만 내 역할은 작업 순서와 요청 상태를 조정하는 것이었다. 장비 운전은 별도의 승인과 교육을 받은 담당자가 수행했다.',
          'Forklift operator certificate: not held.',
          '장비 작업이 필요한 요청은 자격이 있는 담당자에게 전달했고 완료 확인을 handoff 기록에 연결했다. 내가 일정 조정에 참여했다는 이유로 장비 운전 경험이나 자격을 보유한 것으로 표시하지 않는다.',
          '안전 관련 부정 근거는 단순히 검색에서 사라지면 안 된다. 자격 보유 질문에는 관련 장비가 등장하는 문장보다 명시적인 미보유 상태가 올바른 판단 근거다.',
        ],
        facts: [
          {
            id: 'E01',
            anchor: 'Forklift operator certificate: not held.',
            actor: 'SELF',
            completionState: 'NOT_APPLICABLE',
            aspects: ['forklift_not_held'],
          },
        ],
      },
      {
        id: 'P04',
        title: 'Warehouse automation actor boundary',
        blocks: [
          '창고 동선 변경 프로젝트에서 vendor는 barcode automation을 설계하고 설치했다. 나는 운영 중단 시간을 조정하고 현장 직원이 변경된 절차를 이해할 수 있도록 작업 순서를 문서화했다.',
          'The automation vendor built the barcode routing system; I documented the operating SOP and did not develop the routing software.',
          '설치 뒤에는 barcode를 읽지 못한 물품, 수동 처리, 재작업 요청을 별도 상태로 기록했다. vendor의 결함 수정과 내 운영 문서 변경은 같은 issue에서 시작했더라도 서로 다른 actor의 작업으로 남겼다.',
          '프로젝트 결과 보고서는 처리 흐름과 예외를 설명하지만 software 개발 경험을 주장하지 않는다. 운영 evidence는 SOP 작성, handoff, 안전한 escalation에 한정한다.',
        ],
        facts: [
          {
            id: 'E01',
            anchor: 'The automation vendor built the barcode routing system; I documented the operating SOP and did not develop the routing software.',
            actor: 'OTHER',
            completionState: 'COMPLETED',
            aspects: ['vendor_automation'],
          },
        ],
      },
    ],
    queries: [
      {
        id: 'Q01',
        text: '여러 현장의 인수인계 누락을 20% 이상 줄인 경험이 있나요?',
        language: 'KO',
        categories: ['numeric_quantity', 'semantic_paraphrase', 'korean'],
        answerability: 'SUPPORTED',
        expected: [{ unit: 'P01-E01', relation: 'DIRECT_SUPPORT', aspect: 'handoff' }],
      },
      {
        id: 'Q02',
        text: 'Did the candidate complete emergency response training?',
        language: 'EN',
        categories: ['completion_state', 'english'],
        answerability: 'SUPPORTED',
        expected: [{ unit: 'P02-E01', relation: 'DIRECT_SUPPORT', aspect: 'training' }],
      },
      {
        id: 'Q03',
        text: '지게차 운전 자격증을 보유하고 있나요?',
        language: 'KO',
        categories: ['negation', 'hard_negative', 'korean'],
        answerability: 'NOT_SUPPORTED',
        expected: [{ unit: 'P03-E01', relation: 'CONTRADICTS', aspect: 'certificate' }],
      },
      {
        id: 'Q04',
        text: 'Did this person develop the warehouse barcode routing software?',
        language: 'EN',
        categories: ['other_actor', 'hard_negative', 'english'],
        answerability: 'NOT_SUPPORTED',
        expected: [{ unit: 'P04-E01', relation: 'CONTRADICTS', aspect: 'actor' }],
      },
    ],
  },
]

function sha256(value) {
  return createHash('sha256').update(value).digest('hex')
}

function stableJson(value) {
  return `${JSON.stringify(value, null, 2)}\n`
}

function normalizeQuery(value) {
  return value
    .normalize('NFKC')
    .toLowerCase()
    .replace(/[^\p{L}\p{N}]+/gu, ' ')
    .trim()
}

function codePointOffset(value, utf16Offset) {
  return Array.from(value.slice(0, utf16Offset)).length
}

function lineAt(value, utf16Offset) {
  return value.slice(0, utf16Offset).split('\n').length
}

function sourceSpan(bundle, document, id, start, end, parentId = null) {
  const text = document.source.slice(start, end)
  return {
    spanId: id,
    ...(parentId ? { parentId } : {}),
    documentId: document.documentId,
    versionId: document.versionId,
    sourcePath: `documents/${bundle.document.fileName}`,
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

function materializeDocument(bundle) {
  const renderedSections = bundle.sections.map((section) => ({
    section,
    text: `${section.title}\n\n${section.blocks.join('\n\n')}`,
  }))
  const source = `${bundle.document.title}\n\n${renderedSections.map((value) => value.text).join('\n\n')}\n`
  const documentId = `${bundle.userBundleId}-D01`
  const versionId = `${documentId}-V01`
  let cursor = bundle.document.title.length + 2
  const sections = renderedSections.map(({ section, text }) => {
    const start = source.indexOf(text, cursor)
    if (start < 0) throw new Error(`Cannot locate section ${bundle.userBundleId}-${section.id}`)
    const end = start + text.length
    cursor = end
    return { ...section, text, start, end }
  })
  return { documentId, versionId, source, sections }
}

function materializeBundle(bundle) {
  const document = materializeDocument(bundle)
  const parents = []
  const evidenceGroups = []
  const evidenceUnits = []
  const unitIds = new Map()

  for (const section of document.sections) {
    const parentId = `${bundle.userBundleId}-${section.id}`
    parents.push({
      parentId,
      userBundleId: bundle.userBundleId,
      documentId: document.documentId,
      versionId: document.versionId,
      label: section.title,
      sourceSpan: sourceSpan(
        bundle,
        document,
        `${parentId}-PS01`,
        section.start,
        section.end,
        parentId,
      ),
    })

    for (const fact of section.facts) {
      const first = document.source.indexOf(fact.anchor)
      const last = document.source.lastIndexOf(fact.anchor)
      if (first < 0 || first !== last || first < section.start || first >= section.end) {
        throw new Error(`Fact anchor must be unique inside its parent: ${bundle.userBundleId}-${section.id}-${fact.id}`)
      }
      const unitId = `${bundle.userBundleId}-${section.id}-${fact.id}`
      const groupId = `${bundle.userBundleId}-G-${section.id}-${fact.id}`
      const sourceFactId = `SV3-LF-FACT-${bundle.userBundleId.slice(7)}-${section.id}-${fact.id}`
      unitIds.set(`${section.id}-${fact.id}`, unitId)
      evidenceGroups.push({
        groupId,
        userBundleId: bundle.userBundleId,
        sourceFactId,
        description: fact.aspects.join(', '),
        evidenceUnitIds: [unitId],
      })
      evidenceUnits.push({
        evidenceUnitId: unitId,
        userBundleId: bundle.userBundleId,
        parentId,
        groupId,
        documentId: document.documentId,
        versionId: document.versionId,
        sourceFactId,
        sourceSpans: [sourceSpan(
          bundle,
          document,
          `${unitId}-S01`,
          first,
          first + fact.anchor.length,
          parentId,
        )],
        primarySpanId: `${unitId}-S01`,
        contextSpanIds: [],
        actor: fact.actor,
        completionState: fact.completionState,
        aspects: fact.aspects,
        entities: fact.entities ?? [],
        numerics: fact.numerics ?? [],
        dates: fact.dates ?? [],
      })
    }
  }

  const queries = bundle.queries.map((query) => ({
    queryId: `${bundle.userBundleId}-${query.id}`,
    userBundleId: bundle.userBundleId,
    questionGroupId: `${bundle.userBundleId}-${query.id}-FAMILY`,
    query: query.text,
    normalizedQuery: normalizeQuery(query.text),
    categories: query.categories,
    language: query.language,
    answerability: query.answerability,
    aspectExpression: {
      operator: query.expected.length > 1 ? 'ALL' : 'ANY',
      requiredAspectIds: query.expected.map((value) => value.aspect),
      minShouldMatch: query.expected.length,
    },
    aspects: query.expected.map((expected) => {
      const evidenceUnitId = unitIds.get(expected.unit)
      if (!evidenceUnitId) throw new Error(`Missing query unit ${bundle.userBundleId}-${expected.unit}`)
      return {
        aspectId: expected.aspect,
        required: true,
        answerability: expected.relation === 'DIRECT_SUPPORT' ? 'SUPPORTED' : 'NOT_SUPPORTED',
        expectedEvidence: [{ evidenceUnitId, supportRelation: expected.relation }],
        requiredEvidenceGroupIds: expected.relation === 'DIRECT_SUPPORT'
          ? [evidenceUnits.find((unit) => unit.evidenceUnitId === evidenceUnitId).groupId]
          : [],
        minEvidenceGroups: expected.relation === 'DIRECT_SUPPORT' ? 1 : 0,
        constraints: {
          entities: [],
          numerics: [],
          dates: [],
          actors: [],
          completionStates: [],
          polarity: 'POSITIVE',
        },
      }
    }),
    safetyExclusions: [],
  }))

  return { bundle, document, parents, evidenceGroups, evidenceUnits, queries }
}

function countBy(values, selector) {
  const result = {}
  for (const value of values) {
    const key = selector(value)
    result[key] = (result[key] ?? 0) + 1
  }
  return Object.fromEntries(Object.entries(result).sort(([left], [right]) => left.localeCompare(right)))
}

function buildArtifacts() {
  const materialized = bundles.map(materializeBundle)
  const lengths = materialized.map((value) => Array.from(value.document.source).length)
  if (lengths.some((length) => length < 1500)) {
    throw new Error(`Every long-form document must be at least 1,500 code points: ${lengths.join(', ')}`)
  }
  if (lengths.filter((length) => length >= 3000).length < 2) {
    throw new Error(`At least two documents must be 3,000+ code points: ${lengths.join(', ')}`)
  }
  if (bundles.filter((bundle) => ['BACKEND', 'FRONTEND_MOBILE', 'DATA_AI_INFRA'].includes(bundle.professionGroup)).length > bundles.length / 2) {
    throw new Error('Development profession documents must not exceed half of the expansion')
  }

  const files = new Map()
  const splitHashes = {}
  for (const split of ['DEV', 'CALIBRATION']) {
    const splitDirectory = split === 'DEV' ? 'dev' : 'calibration'
    const values = materialized.filter((value) => value.bundle.split === split)
    const userBundles = values.map(({ bundle, document }) => ({
      userBundleId: bundle.userBundleId,
      split,
      professionGroup: bundle.professionGroup,
      profession: bundle.profession,
      languageProfile: bundle.language,
      documentFamilyId: `${bundle.userBundleId}-DOC-FAMILY`,
      templateFamilyId: bundle.templateFamilyId,
      documents: [{
        documentId: document.documentId,
        logicalDocumentId: `${document.documentId}-LOGICAL`,
        versionLineageId: `${document.documentId}-LINEAGE`,
        versionId: document.versionId,
        versionNumber: 1,
        active: true,
        title: bundle.document.title,
        documentType: bundle.document.documentType,
        documentStructure: bundle.document.documentStructure,
        fileType: 'TXT',
        language: bundle.language,
        contentPath: `documents/${bundle.document.fileName}`,
        contentSha256: sha256(document.source),
        supportScope: 'SUPPORTED_BY_CURRENT',
        visibility: 'OWNER_ONLY',
        provenance: {
          classification: 'SYNTHETIC',
          license: 'Apache-2.0',
          generatorName: 'prizm-prz026-long-form-materializer',
          generatorRevision: '1.1.0',
          generatorSeedId: bundle.generatorSeedId,
        },
      }],
    }))
    const corpus = {
      artifactType: 'CORPUS',
      schemaVersion: SCHEMA_VERSION,
      datasetVersion: DATASET_VERSION,
      split,
      userBundles,
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
      files.set(`${splitDirectory}/documents/${value.bundle.document.fileName}`, value.document.source)
    }
    files.set(`${splitDirectory}/corpus.json`, stableJson(corpus))
    files.set(`${splitDirectory}/questions.json`, stableJson(questions))
    files.set(`${splitDirectory}/gold-evidence.json`, stableJson(gold))

    const splitPaths = [...files.keys()]
      .filter((file) => file.startsWith(`${splitDirectory}/`))
      .sort()
    const splitFileEntries = splitPaths.map((file) => ({
      path: file.slice(splitDirectory.length + 1),
      bytes: Buffer.byteLength(files.get(file)),
      sha256: sha256(files.get(file)),
    }))
    const combinedSha256 = sha256(splitFileEntries.map((entry) => `${entry.path}:${entry.sha256}`).join('\n'))
    splitHashes[split] = combinedSha256
    files.set(`${splitDirectory}/manifest.json`, stableJson({
      artifactType: 'MANIFEST',
      schemaVersion: SCHEMA_VERSION,
      datasetVersion: DATASET_VERSION,
      split,
      status: 'DEV_CAL_LONG_FORM_FROZEN',
      mutable: false,
      frozenAt: FROZEN_AT,
      previousVersion: PREVIOUS_VERSION,
      materializationBaseRevision: MATERIALIZATION_BASE_REVISION,
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
        queryLanguage: countBy(questions.queries, (query) => query.language),
        answerability: countBy(questions.queries, (query) => query.answerability),
        category: countBy(questions.queries.flatMap((query) => query.categories), (category) => category),
      },
      files: splitFileEntries,
      combinedSha256,
    }))
  }

  const lineage = {
    artifactType: 'LINEAGE',
    schemaVersion: SCHEMA_VERSION,
    datasetVersion: DATASET_VERSION,
    previousVersion: PREVIOUS_VERSION,
    changeReason: 'Remove the short-document ceiling for PRZ-026 Structural Child evaluation without changing SEALED_FINAL_TEST.',
    sealedFinalPolicy: 'PRZ-025 search-v3-fresh-seed-1.0.1 SEALED_FINAL_TEST remains byte-identical and unopened.',
    generator: 'scripts/evaluation/search-v3/materialize-prz026-devcal.mjs',
    materializationBaseRevision: MATERIALIZATION_BASE_REVISION,
    bundles: materialized.map(({ bundle, document, evidenceUnits, queries }) => ({
      userBundleId: bundle.userBundleId,
      split: bundle.split,
      documentFamilyId: `${bundle.userBundleId}-DOC-FAMILY`,
      templateFamilyId: bundle.templateFamilyId,
      generatorSeedId: bundle.generatorSeedId,
      logicalDocumentIds: [`${document.documentId}-LOGICAL`],
      versionLineageIds: [`${document.documentId}-LINEAGE`],
      sourceFactIds: evidenceUnits.map((unit) => unit.sourceFactId),
      sourceFactSignatures: evidenceUnits.map((unit) =>
        sha256(normalizedSignatureText(unit.sourceSpans[0].text))),
      questionGroupIds: queries.map((query) => query.questionGroupId),
      normalizedQueries: queries.map((query) => query.normalizedQuery),
    })),
  }
  files.set('lineage.json', stableJson(lineage))
  files.set('README.md', `# PRZ-026 DEV/CAL Long-form Expansion\n\n- dataset: \`${DATASET_VERSION}\`\n- previous seed: \`${PREVIOUS_VERSION}\`\n- scope: synthetic DEV/CAL only; no personal data\n- SEALED FINAL: not copied, not opened, not searched\n- generator: \`scripts/evaluation/search-v3/materialize-prz026-devcal.mjs\`\n- PDF: \`BLOCKED_FOR_LATER_LAYOUT_PHASE\`; page-local gold/runtime model is not changed in this ablation\n\nRun \`node scripts/evaluation/search-v3/materialize-prz026-devcal.mjs --check\` to verify byte-for-byte materialization.\n`)

  const manifestInputs = [...files.entries()]
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([file, content]) => ({ path: file, bytes: Buffer.byteLength(content), sha256: sha256(content) }))
  const combinedSha256 = sha256(manifestInputs.map((entry) => `${entry.path}:${entry.sha256}`).join('\n'))
  files.set('manifest.json', stableJson({
    artifactType: 'MANIFEST',
    schemaVersion: SCHEMA_VERSION,
    datasetVersion: DATASET_VERSION,
    split: 'DEV_CAL_ONLY',
    status: 'DEV_CAL_LONG_FORM_FROZEN',
    mutable: false,
    opened: true,
    executionPolicy: 'DEV_CAL_EVALUATION_ALLOWED',
    executionStateRecord: 'ignored benchmark report plus specs/PRZ-026-structural-parsing-parent-child/evidence.md',
    frozenAt: FROZEN_AT,
    previousVersion: PREVIOUS_VERSION,
    materializationBaseRevision: MATERIALIZATION_BASE_REVISION,
    splitCombinedSha256: splitHashes,
    counts: {
      userBundles: materialized.length,
      documents: materialized.length,
      queries: materialized.flatMap((value) => value.queries).length,
      evidenceParents: materialized.flatMap((value) => value.parents).length,
      evidenceGroups: materialized.flatMap((value) => value.evidenceGroups).length,
      evidenceUnits: materialized.flatMap((value) => value.evidenceUnits).length,
    },
    distributions: {
      split: countBy(materialized, (value) => value.bundle.split),
      profession: countBy(materialized, (value) => value.bundle.professionGroup),
      documentLanguage: countBy(materialized, (value) => value.bundle.language),
      documentCodePointLength: Object.fromEntries(materialized.map((value) => [
        value.document.documentId,
        Array.from(value.document.source).length,
      ])),
      queryLanguage: countBy(materialized.flatMap((value) => value.queries), (query) => query.language),
      answerability: countBy(materialized.flatMap((value) => value.queries), (query) => query.answerability),
      category: countBy(materialized.flatMap((value) => value.queries).flatMap((query) => query.categories), (category) => category),
    },
    files: manifestInputs,
    combinedSha256,
  }))
  return files
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
  const findings = []
  for (const [relative, expected] of files) {
    const actual = await readFile(path.join(OUTPUT_ROOT, relative), 'utf8').catch(() => null)
    if (actual !== expected) findings.push(`content mismatch: ${relative}`)
  }
  const actualFiles = await listFiles(OUTPUT_ROOT)
  const expectedFiles = [...files.keys()].sort()
  if (JSON.stringify(actualFiles) !== JSON.stringify(expectedFiles)) {
    findings.push('file inventory mismatch')
  }
  const lineage = JSON.parse(files.get('lineage.json'))
  for (const field of [
    'userBundleId',
    'documentFamilyId',
    'templateFamilyId',
    'generatorSeedId',
  ]) {
    const values = lineage.bundles.map((bundle) => bundle[field])
    if (new Set(values).size !== values.length) findings.push(`split lineage duplicate: ${field}`)
  }
  for (const field of [
    'logicalDocumentIds',
    'versionLineageIds',
    'sourceFactIds',
    'sourceFactSignatures',
    'questionGroupIds',
    'normalizedQueries',
  ]) {
    const values = lineage.bundles.flatMap((bundle) => bundle[field])
    if (new Set(values).size !== values.length) findings.push(`split leakage duplicate: ${field}`)
  }
  const originalLineagePath = path.resolve('src/test/resources/search-v3-evaluation/lineage.json')
  const originalLineage = JSON.parse(await readFile(originalLineagePath, 'utf8'))
  for (const field of [
    'userBundleId',
    'documentFamilyId',
    'templateFamilyId',
    'generatorSeedId',
  ]) {
    const original = new Set(originalLineage.bundles.map((bundle) => bundle[field]))
    if (lineage.bundles.some((bundle) => original.has(bundle[field]))) {
      findings.push(`original-seed lineage collision: ${field}`)
    }
  }
  for (const field of ['sourceFactIds', 'sourceFactSignatures', 'questionGroupIds', 'normalizedQueries']) {
    const original = new Set(originalLineage.bundles.flatMap((bundle) => bundle[field]))
    if (lineage.bundles.flatMap((bundle) => bundle[field]).some((value) => original.has(value))) {
      findings.push(`original-seed leakage collision: ${field}`)
    }
  }
  const sealedManifest = JSON.parse(await readFile(
    path.resolve('src/test/resources/search-v3-evaluation/sealed-final/manifest.json'),
    'utf8',
  ))
  if (sealedManifest.combinedSha256 !== 'e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383'
      || sealedManifest.opened !== false
      || sealedManifest.searchExecuted !== false) {
    findings.push('PRZ-025 SEALED FINAL metadata changed')
  }
  if (findings.length > 0) {
    for (const finding of findings) console.error(`FAIL ${finding}`)
    process.exitCode = 1
    return
  }
  const manifest = JSON.parse(files.get('manifest.json'))
  console.log(`PASS dataset=${DATASET_VERSION}`)
  console.log(`PASS files=${files.size}`)
  console.log(`PASS documents=${manifest.counts.documents}`)
  console.log(`PASS queries=${manifest.counts.queries}`)
  console.log(`PASS combinedSha256=${manifest.combinedSha256}`)
}

async function materialize(files) {
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
