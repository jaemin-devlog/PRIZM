# PRZ-017 — 채용공고 항목별 Career Evidence V1 Tasks

## ORIENT / SPEC / PLAN

- [x] branch, HEAD, `origin/main`, dirty/untracked 파일과 stash 목록 확인
- [x] 기존 Search, Evidence UI, PDF/TXT 이동, 인증·owner/ACTIVE 경계 확인
- [x] Qwen/LLM과 과거 requirement/claim 구조의 Production 참조 여부 확인
- [x] migration/persistence 부재와 V9 `JOB_POSTING` DocumentType 경계 확인
- [x] V1 Spec, Plan, Tasks와 초기 Evidence 작성

## IMPLEMENT

- [x] deterministic segmentation DTO/service/controller 구현
- [x] 500자 이하 무손실 분할과 최대 100개 Search fan-out 상한 구현
- [x] segmentation endpoint를 활성 `ROLE_USER` 경계에 추가
- [x] 입력·분리·checkbox·전체 선택/해제·선택 수·재수정 UX 구현
- [x] 선택 항목별 기존 Career Evidence Search orchestration 구현
- [x] 항목별 Evidence/empty/error와 재시도 UI 구현
- [x] 기존 PDF page/TXT document detail 이동 연결
- [x] 금지된 판정·score UI와 Qwen/LLM Production 참조 0 확인
- [x] section heading을 그룹 제목으로 분리하고 career child만 selectable하게 보정
- [x] metadata section/독립 metadata 제외와 Unicode bullet 정규화 보정
- [x] frontend section grouping과 selectable count 표시 보정
- [x] 명확한 alternative compound query를 원문과 최대 5개 variant로 결정적 분해
- [x] 원문 우선 결과 병합·selected chunk dedup·Top 5 제한 구현
- [x] segmentation 결과를 접근 가능한 항목 선택 modal로 분리
- [x] 검색 시작 시 결과 전용 route와 loading 화면으로 전환
- [x] requirement rail → document/version → Evidence row workspace 구현
- [x] 화면상 정확 중복 행 정리와 같은 page의 서로 다른 Evidence 보존
- [x] 항목 다시 선택·입력 복귀·결과 route 직접 진입 안전 처리
- [x] desktop 2열·mobile 단일 열과 PDF/TXT row 이동 유지
- [x] requirement rail을 기록 있음·기록 없음 상태 탭으로 분리
- [x] loading/error를 조건부 확인 필요 탭으로 분리
- [x] 상태 탭 안에서 원래 requirement 순서·번호와 Search 비호출 유지

## VERIFY

- [x] backend segmentation 구조·경계·상한 계약 unit test
- [x] backend controller validation·인증 test
- [x] 선택·다중 Search·그룹 Evidence·empty·error·이동 frontend test
- [x] PRZ-009 Tag, upload/detail, 인증, 기존 Search focused regression
- [x] 전체 backend unit
- [x] PostgreSQL integration
- [x] frontend unit·typecheck·lint·build
- [x] `git diff --check`와 Markdown local link 검사
- [x] Search Production·migration diff 0 확인
- [x] Docker 최신 source rebuild와 인증 compound/PDF targeted browser Gate
- [ ] 기존 44개 전체 원문과 TXT 이동 browser Gate 재실행
- [x] segmentation UX 보정 focused/backend/frontend/integration regression
- [x] Search Production 추가 diff 0과 compound posting Before/After 확인
- [x] compound query frontend unit·typecheck·lint·build
- [x] 동일 owner/ACTIVE PDF에서 Docker·Git compound 실제 브라우저 재검증
- [x] 선택 modal·결과 route·requirement 전환·document grouping frontend test
- [x] 새 UI frontend unit·typecheck·lint·build
- [ ] 실제 브라우저 modal → 결과 workspace → PDF page Gate — `BLOCKED_BY_AUTH_ENVIRONMENT`
- [x] 결과 상태 탭 count·전환·empty/error 구분 frontend test
- [ ] Docker 최신 frontend에서 기록 있음·기록 없음 탭 browser Gate — `BLOCKED_BY_AUTH_ENVIRONMENT`

## AUDIT / INTEGRATE

- [x] Spec acceptance criteria와 최종 diff 독립 감사
- [x] blocking finding 0 확인 또는 수정 뒤 재감사
- [x] Production 주석 `6cc4726`, PRZ-017 source `de98bcf`를 현재 branch에 commit·push
- [x] PR/merge는 사용자 지시에 따라 `NOT_RUN`
- [x] stash와 unrelated dirty/untracked 파일 보존
- [x] 새 UI diff 독립 감사와 보호 대상 해시 재확인
