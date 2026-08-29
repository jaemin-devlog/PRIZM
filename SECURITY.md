# 보안 정책

## 지원 범위

PRIZM은 최신 GitHub Release에 공개된 소스 코드를 지원 대상으로 삼습니다. 공개 컨테이너
이미지, 호스팅 서비스, 모델 가중치와 OpenSQL 자산은 제공하지 않습니다. 기본
Docker Compose는 로컬 개발용이며 외부에 공개하는 운영 구성이 아닙니다. 일반 지원
범위는 [지원 정책](SUPPORT.md)을 확인해 주세요.

## 취약점 신고

[GitHub Private Vulnerability Reporting](https://github.com/jaemin-devlog/PRIZM/security/advisories/new)을 이용해
비공개로 신고해 주세요. 취약점 세부 내용은 공개 Issue, Discussion, Pull Request나
로그에 올리지 마세요.

신고에는 가능한 범위에서 다음 정보를 포함해 주세요.

- 영향을 받는 commit이나 버전
- 재현 조건과 필요한 권한
- 예상 영향
- 민감한 값을 제거한 최소 재현 절차

API 토큰, JWT, 비밀번호, 개인 문서와 실제 사용자 데이터는 신고에도 포함하지 말고
안전한 대체값으로 마스킹해 주세요. 유지관리자는 접수 내용을 확인해 재현 여부와
대응 범위를 안내하지만 정해진 응답 시간은 보장하지 않습니다. 수정 전에는 취약점
세부 내용을 공개하지 말아 주세요.

## 범위 참고

인증, 사용자별 데이터 분리, `ACTIVE` 버전 전환과 안전한 파일 정리 방식은 [아키텍처](docs/architecture.md)에 설명돼 있습니다. 이메일 인증, 비밀번호 재설정, OIDC와 공개 SaaS 보호는 현재 구현 범위가 아닙니다.
