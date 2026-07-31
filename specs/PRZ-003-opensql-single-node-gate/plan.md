# PRZ-003 계획

## 선택한 접근

1. 공급사가 허용한 VirtualBox의 Rocky Linux 9.7 x86_64 VM 한 대에 OpenSQL
   `single` 구성만 설치한다.
2. 패키지 접근용 NAT와 Windows 호스트 전용의 비공개 고정 Host-only 네트워크를
   분리하고 공용 port forwarding은 사용하지 않는다.
3. 라이선스에 귀속된 hostname·CPU topology와 VM 식별값을 고정하되 정확한 값은
   Git 밖의 비공개 근거로 보존한다.
4. Windows 호스트에서 OpenSQL DB에 직접 연결하고 OpenProxy·OpenHA는 첫 Gate에서
   제외한다.
5. 전용 빈 database, Flyway owner와 최소 권한 runtime role을 분리하고 credential은
   실행 중 메모리와 비공개 환경변수로만 전달한다.
6. Windows 문자셋에 의존하는 TXT 검증은 파일 읽기에 UTF-8을 명시하고 JVM 전체
   encoding은 강제하지 않는다.
7. Linux `SecureDirectoryStream` 경로와 Windows fail-closed 경로를 환경별로
   나누어 검증한다.

## 예상 변경

| 범위 | 예상 변경 |
|---|---|
| 검증 환경 | VirtualBox guest, Host-only 네트워크, NTP와 single-node OpenSQL |
| 테스트 | OpenSQL opt-in infrastructure test와 Windows UTF-8 회귀 교정 |
| 운영 보조 | 일회성 SSH key와 공유 `tmux` 세션 |
| 문서 | Spec·plan·tasks·evidence, 상태·대회 문서의 검증 경계 |
| 제외 | 제품 API, Flyway migration, dependency, Docker Compose와 frontend 기능 |

## 위험

| 위험 | 처리 |
|---|---|
| 지원하지 않는 OS·구성 | Rocky Linux 9.7과 `single` 외 설치 중단 |
| Hostname·topology 변경 | 라이선스 재발급 확인 전 변경 금지 |
| 공급 자산·credential 노출 | 즉시 작업 중단하고 공개 diff·history 영향 확인 |
| 관리자 권한 부족 | 권한 우회 없이 검증된 DB 관리자 인증 방법 확인 |
| 일부 provisioning 뒤 전송 중단 | 대상 재사용 금지, 정확한 전용 DB·role만 정리 |
| Flyway 적용 뒤 Gate 실패 | 실패 대상을 보존하고 새 database·credential로 재시도 |
| Windows 플랫폼 skip | `PASS`로 바꾸지 않고 Linux 재실행 또는 `NOT_RUN` 기록 |
| OpenProxy 관찰 | Runtime·인증 Gate 없이 호환성 주장 금지 |

## 검증 환경

- Windows host: Java 17, Gradle Wrapper와 VirtualBox 관리
- Rocky Linux 9.7 x86_64 guest: 라이선스가 적용된 single-node OpenSQL
- PostgreSQL·pgvector·Ollama: Windows 회귀 검증에서만 사용하며 OpenSQL 근거와 분리
- Linux JDK container: `SecureDirectoryStream` 의존 테스트 재실행
- GitHub Actions: 공개 source·문서와 기존 회귀 확인

OpenSQL Gate 명령은 비공개 endpoint와 분리 credential을 설정한 세션에서 다음
테스트만 실행한다.

```powershell
.\gradlew.bat integrationTest --no-daemon --rerun-tasks `
  --tests com.prizm.infrastructure.OpenSqlInfrastructureTest
```

## Rollback과 중단 조건

- Gate credential은 파일·명령행·공개 log에 남기지 않는다.
- 첫 실패 대상은 빈 대상으로 간주해 재사용하지 않고 원인 확인 뒤 정확한 이름으로
  정리한다.
- 일회성 SSH key, helper와 임시 output은 Gate 종료 뒤 양쪽에서 제거한다.
- Windows UTF-8 교정에 문제가 있으면 해당 test assertion만 되돌린다.
- 공급사의 라이선스 안내가 snapshot·복구를 제한하면 공급사 지침을 우선한다.
- OpenSQL·vector·최소 권한 준비가 확인되지 않으면 Gradle Gate를 시작하지 않는다.

## Dependency·license 고려

- VirtualBox, Rocky Linux와 OpenSQL은 저장소 외부 검증 환경이다.
- 공급 archive, 라이선스, fingerprint, 내부 metadata·설정·log는 복제하거나
  공개 저장소에 넣지 않는다.
- OpenSQL은 source-only PRIZM 배포물에 포함하지 않는 외부 runtime이다.
- 일회성 SSH와 `tmux`는 검증 운영 도구이며 PRIZM runtime dependency가 아니다.
- PostgreSQL·pgvector 성공은 OpenSQL 성공을 대체하지 않는다.

## Branch·PR 계획

- 환경 준비와 Gate 교정은 최신 `main`에서 분기한 `PRZ-003-<slug>` 임시 branch로
  나누어 진행한다.
- 실제 Issue·PR은 외부 쓰기가 승인된 경우에만 만들고 과거 기록을 소급하지 않는다.
- VERIFY와 독립 AUDIT 뒤 실제 변경을 PR로 통합한다.
- Reviewer가 없으면 사용자 승인과 `REVIEW_NOT_AVAILABLE_SOLO`를 기록한다.
- 병합된 `main`을 먼저 push하고 merge base·unique commit·변경 파일·연결 PR을
  확인한 뒤 정확한 임시 branch만 삭제한다.
