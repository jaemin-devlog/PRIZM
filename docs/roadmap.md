# PRIZM 제품 범위

> 기준일: 2026-08-27

PRIZM은 Spring Boot backend와 React frontend로 실행하는 self-hosted 오픈소스
커리어 문서 관리·원문 근거 검색 웹 애플리케이션입니다. 이 문서는 미래 기능
목록을 유지하지 않고 현재 제품 정의와 새 변경을 시작하는 원칙을 고정합니다.
구현·검증 상태는 [현재 구현 현황](project-status.md), 상세 근거와 lifecycle은
[기능별 검증 기록](../specs/README.md)을 따릅니다.

## 현재 제품 정의

- UTF-8 TXT와 text-layer PDF 원본, SHA-256과 변경 불가능한 문서 버전을 보존합니다.
- ChangeLog, Dispatcher와 Worker가 추출·문서 분할·Ollama `bge-m3` 임베딩을
  처리합니다.
- 처리가 끝난 버전만 `ACTIVE`로 전환하고, 실패하면 기존 `ACTIVE` 버전을
  유지합니다.
- 로그인한 사용자의 문서와 현재 `ACTIVE` 버전에서만 관련 원문을 찾고 TXT 구간
  또는 PDF 페이지를 함께 보여 줍니다.
- 문서 목록·상세·버전·원문 보기, 사용자 관리형 문서 태그와 채용공고 항목별
  원문 근거 검색을 제공합니다.
- `search_career_evidence` MCP 도구는 같은 owner-scoped 검색을 읽기 전용으로
  재사용합니다.

검색 결과는 경력의 진위, 경험 보유, 채용 요구 충족, 직무 적합도나 합격
가능성을 판정하지 않습니다. 관련 근거가 없으면 현재 등록된 문서에서 찾지
못했다고 표시합니다.

## 실행·검증 경계

- 기본 로컬 실행은 PostgreSQL 16+pgvector와 호스트 Ollama를 사용합니다.
- OpenSQL 근거는 기록된 단일 서버 direct 연결과 OpenProxy single-Primary 경로에
  한정합니다. PostgreSQL 결과를 OpenSQL 결과로 바꾸어 쓰지 않습니다.
- 기본 Compose는 loopback에 바인딩된 로컬 self-hosted 개발 구성입니다.
- 배포물은 Apache-2.0 source-only 범위이며 DB volume, 업로드 원본, 모델 가중치와
  OpenSQL 공급 자산을 포함하지 않습니다.

검색 연구의 lifecycle과 현재 제품 검색은 분리해 읽습니다. PRZ-008·PRZ-016의
원문 판정과 현재 검색 진입점은
[연구·미채택 기록](../specs/README.md#연구미채택-기록)에서 확인합니다.
역사적 비채택 결정도 [같은 기록](../specs/README.md#연구미채택-기록)에
원문 상태로 보존합니다.

## 새 변경을 시작하는 방법

새 제품 변경은 미리 적어 둔 backlog에서 가져오지 않습니다. 실제 필요가 생겼을 때
Issue로 문제와 사용자 영향을 확인하고, 새 `PRZ-###` Spec에서 범위·비범위·보존
계약과 검증 방법을 정의합니다. 구현 여부는 source code, 적용된 Flyway migration,
실행 가능한 test와 필요한 환경 evidence로만 판정합니다.

문서 설명만으로 기능을 제품 범위에 추가하지 않습니다. 작업을 미루거나 채택하지
않는 결정은 관련 Spec과 Evidence에 이유와 당시 판정을 남겨 역사 기록으로
보존합니다.
