# PRZ-002 계획

## 선택한 접근

1. 공개 Git 저장소와 source ZIP을 현재 배포 경계로 두고 JAR, frontend `dist`,
   container image, Ollama binary와 모델 가중치는 후속 배포 Gate로 분리한다.
2. 공식 자료는 원문을 복제하지 않고 source register에 URL, hash, 권리 상태와
   PRIZM 적용 항목을 기록한다.
3. Lockfile·resolved graph·artifact license·bundle·image·model metadata를 함께
   대조하고 자동 도구 결과만으로 license 판정을 끝내지 않는다.
4. 전체 감사를 마친 뒤 사용자가 MIT와 Apache-2.0을 비교해 outgoing license를
   선택하도록 한다.
5. 감사되지 않은 제3자 SBOM plugin 대신 저장소가 소유하는 deterministic
   generator와 verifier를 사용한다.
6. G-02·T-06·T-07은 실제 외부 운영 경로가 생길 때까지 `DEFERRED`로 둔다.
7. PostgreSQL 회귀, OpenSQL single-node Gate와 PRZ-002 source-only 검증을
   서로 다른 근거로 유지한다.

## 예상 변경

| 범위 | 예상 산출물 |
|---|---|
| 공식 근거 | `docs/contest/2026-source-register.md` |
| License·provenance | `LICENSE`, `NOTICE`, license·asset 감사 |
| SBOM·AI | `sbom/` manifest·checksum, 생성·검증 script |
| 공개 진입 문서 | README, Quickstart, docs index와 상태·대회 문서 |
| 자동 검증 | 로컬 OSS readiness 명령과 GitHub Actions |
| 추적성 | Spec, tasks, evidence, Registry와 개발 기록 |

제품 source, Flyway migration, production configuration과 frontend 기능은 예상
변경 범위에 넣지 않는다.

## 위험과 중단 조건

| 위험 | 처리 |
|---|---|
| 외부 code·asset·fixture 출처 불명 | `UNKNOWN`으로 두고 공개 통합 중단 |
| Dependency·model license 충돌 | 대체·제거·별도 허가 전 `BLOCKED` |
| 모델 revision·manifest 식별 실패 | 모델 Gate 중단, PostgreSQL 성공으로 대체 금지 |
| 배포 산출물 범위 불명 | NOTICE·SBOM 확정 전 사용자 결정 요청 |
| Outgoing license 미승인 | `LICENSE`·`NOTICE` 생성 중단 |
| 비공개 security channel 미확정 | 현재 source-only 범위에서는 거버넌스 문서를 `DEFERRED`로 유지 |
| 공식 자료 hash 불일치 | Source를 `CONFLICT`로 바꾸고 원인 확인 |
| 민감정보나 공급사 자산 발견 | 즉시 통합 중단하고 공개 이력 영향 별도 판단 |
| 기존 사용자 변경과 branch 충돌 | reset·stash로 우회하지 않고 중단 |

## 검증 환경

- Windows: Java 17, Gradle Wrapper, Node 22.17.0, npm 10.9.2
- Linux clean clone: 같은 SBOM 생성·검증 명령의 운영체제 독립성 확인
- GitHub Actions: clean checkout의 OSS readiness와 기존 CI 확인
- Docker·PostgreSQL·pgvector: 애플리케이션 회귀가 필요한 변경에서만 사용
- Ollama·OpenSQL·OpenProxy·OpenHA: 이 source-only 검증의 필수 환경이 아니며,
  실행하지 않으면 `NOT_RUN`으로 기록

## Rollback과 중단

- 생성기나 verifier가 실패하면 generated SBOM과 checksum을 갱신하지 않는다.
- License나 배포 경계에 blocking unknown이 남으면 공개 release를 중단한다.
- 문서·script·SBOM 변경은 해당 임시 branch에서 되돌릴 수 있어야 하며 제품
  migration이나 사용자 데이터에는 rollback을 적용하지 않는다.
- 실패 기록은 evidence에서 지우지 않고 보완 뒤 새 검증 결과와 함께 보존한다.

## Dependency·license 고려

- Java runtime/test/build, npm runtime/dev, CI Action, container, model,
  fixture·asset을 서로 다른 scope로 기록한다.
- Component마다 exact version·revision·digest, upstream, SPDX, 사용 목적,
  배포 여부와 NOTICE 의무를 기록한다.
- Ollama source, 실행 binary, `bge-m3` 가중치와 PRIZM integration code를 별도
  구성요소로 취급한다.
- 모델 파일·cache와 공급사 OpenSQL 자산은 Git과 기본 제출물에 넣지 않는다.

## Branch·PR 계획

- 실제 작업은 최신 `main`에서 분기한 `PRZ-002-<slug>` 임시 branch로 나눈다.
- GitHub Issue는 권한이 있고 실제 착수 시점에 필요한 경우에만 만든다.
- VERIFY와 독립 AUDIT 뒤 실제 변경을 담은 PR을 만든다.
- Reviewer가 없으면 사용자 승인과 `REVIEW_NOT_AVAILABLE_SOLO`를 기록하되
  GitHub review로 주장하지 않는다.
- 병합된 `main`을 먼저 push하고 branch 안전 확인 뒤 정확한 임시 branch만 삭제한다.
