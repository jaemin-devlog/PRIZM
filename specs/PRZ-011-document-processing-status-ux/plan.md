# PRZ-011 구현 계획

## 선택한 접근

기존 ProcessingJob과 문서 조회 API를 확장하는 작은 수직 변경으로 구현한다. 별도
진행 작업 테이블이나 push 채널은 만들지 않는다. 기존 retry 필드를 그대로 읽고,
현재 없는 진행·안전 오류 정보만 V15 컬럼으로 추가한다.

## 예상 변경

1. `V15__add_processing_job_progress.sql`
   - `progress_stage`, `completed_chunks`, `total_chunks`, `failure_code` nullable 컬럼과
     값 범위 check를 추가한다.
   - 기존 row를 backfill하지 않고 기존 migration을 수정하지 않는다.
2. ingestion domain/repository/service
   - 처리 단계와 안전 실패 코드 enum을 추가한다.
   - claim SQL이 새 처리 시도를 `FILE_READING`에서 시작하고 이전 진행/오류를 지운다.
   - 별도 짧은 transaction의 진행 서비스가 owner/status/claim-version fenced UPDATE를
     수행한다.
   - processor는 읽기·추출·청킹 직전 단계와 실제 embedding 완료 수를
     정수 퍼센트 checkpoint·최종 청크에서 기록한다.
   - completion/failure/recovery는 기존 원자성·retry 정책을 유지하며 최종 진행/오류
     필드만 함께 확정한다.
3. embedding failure 분류
   - Ollama exception cause chain의 allowlist 패턴으로 model 미설치와 GPU/model runner
     실패를 연결 실패와 구분한다.
   - coordinator는 원래 exception을 서버 로그에 남기고 안전 코드만 job에 저장한다.
4. 문서 API
   - 요약과 버전 응답에 단계, n/N, nullable 실제 퍼센트, retry 횟수·최대·실제 시각,
     안전 오류 코드를 추가한다.
   - 내부 `error_message`는 반환하지 않는다.
5. frontend
   - 문서 목록 또는 열린 상세에 비종료 상태가 있을 때만 약 2초 silent polling을
     수행하고 terminal 응답 뒤 timer를 중지한다.
   - status를 우선해 `COMPLETED`만 완료로 표시하고, 전체 청크 수 전에는
     spinner+단계, 확정 뒤에는 embedding n/N과 실제 비율,
     retry 때는 실제 countdown/횟수, 실패 때는 안전 메시지를 표시한다.
6. 문서
   - 현재 architecture와 project status에 구현 사실과 제한을 최소 반영하고,
     evidence에 실제 VERIFY 결과만 기록한다.

## 보안·ownership·동시성

- API는 기존 current user와 owner-scoped repository 조회만 사용한다.
- 진행 UPDATE는 `id`, `owner_user_id`, `status=PROCESSING`, `claim_version`을 모두
  조건으로 사용한다. 불일치는 stale claim으로 중단한다.
- failure code는 enum allowlist만 저장·직렬화하며 원래 stack/cause, 내부 경로와 URL은
  API에 포함하지 않는다.
- 완료 transaction의 chunk 교체, version ACTIVE, active pointer, job COMPLETED 원자성과
  기존 active version 보존 계약은 변경하지 않는다.

## migration·호환성

- 현재 마지막 migration V14 다음의 V15만 추가한다.
- 새 컬럼은 nullable이므로 기존 row와 구버전 reader를 깨지 않는다.
- 새 코드는 V15 적용을 전제로 하므로 rollback은 migration 삭제가 아니라 새 코드와
  V15를 유지한 roll-forward 수정으로 수행한다.
- 배포 중 구버전 worker와 신버전 API가 섞이면 진행 필드가 null일 수 있으며 UI는
  spinner/status만 표시해 fail-safe로 동작한다.

## 테스트 계획

- backend unit
  - Ollama 연결/model/runtime 분류
  - 진행 서비스 owner/claim fencing과 processor 단계/n/N
  - failure/recovery safe code, completion 100%
  - 문서 API 필드와 내부 오류 비노출
  - 15,000 청크의 progress UPDATE checkpoint 최대 100회
- backend integration
  - V15 schema/check와 정상 색인 완료·기존 검색 회귀
  - retry 정보와 owner-scoped 조회
- frontend
  - status 우선 표시 unit test, typecheck를 포함하는 build와 lint
  - 실행 브라우저에서 자동 상태 반영, retry 표시, terminal polling 중지 관찰
- 공통
  - `docker compose config --quiet`
  - `git diff --check`

## 실환경 검증

현재 PostgreSQL·pgvector Compose와 host Ollama `bge-m3`를 사용한다. 합성 문서를 새로
업로드해 DB 단계와 API/UI 전이를 확인한다. Ollama 연결 실패와 runtime 실패 구분은
unit/integration fixture로 검증하며 정상 Ollama/GPU 프로세스를 의도적으로 중단하거나
GPU 설정을 바꾸지 않는다.

PostgreSQL 결과를 OpenSQL 증거로 표현하지 않는다. 이번 VERIFY에서 OpenSQL은 필수
대상이 아니며 실행하지 않으면 `NOT_RUN`으로 기록한다.

## 중단·복구 조건

- 검색/P18, retry 정책, chunk 계약 또는 active version 원자성을 바꿔야 하면 구현을
  중단하고 SPEC/PLAN으로 돌아간다.
- 진행 UPDATE가 stale worker를 허용하거나 owner 조건을 잃으면 VERIFY로 진행하지 않는다.
- 필수 unit/integration/lint/build 또는 실제 정상 문서 흐름이 실패하면 VERIFY Gate는
  `FAIL`이며 `VERIFIED`로 기록하지 않는다.

## branch·통합 경계

- 임시 branch: `PRZ-011-document-processing-status-ux`
- 재-AUDIT까지 승인되었다. commit, push, PR, INTEGRATE는 수행하지 않는다.

## PLAN Gate

- 모든 acceptance criterion이 migration/source/test/실환경 확인과 연결됐다.
- migration, ownership, fencing, retry, active version, 검색 보존 영향이 빠지지 않았다.
- 실패 시 중단 및 roll-forward 복구 방법이 있다.

판정: `PASS`
