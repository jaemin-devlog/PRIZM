# PRIZM 2026 배포·라이선스·SBOM 결론

> 기준일: 2026-08-01
>
> 이 문서는 사람이 빠르게 확인할 수 있는 compliance 결론이다. 패키지별
> 전체 identity, exact version과 checksum은 [기계 판독용 SBOM](../../sbom/README.md)을
> 단일 원본으로 사용한다. 이 문서는 법률 자문이 아니다.

> 아래 source-only `VERIFIED` 결론은 PRZ-002 source commit
> `f54e3d98e3eddc20dc3c89d9b3e2b84e1649bea1`과 GitHub CI 기준
> `777e184f206d2a2770d055940ddabf139abfed9d`의 역사적 결과다. PRZ-004 local
> 구현 commit `25d09e9eee9837cf4a63d7461699825ff22743e2`의 frontend
> SBOM·checksum·npm audit·OSS readiness는 검증을 통과했다. 다만 독립 최종
> `AUDIT`와 GitHub 통합 전이므로 상태는 `IMPLEMENTED_UNVERIFIED`다. 이 결과는
> 공개 main 결론을 대체하지 않는다.

> 공개 GitHub main에는 PRZ-004가 아직 통합되지 않았다. local 검증 source는
> `25d09e9eee9837cf4a63d7461699825ff22743e2`이며, GitHub push·PR·CI·review·merge는
> 모두 `NOT_RUN`이다.

## 한눈에 보는 결론

| 항목 | 현재 결론 |
|---|---|
| 공개 범위 | source-only Git repository와 source ZIP |
| PRIZM 직접 작성 source | `Apache-2.0`; root [`LICENSE`](../../LICENSE)와 [`NOTICE`](../../NOTICE) 적용 |
| 최종 source-only 검증 | source commit `f54e3d98e3eddc20dc3c89d9b3e2b84e1649bea1`, 2026-07-30 |
| 현재 blocker | source-only 범위에서 `UNKNOWN`·`CONFLICT`·`BLOCKED` 0 |
| CI | commit `777e184f206d2a2770d055940ddabf139abfed9d`; [OSS Readiness 30477035697](https://github.com/jaemin-devlog/PRIZM/actions/runs/30477035697)·[CI 30477035700](https://github.com/jaemin-devlog/PRIZM/actions/runs/30477035700) `PASS` |
| 미래 배포 | JAR, frontend `dist`, container image, Ollama binary와 model bytes를 배포하려면 별도 Gate 필요 |

현재 결론은 PRIZM source에만 적용된다. 외부 구성요소의 라이선스를
Apache-2.0으로 바꾸거나, binary·image·model을 PRIZM이 재배포한다는 뜻이
아니다.

## 현재 source-only 배포 경계

포함하는 것은 PRIZM source, 문서, 실행 설정, Gradle Wrapper와 synthetic
fixture다. 다음 항목은 저장소와 source release에 넣지 않는다.

- Java JAR과 frontend `dist`
- PostgreSQL·pgvector·Ollama container image와 database volume
- Ollama binary와 `bge-m3` model weights/cache
- OpenSQL 설치 파일과 테스트 라이선스
- 실제 업로드 원본, `.env`, credentials, token, IDE·build 산출물

`LICENSE`의 SHA-256은
`CFC7749B96F63BD31C3C42B5C471BF756814053E847C10F3EB003417BC523D30`,
`NOTICE`의 SHA-256은
`155665012F4D119B5929061150DA6147E77151D29CD1020464800AA8789EE1F6`다.
전체 감사와 결정 근거는 [license audit](2026-license-audit.md)에 있다.

## 외부 구성요소 경계

| 구성요소 | 확인한 identity·license | 현재 취급 |
|---|---|---|
| PostgreSQL·pgvector | local development와 integration test용 `pgvector/pgvector:0.8.2-pg16-bookworm`; upstream 별도 license | 사용자가 image를 내려받으며 PRIZM이 재배포하지 않음 |
| Ollama | v0.32.3 Linux AMD64 archive, `MIT`, SHA-256 `2597d74fbe654ef6a37db56f771cf37d4a85c6bde4018127874e3927d3113800` | external prerequisite, `NOT_DISTRIBUTED` |
| BAAI `bge-m3` | revision `5617a9f61b028005a4858fdac845db406aefb181`, source license `MIT` | weights/cache `NOT_DISTRIBUTED` |
| Ollama `bge-m3:latest` | manifest SHA-256 `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab` | registry artifact와 BAAI revision 사이 변환 lineage는 `UNVERIFIED_LINEAGE`; model bytes 재배포 금지 |
| OpenSQL | 대회 수행을 위해 외부에서 공급된 runtime·license | 비공개·비재배포 자산이며 source archive, release asset, SBOM component 재배포물에 포함하지 않음 |
| Codex | authoring assistance | runtime이나 redistributed component가 아니며 사용 비율·모델 license·output provenance를 추정하지 않음 |

모델의 blob identity와 출처는 [AI 모델 명세](2026-sbom-model-manifest.md)와
[`prizm-ai-model-manifest.json`](../../sbom/prizm-ai-model-manifest.json)에 있다.

## 자산 provenance

저장소가 추적하는 fixture, sample, image, 문서와 binary 후보를 별도로
감사했다. 외부 design token은 PRIZM token으로 교체했고, 현재 source-only
범위에는 자산 provenance blocker가 없다. 공식 대회 PDF·ZIP·이미지와 사용자
제공 캡처는 URL·hash·최소 요약만 기록하고 저장소에 복사하지 않는다.

자세한 근거는 [asset provenance audit](2026-asset-provenance-audit.md)와
[공식 source register](2026-source-register.md)를 따른다.

## SBOM과 component 수

| 기록 | 형식 | 현재 수 |
|---|---|---:|
| [`prizm-backend-runtime.cdx.json`](../../sbom/prizm-backend-runtime.cdx.json) | CycloneDX 1.6 | 169 components |
| [`prizm-frontend.cdx.json`](../../sbom/prizm-frontend.cdx.json) | CycloneDX 1.6 | 183 components |
| [`prizm-ai-model-manifest.json`](../../sbom/prizm-ai-model-manifest.json) | PRIZM manifest 1.0 | 4 records |
| [`prizm-scope-manifest.json`](../../sbom/prizm-scope-manifest.json) | PRIZM manifest 1.0 | 7 scope records |
| [`SHA256SUMS`](../../sbom/SHA256SUMS) | SHA-256 | 위 네 JSON file의 4 checksums |

backend 사람용 감사의 167 module identity에는 물리 JAR이 없는
platform/BOM 2개가 들어 있다. 반대로 `netty-codec-native-quic` 한 module은
platform classifier JAR 5개로 나뉜다. 따라서 artifact SBOM은
`167 - 2 + (5 - 1) = 169` components다. frontend 사람용 감사와 machine
SBOM은 모두 183 entries다.

사람용 감사의 추가 범위는 runtime module identity 167개,
`testRuntimeClasspath` 217개, build-only 20개와 annotation processor 1개를
합친 중복 제거 Maven component 238개다. Gradle Wrapper는 별도다. 저장소의
dependency verification metadata는 377 components와 740 artifact SHA-256을
보존하지만, 과거 cache 흔적까지 포함할 수 있어 현재 runtime SBOM component
수와 같은 의미로 사용하지 않는다.

패키지별 전체 version, PURL, license expression, source URL과 checksum은
위 JSON과 [`SHA256SUMS`](../../sbom/SHA256SUMS)에만 둔다. 이 문서에는 같은
목록을 복제하지 않는다.

## Blocker와 남은 Gate

### 해결됨

- 직접 작성 source의 outgoing license와 copyright holder 확정
- root `LICENSE`·source-only `NOTICE` 적용
- 외부 design token 교체와 자산 provenance 감사
- source-only dependency·model·asset inventory와 SBOM 생성·검증 CI
- 현재 source-only scope manifest의 blocking 상태 0

### 현재 범위

현재 source-only 배포를 막는 `UNKNOWN`, `CONFLICT`, `BLOCKED`는 없다.
`UNVERIFIED_LINEAGE`는 model bytes를 배포하거나 정확한 변환 계보를 주장할
때의 Gate이며 현재 source-only 공개를 막지 않는다.

### 미래 산출물

JAR, frontend bundle, container image, Ollama binary 또는 model bytes를
배포하려면 실제 산출물을 기준으로 다음을 다시 감사한다.

- 포함된 component와 exact version·license·NOTICE
- 복수 license의 선택 경로
- binary/image/model별 SBOM과 checksum
- 모델 가중치 접근·재배포 조건과 lineage
- 새 dependency·asset·dataset·외부 공급 파일의 provenance

새 dependency, asset 또는 배포 경계 변경은 merge 전에 license audit,
asset audit, model manifest와 machine SBOM을 갱신하고 검증해야 한다.

## 최종 감사 판정

- 사람용 결론과 machine inventory의 component 수가 일치한다.
- 전체 checksum은 machine record에 보존하며 이 문서에서 재계산하거나
  다른 값으로 바꾸지 않았다.
- 해결된 blocker와 미래 artifact Gate를 분리했다.
- PRIZM이 Ollama·`bge-m3`·OpenSQL을 재배포한다고 표현하지 않았다.
- 공식 요구사항과 평가항목의 현재 evidence는
  [요구사항 추적표](2026-requirements-traceability.md)에서 관리한다. 공식 점수로
  오해할 수 있는 내부 예상 점수는 공개 문서에서 관리하지 않는다.
