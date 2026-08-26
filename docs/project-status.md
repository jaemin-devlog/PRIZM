# PRIZM 현재 구현 현황

> 기준일: 2026-08-27
>
> 현재 `main`: `b78ec42e8cd06ebe001dd02fbaf2a3abd0e15d22`
>
> 최종 통합: [PR #53](https://github.com/jaemin-devlog/PRIZM/pull/53)

현재 상태는 소스 코드, 적용된 Flyway migration과 실행 가능한 test를 기준으로 판단합니다. Spec과 이 문서는 구현 증거를 대신하지 않습니다.

## 한눈에 보기

| 구분 | 현재 상태 |
|---|---|
| 제품 | Spring Boot와 React로 만든 Career Vault Reference App |
| 핵심 흐름 | 업로드 → ChangeLog → Worker → embedding → `ACTIVE` → Evidence Search |
| 문서 | UTF-8 TXT, 텍스트가 포함된 비암호화 PDF |
| 검색 결과 | 관련 원문, 문서·버전, TXT 구간 또는 PDF 페이지 |
| 확장 기능 | Document Tags, Job Posting Evidence, 읽기 전용 MCP 검색 |
| 데이터 경계 | 활성 `USER`별 문서·버전·처리 작업·검색 결과 격리 |
| 배포 범위 | Apache-2.0 소스 전용. DB, 모델, OpenSQL 자산은 포함하지 않음 |

## 현재 사용자 흐름

```text
회원가입 → 로그인
→ TXT/PDF 업로드
→ 원본·새 버전·ChangeLog 저장
→ Dispatcher가 색인 작업 생성 또는 재사용
→ Worker가 추출·분할·임베딩
→ 성공한 버전을 ACTIVE로 전환
→ 관련 원문과 TXT 구간/PDF 페이지 확인
```

문서에 태그를 연결해 같은 이름으로 관련 근거를 찾을 수 있습니다. 채용공고에서는 필요한 항목을 직접 선택하고 항목별 Evidence를 확인할 수 있습니다. MCP client는 Bearer JWT로 같은 검색을 읽기 전용으로 호출합니다.

## 구현된 기능

### 인증과 사용자 격리

- 이메일·비밀번호 기반 자체 호스팅 회원가입과 JWT 로그인
- 요청마다 사용자의 활성 상태, 이메일과 역할을 DB에서 재확인
- 문서, 버전, 처리·정리 작업과 검색 결과의 owner isolation
- `SYSTEM_ADMIN`도 개인 `USER` 데이터 경계를 우회하지 않음

이메일 인증, 비밀번호 재설정, refresh token, OIDC와 공개 SaaS 운영 보호는 제공하지 않습니다.

### 문서와 버전

- UTF-8 TXT와 텍스트가 포함된 비암호화 PDF 업로드
- 원본 파일, SHA-256 hash와 변경 불가능한 버전 보존
- 문서 목록·상세·수정·삭제, 새 버전 등록과 PDF 열람
- 12개 `DocumentType`과 사용자 관리형 Document Tag

### 색인과 상태 전이

- 문서 버전과 owner-scoped ChangeLog의 원자적 저장
- Dispatcher의 멱등 ProcessingJob 전달
- 텍스트 추출, chunk 생성, Ollama `bge-m3` 임베딩과 pgvector 저장
- 처리 완료 뒤에만 `ACTIVE` 전환
- 실패 시 이전 `ACTIVE` 유지, lease·recovery·claim-version fencing으로 stale Worker 차단
- 안전한 파일 삭제 조건을 만족하지 않으면 정리를 중단하는 fail-closed 동작

### 검색과 원문 위치

- 단일 결과와 최대 5개의 Career Evidence 결과
- TXT `TEXT_CHUNK`, PDF `PAGE` source metadata
- 원문을 보존하면서 관련 1–3문장을 먼저 표시
- 문서, 버전, 관련도와 전체 원문 확인
- Document Tag 이름을 질의로 사용하되 태그가 연결된 문서로 검색 범위를 제한하지 않음

Search는 관련 근거를 찾고 위치를 연결합니다. 경력의 진위, 경험 보유 여부, 채용 요구 충족, 직무 적합도나 합격 가능성은 판정하지 않습니다.

### Job Posting Evidence

- 채용공고를 LLM 없이 줄·목록·문장 경계로 분리
- 사용자가 검색할 항목을 직접 선택
- 기존 Career Evidence Search를 사용해 항목별 결과를 표시
- 결과에서 PDF 페이지 또는 TXT 문서 상세로 이동

채용공고 persistence, Tag filter, 적합도 판정과 별도 Search algorithm은 포함하지 않습니다. PRZ-017은 PR #53, source `94715cf`, `main` merge `b78ec42`로 통합돼 `VERIFIED`입니다.

### MCP

- `POST /mcp`, stateless Streamable HTTP, protocol `2025-11-25`
- `search_career_evidence`와 `{"query":"..."}` 입력
- 활성 `ROLE_USER` Bearer JWT 필요
- REST와 같은 검색, owner isolation과 `ACTIVE` isolation 적용

연결 방법은 [MCP Quick Start](quickstart.md#mcp-career-evidence-검색)를 따릅니다.

## 검증 경계

| 범위 | 판정 | 근거 |
|---|---|---|
| PostgreSQL·pgvector clean clone | `VERIFIED` | PRZ-004, PR #25 |
| OpenSQL direct `:5432` + Ollama E2E | `VERIFIED` | PRZ-005, PR #26 |
| ChangeLog synchronization | `VERIFIED` | PRZ-010, PR #39 |
| Processing status UX | `VERIFIED` | PRZ-011, PR #41 |
| Search evidence presentation | `VERIFIED` | PRZ-012 |
| OpenProxy single Primary runtime | `VERIFIED` | PRZ-013 |
| Read-only MCP E2E | `VERIFIED` | PRZ-015, PR #46 |
| Document Tags | `VERIFIED` | PRZ-009, [PR #51](https://github.com/jaemin-devlog/PRIZM/pull/51), merge `d44f30e` |
| Search production baseline | 통합됨 | PRZ-016, [PR #50](https://github.com/jaemin-devlog/PRIZM/pull/50), merge `3cfe9dc` |
| Job Posting Evidence V1 | `VERIFIED` | PRZ-017, PR #53, merge `b78ec42` |

PostgreSQL 성공은 OpenSQL 증거가 아닙니다. OpenSQL·OpenProxy 검증은 single-node 환경에 한정하며 다중 노드, DB 장애 전환과 서비스 연속성을 포함하지 않습니다. 명령, 환경과 수치는 [Spec Registry](../specs/README.md)의 Evidence에서 확인할 수 있습니다.

## 남아 있는 검증 기록

- PRZ-008은 통합된 검색 범위와 별개로 일부 최적화 Gate를 완료하지 않아 형식 상태가 `IN_PROGRESS`입니다.
- PRZ-016은 제품 기준선을 PR #50으로 통합했지만 P15 인증 PDF 페이지 이동이 `NOT_VERIFIED`이고, P16 실험은 `NEEDS_ADJUSTMENT`로 Production에 적용하지 않아 형식 상태를 `IN_PROGRESS`로 보존합니다.
- 이 두 상태는 현재 기능 개발이 진행 중이라는 뜻이 아닙니다. 자세한 배경은 [제품 범위와 향후 방향](roadmap.md)을 확인하세요.

## 구현되지 않은 기능

- 구조화 CareerFact와 확인·거절 흐름
- 근거 기반 포트폴리오 생성
- `/api/v1`, OpenAPI와 webhook/outbox
- 독립 Engine artifact와 기관용 workspace
- persistent journal

## 알려진 한계

- 이미지로만 된 PDF와 암호화 PDF는 지원하지 않습니다.
- 전체 처리 시간과 버전당 최대 chunk 수 제한은 없습니다.
- 브라우저 E2E 전체 자동화는 제공하지 않습니다.
- 일부 파일시스템에서 안전한 삭제 기능을 사용할 수 없으면 자동 파일 정리를 중단합니다.
- 기본 Compose는 로컬 개발용이며 공개 SaaS 배포 구성이 아닙니다.
- 공개 저장소에 제품 화면 screenshot은 아직 없습니다.

## 상세 문서

- [Architecture](architecture.md)
- [Quick Start](quickstart.md)
- [OpenSQL Gate](opensql-gate.md)
- [Search Final Summary](../specs/PRZ-016-search-performance-v2/SEARCH-FINAL-SUMMARY.md)
- [Spec Registry](../specs/README.md)
