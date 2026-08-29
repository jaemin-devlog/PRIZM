# PRIZM 기능 명세와 검증 기록

`specs/`는 PRIZM의 기능과 주요 변경을 작업 단위로 기록하는 곳입니다. 각 기록에는
무엇을 왜 바꾸려 했는지, 어디까지 구현하기로 했는지, 어떤 방법으로 검증했는지가
담겨 있습니다. 완료된 작업뿐 아니라 검증이 남은 연구와 채택하지 않은 결정도
지우지 않고 보존합니다.

이 문서는 제품 사용 설명서가 아닙니다. PRIZM을 처음 실행하려면
[프로젝트 README](../README.md)와 [빠른 시작](../docs/quickstart.md)을 먼저 읽어
주세요. 지금 사용할 수 있는 기능과 검증 범위는
[현재 구현 현황](../docs/project-status.md)에서 확인할 수 있습니다.

> 중요: Spec은 구현 증거가 아닙니다. 실제 구현 여부는 소스 코드, 적용된
> Flyway 마이그레이션, 실행 가능한 테스트와 필요한 환경에서 얻은 검증 결과로
> 판단합니다.

## PRZ 기록이란 무엇인가요?

PRZ&#8209;###는 하나의 기능이나 변경 범위를 식별하는 번호입니다. 번호는 기록을 찾기
위한 식별자일 뿐, 중요도나 완료 순서를 뜻하지 않습니다. 새 제품 변경을 시작하면
GitHub Issue에 문제와 사용자 영향을 기록하고, 새 PRZ의 `spec.md`에서 범위와
검증 방법을 정합니다.

Spec 목록은 2026년 7월 23일 커밋 `3233bad7`에서 처음 도입됐습니다. 그 전에
구현된 내용은 [Spec 목록 도입 전 구현 이력](000-pre-spec-implementation-history.md)에
소스 기준으로 정리했습니다. [PRZ&#8209;000](PRZ-000-platform-baseline/spec.md)은
그 구현을 뒤늦은 계획으로 꾸미지 않고 `AS_BUILT_BASELINE`으로 남긴 기준선입니다.

## 한눈에 보는 PRZ 기록과 제품 흐름

> 이 그림은 아래 Registry를 바탕으로 PRZ&#8209;022까지의 제목과 상태를 정리한
> 스냅샷입니다. 이후 새 기록이 생기거나 상태가 바뀌면 아래 목록과 각
> `spec.md`·`evidence.md`를 현재 기준으로 사용해 주세요.

[![PRIZM의 공식 변경 절차, PRZ-000부터 PRZ-022까지의 제목과 상태, 문서 업로드부터 ACTIVE 전환과 근거 검색까지의 흐름](assets/prizm-spec-registry-and-product-flow.svg)](assets/prizm-spec-registry-and-product-flow.svg)

*그림 1. PRZ 기록과 현재 제품 흐름. 이미지를 선택하면 원본 크기로 볼 수 있습니다.*

그림 위쪽의 카드는 PRZ&#8209;000부터 PRZ&#8209;022까지를 빠짐없이 보여 줍니다.
회색은 Registry 도입 전 구현 기준선인 `AS_BUILT_BASELINE`, 파란색은
`VERIFIED`, 주황색은 `IN_PROGRESS`, 빨간색은 `REJECTED`입니다. 이 색은 작업
전체의 진행 상태를 나타내며, 개별 테스트의 `PASS`·`FAIL`과는 구분해야 합니다.
그 위의 띠에는 제품 변경에 적용하는 공식
`ORIENT → SPEC → PLAN → IMPLEMENT → VERIFY → AUDIT → INTEGRATE` 절차를
표시했습니다.

아래쪽은 현재 제품의 대표 처리 흐름입니다. 사용자가 가입·로그인한 뒤 UTF-8 TXT나
텍스트가 포함된 비암호화 PDF를 올리면, PRIZM은 원본과 변경 불가능한 버전,
`PENDING ChangeLog`를 저장합니다. Dispatcher와 비동기 Worker가 원문 추출·분할과
Ollama `bge-m3` 임베딩을 처리하고, 모든 문서 조각을 저장한 버전만 `ACTIVE`로
전환합니다. 처리가 실패하면 새 버전은 검색에서 제외하고 이전 `ACTIVE` 버전을
유지합니다.

웹과 MCP 검색은 모두 로그인한 사용자가 소유한 `ACTIVE` 근거만 조회하며, 결과를
TXT 구간이나 PDF 페이지로 연결합니다. 기본 실행 환경은 PostgreSQL 16과 pgvector를
사용합니다. OpenSQL direct 연결과 OpenProxy single-Primary는 이 기본 환경과 구분해
검증한 선택적 DB 경로이며 MCP 처리의 다음 단계가 아닙니다.

## PRZ 폴더에는 무엇이 들어 있나요?

대부분의 PRZ 폴더에는 다음 네 문서가 있습니다.

- `spec.md` — 무엇을 바꿀지: 문제, 사용자 시나리오, 범위와 제외 범위,
  보존해야 할 계약, 완료 조건을 정의합니다.
- `plan.md` — 어떻게 바꿀지: 수정할 코드와 데이터 흐름, 보안·마이그레이션
  영향, 테스트 방법과 실패 시 대응을 정리합니다.
- `tasks.md` — 어떤 순서로 진행할지: 구현, 테스트, 문서화와 검증 작업을
  실제 파일 단위로 나눕니다. 진행 중 계획이 달라졌다면 그 차이도 기록합니다.
- `evidence.md` — 무엇으로 확인했는지: 기준 소스, 실행한 명령과 환경,
  성공·실패·미실행 결과, Pull Request와 병합 근거를 기록합니다.

필요한 경우 벤치마크 결과, 데이터 세트, 실패 분석이나 구현 보고서가 추가됩니다.
PRZ&#8209;000은 이미 존재하던 구현의 기준선이므로 `plan.md`와 `tasks.md`가 없습니다.

## 개발은 어떤 단계로 진행하나요?

사용자가 확인할 수 있는 동작, API, 데이터 계약, 보안, 인프라처럼 제품에 영향을
주는 변경은 다음 일곱 단계를 거칩니다. 각 단계의 통과 조건을 충족해야 다음
단계로 넘어갑니다.

용어가 낯설다면 다음 뜻만 먼저 알아두면 됩니다.

- 브랜치와 `main`: 브랜치는 변경을 분리해 작업하는 공간이고, `main`은 검토를
  마친 변경을 모으는 기본 브랜치입니다.
- 원격 기준선과 작업 트리: 원격 기준선은 GitHub에 올라간 최신 기준이고,
  작업 트리는 내 컴퓨터에서 현재 편집 중인 파일 상태입니다.
- Flyway 마이그레이션: 데이터베이스 구조를 순서대로 바꾸는 파일입니다. 이미
  적용한 파일은 고치지 않고 다음 번호의 파일을 추가합니다.
- diff, Pull Request, CI: diff는 바뀐 줄의 비교 결과, Pull Request는 변경을
  `main`에 합치기 위한 제안, CI는 그 변경을 자동으로 검사하는 절차입니다.

1. `ORIENT` — 현재 상태 확인

   브랜치와 원격 기준선, 작업 트리, 관련 소스·테스트·문서를 확인합니다. 범위,
   위험, 보존할 계약과 검증 명령을 정하며 이 단계에서는 파일을 수정하지 않습니다.

2. `SPEC` — 요구사항 확정

   사용자 문제, 범위와 제외 범위, 보안·호환성 영향, 실행 결과로 판단할 수 있는
   완료 조건을 `spec.md`에 적습니다.

3. `PLAN` — 구현과 검증 설계

   어떤 파일과 데이터 흐름을 바꿀지, 완료 조건을 어떤 테스트로 확인할지,
   실패하면 어디서 멈추거나 되돌릴지를 `plan.md`와 `tasks.md`에 연결합니다.

4. `IMPLEMENT` — 최소 범위 구현

   합의한 범위만 구현하고 필요한 테스트를 함께 작성합니다. 적용된 Flyway
   마이그레이션이나 관계없는 작업은 수정하지 않습니다.

5. `VERIFY` — 실제 환경에서 검증

   정해 둔 명령을 실행하고 결과를 있는 그대로 기록합니다. 실행하지 못한 검사는
   `NOT_RUN`으로 남깁니다. 그 때문에 목표 동작을 입증하지 못했다면
   `NOT_VERIFIED`로 판정합니다.

6. `AUDIT` — 최종 변경 검토

   요구사항과 실제 diff가 맞는지, 사용자 데이터 경계와 보안 계약을 지켰는지,
   테스트와 문서가 결과를 과장하지 않는지 다시 확인합니다.

7. `INTEGRATE` — Pull Request와 병합

   GitHub 쓰기와 병합이 작업 범위에 포함될 때만 진행합니다. 실제 Pull Request,
   CI와 검토 결과를 확인한 뒤 `main`에 병합하고, 기준 소스와 통합 근거를
   `evidence.md`와 이 목록에 반영합니다.

오타, 깨진 링크, 이미 확인된 사실을 더 쉽게 설명하는 문서 수정은 제품 동작이
바뀌지 않는다는 점을 확인한 뒤 축소된 절차를 사용할 수 있습니다. 자세한 유지관리
절차는 [AI 에이전트 작업 절차](../docs/ai-agent-workflow.md), 일반적인 기여 방법은
[기여 안내](../CONTRIBUTING.md)를 확인해 주세요.

## 상태와 검사 결과를 구분해서 읽어 주세요

PRZ의 진행 상태는 작업 전체가 어디까지 왔는지를 나타냅니다.

- `AS_BUILT_BASELINE` — Spec 목록 도입 전에 존재하던 구현을 소스 기준으로
  기록한 역사적 기준선입니다.
- `PLANNED` — 요구사항과 완료 조건을 정했지만 구현을 시작하지 않았습니다.
- `IN_PROGRESS` — 구현이나 필수 검증 중 일부가 남아 있습니다.
- `IMPLEMENTED_UNVERIFIED` — 코드는 구현했지만 필수 검증 근거가 부족합니다.
- `VERIFIED` — 해당 PRZ의 근거를 검토해 현재 목록에서 검증 완료로
  판정했습니다. 현재 작업 절차에서는 필수 `VERIFY`와 `AUDIT`를 모두 마쳐야 합니다.
- `DEFERRED` — 재개 조건을 남기고 작업을 미뤘습니다.
- `REJECTED` — 검토나 실험 뒤 채택하지 않기로 결정했습니다.

반면 아래 표기는 특정 검사나 환경에서 얻은 개별 결과입니다.

- `PASS` / `FAIL` — 기록된 소스와 환경에서 해당 검사가 통과하거나
  실패했습니다.
- `SKIPPED` — 검사 항목을 건너뛰었습니다. 통과한 결과로 보지 않습니다.
- `NOT_RUN` — 검사를 실행하지 않았습니다.
- `NOT_VERIFIED` — 목표 동작을 입증할 근거가 충분하지 않습니다.
- `HISTORICAL_PASS_NOT_RERUN` — 과거에는 통과했지만 현재 소스에서 다시
  실행하지 않았습니다.

`PASS` 하나가 PRZ 전체의 `VERIFIED`를 뜻하지는 않습니다. PostgreSQL에서 얻은
결과를 OpenSQL의 근거로 바꾸어 쓸 수도 없습니다. 데이터베이스, 연결 경로,
사용자 흐름과 소스 버전은 각 `evidence.md`에 적힌 범위 안에서만 해석합니다.

현재 절차를 정비하기 전에 작성한 PRZ는 개별 문서에 당시 상태와 작업 범위를
그대로 남겨 두기도 합니다. 이런 경우 이 목록과
[현재 구현 현황](../docs/project-status.md)에서 현재 판정을 확인하고,
`evidence.md`에서 당시 검증 범위를 함께 확인합니다.

## 처음 읽는다면 이 순서가 가장 빠릅니다

1. 현재 제품이 궁금하다면 [현재 구현 현황](../docs/project-status.md)을 읽습니다.
2. 관심 있는 PRZ의 `spec.md`에서 문제, 범위와 완료 조건을 확인합니다.
3. `evidence.md`에서 실제 구현 소스, 실행 환경과 최종 판정을 확인합니다.
4. 왜 그런 구현 순서를 택했는지 필요할 때 `plan.md`와 `tasks.md`를 읽습니다.
5. 벤치마크나 실패 원인이 필요할 때만 하위 실험 기록과 원시 결과를 확인합니다.

현재 동작과 과거 실험 결과가 다르면 현재 소스·테스트와
[현재 구현 현황](../docs/project-status.md)을 우선합니다.

## Spec 목록 도입 전 기준선

- [Spec 목록 도입 전 구현 이력](000-pre-spec-implementation-history.md) —
  2026년 7월 23일 이전 구현 순서를 소스 기준으로 정리했습니다. 당시
  계획·실험·실패와 판정을 포함한 날짜별 원문은
  [전체 개발 기록](../docs/archive/development-log-full-history.md)에 보존합니다.
- [PRZ&#8209;000 · 플랫폼·문서 보관함 기준](PRZ-000-platform-baseline/spec.md) —
  `AS_BUILT_BASELINE`; 기준 소스 `e995a5f`.

## 현재 제품에 통합되고 검증된 기록

아래 PRZ는 현재 제품에 통합됐고 각 Spec의 필수 검증을 마쳤습니다. 최신 제품
전체의 상태를 뜻하는 목록은 아니므로, 현재 지원 범위는
[현재 구현 현황](../docs/project-status.md)과 함께 확인해 주세요.

### 제품 기반과 실행 환경

- [PRZ&#8209;001 · 검색 평가 정합성](PRZ-001-search-evaluation-integrity/spec.md) —
  `VERIFIED`; 기준 소스 `36c8610`.
- [PRZ&#8209;002 · 오픈소스 준비](PRZ-002-open-source-readiness/spec.md) —
  `VERIFIED`; 기준 소스 `f54e3d9`.
- [PRZ&#8209;003 · OpenSQL 단일 서버 검증](PRZ-003-opensql-single-node-gate/spec.md) —
  `VERIFIED`; 기준 소스 `777e184`.
- [PRZ&#8209;004 · 새 설치 환경 데모](PRZ-004-clean-clone-demo/spec.md) —
  `VERIFIED`; 기준 소스 `aff3e87`,
  [PR #25](https://github.com/jaemin-devlog/PRIZM/pull/25).
- [PRZ&#8209;005 · OpenSQL·Ollama E2E](PRZ-005-opensql-ollama-e2e/spec.md) —
  `VERIFIED`; 기준 소스 `eab32c8`,
  [PR #26](https://github.com/jaemin-devlog/PRIZM/pull/26).
- [PRZ&#8209;006 · 로컬 빠른 시작](PRZ-006-local-single-user-demo/spec.md) —
  `VERIFIED`; 기준 소스 `bfd8600`.
- [PRZ&#8209;007 · 자체 호스팅 회원가입](PRZ-007-self-hosted-signup/spec.md) —
  `VERIFIED`; 기준 소스 `2b8b600`,
  [PR #33](https://github.com/jaemin-devlog/PRIZM/pull/33).

### 문서 처리와 근거 검색

- [PRZ&#8209;009 · 사용자가 관리하는 문서 태그](PRZ-009-career-keyword-map/spec.md) —
  `VERIFIED`; P4 소스 `1c1d8d2`, [PR #51](https://github.com/jaemin-devlog/PRIZM/pull/51),
  병합 `d44f30e`.
- [PRZ&#8209;010 · ChangeLog 동기화](PRZ-010-change-log-sync/spec.md) —
  `VERIFIED`; 기준 소스 `26c546b`,
  [PR #39](https://github.com/jaemin-devlog/PRIZM/pull/39).
- [PRZ&#8209;011 · 문서 처리 상태 UX](PRZ-011-document-processing-status-ux/spec.md) —
  `VERIFIED`; 기준 소스 `fbb3481`,
  [PR #41](https://github.com/jaemin-devlog/PRIZM/pull/41).
- [PRZ&#8209;012 · 검색 근거 표현](PRZ-012-search-evidence-presentation/spec.md) —
  현재 목록 상태 `VERIFIED`; 2026년 8월 13일 `VERIFY` 결과 `PASS`. 당시
  `AUDIT`와 `INTEGRATE`는 요청 범위 밖이었다는 기록을 함께 보존합니다
  ([검증 기록](PRZ-012-search-evidence-presentation/evidence.md)).
- [PRZ&#8209;013 · OpenProxy 단일 Primary 검증](PRZ-013-openproxy-single-primary-gate/spec.md) —
  `VERIFIED`; 기준 소스 `a65f91d`.
- [PRZ&#8209;015 · 읽기 전용 MCP 검색](PRZ-015-mcp-career-evidence-search/spec.md) —
  `VERIFIED`; 기준 소스 `97c01cb`, [PR #46](https://github.com/jaemin-devlog/PRIZM/pull/46),
  병합 `23166e7`.

### 화면과 첫 사용자 경험

- [PRZ&#8209;017 · 채용공고 항목별 근거 검색 V1](PRZ-017-job-posting-evidence-v1/spec.md) —
  `VERIFIED`; 기준 소스 `94715cf`, [PR #53](https://github.com/jaemin-devlog/PRIZM/pull/53),
  병합 `b78ec42`.
- [PRZ&#8209;018 · 문서 상세 미리보기 페이지](PRZ-018-document-detail-page/spec.md) —
  `VERIFIED`; 기준 소스 `186be99`, [PR #56](https://github.com/jaemin-devlog/PRIZM/pull/56),
  병합 `a9ca679`.
- [PRZ&#8209;019 · 태그 문서 수 명확화와 TXT 원문 미리보기](PRZ-019-document-usability-fixes/spec.md) —
  `VERIFIED`; 기준 소스 `4932aa8`, [PR #60](https://github.com/jaemin-devlog/PRIZM/pull/60),
  병합 `01d6c46`.
- [PRZ&#8209;020 · 인증 초기화 제거와 빠른 시작 단순화](PRZ-020-auth-bootstrap-cleanup/spec.md) —
  `VERIFIED`; 기준 소스 `831b2bb`, [PR #62](https://github.com/jaemin-devlog/PRIZM/pull/62),
  병합 `adb033b`.
- [PRZ&#8209;021 · Fresh Clone 첫 사용자 경험 정합화](PRZ-021-first-user-experience/spec.md) —
  `VERIFIED`; 기준선 `fb8befe`, 구현 `a0c2977`,
  [PR #65](https://github.com/jaemin-devlog/PRIZM/pull/65), 병합 `60e5fc6`.

### 백엔드 신뢰성 근거

- [PRZ&#8209;022 · 백엔드 신뢰성 근거 재검증](PRZ-022-backend-reliability-evidence/spec.md) —
  `VERIFIED`; 기준선 `3af4db0`에서 Worker, `USER` 소유권 격리와 파일 정리를
  PostgreSQL·Linux 환경에서 재검증했습니다. 소유권 격리에는 REST·MCP 검색 경로가
  포함됐습니다. 검색 품질·일반화 평가는 새로 실행하지 않았고 과거 동결 근거의
  무결성만 확인해 `HISTORICAL_EVIDENCE_VERIFIED`로 한정했습니다.
  [PR #67](https://github.com/jaemin-devlog/PRIZM/pull/67), 병합 `6781d21`.

## 검색 연구·평가 기록

아래 두 PRZ의 `IN_PROGRESS`는 현재 제품에 검색 기능이 없거나 새 기능을 개발하고
있다는 뜻이 아닙니다. 제품에 반영된 개선과 아직 끝내지 않은 연구 검증이 한 기록에
함께 남아 있어 진행 상태를 그대로 보존한 것입니다.

- [PRZ&#8209;008 · 검색 근거 신뢰성](PRZ-008-search-evidence-reliability/spec.md) —
  `IN_PROGRESS`; 기준 소스 `2190d47`,
  [PR #40](https://github.com/jaemin-devlog/PRIZM/pull/40). 제품에 통합된 검색
  개선과 완료하지 않은 최적화 검증을 함께 보존합니다.
- [PRZ&#8209;016 · Search Performance V2](PRZ-016-search-performance-v2/README.md) —
  `IN_PROGRESS`; 당시 통합 [PR #50](https://github.com/jaemin-devlog/PRIZM/pull/50),
  병합 `3cfe9dc`. 현재 검색 소스와 테스트 진입점, P15 `NOT_VERIFIED`, 제품에
  적용하지 않은 P16 판정을 구분해 안내합니다.

현재 검색 동작은 실제 소스·테스트와
[PRZ&#8209;016 검색 문서 안내](PRZ-016-search-performance-v2/README.md)를 우선해
확인해 주세요. 연구 기록의 `FAIL`, `NO_GO`, `NOT_VERIFIED`, `NEEDS_ADJUSTMENT`는
그 실험에서 얻은 판정이며 현재 제품 전체의 실패를 뜻하지 않습니다.

## 역사적 비채택 결정

- [PRZ&#8209;014 · OpenHA topology 검토](PRZ-014-openha-topology-gate/spec.md) —
  `REJECTED`. 당시 탐색과 `PASS`·`NOT_RUN`, 되돌림 근거를 보존하지만 현재 제품의
  변경 후보로 해석하지 않습니다.

## 기록을 보존하는 원칙

- 현재 진행 상태와 기준 소스는 이 목록과
  [현재 구현 현황](../docs/project-status.md)에서 먼저 확인합니다.
- 실행한 명령, 환경과 세부 결과는 각 PRZ의 `evidence.md`에서 확인합니다. 문서에
  역사 체크포인트 안내가 있으면 당시 결과와 현재 통합 상태를 구분해 읽습니다.
- 긴 실행 로그, 벤치마크 결과와 실패 분석은 해당 PRZ의 상세 기록에 둡니다.
- README와 제품 문서에는 현재 기능을 이해하는 데 필요한 결론만 연결합니다.
- Issue, Pull Request, 커밋, 검토와 CI는 실제 이력이 있을 때만 기록합니다.
- 현재 상태와 과거 실험 결과를 섞거나, 완료되지 않은 일을 나중에 완료된 것처럼
  고쳐 쓰지 않습니다.
