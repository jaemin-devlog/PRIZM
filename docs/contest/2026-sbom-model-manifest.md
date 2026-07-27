# PRIZM 2026 SBOM 및 AI 모델 명세

| 항목 | 값 |
|---|---|
| 관련 spec | [PRZ-002](../../specs/PRZ-002-open-source-readiness/spec.md) |
| 구현 단위 | T-05 — SBOM·AI 모델 명세 |
| 상태 | `IMPLEMENTED_UNVERIFIED` |
| 기준 배포물 | source-only Git repository / source ZIP |
| 마지막 생성일 | 2026-07-27 |

이 문서는 PRIZM의 현재 source-only 배포물을 위한 기계 판독용 공급망
기록의 범위와 재현 방법을 설명한다. 이것은 OpenSQL, OpenProxy 또는 OpenHA
호환성 증거가 아니며, PostgreSQL 기반 개발·테스트 결과를 OpenSQL 결과로
바꾸어 말하지 않는다.

## 배포 경계

현재 공개 배포물에는 PRIZM source, 문서, 실행 설정, Gradle Wrapper와
synthetic fixture만 포함된다. Java JAR, frontend `dist`, container image,
PostgreSQL·pgvector image/volume, Ollama binary, `bge-m3` model weights/cache,
실제 업로드 문서는 포함하거나 재배포하지 않는다.

따라서 SBOM은 source archive에 포함된 파일과 사용자가 직접 설치·다운로드하는
운영 구성요소를 구분해 기록한다. fat JAR, bundle, image, Ollama binary 또는
model bytes를 실제로 배포하려면 해당 산출물별 SBOM·NOTICE·license 감사를
다시 열어야 한다.

## 기계 판독용 기록

| 파일 | 형식·범위 | 생성 입력 | 현재 확인 |
|---|---|---|---|
| [`../../sbom/prizm-backend-runtime.cdx.json`](../../sbom/prizm-backend-runtime.cdx.json) | CycloneDX 1.6, Java `runtimeClasspath` | Gradle resolved runtime graph | 169 components; timestamp·serial 없음 |
| [`../../sbom/prizm-frontend.cdx.json`](../../sbom/prizm-frontend.cdx.json) | CycloneDX 1.6, npm runtime/dev/optional inventory | `frontend/package-lock.json` v3 | 183 versioned lockfile entries; timestamp·serial 없음 |
| [`../../sbom/prizm-ai-model-manifest.json`](../../sbom/prizm-ai-model-manifest.json) | PRIZM JSON manifest 1.0 | checked upstream/release/registry identities | Ollama, BAAI `bge-m3`, Ollama registry artifact, Codex assistance 분리 |
| [`../../sbom/prizm-scope-manifest.json`](../../sbom/prizm-scope-manifest.json) | PRIZM JSON manifest 1.0 | [license audit](2026-license-audit.md), [asset audit](2026-asset-provenance-audit.md) | runtime, test/build, CI, container, model, asset scope 분리 |
| [`../../sbom/SHA256SUMS`](../../sbom/SHA256SUMS) | SHA-256 integrity record | 위 네 JSON file | 생성물 drift 검출 |

CycloneDX record의 `specVersion`은 `1.6`으로 고정한다. repository
[`scripts/verify-sbom.mjs`](../../scripts/verify-sbom.mjs)는 JSON parse,
필수 구조, primary component, component 존재, timestamp/serial 부재,
`bom-ref` 전역 고유성, CycloneDX hash algorithm 표준 이름, checksum drift,
local path/JDBC URL/credential-shaped field 부재를 검사한다.
[`verify-sbom.test.mjs`](../../scripts/verify-sbom.test.mjs)는 비표준 `SHA512`와
중복 `bom-ref`를 거부하는 회귀 테스트를 제공한다. 이는 repository의
structural conformance gate이며, 제3자 full-schema validator 도입과 CI gate는
T-09에서 별도로 검증한다.

## 생성기·license 감사

| 생성기 | 버전·근거 | license·배포 | 판정 |
|---|---|---|---|
| backend | `generateBackendSbom` in [`../../build.gradle`](../../build.gradle) | first-party Apache-2.0 Gradle task; resolved `runtimeClasspath`와 build에 이미 있는 Groovy/JDK API만 사용하며 external SBOM plugin을 추가하지 않음 | `VERIFIED_FIRST_PARTY_TOOL` |
| frontend | [`generate-frontend-sbom.mjs`](../../scripts/generate-frontend-sbom.mjs) | Jaemin Jeong의 PRIZM first-party Apache-2.0 source; Node standard-library (`crypto`, `fs`, `path`)만 사용 | `VERIFIED_FIRST_PARTY_TOOL` |
| rejected candidate | `@cyclonedx/cyclonedx-npm` 6.0.0 및 4.0.1 검토 | 6.0.0은 당시 full npm audit에서 high finding 10건을 보였고, 4.0.1은 full audit endpoint가 package tree를 거부해 신뢰 가능한 전이 취약점 판정을 만들지 못함 | `NOT_ADOPTED` |

frontend 생성기는 registry 또는 `node_modules`를 읽거나 요청하지 않는다. lockfile의
version·integrity·resolved URL·명시된 license·dev/optional scope만 옮기며,
없는 license를 추론하지 않는다. backend generator는 existing Gradle dependency
verification metadata로 이미 검증되는 resolved runtime artifact의 SHA-256을
CycloneDX record에 적는다. backend JSON은 운영체제와 무관하게 LF로 끝나며,
동일 Maven module의 platform별 JAR은 `classifier` PURL qualifier와
`prizm:maven-classifier` property로 구분한다. frontend integrity algorithm은
CycloneDX enum인 `SHA-256`·`SHA-384`·`SHA-512`로 변환한다. 이 선택은 외부
generator의 전이 의존성을 source-only 개발 도구에 새로 도입하지 않으면서도
Gradle/npm의 exact resolved inventory를 재현하기 위한 것이다.

사람이 읽는 license·provenance 판단은 계속
[2026 license audit](2026-license-audit.md)가 기준이다. frontend는 human
audit과 machine SBOM 모두 183개 lockfile entry다. backend human audit의
167개 module identity에는 물리 JAR이 없는 platform/BOM 2개가 포함되고,
`netty-codec-native-quic` 한 module은 platform classifier JAR 5개로 해소된다.
따라서 artifact SBOM은 `167 - 2 + (5 - 1) = 169`개이고, 모두 고유한
classifier-aware `bom-ref`를 가진다. 이 조정 규칙의 독립 재검증은 T-05
VERIFY/AUDIT gate로 남아 있다.

## AI model 명세와 source license 경계

PRIZM의 Apache-2.0은 PRIZM이 직접 작성한 source에만 적용된다. 다음 구성요소는
별도의 upstream·license·배포 경계를 가진다.

| 구성요소 | 기록한 identity | 현재 배포 판정 | 제한 |
|---|---|---|---|
| Ollama | v0.32.3 Linux AMD64 archive SHA-256 `2597d74fbe654ef6a37db56f771cf37d4a85c6bde4018127874e3927d3113800` | external prerequisite, `NOT_DISTRIBUTED` | 사용자가 upstream에서 설치 |
| BAAI `bge-m3` | upstream revision `5617a9f61b028005a4858fdac845db406aefb181`, source license `MIT` | model weights `NOT_DISTRIBUTED` | PRIZM은 weights/cache를 Git·release에 넣지 않음 |
| Ollama `bge-m3:latest` registry artifact | manifest SHA-256 `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab`, model·license blob hashes | model weights/cache `NOT_DISTRIBUTED` | BAAI revision에서 registry artifact까지의 변환은 `UNVERIFIED_LINEAGE` |
| Codex | authoring assistance | runtime·redistributed component 아님 | 사용 비율·모델 license·output provenance를 추정하지 않음 |

이 record는 모델 자체의 license를 PRIZM Apache-2.0으로 바꾸지 않는다. 또한
Ollama registry artifact와 BAAI upstream revision의 정확한 conversion lineage를
증명하지 않는다. 이 제한은 source-only 배포를 막지는 않지만 model bytes 재배포와
정확한 lineage 주장을 막는 future release gate다.

## 재생성·검증

Java 17, Node 22.17.0, npm 10.9.2에서 repository root 기준으로 실행한다.

```powershell
.\gradlew.bat generateBackendSbom --no-daemon --dependency-verification=strict
npm --prefix frontend run sbom
node scripts/verify-sbom.mjs --write-checksums
node scripts/verify-sbom.mjs
node --test scripts/verify-sbom.test.mjs
```

`--write-checksums`는 의도적인 inventory 변경을 검토한 뒤에만 사용한다. 일반
검증은 마지막 명령처럼 checksum을 갱신하지 않아야 하며, source input 또는 생성물이
달라지면 실패해야 한다.

이번 구현은 Ollama runtime/model pull, Docker, PostgreSQL, pgvector, OpenSQL,
OpenProxy, OpenHA를 실행하지 않는다. 해당 환경 결과는 각각 `NOT_RUN`이며
OpenSQL 관련 결과는 존재하지 않는다.

## 남은 gate

- T-05: 수정된 human/machine 조정 규칙과 clean checkout 재현의 독립 검증,
  commit·환경·hash evidence 고정, 독립 읽기 전용 감사
- T-09: SBOM formal-schema/structural validation과 regeneration drift를 CI에서
  실행하고, tool·workflow provenance를 다시 검증
- future artifact: JAR, `dist`, image, Ollama binary, model bytes 재배포 전
  artifact-specific NOTICE·SBOM·license coverage
