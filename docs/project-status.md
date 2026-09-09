# PRIZM 현재 구현 현황

> 기준일: 2026-09-02
>
> PRZ-020 기능 통합 근거: [PR #62](https://github.com/jaemin-devlog/PRIZM/pull/62), 병합 `adb033b`
>
> PRZ-021 기능 통합 근거: 구현 `a0c2977`, [PR #65](https://github.com/jaemin-devlog/PRIZM/pull/65), 병합 `60e5fc6` — main 통합 완료
>
> PRZ-022 Evidence 감사 기준선은 `3af4db0`입니다. PostgreSQL Worker·USER A/B/C 격리·cleanup D1–D6와 Linux `LocalFileStorage` 23건을 실제 실행했습니다. 검색은 동결 원시 자료의 무결성만 재검증했으며 현재 정확도로 확대하지 않습니다. [최종 판정](../specs/PRZ-022-backend-reliability-evidence/evidence.md)은 `BACKEND_EVIDENCE_READY`입니다.

현재 상태는 소스 코드, 적용된 Flyway migration과 실행 가능한 test를 기준으로 판단합니다. Spec과 이 문서는 구현 증거를 대신하지 않습니다.

## 한눈에 보기

| 구분 | 현재 상태 |
|---|---|
| 제품 | Spring Boot와 React로 만든 PRIZM 웹 애플리케이션 |
| 핵심 흐름 | 업로드 → 변경 기록 → 백그라운드 처리 → 임베딩 → 검색 대상 전환 → 경력 근거 검색 |
| 문서 | UTF-8 TXT, 텍스트가 포함된 비암호화 PDF |
| 검색 결과 | 관련 원문, 문서·버전, TXT 구간 또는 PDF 페이지 |
| 확장 기능 | 문서 태그, 채용공고 항목별 근거 검색, 읽기 전용 MCP 검색 |
| 데이터 경계 | 활성 `USER`별 문서·버전·처리 작업·검색 결과 분리 |
| 배포 범위 | Apache-2.0 소스코드 배포. DB, 모델, OpenSQL 자산은 포함하지 않음 |

## 현재 사용자 흐름

```text
회원가입 → 로그인
→ TXT/PDF 업로드
→ 원본·새 버전·변경 기록(ChangeLog) 저장
→ 작업 전달기(Dispatcher)가 처리 작업 생성 또는 재사용
→ 백그라운드 처리기(Worker)가 추출·분할·임베딩
→ 성공한 버전을 `ACTIVE`, 즉 검색 대상으로 전환
→ 관련 원문과 TXT 구간/PDF 페이지 확인
```

문서에 태그를 연결해 같은 이름으로 관련 근거를 찾을 수 있습니다. 채용공고에서는 필요한 항목을 직접 선택하고 항목별 근거를 확인할 수 있습니다. MCP client는 Bearer JWT로 같은 검색을 읽기 전용으로 호출합니다.

## 구현된 기능

### 인증과 사용자 격리

- 이메일·비밀번호 기반 자체 호스팅 회원가입과 JWT 로그인
- 서버 기동과 분리된 일반 사용자 가입 흐름. 계정 bootstrap 없음
- 요청마다 사용자의 활성 상태, 이메일과 역할을 DB에서 재확인
- 문서, 버전, 처리·정리 작업과 검색 결과의 사용자별 데이터 분리
- 현재 역할은 `USER` 하나이며 역할 기반 데이터 우회 권한 없음
- V17에서 기존 `SYSTEM_ADMIN` 계정을 비활성화·`USER` 전환하고 소유 관계는 보존

이메일 인증, 비밀번호 재설정, refresh token, OIDC와 공개 SaaS 운영 보호는 제공하지 않습니다.

### 문서와 버전

- UTF-8 TXT와 텍스트가 포함된 비암호화 PDF 업로드
- 원본 파일, SHA-256 hash와 변경 불가능한 버전 보존
- 문서 목록·상세·수정·삭제와 새 버전 등록
- PDF 첫 페이지 썸네일, TXT 카드 260자·상세 2,000자 미리보기
- 선택한 TXT/PDF 버전의 원문 열기
- 12개 `DocumentType`과 사용자가 관리하는 문서 태그
- 태그 사용 수를 본문 출현 수가 아닌 `N개 연결 문서`로 표시

### 문서 처리와 상태 변화

- 문서 버전과 사용자별 ChangeLog의 원자적 저장
- Dispatcher의 중복 실행을 막는 ProcessingJob 전달
- 텍스트 추출, 문서 분할(chunking), Ollama `bge-m3` 임베딩과 pgvector 저장
- 처리 완료 뒤에만 `ACTIVE` 전환
- 실패 시 이전 `ACTIVE` 유지, 작업 유효시간·복구·선점 세대 확인으로 이전 Worker 결과 차단
- 안전한 파일 삭제 조건을 만족하지 않으면 정리를 중단하는 fail-closed 동작

### Search V3 리팩토링 branch

- V18은 기존 `document_chunks` 옆에 generation, V3 전용 작업, `RetrievalPassage`, `EvidenceChild`와 두 vector
  계열을 저장하는 shadow schema를 정의함. V19은 검증된 inventory fingerprint와 V2 lifecycle 호환 trigger를,
  V20은 Worker가 claim 뒤 expected manifest를 동결할 수 있는 전이 제약을 추가함
- owner·문서·version·generation composite FK, nullable active-generation pointer와 artifact/vector 중복·orphan
  방지 제약을 포함함
- V3 전용 JDBC job runtime은 full owner·문서·version·generation identity로 claim, lease renew, retry/failure,
  recovery lock과 exact-token reclaim을 수행함
- 실제 PostgreSQL inventory의 key·순서·membership·hash·provenance와 vector 계약을 검증하고,
  `BUILDING → READY`와 같은-version generation 활성화를 full claim fencing 아래 원자적으로 수행함
- Search V3 Worker는 TXT와 text-layer PDF 원문을 구조 분석해 B3 `RetrievalPassage`와
  `EvidenceChild`를 만들고, 동일 BGE-M3로 두 vector 계열을 미리 계산해 generation 단위로 원자 저장함
- Worker는 원문 읽기부터 activation 전까지 lease를 갱신하며, reclaim된 이전 claim의 저장·READY·activation을
  차단함. inactive version은 `READY`와 activation 재시도 상태에 두고 같은 Production active version만 활성화함
- Production Search V2 source·query·API·frontend·MCP는 shadow schema를 직접 사용하지 않음. 다만 V19 trigger는
  V2가 active version을 바꾸거나 해제할 때 stale V3 generation을 `SUPERSEDED`로 바꾸고 shadow pointer만 비움
- opt-in Search V3 scheduler는 현재 active version을 원자 dispatch하고, 일반 claim과 만료 lease exact-token
  recovery를 같은 Worker 경로로 처리함. 기본 설정은 꺼져 있어 Search V2 scheduler와 나란히 존재함
- 비공개 shadow query service는 V3 pointer가 가리키는 `ACTIVE` generation과 `COMPLETED` job만 owner 범위에서 읽고,
  Passage exact cosine Top20 뒤 Top5 Passage 내부 저장 Child vector로 `CHILD_DENSE_V1`을 적용함. 선택된 원문 근거는
  문서별 상위 비중복 Passage 최대 2개의 평균 점수로 문서 순서만 정한 뒤 최대 5건을 반환함
- PostgreSQL 16+pgvector Testcontainers와 실제 로컬 Ollama `bge-m3`로 TXT 색인·activation·query smoke를 통과함.
  전체 backend `check`는 unit `657`건과 integration `164`건에서 failure/error `0`이며 OpenSQL은 `NOT_RUN`
- Search V3 API/cutover는 구현하지 않았고 Production Search V2는 계속 기본 검색임
- Search V3 job fencing은 PostgreSQL 시나리오 `6/6`에서 concurrent duplicate claim 0, recovery token과
  stale claim·cross-lineage 차단을 확인했으며 자세한 범위는 PRZ-038 evidence를 따름
- Search V3 inventory·activation은 PostgreSQL 시나리오 `11/11`에서 exact inventory, READY, 첫 activation,
  같은-version 재색인, rollback·동시성과 V2 active-version 변경 경계를 확인했으며 자세한 범위는 PRZ-039 evidence를 따름

### 검색과 원문 위치

- 단일 결과와 최대 5개의 경력 근거 결과
- TXT `TEXT_CHUNK`, PDF `PAGE` 원문 위치 정보
- 원문을 보존하면서 관련 1–3문장을 먼저 표시
- 문서, 버전, 관련도와 전체 원문 확인. TXT 결과는 해당 version의 문서 상세,
  PDF 결과는 해당 version의 페이지 원문으로 이동
- 문서 태그 이름을 질문으로 사용하되 태그가 연결된 문서로 검색 범위를 제한하지 않음

검색은 관련 근거를 찾고 원문 위치를 연결합니다. 경력의 진위, 경험 보유 여부, 채용 요구 충족, 직무 적합도나 합격 가능성은 판정하지 않습니다.

### 채용공고 항목별 근거 검색

- 채용공고를 LLM 없이 줄·목록·문장 경계로 분리
- 사용자가 검색할 항목을 직접 선택
- 기존 경력 근거 검색을 사용해 항목별 결과를 표시
- 결과에서 PDF 페이지 또는 TXT 문서 상세로 이동
- 추가 문맥이 없는 결과도 비활성 상태로 표시해 문서 보기와 문맥 보기의 위치를 통일

채용공고 저장, 태그 필터, 적합도 판정과 별도 검색 알고리즘은 포함하지 않습니다. PRZ-017은 PR #53, 소스 `94715cf`, `main` 병합 `b78ec42`로 통합돼 `VERIFIED`입니다.

### MCP

- `POST /mcp`, stateless Streamable HTTP, protocol `2025-11-25`
- `search_career_evidence`와 `{"query":"..."}` 입력
- 활성 `ROLE_USER` Bearer JWT 필요
- REST와 같은 검색 및 사용자별·`ACTIVE` 버전별 데이터 분리 적용

연결 방법은 [MCP로 경력 근거 검색하기](quickstart.md#mcp로-경력-근거-검색하기)를 따릅니다.

## 검증 경계

| 범위 | 판정 | 근거 |
|---|---|---|
| PostgreSQL·pgvector 새 설치 환경 | `VERIFIED` | PRZ-004, PR #25 |
| OpenSQL direct `:5432` + Ollama E2E | `VERIFIED` | PRZ-005, PR #26 |
| ChangeLog 동기화 | `VERIFIED` | PRZ-010, PR #39 |
| 문서 처리 상태 화면 | `VERIFIED` | PRZ-011, PR #41 |
| 검색 근거 표시 | `VERIFIED` | PRZ-012 |
| OpenProxy 단일 Primary 실행 경로 | `VERIFIED` | PRZ-013 |
| 읽기 전용 MCP 전체 흐름 | `VERIFIED` | PRZ-015, PR #46 |
| 문서 태그 | `VERIFIED` | PRZ-009, [PR #51](https://github.com/jaemin-devlog/PRIZM/pull/51), 병합 `d44f30e` |
| 현재 적용 검색 구조 | 현재 source 확인 | [PRZ-016 현재 검색 문서](../specs/PRZ-016-search-performance-v2/README.md); 당시 통합 [PR #50](https://github.com/jaemin-devlog/PRIZM/pull/50), 병합 `3cfe9dc` |
| 채용공고 항목별 근거 검색 V1 | `VERIFIED` | PRZ-017, PR #53, 병합 `b78ec42` |
| 문서 상세 미리보기 페이지 | `VERIFIED` | PRZ-018, [PR #56](https://github.com/jaemin-devlog/PRIZM/pull/56), 병합 `a9ca679` |
| 채용공고 근거 보기 동작 통일 | 통합됨 | [PR #58](https://github.com/jaemin-devlog/PRIZM/pull/58), 병합 `12acb3a` |
| 태그 연결 문서 수·TXT 미리보기·TXT/PDF 원문 보기 | `VERIFIED` | PRZ-019, [PR #60](https://github.com/jaemin-devlog/PRIZM/pull/60), 병합 `01d6c46` |
| 인증 초기화 제거와 단일 `USER` 역할 전환 | `VERIFIED` | PRZ-020, [PR #62](https://github.com/jaemin-devlog/PRIZM/pull/62), 병합 `adb033b` |
| Fresh Clone 첫 사용자 경험 정합화 | `VERIFIED` | 구현 `a0c2977`, [PR #65](https://github.com/jaemin-devlog/PRIZM/pull/65), 병합 `60e5fc6`, [PRZ-021 검증 기록](../specs/PRZ-021-first-user-experience/evidence.md) |
| Worker·USER 격리·cleanup·Linux 파일 저장소 재검증 | `VERIFIED` | 기준선 `3af4db0`, [PRZ-022 검증 기록](../specs/PRZ-022-backend-reliability-evidence/evidence.md) — 검색은 과거 동결 자료 무결성만 재확인 |
| 프로젝트 최종 Closeout | `VERIFIED` | final baseline `6e966e5`, [PRZ-023 검증 기록](../specs/PRZ-023-project-closeout/evidence.md) — OpenSQL은 final main에서 재실행하지 않은 역사 근거 |
| `v1.0.0` 소스 릴리스 | `VERIFIED` | [GitHub Release](https://github.com/jaemin-devlog/PRIZM/releases/tag/v1.0.0), [PRZ-024 근거](../specs/PRZ-024-release-v1.0.0/evidence.md) — source-only, release source `76a8748` |

PostgreSQL 성공은 OpenSQL 증거가 아닙니다. OpenSQL·OpenProxy 결과는 기록된 단일
서버 direct 연결과 single-Primary 실행 경로에 한정합니다. 명령, 환경과 수치는
[기능별 검증 기록](../specs/README.md)에서 확인할 수 있습니다.

## 검색 연구·검증 이력

- PRZ-008은 제품에 통합된 검색 범위와 별개로 일부 최적화 검증을 완료하지 않아
  원문 lifecycle 상태 `IN_PROGRESS`를 보존합니다.
- PRZ-016은 현재 검색 구조를 통합한 뒤에도 P15 `NOT_VERIFIED`와 제품에 적용하지
  않은 P16 `NEEDS_ADJUSTMENT`를 역사 판정으로 보존해 lifecycle 상태가
  `IN_PROGRESS`입니다.
- 이 상태들은 현재 기능 개발이 진행 중이라는 뜻이 아닙니다. 현재 검색과 연구
  기록의 경계는 [PRZ-016 검색 문서 안내](../specs/PRZ-016-search-performance-v2/README.md)를
  따릅니다.

## 알려진 한계

- 이미지로만 된 PDF와 암호화 PDF는 지원하지 않습니다.
- 전체 처리 시간과 버전당 최대 문서 조각 수 제한은 없습니다.
- 브라우저 E2E 전체 자동화는 제공하지 않습니다.
- 일부 파일시스템에서 안전한 삭제 기능을 사용할 수 없으면 자동 파일 정리를 중단합니다.
- 기본 Compose는 로컬 개발용이며 공개 SaaS 배포 구성이 아닙니다.
- Spring AI/MCP Java SDK `2.0.0` stateless server는 정상적인
  `notifications/initialized` 뒤에도 handler warning을 남길 수 있습니다.

## 상세 문서

- [아키텍처](architecture.md)
- [빠른 시작](quickstart.md)
- [OpenSQL 검증 기록](opensql-gate.md)
- [PRZ-016 검색 문서 안내](../specs/PRZ-016-search-performance-v2/README.md)
- [기능별 검증 기록](../specs/README.md)
