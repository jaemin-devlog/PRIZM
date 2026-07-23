# 2026 오픈소스 개발자대회 티맥스티베로 과제 대응 계획

> 기준일: 2026-07-24
>
> 제출 마감: 2026-08-27
>
> 원칙: 구현하지 않은 기능과 검증하지 않은 환경을 주장하지 않는다.

## 결론

PRIZM의 현재 기반은 지정과제와 잘 맞지만 아직 출품 요건을 완성한 상태는
아니다.

- 이미 구현: 문서 업로드, 자동 임베딩, 메타데이터·immutable version,
  owner-scoped pgvector 검색, 비동기 작업 복구
- 준비만 완료: 실제 OpenSQL 호환성 Gate
- 미구현: 변경 로그 기반 동기화, MCP 검색 API
- 미검증: OpenSQL 실환경과 DB 장애전환·서비스 연속성
- 별도 제품 계획: CareerFact와 근거 기반 portfolio

따라서 CareerFact·portfolio보다 `OpenSQL → 장애복구 → 동기화 → MCP`를
먼저 개발한다. PRIZM 고유 기능은 이 핵심 Gate가 통과한 뒤 작은 수직
슬라이스로 추가한다.

## 공식 과제 해석

[KOSSA 티맥스티베로 지정과제](https://www.kossa.kr/materials/2026/ossp/tasks-tmax.html)의
과제명은 **OpenSQL 기반 AI 검색 및 벡터 데이터 플랫폼 개발**이다. 미션은
OpenSQL 위에서 문서를 업로드하면 AI가 내용을 이해하고 맞춤 검색을 제공하는
자동화된 AI 문서 관리 플랫폼을 만드는 것이다.

공식 페이지는 다음을 `개발과제 예시`로 제시한다.

1. 문서 업로드
2. 자동 임베딩
3. 메타데이터·버전 관리
4. 변경 로그 기반 동기화
5. MCP 기반 검색 API

또한 DB 노드 장애에도 중단 없이 자동 복구하고, 업로드부터 임베딩·동기화·MCP
검색까지 원스톱으로 처리하는 목표를 설명한다. 다섯 예시를 모두 확정
필수조건이라고 과장하지는 않지만, 제출 전 핵심 평가 대응 항목으로 관리한다.
OpenSQL의 기술 소개만으로 PRIZM의 호환성이나 가용성을 주장하지 않는다.

요구사항별 현재 상태와 평가 대응은
[공식 요구사항·평가기준 추적표](2026-requirements-traceability.md)를 기준으로
한다.

## 확정된 일정과 평가

[오픈소스 개발자대회 공식 페이지](https://www.oss.kr/pages/2)와 제공받은
오리엔테이션 슬라이드를 함께 기준으로 한다.

| 단계 | 일정 | 핵심 내용 |
|---|---|---|
| 출품 마감 | 2026-08-27 | 결과보고서, 3분 시연영상, 소스코드 |
| 1차 서면평가 | 2026-09-03~09-04 | 코드·OSS 발전성·문서·혁신성·팀워크, 각 6점, 합계 30점 |
| 합격 발표 | 2026-09-09 예정 | 홈페이지 공지 |
| 멘토링 | 2026-09-18~10-09 | 지정과제는 해당 기업 멘토링 |
| 기능·라이선스 검증 | 2026-10-12~10-28 | 기능 10점, 라이선스 5점 |
| 2차 발표평가 | 2026-11-04 | 현장 PPT 10분+질의응답 5분, 합계 70점 |

2차 70점은 PT 10, 활용성 15, 데모 10, 커뮤니티 확장 10,
오픈소스SW 적절성 10, 기능테스트 10, 라이선스 검증 5점이다. 발표자료에는
활용한 오픈소스 라이브러리를 표시한다.

## Spec Kit 적용 방식

[GitHub Spec Kit](https://github.github.com/spec-kit/)의
`Spec → Plan → Tasks → Implement`와
[robo-architect specs](https://github.com/uengine-oss/robo-architect/tree/main/specs)의
독립 시나리오·검증 Gate·evidence 분리를 작은 수직 슬라이스에만 적용한다.

- `spec.md`: 사용자 시나리오, 범위·비범위, 요구사항, acceptance criteria
- `plan.md`: 파일·API·migration·보안·라이선스 영향과 검증 환경
- `tasks.md`: 정확한 작업 경로, test, 문서와 의존 순서
- `evidence.md`: 요구사항→source→test→환경→결과→실제 GitHub 기록

Spec checkbox는 구현 증거가 아니다. source, Flyway migration과 실행 가능한
test가 최종 진실이다. 기존 저장소에 `specify init --here --force`를 실행하지
않고 `AGENTS.md`를 유일한 작업 규칙 원본으로 유지한다.

`PRZ-000-platform-baseline`은 과거 구현을 사전 명세처럼 꾸미지 않은
`AS_BUILT_BASELINE`이다. 이후 ID는 실제 작업을 시작하는 `SPEC` 단계에서
하나씩 할당한다. 현재 예정된 첫 두 작업은 다음과 같다.

```text
PRZ-001-opensql-vector-gate
PRZ-002-clean-clone-demo
```

그다음 ID는 실제 착수 순서에 따라 DB 가용성 Gate, 변경 로그 동기화,
MCP 검색, CareerFact, portfolio, 제출 감사를 각각 작은 spec으로 만든다.
존재하지 않았던 과거 Issue·PR·review는 만들지 않는다.

## 제출 전 개발 순서

### P0. 공식 기준·오픈소스 준비 — 7월 24~27일

- 공식 지정과제와 평가기준 내용 추출·초기 매핑 —
  `CONTENT_EXTRACTED_SOURCE_PENDING`
- 공식 오리엔테이션 PDF의 URL·버전·hash 확보 후 추적표 source 고정
- dependency, `bge-m3`, 합성 데이터, asset, OpenSQL 구성요소 라이선스를
  `docs/contest/2026-license-audit.md`에 감사
- 감사 결과에 맞는 root LICENSE, NOTICE, CONTRIBUTING, SECURITY 결정
- Issue·PR template과 실제 신규 작업용 GitHub 흐름 준비

### P1. OpenSQL·clean-clone — 7월 28일~8월 3일

- 실제 착수 시 `PRZ-001-opensql-vector-gate`와 실제 GitHub Issue 생성
- 전용 OpenSQL DB/schema에서 Flyway와 `vector(1024)` 검색 실행
- owner·ACTIVE, claim·lease·fencing·`SKIP LOCKED` 결과를 PostgreSQL과 분리
- `PRZ-002-clean-clone-demo`에서 안전한 demo `USER`와 합성 TXT/PDF 준비
- 두 번째 깨끗한 환경에서 로그인→업로드→ACTIVE→검색 재현
- 처리 완료 확인 절차와 browser E2E 또는 고정 수동 UI 시험표 작성

### P2. DB 장애복구 Gate — 8월 4~7일

- 실제 제공되는 OpenSQL 다중 노드 구성과 장애전환 방법을 먼저 확인
- 구현 전에 topology, 장애 시나리오, RTO·RPO, 허용 오류·중복·유실과
  반복 횟수를 spec의 측정 가능한 성공조건으로 고정
- 노드 장애 주입, leader 전환, 애플리케이션 재연결과 검색 복구 검증
- 처리 중인 문서의 중복·누락, version 활성화와 owner 격리 확인
- OpenProxy·OpenHA 이름은 실제 사용한 구성일 때만 기록

환경을 확보하지 못하면 `NOT_RUN`으로 남기고 Worker crash recovery나
PostgreSQL 단일 노드 성공으로 대체하지 않는다.

### P3. 변경 로그 동기화·MCP — 8월 8~14일

- 별도 spec에서 동기화 대상, 이벤트 경계, 멱등성, 재시도와 복구 정의
- 문서·version 변경이 검색 데이터에 누락·중복 없이 반영되는 최소 slice
- 기존 Career Evidence 검색을 재사용하는 owner-scoped 읽기 전용 MCP 도구
- 인증, 다른 사용자 차단, source 반환과 현재 HTTP 200 빈 결과 계약을
  기준으로 MCP 응답을 spec에 정의하고 contract test 작성

전체 범용 CDC 플랫폼이나 공개 쓰기 도구는 이번 범위에 넣지 않는다.

### P4. PRIZM 차별 slice — 8월 15~17일

P1~P3의 **PRIZM 내부 제출 Gate**가 통과하고 치명적 라이선스 문제가 없을
때만 착수한다. 이는 공식 페이지의 확정 필수조건이라는 뜻이 아니다.

- `PROJECT` 또는 `SKILL` 한 종류의 CareerFact 후보·확인·거절 흐름
- source chunk·문서·version·페이지/구간 연결
- 다른 사용자와 실패·비활성 version 차단 test

기간 안에 검증하지 못하면 현재 업로드·검색·근거 확인만 데모한다.
Portfolio는 CareerFact 완료 뒤의 별도 계획으로 유지하며 제출을 위해 억지로
추가하지 않는다.

### P5. 제출 증거 — 8월 18~23일

- README Quickstart, architecture/data flow, OpenSQL 설정과 troubleshooting
- SBOM, third-party·model·data·asset 라이선스 표와 비밀정보 검사
- 요구사항→code→test→환경→demo 장면 추적성 완성
- 문제→해결→검증→확장 순서의 결과보고서와 3분 영상 제작
- 기능검증기관용 시스템 소개·구현 환경·소스 실행 절차 작성
- 검증된 commit/tag의 tracked 파일만 `git archive` 또는 Release artifact로
  만들고 제출 manifest 확인

### P6. 동결 — 8월 24~27일

- 기능 추가 중단
- clean clone에서 전체 필수 검증
- 보고서·영상·README·실제 화면의 기능 범위 일치 확인
- 치명적 finding이 없을 때만 `v0.1.0-contest` tag와 Release 생성

### P7. 1차 평가·멘토링 준비 — 8월 28일~9월 17일

- 제출 commit, 보고서와 영상의 재현 환경을 보존
- 9월 3~4일 서면평가 대응 자료와 예상 질문 정리
- 1차 선발 시 9월 9~11일 멘토링 수요조사 제출
- 평가 전에는 제출 이력을 다시 쓰지 않고 치명적 재현 오류만 별도 기록

### P8. 기업 멘토링·보완 — 9월 18일~10월 9일

- 멘토 feedback을 실제 Issue·spec·evidence 또는 명시적 비채택 사유로 연결
- 공식 과제 정합성과 기능검증 재현성을 우선해 작은 보완만 수행
- 변경된 기능은 전체 필수 test, clean-clone, 라이선스 감사를 다시 실행

### P9. 외부 기능·라이선스 검증 — 10월 12~28일

- 시스템 소개, 구현 환경, source와 정상·예외·장애 시험 절차 제공
- 검증 finding을 실제 Issue로 추적하고 수정 뒤 재검증
- 복수 license 충돌과 해결 방안, SBOM과 제출 source manifest 확정

### P10. 2차 발표 — 10월 29일~11월 4일

- 사용 OSS 라이브러리를 표시한 10분 PPT와 5분 Q&A 준비
- clean-clone demo, 장애·근거 없음·복구 시나리오와 fallback 영상 점검
- 발표자료, 실제 Release와 기능·라이선스 검증 결과의 범위 일치 확인

## 실제 GitHub 작업 원칙

다음은 공식 출품 의무가 아니라 **PRIZM 내부 contest evidence 정책**이다.
앞으로의 contest code는 실제 작업 시점에
`Issue → spec → temporary branch → commits → PR → review → merge`로 남긴다.
Agent의 독립 감사는 품질 evidence지만 GitHub review를 대신하지 않는다.
병합 뒤 `main`을 push하고 기존 안전 절차로 임시 local·remote branch를
삭제해 장기 브랜치는 `main`만 유지한다.

실제 reviewer가 없는 개인 작업은 독립 감사 완료, 사용자 승인과
`REVIEW_NOT_AVAILABLE_SOLO` 기록 뒤 통합할 수 있다. GitHub 접근이 없고
사용자가 local-only 작업을 승인한 경우에는 진행할 수 있지만 Issue·PR·review
증거로 계산하지 않는다. 과거 기록을 소급 생성하거나 가짜 승인으로 채우지
않는다.

## 중단 기준

- 실제 OpenSQL이 없으면 호환성을 `PASS`로 표시하지 않는다.
- 필수 환경이 `NOT_RUN`, test가 실패, 라이선스 충돌이 미해결이면 해당 Gate를
  완료하거나 제출 주장으로 바꾸지 않는다.
- 모델·데이터·OpenSQL 구성요소 재배포 조건이 불명확하면 저장소에 넣지 않고
  공식 다운로드 절차만 제공한다.
- 모바일, 소셜, 통계 dashboard, 여러 DB adapter, 전체 관리자 화면과 자체
  모델 학습은 대회 핵심 범위에서 제외한다.
