# PRIZM 현재 구현 현황

> 기준일: 2026-08-29
>
> PRZ-020 기능 통합 근거: [PR #62](https://github.com/jaemin-devlog/PRIZM/pull/62), 병합 `adb033b`
>
> 현재 작업: PRZ-021 Fresh Clone 첫 사용자 경험 정합화는 구현 commit `a0c2977`로 검증됐으며 [PR #65](https://github.com/jaemin-devlog/PRIZM/pull/65)가 열려 있음

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
| Fresh Clone 첫 사용자 경험 정합화 | `VERIFIED` (PR open) | 구현 `a0c2977`, [PR #65](https://github.com/jaemin-devlog/PRIZM/pull/65), [PRZ-021 검증 기록](../specs/PRZ-021-first-user-experience/evidence.md) |

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
- 공개 저장소에 제품 화면 이미지는 아직 없습니다.
- Spring AI/MCP Java SDK `2.0.0` stateless server는 정상적인
  `notifications/initialized` 뒤에도 handler warning을 남길 수 있습니다.

## 상세 문서

- [아키텍처](architecture.md)
- [빠른 시작](quickstart.md)
- [OpenSQL 검증 기록](opensql-gate.md)
- [PRZ-016 검색 문서 안내](../specs/PRZ-016-search-performance-v2/README.md)
- [기능별 검증 기록](../specs/README.md)
