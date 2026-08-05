# PRZ-007 자체 호스팅 회원가입 — Plan

최신 사용자 지시에 따라 문서 파일은 코드 검증 뒤 작성했지만, 아래 범위와 완료
조건은 구현 전에 고정했다.

## 배치

1. 기존 auth controller/service와 `LoginRequest`를 재사용해 `POST /api/auth/signup`,
   중복 `409`, BCrypt와 서버 고정 `USER`를 구현하고 단위 테스트한다.
2. 로컬 데모 클래스 5개와 공개 경로·설정을 제거하고 통합 시나리오를 회원가입 기반
   JWT 재검증·owner isolation으로 바꾼다.
3. 기존 로그인 화면 안에 최소 회원가입 모드를 추가하고 local-demo 호출을 제거한 뒤
   lint/build를 실행한다.
4. 현재 문서와 evidence를 실제 결과에 맞춰 갱신하고 전체 회귀·범위·보안을 감사한다.

## 안전·호환성

- `users`의 unique email과 기존 role 구조를 사용하며 migration·dependency를 추가하지 않는다.
- 중복 사전 조회 뒤 `saveAndFlush`의 unique 충돌도 같은 `409`로 처리한다.
- signup만 공개하고 문서·검색·현재 사용자 API의 정책은 바꾸지 않는다.
- 실패 시 신규 signup 변경과 로컬 데모 삭제를 함께 되돌릴 수 있으며 기존 데이터
  schema와 JWT는 영향을 받지 않는다.
- 브랜치·commit·push·PR은 사용자 금지 범위이므로 수행하지 않는다.
