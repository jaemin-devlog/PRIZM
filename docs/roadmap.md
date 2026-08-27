# PRIZM 제품 범위와 향후 방향

> 기준일: 2026-08-27

현재 `main`에 통합된 구현 범위와 일정이 정해지지 않은 장기 방향을 구분합니다. 구현·검증 상태는 [현재 구현 현황](project-status.md), 상세 근거는 [기능별 검증 기록](../specs/README.md)을 기준으로 확인합니다.

## 현재 구현 범위

현재 저장소에는 Spring Boot와 React로 만든 PRIZM 웹 애플리케이션이 있습니다. 다음 흐름을 `main`에 통합했습니다.

- TXT/PDF 원본과 변경 불가능한 버전 보존
- 변경 기록(ChangeLog) 기반 비동기 추출·문서 분할·임베딩
- 처리가 끝난 버전만 `ACTIVE`로 전환
- 사용자별 경력 근거 검색과 원문 위치 연결
- 사용자가 관리하는 문서 태그
- 채용공고 항목별 근거 검색
- 기존 검색을 재사용하는 읽기 전용 MCP 도구
- PostgreSQL·pgvector 로컬 경로와 단일 서버 OpenSQL·OpenProxy 검증 경로

PRZ-017은 [PR #53](https://github.com/jaemin-devlog/PRIZM/pull/53), `main` 병합 커밋 `b78ec42`로 통합됐습니다. 현재 진행 중인 기능 개발 단계는 없습니다.

## 남아 있는 검증 기록

PRZ-008과 PRZ-016에는 완료하지 않았거나 채택하지 않은 평가 항목이 남아 있어 목록의 형식 상태를 `IN_PROGRESS`로 보존합니다. 이는 현재 기능 개발이 진행 중이라는 뜻이 아닙니다.

- PRZ-008의 일부 문서 분할·검색 처리 최적화 검증은 완료되지 않았습니다.
- PRZ-016의 P15 인증 PDF 페이지 이동은 `NOT_VERIFIED`입니다.
- PRZ-016 P16 literal candidate 실험은 `NEEDS_ADJUSTMENT`였고 현재 검색에 적용하지 않았습니다.

현재 적용된 검색과 알려진 한계는 [검색 최종 요약](../specs/PRZ-016-search-performance-v2/SEARCH-FINAL-SUMMARY.md)에 정리돼 있습니다.

## 일정이 정해지지 않은 장기 방향

다음 항목은 현재 구현이 아니며 착수 일정도 정하지 않았습니다.

- 원문과 연결된 구조화된 경력 정보
- 검증된 근거로 만드는 JSON·Markdown 포트폴리오와 원문 출처 목록
- 교체 가능한 문서 분석기, 분할기, 임베딩, 벡터 DB와 저장소 어댑터
- `/api/v1`, OpenAPI와 webhook/outbox
- 독립 실행 가능한 커리어 문서 분석·검색 모듈과 멀티모듈 패키징
- 기관용 workspace, profile, membership와 권한

새 작업을 시작한다면 소스와 실행 검증으로 범위를 다시 확정합니다. 계획만으로 구현됐다고 표시하지 않습니다.

## 제품 범위에서 제외한 항목

- 다중 OpenSQL DB node와 DB 장애 전환
- OpenProxy 이중화·VIP
- 다중 노드 서비스 연속성 보장

검증 대상 OpenSQL 환경을 단일 서버로 고정했으며, PRZ-014에서 다중 노드 구성을 검토 후 거절했습니다.
