# PRIZM 오픈소스 엔진 전환 실행 계획

> 문서 버전: 1.0
> 작성일: 2026-07-15
> 목적: PRIZM을 재사용 가능한 오픈소스 Career Intelligence Engine과 이를 검증하는 Reference App으로 단계적으로 전환한다.
> 사용 방법: 각 단계는 별도 Codex 작업에서 실행하고, 구현 작업과 독립 검토 작업을 분리한다.

## 1. 목표 정체성

PRIZM의 공식 제품 정의는 다음과 같다.

> PRIZM은 커리어 문서의 분석, 정보 구조화, 근거 검색 및 포트폴리오 생성을 위한 오픈소스 Career Intelligence Engine과 이를 검증하는 Reference App이다. 개인뿐 아니라 대학, 취업 지원기관, 기업 및 개발자가 각자의 환경에 맞는 커리어 관리 서비스를 구축할 수 있도록 재사용 가능한 모듈과 확장 지점을 제공한다.

이 정의는 목표 제품 경계이며 재사용 가능한 모듈과 포트폴리오 생성이 현재 구현됐다는 뜻이 아니다. 현재 `frontend/`의 Career Vault는 엔진의 개인용 기능과 통합 방식을 보여주는 Reference App으로 유지한다.

```text
PRIZM Engine
├─ 문서 등록·버전·원본 보존
├─ 추출·청킹·임베딩 파이프라인
├─ 출처 보존 근거 검색
├─ 근거 연결 커리어 정보 구조화
└─ 검증된 커리어 정보 기반 포트폴리오 생성

Reference Applications
├─ Personal Career Vault
└─ University / Career Center Example
```

## 2. Codex 모델과 추론 강도 선택 원칙

Codex 공식 모델 선택 지침을 기준으로 다음 원칙을 사용한다.

- `5.6 Sol`: 복잡하고 열린 설계, 고위험 리팩터링, 데이터 모델, 보안, 최종 감사
- `5.6 Terra`: 범위와 완료 기준이 명확한 일상적 구현, 인프라 구성, 문서·패키징 작업
- `5.6 Luna`: 반복적이고 기계적인 정리, 형식 변환, 명확한 후속 수정
- `중간`: 범위가 명확하고 위험이 낮은 작업
- `높음`: 여러 파일과 검증 단계가 연결된 구현
- `매우 높음`: 아키텍처, migration, 동시성, 출처 무결성, 멀티테넌시처럼 되돌리기 어려운 작업
- `울트라`: 서로 독립적인 여러 관점의 검토를 병렬화할 수 있는 최종 감사

공식 지침은 필요한 결과를 얻는 가장 낮은 추론 강도부터 시작하라고 권장한다. 이 계획에서는 구현 위험에 따라 강도를 미리 높여 두었다. `울트라`를 사용할 수 없으면 `5.6 Sol / 매우 높음`으로 대체한다.

참고:

- [Codex model selection](https://learn.chatgpt.com/docs/models.md)
- [Codex prompting](https://learn.chatgpt.com/docs/prompting.md)

## 3. 모든 단계에 적용하는 불변식

다음 규칙은 어느 단계에서도 완화하지 않는다.

1. 등록 문서에서 확인되지 않은 경력, 기술, 성과, 숫자를 생성하지 않는다.
2. 근거가 없으면 `현재 등록된 문서에서 근거를 찾지 못함`으로 표현하고 거짓이라고 판정하지 않는다.
3. 기존 문서 버전 관리, `active_version_id`, 문서 청크, 임베딩, pgvector 검색을 보존한다.
4. lease, retry/backoff, `claim_version` fencing, Worker crash recovery를 보존한다.
5. 완성된 문서 버전의 원자적 ACTIVE 전환을 보존한다.
6. 문서·버전·작업·청크의 사용자 소유권과 검색 후보 단계 격리를 보존한다.
7. 12개 `DocumentType` 계약을 제거하거나 임의 변경하지 않는다. 확장은 tag와 custom metadata로 한다.
8. TXT의 `TEXT_CHUNK`, PDF의 `PAGE` 출처 계약을 보존한다.
9. 기존 단일 검색과 최대 5개 Career Evidence API 계약을 호환 경로로 유지한다.
10. 이미 적용된 Flyway migration은 수정하지 않고 다음 번호의 forward migration을 추가한다.
11. PostgreSQL 검증 결과를 OpenSQL·OpenProxy·OpenHA 검증으로 표현하지 않는다.
12. 사용자 작업과 무관한 파일을 되돌리거나 삭제하지 않는다.
13. 명시적으로 요청받지 않은 commit, push, PR 생성은 하지 않는다.

## 4. 단계 운영 방법

각 단계는 다음 순서로 진행한다.

1. 아래 상태표에서 선행 단계가 `COMPLETE`인지 확인한다.
2. 해당 단계의 권장 모델과 추론 강도를 Codex 화면에서 선택한다.
3. 새 Codex 작업에서 해당 단계의 `실행 프롬프트`를 그대로 붙여 넣는다.
4. 구현 결과와 검증 결과를 확인한다.
5. 별도의 새 Codex 작업에서 `공통 독립 검토 프롬프트`를 실행한다.
6. 독립 검토가 통과한 경우에만 상태를 `COMPLETE`로 변경한다.
7. 실패하면 상태를 `BLOCKED` 또는 `IN_PROGRESS`로 유지하고 발견 사항을 기록한다.

상태 값:

- `NOT_STARTED`: 시작 전
- `IN_PROGRESS`: 구현 또는 검토 진행 중
- `BLOCKED`: 외부 환경, 사용자 결정 또는 선행 결함으로 중단
- `COMPLETE`: 완료 조건과 검증을 모두 충족

## 5. 전체 진행 상태

| 단계 | 이름 | 상태 | 완료 증거 |
|---|---|---|---|
| 0 | 구현 기준선과 제품 경계 확정 | COMPLETE | 최종 독립 재검토 PASS: 제품 경계·단계 8 canonical module graph·다섯 JavaDoc 및 `.env.example` 기술 부채 기록 확인 |
| 1 | 오픈소스 거버넌스와 저장소 위생 | NOT_STARTED | |
| 2 | 재현 가능한 Quickstart와 참조 실행환경 | NOT_STARTED | |
| 3 | 코어 포트와 어댑터 경계 정리 | NOT_STARTED | |
| 4 | Canonical Source와 처리 이력 계약 | NOT_STARTED | |
| 5 | 근거 연결 CareerFact 수직 슬라이스 | NOT_STARTED | |
| 6 | 검증된 Portfolio Composer 수직 슬라이스 | NOT_STARTED | |
| 7 | 개발자용 API v1과 비동기 통합 계약 | NOT_STARTED | |
| 8 | 멀티모듈 패키징과 재사용 예제 | NOT_STARTED | |
| 9 | 기관용 workspace·profile·권한 모델 | NOT_STARTED | |
| 10 | 최종 기능·보안·라이선스·재현성 감사 | NOT_STARTED | |

대회용 핵심 MVP는 단계 0~8이다. 단계 9는 대학·기관의 실제 다중 사용자 운영을 위한 후속 확장으로 분리할 수 있다. 단계 10은 실제 완료한 범위를 대상으로 수행한다.

## 6. 공통 독립 검토 프롬프트

모든 단계가 끝난 후 새 Codex 작업에서 사용한다.

**권장 모델:** `5.6 Sol`
**추론 강도:** `매우 높음`

```text
PRIZM 오픈소스 엔진 전환 계획의 단계 {단계 번호} 결과를 독립적으로 검토해줘.

반드시 먼저 AGENTS.md와 docs/oss-transition-execution-plan.md 전체를 읽고, 현재 git status와 해당 단계의 실제 diff를 확인해. 구현자의 설명이나 계획 문서만 믿지 말고 소스 코드, Flyway migration, 실행 가능한 테스트를 기준으로 판단해.

검토 범위:
1. 해당 단계의 목표와 완료 조건이 실제로 충족됐는지 확인
2. PRIZM의 기존 불변식인 owner 격리, active_version_id, lease/fencing, 원자적 활성화, 출처 계약, 기존 API 호환성이 회귀하지 않았는지 확인
3. 미구현 기능을 구현된 것처럼 문서화하지 않았는지 확인
4. 사용자 작업이나 무관한 파일을 덮어쓰지 않았는지 확인
5. 필요한 단위·통합·프런트엔드·Compose 검증을 실제로 실행하고, PostgreSQL·pgvector·Ollama·Docker 사용 여부를 구분해 보고
6. 보안, 데이터 migration, 공개 API 호환성, 재현성 위험을 우선순위별로 보고

먼저 검토만 수행해. 결함을 발견해도 바로 수정하지 말고 파일과 근거를 포함한 findings를 우선순위 순서로 제시해. 치명적 또는 중요 finding이 하나라도 있으면 단계 상태를 COMPLETE로 바꾸지 마. finding이 없고 모든 완료 조건이 충족된 경우에만 docs/oss-transition-execution-plan.md의 상태표와 완료 증거를 갱신하고 docs/development-log.md에 검토 결과를 짧게 기록해.

commit, push, PR 생성은 하지 마.
```

---

## 단계 0. 구현 기준선과 제품 경계 확정

**권장 모델:** `5.6 Sol`
**추론 강도:** `매우 높음`
**선행 단계:** 없음

### 목표

- 현재 구현과 장기 계획을 명확히 분리한다.
- PRIZM Engine과 Career Vault Reference App의 책임을 문서로 확정한다.
- 이후 리팩터링이 보존해야 할 실행 기준선을 기록한다.

### 주요 작업

- `README.md`, `docs/project-status.md`, 장기 기획안의 B2C 중심 표현을 오픈소스 엔진 중심으로 재정리
- 실제 구현과 미구현 기능 matrix 작성
- `docs/architecture/oss-product-boundary.md` 작성
- 현재 API, migration, 테스트, 외부 의존성 기준선 기록
- 당시 작업 중이던 Cleanup Worker 변경을 별도 작업으로 보존해 독립 감사한 뒤 main 기준선으로 편입
- 다중 Career Evidence UI 등 문서와 코드가 어긋난 부분 수정

### 완료 조건

- 문서만 읽어도 Engine, adapter, reference app, 향후 기능의 구분이 명확하다.
- 커리어 구조화, 포트폴리오 생성, MCP, OpenSQL HA를 현재 구현으로 표현하지 않는다.
- 현재 제공 API와 테스트 명령이 실제 소스와 일치한다.
- 애플리케이션 동작 코드는 변경하지 않는다.

### 독립 검토 상태

- 2026-07-15 1차 독립 검토에서 공식 제품 정의, 목표 모듈 구조, JavaDoc·`.env.example` 잔여 불일치 기록에 대한 지적이 나왔다.
- 공식 정의를 Engine과 Reference App 경계로 통일하고, 단계 8 목표 구조를 canonical target으로 지정하며, 잔여 불일치를 `docs/project-status.md`에 기술 부채로 기록했다.
- 2026-07-15 2차 독립 재검토는 `DocumentIndexingProcessor`, `IngestionProperties`, `TextChunker`의 오래된 TXT 전용 JavaDoc이 기술 부채 목록에서 빠진 점을 Medium finding으로 판정해 FAIL했다.
- `src/main/java` 전체 JavaDoc·일반 주석 전수 검사에서 확인한 다섯 개의 부정확한 설명을 기준선과 단계 3 후속 범위에 반영했으며, 실제 JavaDoc은 아직 수정하지 않았다.
- 이 수정은 문서 기준선 보완이며 애플리케이션 코드, migration, build 설정과 V13 Cleanup Worker를 변경하지 않는다.
- 2026-07-16 최종 독립 재검토는 위 세 지적사항을 모두 RESOLVED로 판정하고 PASS했다. 제품 경계, Career Vault의 Reference App 역할, 단계 8의 유일한 canonical module graph, 다섯 JavaDoc과 `.env.example` 기술 부채의 단계 3·단계 2 후속 연결을 확인했다.
- V13 Cleanup Worker는 별도 보안·동시성 감사에서 CRITICAL/HIGH/MEDIUM finding 없이 통과했고 `86387e7c227ede3be96c538aafc48b0205bc5e18`로 main에 병합됐다. 단계 0에 추가 문서 감사는 필요하지 않다.
- 단계 0은 `COMPLETE`다. JavaDoc은 단계 3, `.env.example`은 단계 2의 후속 수정 대상으로 유지한다.

### 실행 프롬프트

```text
PRIZM 오픈소스 엔진 전환 계획의 단계 0을 수행해줘.

권장 설정은 5.6 Sol, 추론 강도 매우 높음이다.

먼저 AGENTS.md와 docs/oss-transition-execution-plan.md를 전체 읽고, git status를 확인해. 현재 작업 중인 cleanup Worker와 다른 사용자 변경을 절대 되돌리거나 정리하지 마.

실제 source, Flyway migration, test, frontend를 기준으로 현재 구현을 다시 확인한 뒤 다음을 수행해:
- PRIZM Engine과 Career Vault Reference App의 제품 경계를 문서화
- README와 project-status의 현재/계획 기능을 정확히 분리
- docs/architecture/oss-product-boundary.md 작성
- 현재 API, 저장 모델, 비동기 처리, 검색, 인증, 외부 의존성 기준선 기록
- 장기 기획안의 B2C·가격 중심 결론을 오픈소스 엔진 중심 방향으로 현행화하되, 역사적 결정이 필요한 내용은 함부로 삭제하지 말고 변경 이유를 명시
- 코드와 문서가 어긋난 항목을 수정

이 단계에서는 애플리케이션 코드, migration, build 설정을 변경하지 마. Markdown 링크와 git diff --check를 검증해. 완료 후 변경 파일, 확인한 구현 사실, 남은 불일치를 보고하고 docs/development-log.md에 설계 결정을 짧게 기록해. 독립 검토 전에는 상태를 COMPLETE로 바꾸지 마. commit, push, PR은 하지 마.
```

---

## 단계 1. 오픈소스 거버넌스와 저장소 위생

**권장 모델:** `5.6 Terra`
**추론 강도:** `높음`
**선행 단계:** 0

### 목표

제3자가 저장소를 합법적이고 안전하게 검토·기여할 수 있는 최소 조건을 만든다.

### 주요 작업

- 기획안에서 결정한 Apache License 2.0 적용
- `LICENSE`, `NOTICE`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`, `CHANGELOG.md` 추가
- DCO 또는 기여자 확인 방식을 문서화
- 의존성·모델·샘플 데이터·폰트의 라이선스와 재배포 조건 분리
- SBOM과 dependency license report 생성 경로 추가
- `local/`, 모델 cache, 평가 결과, `__pycache__`, 실제 커리어 문서, 비밀정보의 commit 차단
- issue와 PR template 추가

### 완료 조건

- 저장소 루트에 실제 라이선스와 기여·보안 문서가 있다.
- Ollama 자체와 `bge-m3` 모델의 배포·다운로드 책임이 코드 라이선스와 구분된다.
- 합성 여부가 확인되지 않은 데이터는 공개 fixture에 포함되지 않는다.
- CI에서 critical license conflict와 비밀정보 commit 위험을 확인할 경로가 있다.

### 실행 프롬프트

```text
PRIZM 오픈소스 엔진 전환 계획의 단계 1을 수행해줘.

권장 설정은 5.6 Terra, 추론 강도 높음이다.

AGENTS.md, docs/oss-transition-execution-plan.md, 단계 0 산출물과 현재 git status를 먼저 확인해. 기획안의 Apache License 2.0 결정을 기준으로 오픈소스 거버넌스 파일을 추가하되, 제3자 의존성·Ollama 모델·샘플 자료의 라이선스를 PRIZM 코드 라이선스와 동일하다고 가정하지 마. 공식 라이선스 근거를 확인할 수 없는 항목은 재배포하지 말고 다운로드 절차 또는 확인 필요 상태로 남겨.

LICENSE, NOTICE, CONTRIBUTING.md, CODE_OF_CONDUCT.md, SECURITY.md, CHANGELOG.md, issue/PR template, SBOM과 dependency license report 경로를 추가해. .gitignore와 CI를 보완해 local 모델 cache, 평가 산출물, __pycache__, 실제 커리어 문서, .env와 비밀정보가 commit되지 않게 해. 현재 untracked 파일을 임의 삭제하지 마.

관련 생성 명령과 CI 검증을 실제로 실행해. 결과와 확인하지 못한 라이선스 항목을 분리해 보고하고 docs/development-log.md를 갱신해. 독립 검토 전에는 상태를 COMPLETE로 바꾸지 마. commit, push, PR은 하지 마.
```

---

## 단계 2. 재현 가능한 Quickstart와 참조 실행환경

**권장 모델:** `5.6 Terra`
**추론 강도:** `높음`
**선행 단계:** 1

### 목표

새 개발자가 별도 SQL 수작업 없이 합성 데이터로 PRIZM의 핵심 흐름을 실행할 수 있게 한다.

### 주요 작업

- 백엔드와 프런트엔드용 Dockerfile
- DB, API/Worker, Web, Ollama를 설명하는 Compose 구성
- 무거운 모델 다운로드를 명시적 profile 또는 init 절차로 분리
- 임의 기본 비밀번호가 없는 명시적 demo USER bootstrap 또는 안전한 사용자 생성 CLI
- 합성 TXT/PDF fixture와 end-to-end curl 예제
- readiness와 health 확인
- Windows와 Linux 실행 절차 분리

### 완료 조건

- README의 Quickstart만으로 로그인 가능한 `USER`와 합성 문서를 준비할 수 있다.
- `SYSTEM_ADMIN`만 생성되고 문서 API는 사용할 수 없는 현재 dead end가 제거된다.
- `docker compose config`가 성공한다.
- 가능한 환경에서는 업로드→ACTIVE→검색까지 실제로 검증한다.
- Docker, PostgreSQL, pgvector, Ollama를 실제 사용했는지 보고서에 구분한다.

### 실행 프롬프트

```text
PRIZM 오픈소스 엔진 전환 계획의 단계 2를 수행해줘.

권장 설정은 5.6 Terra, 추론 강도 높음이다.

AGENTS.md와 실행 계획을 읽고 현재 실행 경로를 실제로 재현해. 현재 README는 SYSTEM_ADMIN만 bootstrap할 수 있고 문서 API는 USER 역할만 허용하므로, 새 설치자가 별도 SQL 없이 합성 demo USER를 만들 수 있는 안전한 경로를 구현해. 운영 기본값으로 고정 계정이나 고정 비밀번호를 만들지 말고 demo/local profile 또는 명시적 CLI로 제한해.

DB, API/Worker, Web, Ollama의 관계가 드러나는 Dockerfile과 Compose 구성을 제공해. bge-m3 다운로드가 크므로 자동 다운로드의 비용과 profile/init 절차를 명시해. 재배포 가능한 합성 TXT/PDF fixture, 로그인·업로드·상태 확인·검색 curl 튜토리얼, readiness/health 확인을 추가해.

docker compose config와 가능한 실제 end-to-end 흐름을 검증해. 외부 환경이 없어 실행하지 못한 항목은 성공으로 쓰지 말고 정확한 blocker를 보고해. docs/development-log.md를 갱신하고 독립 검토 전에는 상태를 COMPLETE로 바꾸지 마. commit, push, PR은 하지 마.
```

---

## 단계 3. 코어 포트와 어댑터 경계 정리

**권장 모델:** `5.6 Sol`
**추론 강도:** `매우 높음`
**선행 단계:** 2

### 목표

동작을 바꾸지 않고 Spring MVC, PDFBox, Ollama, pgvector, 로컬 파일시스템 의존을 코어 유스케이스에서 분리한다.

### 주요 작업

- `DocumentUploadService`에서 `MultipartFile`과 HTTP response DTO 제거
- `RegisterDocumentCommand`, `DocumentContent`, application result 계약 도입
- `FileStorage`를 infrastructure 패키지에서 application port로 이동
- `DocumentParser`, `ChunkingStrategy`, `EmbeddingProvider`, `EvidenceRetriever` 포트 도입
- PDFBox, 고정 길이 청커, Ollama, pgvector, local storage를 기본 adapter로 분리
- provider descriptor와 capability 계약 추가
- 기본 adapter는 명시적 설정 또는 `@ConditionalOnMissingBean`으로 교체 가능하게 구성
- 패키지 의존 방향을 검사하는 architecture test 추가
- 전수 검사에서 확인한 `DocumentController`, `DocumentUploadService`, `DocumentIndexingProcessor`, `IngestionProperties`, `TextChunker`의 오래된 TXT 전용 JavaDoc을 실제 TXT/PDF 책임에 맞게 정리

### 완료 조건

- core/application 계층이 Spring MVC의 `MultipartFile`을 참조하지 않는다.
- parser, chunker, embedding, search, storage 기본 구현을 교체할 수 있다.
- provider 중복과 잘못된 설정은 기동 시 명확히 실패한다.
- 기존 업로드·색인·검색·실패 복구 테스트가 그대로 통과한다.
- 공용 upload·ingestion·chunking 경로의 JavaDoc이 TXT 전용 지원으로 잘못 한정되지 않고 실제 TXT/PDF 처리 범위와 일치한다.
- 아직 물리적인 대규모 Gradle 멀티모듈 이동은 하지 않는다.

### 실행 프롬프트

```text
PRIZM 오픈소스 엔진 전환 계획의 단계 3을 수행해줘.

권장 설정은 5.6 Sol, 추론 강도 매우 높음이다.

AGENTS.md와 실행 계획을 읽고 현재 upload, ingestion, embedding, search, storage 의존 그래프와 테스트를 먼저 조사해. 이번 단계의 목적은 동작 변경이 아니라 포트와 어댑터 경계 정리다.

DocumentUploadService에서 MultipartFile과 HTTP DTO 결합을 제거하고 application command/result 계약을 도입해. FileStorage를 올바른 port 패키지로 이동하고 DocumentParser, ChunkingStrategy, EmbeddingProvider, EvidenceRetriever 계약을 추가해. 기존 PDFBox, fixed-window chunker, Ollama bge-m3, pgvector exact search, local storage는 기본 adapter가 되게 해. provider ID, version, capabilities를 명시하고 중복 provider는 기동 시 실패시켜.

`docs/project-status.md`에 기록된 오래된 TXT 전용 JavaDoc도 전부 실제 책임에 맞게 고쳐. 범위는 `DocumentController`, `DocumentUploadService`, `DocumentIndexingProcessor`, `IngestionProperties`, `TextChunker`이며, TXT 전용 분기나 PDF 전용 설정처럼 실제 책임이 한 형식에 한정된 설명은 불필요하게 일반화하지 마.

active_version_id, owner 격리, lease/heartbeat, claim_version fencing, retry/backoff, 청크 저장과 ACTIVE 전환의 원자성을 바꾸지 마. 기존 API 응답 계약도 바꾸지 마. 구조화·포트폴리오·멀티테넌시는 추가하지 마. architecture test와 adapter contract test의 최소 기반을 추가하고 전체 관련 테스트를 실행해.

변경 전후 의존 방향, 보존한 동작, 실행한 검증을 보고하고 docs/development-log.md를 갱신해. 독립 검토 전에는 상태를 COMPLETE로 바꾸지 마. commit, push, PR은 하지 마.
```

---

## 단계 4. Canonical Source와 처리 이력 계약

**권장 모델:** `5.6 Sol`
**추론 강도:** `매우 높음`
**선행 단계:** 3

### 목표

검색 결과, 커리어 정보, 포트폴리오 문장이 동일한 재현 가능한 출처 계약을 사용하도록 만든다.

### 주요 작업

- canonical `SourceLocator` 도입
- document/version/chunk, source type/index, page, char range, quote, quote hash 보존
- parser/chunker/embedding provider와 model/schema version을 담는 `ProcessingDescriptor`
- 표시용 한국어 `sourceLabel`과 기계 판독 가능한 source data 분리
- embedding space의 provider/model/dimension/metric 기록
- 현재 DB의 `vector(1024)` 제약과 재색인 정책 문서화
- 필요한 forward migration과 backfill
- 기존 응답에는 호환 필드를 유지하고 새 계약을 추가

### 완료 조건

- source locator로 원문 위치와 버전을 재현할 수 있다.
- `TEXT_CHUNK`와 `PAGE` 계약이 유지된다.
- quote hash 불일치와 owner/version 불일치를 테스트한다.
- 다른 차원의 embedding을 환경변수만 바꿔 지원한다고 표현하지 않는다.
- 기존 검색 API의 응답과 의미가 깨지지 않는다.

### 실행 프롬프트

```text
PRIZM 오픈소스 엔진 전환 계획의 단계 4를 수행해줘.

권장 설정은 5.6 Sol, 추론 강도 매우 높음이다.

AGENTS.md와 실행 계획, 단계 3의 port 계약을 읽고 현재 PageText, TextChunk, IndexedChunk, document_chunks, 검색 응답이 보존하는 출처 정보를 조사해. 기존 migration은 수정하지 말고 다음 forward migration만 사용해.

document ID, document version ID, chunk ID, source type/index, PDF page 또는 TXT char range, quote, quote hash를 표현하는 canonical SourceLocator를 설계·구현해. parser/chunker/embedding provider와 model/schema version을 기록하는 ProcessingDescriptor도 추가해. sourceLabel은 기존 API 호환을 위해 유지하되 canonical 데이터와 분리하고 UI 현지화 값으로 취급해.

현재 vector(1024) DB 계약과 설정값의 관계를 명확히 하고 embedding space 또는 지원 범위를 기록해. 다른 차원의 모델을 실제 migration과 재색인 전략 없이 지원한다고 주장하지 마. processing generation은 현재 active_version 원자성으로 해결되지 않는 구체적인 실패 사례와 테스트가 있을 때만 도입하고, 단지 계획 문서에 있다는 이유로 추가하지 마.

owner/version 불일치, quote hash, TXT/PDF locator, 기존 API 호환성 테스트를 추가하고 전체 관련 테스트를 실행해. docs/development-log.md를 갱신하고 독립 검토 전에는 상태를 COMPLETE로 바꾸지 마. commit, push, PR은 하지 마.
```

---

## 단계 5. 근거 연결 CareerFact 수직 슬라이스

**권장 모델:** `5.6 Sol`
**추론 강도:** `매우 높음`
**선행 단계:** 4

### 목표

전체 커리어 제품이 아니라 `PROJECT`와 `SKILL` 두 종류의 근거 연결 구조화 흐름을 완성한다.

### 주요 작업

- `CareerFact`, `CareerFactSource`, evidence state 도메인
- `EXTRACTED_CANDIDATE`, `USER_CONFIRMED`, `USER_REJECTED`, `INSUFFICIENT_EVIDENCE`
- 숫자·기간·조직·기술명에 대한 evidence policy
- `CareerStructurer` port와 versioned schema
- 규칙 기반 또는 명시적으로 제한된 기본 structurer
- fact 저장과 source 연결을 위한 forward migration
- 후보 생성, 확인, 거절 application use case
- 합성 fixture와 expected fact benchmark

### 완료 조건

- 모든 확정 fact가 하나 이상의 canonical source를 가진다.
- source 없는 수치·기간·조직·기술은 확정할 수 없다.
- 근거 없음은 거짓이 아니라 `INSUFFICIENT_EVIDENCE`다.
- 다른 사용자의 source를 연결할 수 없다.
- LLM을 사용한다면 선택 adapter이며 model/prompt/schema version과 input evidence hash를 기록한다.

### 실행 프롬프트

```text
PRIZM 오픈소스 엔진 전환 계획의 단계 5를 수행해줘.

권장 설정은 5.6 Sol, 추론 강도 매우 높음이다.

AGENTS.md, 실행 계획, canonical SourceLocator 계약을 먼저 읽어. 전체 커리어 제품을 구현하지 말고 PROJECT와 SKILL 두 fact type의 작은 수직 슬라이스만 구현해.

CareerFact, CareerFactSource, evidence state, schema version을 설계하고 forward migration으로 저장해. EXTRACTED_CANDIDATE, USER_CONFIRMED, USER_REJECTED, INSUFFICIENT_EVIDENCE를 구분해. 모든 확정 fact가 같은 owner/profile의 canonical source를 하나 이상 갖도록 application과 DB 경계에서 강제해. 숫자, 기간, 조직, 기술명은 source quote로 검증되지 않으면 확정하지 마.

CareerStructurer port를 만들고 기본 구현은 재현 가능한 규칙 기반 또는 증거 ID 밖의 내용을 생성할 수 없도록 제한된 adapter로 구성해. LLM을 추가한다면 선택적이어야 하며 model, prompt, schema version과 input evidence hash를 기록해. 문서에 없는 내용을 생성하는 테스트와 다른 owner source 연결 거부 테스트를 반드시 포함해.

현재 Career Vault 전체 UI, 채용공고 매칭, 이력서 자동작성은 구현하지 마. 합성 fixture와 expected result를 추가하고 관련 검증을 실행해. docs/development-log.md를 갱신하고 독립 검토 전에는 상태를 COMPLETE로 바꾸지 마. commit, push, PR은 하지 마.
```

---

## 단계 6. 검증된 Portfolio Composer 수직 슬라이스

**권장 모델:** `5.6 Sol`
**추론 강도:** `매우 높음`
**선행 단계:** 5

### 목표

확인된 CareerFact만으로 JSON과 Markdown 포트폴리오를 생성하고 source manifest를 함께 보존한다.

### 주요 작업

- `PortfolioComposer`와 `PortfolioRenderer` port
- JSON과 Markdown 기본 renderer
- template ID/version과 입력 fact snapshot
- artifact content hash와 generation manifest
- 각 문장·항목에서 CareerFact와 SourceLocator로 이어지는 연결
- 근거 없는 문장·숫자의 생성 차단
- 동일 입력의 재현성 또는 명시적 model provenance

### 완료 조건

- 확인된 또는 정책상 지원된 CareerFact만 포트폴리오 입력이 된다.
- 모든 포트폴리오 항목에 fact ID와 source manifest가 있다.
- 근거가 부족한 섹션은 비우거나 중립 상태로 표시한다.
- JSON과 Markdown 결과가 자동 테스트로 검증된다.
- PDF, DOCX, 화려한 템플릿, 완전자동 자기소개서 작성은 범위에 포함하지 않는다.

### 실행 프롬프트

```text
PRIZM 오픈소스 엔진 전환 계획의 단계 6을 수행해줘.

권장 설정은 5.6 Sol, 추론 강도 매우 높음이다.

AGENTS.md, 실행 계획, CareerFact와 SourceLocator 계약을 먼저 읽어. 확인된 CareerFact를 입력으로 받는 PortfolioComposer와 교체 가능한 PortfolioRenderer를 구현해. 첫 출력 형식은 JSON과 Markdown만 지원해.

포트폴리오 artifact에 template ID/version, 사용한 fact ID, document version과 source locator snapshot, 입력 hash, 출력 hash, 생성기 또는 model provenance를 기록해. 모든 생성 항목이 CareerFact와 source manifest로 추적되어야 한다. source 없는 문장, 숫자, 기술, 기간을 추가하면 결과를 거부해. 근거가 부족하면 내용을 채우지 말고 중립적인 미발견 상태를 반환해.

LLM이 직접 최종 Markdown을 자유롭게 작성하게 하지 마. LLM adapter가 필요하면 evidence-bounded candidate 생성에만 사용하고 deterministic policy와 validator를 통과한 뒤 renderer가 출력하게 해. PDF, DOCX, HTML, 다중 디자인 템플릿, 채용공고 자동 최적화는 구현하지 마.

합성 CareerFact로 JSON/Markdown golden test, unsupported content rejection, owner/source 격리, snapshot 재현 테스트를 추가해. docs/development-log.md를 갱신하고 독립 검토 전에는 상태를 COMPLETE로 바꾸지 마. commit, push, PR은 하지 마.
```

---

## 단계 7. 개발자용 API v1과 비동기 통합 계약

**권장 모델:** `5.6 Sol`
**추론 강도:** `매우 높음`
**선행 단계:** 6

### 목표

다른 언어와 프레임워크의 개발자가 PRIZM을 안정적으로 호출할 수 있는 versioned headless API를 제공한다.

### 주요 작업

- `/api/v1` OpenAPI 계약
- 문서 등록과 기존 document에 새 version 추가
- idempotency key와 외부 reference ID
- 처리 상태와 안전한 실패 코드 조회
- evidence search의 `topK`, document type, tag filter
- CareerFact 후보·확인·거절 API
- portfolio 생성과 artifact 조회 API
- 설치된 capability/provider 조회
- 내부 JWT에 종속되지 않는 actor/scope resolver port
- webhook 또는 versioned outbox event 계약은 최소 필요 범위로 도입
- 기존 `/api/search`와 `/api/career-evidence/search` 호환 유지

### 완료 조건

- OpenAPI만으로 upload→processing→search→fact→portfolio 흐름을 이해할 수 있다.
- 비동기 처리에 polling 또는 event 중 최소 하나의 완전한 경로가 있다.
- API가 내부 JPA entity나 storage path를 노출하지 않는다.
- 기존 문서에 version 2를 추가하고 완성 후에만 active pointer가 전환된다.
- 기존 API 회귀 테스트가 통과한다.

### 실행 프롬프트

```text
PRIZM 오픈소스 엔진 전환 계획의 단계 7을 수행해줘.

권장 설정은 5.6 Sol, 추론 강도 매우 높음이다.

AGENTS.md, 실행 계획, 현재 controller/DTO와 단계 3~6의 application 계약을 읽어. 다른 언어의 개발자가 사용할 수 있는 /api/v1 OpenAPI 계약을 설계·구현해.

문서 등록, 기존 document에 새 version 추가, 처리 상태, evidence search, CareerFact 후보·확인·거절, portfolio 생성·artifact 조회, capability/provider 조회를 제공해. idempotency key, 외부 reference ID, 안정된 error code, pagination 또는 명확한 result limit을 포함해. 검색에는 topK와 기존 12개 DocumentType 및 tag filter를 제공해.

내부 JPA entity, local storage path, JWT의 Long user ID 구조를 public contract로 노출하지 마. 인증은 actor/scope resolver port를 통해 reference server의 기존 JWT adapter와 분리해. 현재 개인 사용자는 자기 scope로 매핑해. 비동기 완료는 polling을 완성하고, 실제 소비자가 필요한 경우에만 versioned outbox/webhook 계약을 최소 범위로 추가해.

기존 /api/search 단일 결과와 /api/career-evidence/search 최대 5개 계약은 제거하거나 변경하지 마. 기존 document에 v2를 추가하고 처리 실패 시 v1이 ACTIVE로 유지되며 성공 후에만 전환되는 통합 테스트를 포함해. OpenAPI 검증과 관련 전체 테스트를 실행해. docs/development-log.md를 갱신하고 독립 검토 전에는 상태를 COMPLETE로 바꾸지 마. commit, push, PR은 하지 마.
```

---

## 단계 8. 멀티모듈 패키징과 재사용 예제

**권장 모델:** `5.6 Sol`
**추론 강도:** `높음`
**선행 단계:** 7

### 목표

안정된 경계를 실제 Gradle artifact, starter, server image와 예제로 제공해 재사용성을 증명한다.

### 목표 구조

```text
modules/
  prizm-contracts
  prizm-document-core
  prizm-ingestion
  prizm-evidence-search
  prizm-career-analysis
  prizm-portfolio
  adapter-parser-pdfbox
  adapter-embedding-ollama
  adapter-search-pgvector
  adapter-storage-local
  prizm-spring-boot-starter

apps/
  prizm-server
  career-vault-reference-web

examples/
  personal-vault
  university-portfolio
```

### 완료 조건

- core artifact는 Spring MVC, PDFBox, Ollama, PostgreSQL 구현을 직접 의존하지 않는다.
- starter 기본 adapter는 명시적으로 교체할 수 있다.
- server container와 Java artifact의 버전이 일치한다.
- 개인용과 대학용 예제가 동일 엔진을 서로 다른 설정으로 사용한다.
- adapter contract test kit가 최소 두 구현 또는 기본/fixture 구현을 검증한다.

### 실행 프롬프트

```text
PRIZM 오픈소스 엔진 전환 계획의 단계 8을 수행해줘.

권장 설정은 5.6 Sol, 추론 강도 높음이다.

AGENTS.md, 실행 계획과 단계 3에서 확정된 package dependency, 단계 7의 public contract를 먼저 읽어. 이제 안정된 경계만 Gradle multi-module로 물리적으로 분리해. 한 번에 파일을 이동하기 전에 목표 dependency graph와 이동 순서를 제시하고, 각 이동 뒤 compile/test를 확인해.

contracts, document core, ingestion, evidence search, career analysis, portfolio, 기본 adapters, Spring Boot starter, reference server를 분리해. core가 Spring MVC, PDFBox, Ollama, PostgreSQL adapter를 직접 의존하지 않게 해. Flyway migration은 PostgreSQL adapter가 소유하되 기존 적용 순서와 classpath 위치 호환성을 검증해. Worker scheduler는 starter를 추가했다는 이유만으로 호스트 앱에서 자동 실행되지 않게 명시적 설정을 사용해.

Career Vault는 reference web으로 명시하고, 동일 엔진을 개인 설정과 대학 포트폴리오 설정으로 사용하는 최소 examples를 추가해. 대학 예제 때문에 단계 9의 전체 멀티테넌시를 미리 구현하지 말고 adapter/configuration 재사용성만 증명해. adapter contract test kit와 publication metadata를 추가하고 전체 Gradle, integration, frontend, Compose 검증을 수행해.

docs/development-log.md를 갱신하고 독립 검토 전에는 상태를 COMPLETE로 바꾸지 마. commit, push, PR은 하지 마.
```

---

## 단계 9. 기관용 workspace·profile·권한 모델

**권장 모델:** `5.6 Sol`
**추론 강도:** `매우 높음`
**선행 단계:** 8

### 목표

로그인한 행위자와 커리어 데이터의 실제 주체가 다른 대학·취업지원기관 환경을 안전하게 지원한다.

### 주요 작업

- `workspace`, `membership`, `career_profile`, `actor` 모델
- 개인 사용자를 personal workspace와 자기 profile로 backfill
- `EvidenceScope(workspaceId, careerProfileId, actorId)`
- 역할과 위임 정책: ADMIN, COUNSELOR, MEMBER 등
- 문서·버전·청크·작업·fact·portfolio 전체 scope 격리
- 기관별 custom taxonomy/tag/metadata
- 내부 JWT, OIDC, API key 또는 service account adapter 경계
- 데이터 export, 삭제, 보존기간, audit event 기반

### 완료 조건

- tenant A가 tenant B의 데이터를 조회하거나 연결할 수 없다.
- 같은 workspace에서도 counselor가 권한 없는 profile에 접근할 수 없다.
- 기존 개인 사용자 데이터와 API가 안전하게 backfill된다.
- `owner_user_id` 방어를 무작정 제거하지 않고 새 scope의 중복 방어로 일반화한다.
- SYSTEM_ADMIN은 명시적 위임 없이 개인 커리어 본문을 조회하지 못한다.

### 실행 프롬프트

```text
PRIZM 오픈소스 엔진 전환 계획의 단계 9를 수행해줘.

권장 설정은 5.6 Sol, 추론 강도 매우 높음이다.

AGENTS.md와 실행 계획을 읽고 현재 V8 owner composite FK, JWT 재검증, 검색 SQL, Worker claim/completion/failure의 owner 방어를 모두 조사해. 기관에서는 로그인한 actor와 career profile의 주체가 다르므로 owner_user_id를 tenant_id로 단순 치환하지 마.

workspace, membership, career_profile, actor와 EvidenceScope를 설계하고 forward migration으로 기존 개인 사용자를 personal workspace와 자기 profile에 안전하게 backfill해. 문서, version, chunk, processing job, CareerFact, portfolio artifact에 일관된 scope를 적용해. application policy와 DB constraint/검색 후보 조건을 중복 방어로 사용해.

ADMIN, COUNSELOR, MEMBER의 최소 권한 정책을 정의하고, SYSTEM_ADMIN이 위임 없이 개인 본문을 볼 수 없다는 기존 원칙을 보존해. 내부 JWT는 reference adapter로 유지하고 OIDC/API key/service account를 연결할 port를 제공하되, 실제로 검증하지 않은 identity provider 지원을 주장하지 마. 12개 DocumentType은 유지하고 기관별 분류는 tag/custom metadata로 확장해.

tenant 간, profile 간, counselor 권한, service account scope, Worker claim, vector search, fact/source 연결, portfolio 조회에 대한 isolation 통합 테스트를 추가해. docs/development-log.md를 갱신하고 독립 검토 전에는 상태를 COMPLETE로 바꾸지 마. commit, push, PR은 하지 마.
```

---

## 단계 10. 최종 기능·보안·라이선스·재현성 감사

**권장 모델:** `5.6 Sol`
**추론 강도:** `울트라`
**대체 설정:** `5.6 Sol / 매우 높음`
**선행 단계:** 출시 범위에 포함한 모든 단계

### 목표

새 환경에서의 재현성, 기능 진실성, 라이선스, 보안, 데이터 격리와 대회 시연 흐름을 최종 검증한다.

### 감사 영역

- clean clone Quickstart
- API와 reference app 기능
- adapter 교체 경험
- owner/workspace/profile 격리
- lease/fencing/crash recovery
- source와 CareerFact/portfolio provenance
- migration upgrade와 rollback이 아닌 forward recovery 절차
- dependency/model/sample license
- SBOM, secret scan, container scan
- README, OpenAPI, examples, release notes
- PostgreSQL 결과와 OpenSQL 실환경 결과의 명확한 분리

### 완료 조건

- `./gradlew.bat test --no-daemon`
- `./gradlew.bat integrationTest --no-daemon --rerun-tasks`
- `npm --prefix frontend run lint`
- `npm --prefix frontend run build`
- `docker compose config`
- 실제 사용하는 추가 module별 verification
- 합성 문서 upload→ACTIVE→search→CareerFact→Markdown portfolio 시나리오 성공
- critical 보안·라이선스 finding 0
- 미검증 기능을 지원한다고 주장한 문서 0

### 실행 프롬프트

```text
PRIZM 오픈소스 엔진 전환 계획의 단계 10 최종 감사를 수행해줘.

권장 설정은 5.6 Sol, 추론 강도 울트라다. 울트라를 사용할 수 없으면 매우 높음을 사용해.

AGENTS.md, docs/oss-transition-execution-plan.md, README, project-status, architecture 문서, OpenAPI, migration과 현재 git 상태를 먼저 읽어. 구현된 출시 범위를 확정하고, 범위 밖 기능은 감사 대상에서 제외하되 문서가 구현된 것처럼 주장하는지는 검사해.

감사를 다음 독립 관점으로 나눠 수행해:
- clean clone 설치·Quickstart·Compose 재현성
- 백엔드 도메인·migration·동시성·Worker 복구
- API 계약·하위 호환·reference frontend
- source provenance·CareerFact·portfolio 무근거 생성 차단
- owner/workspace/profile 보안 격리
- 라이선스·SBOM·비밀정보·컨테이너와 공급망
- 문서 진실성·예제·대회 3분 시연 경로

실제 검증 명령을 실행하고 PostgreSQL, pgvector, Ollama, Docker, OpenSQL 사용 여부를 각각 구분해 기록해. OpenSQL 접속이 없으면 PostgreSQL 성공으로 대체하지 마. 먼저 findings를 우선순위별로 제시하고, 출시를 막는 결함만 범위 내에서 수정해. 새로운 기능을 추가해 감사를 통과시키려 하지 마.

최종적으로 합성 문서 업로드→비동기 ACTIVE→출처 검색→PROJECT/SKILL CareerFact→source manifest가 있는 Markdown portfolio 흐름을 재현해. critical 보안·라이선스 finding이 없고 모든 필수 검증이 성공한 경우에만 단계 상태를 COMPLETE로 바꾸고 완료 증거를 링크해. docs/development-log.md와 release notes를 갱신해. commit, push, PR은 하지 마.
```

## 7. 대회 시연의 최종 형태

3분 시연에서는 사용자용 화면의 화려함보다 다음 재사용 장면을 우선한다.

1. `docker compose` 기반 설치와 capability 확인
2. 합성 이력서·프로젝트 문서 업로드
3. Worker 중단 또는 재시도 뒤 원자적 ACTIVE 전환
4. 문서 버전·페이지·원문이 연결된 evidence 검색
5. PROJECT와 SKILL CareerFact 후보 및 source 확인
6. 확인된 fact만 사용하는 Markdown 포트폴리오와 source manifest
7. 같은 엔진을 Personal Vault와 University Example이 다른 설정으로 사용
8. parser 또는 storage adapter 하나를 교체하는 개발자 경험

## 8. 의도적으로 뒤로 미루는 범위

- 완성형 B2C 제품 UI와 가격·결제
- DOCX·PPTX·OCR 전부 지원
- 여러 LLM·vector DB·object storage adapter 동시 구현
- PDF/DOCX 포트폴리오 디자인 시스템
- 기업의 지원자 평가·순위화·합격 예측
- 자동 지원과 근거 없는 자기소개서 생성
- 전체 서비스를 마이크로서비스로 분리
- 실제 검증 없는 OpenSQL·OpenProxy·OpenHA 호환성 주장

## 9. 진행 기록 형식

각 단계 완료 증거에는 다음을 기록한다.

```text
상태:
완료일:
구현 작업:
독립 검토 작업:
변경 파일:
실행한 검증:
실제 사용한 외부 환경:
제외하거나 실행하지 못한 검증:
중요 설계 결정:
남은 위험:
commit/PR: 사용자가 별도로 수행한 경우만 기록
```
