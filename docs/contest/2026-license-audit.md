# PRIZM 2026 license·provenance 감사

> 현재 source-only 배포의 사람이 읽는 최종 결론은
> [2026 compliance](2026-compliance.md)에 있다. 이 문서는 그 결론의 상세
> inventory와 판정 근거를 보존한다.

## 문서 상태

| 항목 | 값 |
|---|---|
| PRZ 작업 | [`PRZ-002-open-source-readiness`](../../specs/PRZ-002-open-source-readiness/spec.md) |
| 범위 | T-02 inventory와 G-01·outgoing license 결정·자산 감사 자료 |
| 기준 commit | `846bd06e59aeb1cab88134f02c43ff9731f360fd` (`PR #13` merge) |
| 최종 source-only 검증 기준 | `f54e3d98e3eddc20dc3c89d9b3e2b84e1649bea1`, 2026-07-30 |
| 상태 | `VERIFIED_FOR_CURRENT_SOURCE_ONLY_SCOPE` |
| 직접 작성 코드 저작권자 | `Jaemin Jeong` |
| 공동 개발자·코드 기여자 | 확인된 사람 없음 |
| 정부 지원금·상금·개발비 | 없음 |
| 법적 성격 | 기술적 inventory이며 법률 자문이 아님 |

> 위 `VERIFIED_FOR_CURRENT_SOURCE_ONLY_SCOPE` 판정은 PRZ-002의 공개 source
> commit `f54e3d98e3eddc20dc3c89d9b3e2b84e1649bea1`과 GitHub CI 기준
> `777e184f206d2a2770d055940ddabf139abfed9d`에 대한 역사적 결과다. PRZ-004
> local candidate는 이 결론을 대체하지 않는다. 최종 공급망 검증 전까지
> PRZ-004의 상태는 `IMPLEMENTATION_CANDIDATE_UNVERIFIED`다.

G-01 배포 경계는 2026-07-24 사용자 승인으로 source-only로 확정했다.
같은 날 사용자는 PRIZM 직접 작성 source의 outgoing license로
`Apache-2.0`을 승인했고, 현재 root `LICENSE`와 source-only `NOTICE`가
적용돼 있다. [`2026-asset-provenance-audit.md`](2026-asset-provenance-audit.md)는
외부 design token 교체까지 완료해 현재 source-only 배포물의 자산 blocker가
없음을 확인했다. 따라서 미래 binary·image·model 재배포의 미해결 사항은
현재 source-only `LICENSE`·`NOTICE` 생성을 막지 않으며, 해당 산출물을 실제로
배포하려는 시점의 별도 release gate로 유지한다.

2026-07-25 공급망 IMPLEMENT에서는 Gradle Wrapper와 resolved dependency
artifact의 checksum Gate, GitHub Actions full SHA, Ollama release archive와
`bge-m3` manifest digest Gate를 추가했다. 이 작업은 artifact identity와
무결성을 고정하지만, Gradle artifact publisher 서명이나 Ollama 변환
lineage를 새로 증명하지는 않는다. 이 변경은 `PR #13`으로 main에 병합됐고,
해당 commit을 대상으로 한 GitHub Actions backend·frontend push/PR check 4건이
성공했다. 이 CI 자체는 OpenSQL·OpenProxy·OpenHA 검증이 아니다. 이후
PRZ-003에서 실제 OpenSQL single-node SQL Gate만 별도 `PASS`했으며
OpenProxy·OpenHA는 계속 `NOT_RUN` 또는 `NOT_VERIFIED`다.

## 감사 방법과 상태

| 상태 | 의미 |
|---|---|
| `VERIFIED` | 저장소 선언, resolved graph, lockfile, artifact 내부 고지 또는 공식 upstream을 교차 확인함 |
| `NOT_DISTRIBUTED` | 현재 확인한 배포 후보에 포함되지 않는 build/test 전용 구성요소 |
| `UNKNOWN` | version·권리·고지·배포 여부 중 하나 이상을 확정하지 못함 |
| `CONFLICT` | 현재 산출물 또는 근거가 해당 고지·출처 조건과 충돌함 |
| `BLOCKED` | 외부 결정이나 추가 검증 전에는 release·license·NOTICE 작업을 진행하면 안 됨 |

각 표의 한 row는 artifact 하나 또는 같은 upstream·version·license·배포
조건을 공유하는 artifact family 하나다. 공통 필드는 다음과 같다.

- `Identity`: 이름, version 또는 digest
- `Upstream / evidence`: 공식 upstream과 license 근거
- `SPDX`
- `Purpose / relation`: 사용 목적과 direct·transitive 관계
- `Scope`: runtime, build, test, CI, model, data, asset
- `Distribution`: 현재 배포 여부와 확정된 G-01 경계
- `NOTICE`: 전달·표시 의무 판단
- `Status / unresolved`
- `Verified`: 마지막 검증일

## PRZ-002 검증 기준과 입력 hash

| 입력 | SHA-256 |
|---|---|
| `build.gradle` | `1FAB00EBBB2100510FDA89517384BF08145D7E7B49FC972D4C028990835AB642` |
| `settings.gradle` | `AA627D19F54C16B1F89060449EEEFE3660F20392748789E628D796C6B180E7C5` |
| `gradle/wrapper/gradle-wrapper.properties` | `735B1FFB51D53B1FFAAAD7ECAF66B36014D758AEDAA35EC354CB2B2717B8EE7C` |
| `gradle/wrapper/gradle-wrapper.jar` | `497C8C2A7E5031F6AA847F88104AA80A93532EC32EE17BDB8D1D2F67A194A9C7` |
| `gradle/verification-metadata.xml` | `24B43A1FC2319C7C87475192BDEA62A860BCAD20D0B68F0C60A4EA1730380D71` |
| `frontend/package.json` | `ED40AD99488120CF5A4928050AB4FAC4F69CE4D62CACBD92578EC3F80DDF1725` |
| `frontend/package-lock.json` | `967063C8B12574A1467D492AD5FEC7C6E080E89A6250F153E49ED1F1714FB66C` |
| `.nvmrc` | `157C2EB0DE1187AC028E89BCFF580F1FEAB7EEA2A280B110998C9472E19B4D98` |
| `compose.yaml` | `4B0D8957D993E963888DA2FD539952A5ADBDE5031FBD8152971F3184447673AC` |
| backend Dockerfile | `D7919AF879015F78114DDD1E03A909D51D29895ACBD694536243557120B90DEC` |
| frontend Dockerfile | `C2859300EC00F750BB7E7525F78E7556E3BF9D5F075F64070DF5066A8FA4AF98` |
| `.github/workflows/ci.yml` | `8A686095B7879B7B639CB2E1ADEF4EBC5FCFDCAD6697BF7ED06C4900C4BA444A` |

### PRZ-004 local candidate

- 최초 정적 감사 후보: `0d20454eb9a3c3d9b8c7812d54a20781415b0378`
- 상태: `IMPLEMENTATION_CANDIDATE_UNVERIFIED`
- candidate dependency·Docker·CI·SBOM·checksum 파일은 갱신돼 있고 정적
  일관성만 확인했다.
- 최종 source에서의 `npm ci`, full·production audit, lint·build, Docker builder
  identity, SBOM 재생성, checksum·license·OSS readiness 검증은 `NOT_RUN`이다.

| candidate 입력 | 현재 파일 SHA-256 또는 정적 정보 |
|---|---|
| `frontend/package.json` | `EC0CD5D8BBA2684097E0CF0DBA81C7EF0D95CCEBF5DCE65BDAB43762FD9BE58D` |
| `frontend/package-lock.json` | `F14F034AA910996BE41FF744DC24213C7478B34D1DEA89DE75FB71543FFD922D` |
| frontend Dockerfile | `E84AF0B9D993DE9D550AAC92A45EF69036304ABD6AA2FEA5E11B60F127EEA32B` |
| `.github/workflows/ci.yml` | `49B38EFA01511F0C761F4A23CF72CA1ED9E48287CA5EE63569E16A91EF4D6CBA` |
| `sbom/prizm-frontend.cdx.json` | 183 components; 현재 checksum `cd1ed67bffefdaf4618bf9452d193f52c69aa37c646014b1daaf6354609c254a` |

## GitHub Actions 실행 증거

공급망 고정이 포함된 `636402a`는 `PR #13`으로 `846bd06`에 병합됐다. 다음
check는 GitHub-hosted Ubuntu runner에서 실제로 성공했다. backend job은
Docker Compose 설정·Docker engine, 고정된 Ollama archive checksum 및 `bge-m3`
manifest/blob Gate를 거친 뒤 `./gradlew check --no-daemon
--dependency-verification=strict`를 실행한다. `check`는 integration test를
포함한다.

| Event | Job | 결과 | 실행 증거 |
|---|---|---|---|
| pull request | backend | `SUCCESS` | [job 89638572505](https://github.com/jaemin-devlog/PRIZM/actions/runs/30142592707/job/89638572505) |
| pull request | frontend | `SUCCESS` | [job 89638572499](https://github.com/jaemin-devlog/PRIZM/actions/runs/30142592707/job/89638572499) |
| push | backend | `SUCCESS` | [job 89638570023](https://github.com/jaemin-devlog/PRIZM/actions/runs/30142591688/job/89638570023) |
| push | frontend | `SUCCESS` | [job 89638570030](https://github.com/jaemin-devlog/PRIZM/actions/runs/30142591688/job/89638570030) |

이 결과는 PostgreSQL·pgvector Testcontainers, Docker, Ollama와 `bge-m3`를
사용한 CI 성공 근거다. 실제 OpenSQL, OpenProxy, OpenHA 환경은 사용하지
않았으므로 호환성 결과는 각각 `NOT_RUN`이다.

실제 Java graph는 다음 명령이 2026-07-24에 성공한 결과를 사용했다.
애플리케이션 테스트는 실행하지 않았다.

```powershell
.\gradlew.bat dependencies --configuration runtimeClasspath --no-daemon --console plain
.\gradlew.bat dependencies --configuration testRuntimeClasspath --no-daemon --console plain
.\gradlew.bat buildEnvironment --no-daemon --console plain
```

## Java·Gradle inventory

### 직접 선언과 resolved 범위

| ID | Identity | Purpose / relation | Scope | Distribution |
|---|---|---|---|---|
| `JAVA-DIRECT-BOOT` | Spring Boot starters `4.1.0`: actuator, data-jpa, flyway, security, validation, webmvc, oauth2-resource-server | direct | runtime | fat JAR·backend image를 배포하면 포함 |
| `JAVA-DIRECT-AI` | Spring AI Ollama starter `2.0.0` | direct | runtime | fat JAR·backend image를 배포하면 포함 |
| `JAVA-DIRECT-PDF` | PDFBox `3.0.3` | direct | runtime | fat JAR·backend image를 배포하면 포함 |
| `JAVA-DIRECT-DB` | Flyway PostgreSQL `12.4.0`, PostgreSQL JDBC `42.7.11` | direct runtimeOnly | runtime | fat JAR·backend image를 배포하면 포함 |
| `JAVA-DIRECT-PROCESSOR` | Spring Boot configuration processor `4.1.0` | direct annotationProcessor | build | `NOT_DISTRIBUTED` |
| `JAVA-DIRECT-TEST` | Spring Boot test, Spring Security test, Testcontainers `2.0.5`, H2 `2.4.240`, JUnit launcher `6.0.3` | direct test dependencies | test | 현재 production 산출물에는 `NOT_DISTRIBUTED` |

### Runtime artifact family

| ID | Identity | Upstream / license evidence | SPDX | Purpose / relation | Scope | Distribution | NOTICE | Status / unresolved | Verified |
|---|---|---|---|---|---|---|---|---|---|
| `JVM-BOOT` | Spring Boot `4.1.0` runtime module family | [Spring Boot](https://github.com/spring-projects/spring-boot), POM·JAR `LICENSE`·`NOTICE.txt` | `Apache-2.0` | framework, transitive family | runtime | `NOT_DISTRIBUTED`; source 사용자가 내려받음 | future fat JAR 배포 시 upstream NOTICE 보존 필요 | `VERIFIED`; 최종 fat JAR coverage 미검증 | 2026-07-24 |
| `JVM-SPRING` | Spring Framework `7.0.8`, Spring Data `4.1.0`, Spring Security `7.1.0` | 각 Spring 공식 repository의 POM·JAR 고지 | `Apache-2.0` | web, data, security | runtime | `NOT_DISTRIBUTED`; source 사용자가 내려받음 | future binary 배포 시 upstream NOTICE 집계 필요 | `VERIFIED`; 최종 산출물 미검증 | 2026-07-24 |
| `JVM-SPRING-AI` | Spring AI `2.0.0` Ollama 경로 14 module | [Spring AI](https://github.com/spring-projects/spring-ai), POM | `Apache-2.0` | Ollama embedding client | runtime | `NOT_DISTRIBUTED`; source 사용자가 내려받음 | future binary 배포 시 license 전달 필요 | 현재 resolved graph의 14 module은 `VERIFIED` | 2026-07-24 |
| `JVM-PDFBOX` | `pdfbox`, `pdfbox-io`, `fontbox` `3.0.3`; JAR hashes `5BE38D2E…B4A6`, `123EA318…7BA`, `65690C3F…8030` | [Apache PDFBox](https://pdfbox.apache.org/), 각 JAR `META-INF/LICENSE`·`NOTICE` | `Apache-2.0` | PDF 추출, direct·transitive | runtime | `NOT_DISTRIBUTED`; source 사용자가 내려받음 | future binary 배포 시 세 JAR NOTICE 보존 필수 | `VERIFIED`, `NOTICE_REQUIRED` | 2026-07-24 |
| `JVM-FLYWAY` | core·database-postgresql `12.4.0`; JAR hashes `C3F93DF1…7799`, `ABC075A5…7B85` | [Flyway](https://github.com/flyway/flyway), parent POM·core JAR license | `Apache-2.0` | migration, direct·transitive | runtime | `NOT_DISTRIBUTED`; source 사용자가 내려받음 | future binary 배포 시 license 전달 필요 | `VERIFIED`; DB module 고지는 parent 근거 | 2026-07-24 |
| `JVM-PGJDBC` | PostgreSQL JDBC `42.7.11`; JAR hash `1981B31D…4647` | [pgjdbc](https://github.com/pgjdbc/pgjdbc), POM·JAR license | `BSD-2-Clause` | DB driver, direct | runtime | `NOT_DISTRIBUTED`; source 사용자가 내려받음 | future binary 배포 시 PostgreSQL·shaded OnGres 고지 보존 | `VERIFIED`, `NOTICE_REQUIRED` | 2026-07-24 |
| `JVM-HIBERNATE` | Hibernate ORM `7.4.1.Final`, Models `1.1.1`, Validator `9.1.0.Final` | Hibernate 공식 POM | `Apache-2.0` | JPA·validation, transitive | runtime | `NOT_DISTRIBUTED`; source 사용자가 내려받음 | future binary 배포 시 고지 집계 | `VERIFIED` | 2026-07-24 |
| `JVM-REACTIVE` | Reactor Core `3.8.6`, Reactor Netty `1.3.6`, Netty `4.2.15.Final` family | 공식 POM·artifact 고지 | 주로 `Apache-2.0` | Spring AI HTTP client, transitive | runtime | `NOT_DISTRIBUTED`; source 사용자가 내려받음 | future binary 배포 시 Netty native·third-party 고지 집계 | `VERIFIED`; artifact별 NOTICE coverage 필요 | 2026-07-24 |
| `JVM-SERVER` | Tomcat embed `11.0.22` family | [Apache Tomcat](https://tomcat.apache.org/), POM·artifact 고지 | `Apache-2.0` | web server, transitive | runtime | `NOT_DISTRIBUTED`; source 사용자가 내려받음 | future binary 배포 시 NOTICE 보존 | `VERIFIED` | 2026-07-24 |
| `JVM-JACKSON` | Jackson databind/core `3.1.4`, annotations `2.21` | Jackson 공식 POM·artifact 고지 | `Apache-2.0` | JSON, transitive | runtime | `NOT_DISTRIBUTED`; source 사용자가 내려받음 | future binary 배포 시 NOTICE 집계 | `VERIFIED` | 2026-07-24 |
| `JVM-OBSERVABILITY` | Micrometer `1.17.0`, context propagation `1.2.1`, HdrHistogram `2.2.2` | 공식 POM·artifact 고지 | `Apache-2.0`; HdrHistogram `CC0-1.0 OR BSD-2-Clause` | metrics, transitive | runtime | `NOT_DISTRIBUTED`; source 사용자가 내려받음 | future binary 배포 시 선택 license와 고지 보존 | `VERIFIED` | 2026-07-24 |
| `JVM-LOGGING` | Log4j bridge `2.25.4`, SLF4J `2.0.18`, Logback classic/core `1.5.34` | 공식 POM·artifact 고지 | Log4j `Apache-2.0`; SLF4J `MIT`; Logback `EPL-2.0 OR LGPL-2.1-only` | logging, transitive | runtime | `NOT_DISTRIBUTED`; source 사용자가 내려받음 | future binary 배포 시 Logback 선택 경로와 해당 원문·의무 명시 | identity·복수 license `VERIFIED`; 선택 기록 필요 | 2026-07-24 |
| `JVM-JAKARTA` | Jakarta annotation `3.0.0`, transaction `2.0.1`, persistence `3.2.0`, activation/JAXB family | POM·JAR license·NOTICE | `EPL-2.0 OR (GPL-2.0-only WITH Classpath-exception-2.0)`; `EPL-2.0 OR BSD-3-Clause`; Eclipse Distribution License 명칭은 SPDX `BSD-3-Clause`와 대응 | Java APIs, transitive | runtime | `NOT_DISTRIBUTED`; source 사용자가 내려받음 | future binary 배포 시 permissive option 선택과 NOTICE 기록 | identity·선택식 `VERIFIED`; 선택 기록 필요 | 2026-07-24 |
| `JVM-BYTECODE` | Byte Buddy `1.18.10`, ANTLR `3.5.3`·`4.13.2`, ST4 `4.3.4`, AspectJ `1.9.25.1` | 공식 POM·artifact metadata | Apache/BSD family; AspectJ `EPL-2.0` | ORM·template·bytecode, transitive | runtime | `NOT_DISTRIBUTED`; source 사용자가 내려받음 | future binary 배포 시 license별 고지 보존 | `VERIFIED`; ASM `9.7.1`은 test-only 부록에 분리 | 2026-07-24 |
| `JVM-COMMONS` | Commons Logging `1.3.6` | Apache Commons POM·artifact 고지 | `Apache-2.0` | logging bridge, transitive | runtime | `NOT_DISTRIBUTED`; source 사용자가 내려받음 | future binary 배포 시 NOTICE 집계 | `VERIFIED`; Compress·Codec·IO·Lang은 test-only 부록에 분리 | 2026-07-24 |
| `JVM-OTHER` | HikariCP `7.0.2`, SnakeYAML `2.6`, Nimbus JOSE JWT `10.9`, Victools `5.0.0`, JTokkit `1.1.0`, JSON Schema Validator `3.0.1` 등 | 각 artifact POM·license metadata | Apache/MIT family | persistence, YAML, JWT, schema, transitive | runtime | `NOT_DISTRIBUTED`; source 사용자가 내려받음 | future binary 배포 시 artifact별 고지 집계 | identity·declared license `VERIFIED`; 최종 NOTICE coverage 미검증 | 2026-07-24 |

### Test·build artifact family

| ID | Identity | Upstream / evidence | SPDX | Scope | Distribution | Status / unresolved | Verified |
|---|---|---|---|---|---|---|---|
| `JVM-TESTCONTAINERS` | Testcontainers `2.0.5` family; core JAR hash `0466F481…C2E1` | [Testcontainers](https://github.com/testcontainers/testcontainers-java), POM | `MIT` | test | `NOT_DISTRIBUTED` | `VERIFIED` | 2026-07-24 |
| `JVM-TEST` | JUnit `6.0.3`, Mockito `5.23.0`, AssertJ `3.27.7`, Hamcrest `3.0`, XMLUnit `2.11.0`, JSONassert `1.5.3`, Awaitility `4.3.0` 등 | resolved `testRuntimeClasspath`, 각 POM | EPL/MIT/Apache/BSD family | test | `NOT_DISTRIBUTED` | identity·declared license `VERIFIED` | 2026-07-24 |
| `JVM-H2` | H2 `2.4.240`; JAR hash `29B70E42…7CE0` | [H2](https://github.com/h2database/h2database), POM | `MPL-2.0 OR EPL-1.0` | test | `NOT_DISTRIBUTED` | 복수 license `VERIFIED`; production 포함 시 재판정 | 2026-07-24 |
| `GRADLE-WRAPPER` | Gradle Wrapper·distribution `9.5.1`; wrapper JAR hash 위 표 참조 | [Gradle 9.5.1 release](https://github.com/gradle/gradle-distributions/releases/tag/v9.5.1), embedded license | `Apache-2.0` | build | wrapper scripts·JAR는 source와 함께 배포 | official bin SHA-256 `bafc141b619ad6350fd975fc903156dd5c151998cc8b058e8c1044ab5f7b031f`를 `distributionSha256Sum`에 고정; `VERIFIED` | 2026-07-25 |
| `GRADLE-BOOT-PLUGIN` | Spring Boot Gradle plugin `4.1.0`; JAR hash `861FD80B…5AAC` | plugin POM·JAR LICENSE/NOTICE | `Apache-2.0` | build | source 사용자가 내려받음 | `VERIFIED`; build transitive NOTICE는 SBOM에서 분리 | 2026-07-24 |
| `GRADLE-DM-PLUGIN` | `io.spring.dependency-management` `1.1.7`; JAR hash `9E885EEC…E4C` | [Maven Central 1.1.7 POM](https://central.sonatype.com/artifact/io.spring.gradle/dependency-management-plugin/1.1.7) | `Apache-2.0` | build | source 사용자가 내려받음 | exact POM의 license 선언 확인; `VERIFIED` | 2026-07-25 |
| `GRADLE-TOMLJ` | `org.tomlj:tomlj:1.0.0`; JAR SHA-256 `32697c7567b2921c473678a820b13fc64700aa87bb14576eeb48d0ed5847cfd4` | [Maven Central 1.0.0 POM](https://central.sonatype.com/artifact/org.tomlj/tomlj/1.0.0), [tagged LICENSE](https://github.com/tomlj/tomlj/blob/1.0.0/LICENSE) | `Apache-2.0` | build transitive | source 사용자가 내려받음 | exact POM과 tagged LICENSE 교차 확인; `VERIFIED` | 2026-07-25 |
| `GRADLE-DEPENDENCY-VERIFICATION` | [`gradle/verification-metadata.xml`](../../gradle/verification-metadata.xml); 377 components, 740 artifacts·SHA-256 | [Gradle dependency verification](https://docs.gradle.org/9.5.1/userguide/dependency_verification.html) | 해당 없음 | build integrity | metadata는 source와 함께 배포 | `verify-metadata=true`, default strict mode에서 Gradle `help` 성공하고 CI도 `--dependency-verification=strict`를 명시. GitHub의 깨끗한 cache가 요청한 `junit-bom:5.13.3.module`·`opentelemetry-bom:1.49.0.module` SHA-256도 Gradle Plugin Portal에서 재확인해 추가했다. 현재 repository에서 resolve한 graph로 bootstrap했으므로 artifact publisher 서명·진위를 독립 증명하지 않음; `INTEGRITY_BASELINE_VERIFIED` | 2026-07-25 |

현재 성공한 resolved graph에는 Spring AI pgvector store 5 module과
`com.pgvector:pgvector:0.1.6`이 없다. 로컬 Gradle 실행 이력에는 과거 흔적이
있으나 현재 graph가 아니므로 `STALE_CACHE_EVIDENCE`로 제외했다. 이를 현재
dependency라고 주장하지 않는다.

Java 쪽에서 즉시 incompatible하다고 확정된 license는 없다. 다만 다음 이유로
future fat JAR·binary release는 아직 `BLOCKED`다. 이 판정은 현재 source-only
배포를 막는 blocker가 아니다.

- 실제 `bootJar`를 만들지 않아 fat JAR의 third-party `LICENSE`·`NOTICE`
  보존 여부를 확인하지 않았다.
- Logback·Jakarta·HdrHistogram·JNA 같은 복수 license의 선택 경로를 NOTICE에
  기록하지 않았다.
- generated dependency verification metadata는 checksum 변경을 fail-closed
  하지만 publisher signature 검증을 켜지 않았다. 따라서 checksum은
  reproducible integrity 기준선이며 독립적인 publisher authenticity 증거는
  아니다.

## frontend·npm inventory

### 전체 lockfile 판정

`frontend/package-lock.json` v3의 root 제외 183 entry를 모두 순회했다.

| 항목 | 결과 |
|---|---|
| exact version | 183 / 183 |
| registry tarball URL | 183 / 183; 모두 `registry.npmjs.org` |
| integrity | 183 / 183 |
| license 필드 | 183 / 183 |
| root graph 도달 | 183 / 183; peer-only 누락 0 |
| direct | 14: runtime 2, dev 12 |
| transitive | 169: runtime 1, dev 168 |
| runtime | React, React DOM, scheduler 3개 |
| dev | 180개; optional 33개 포함 |

lockfile이 exact inventory의 canonical record다. 아래 표는 183개 전체를 license
cohort로 집계한다. upstream은 각 row의 `resolved` tarball, license 근거는
각 `packages[node_modules/<name>]`의 `license` 필드다. lock metadata 확인과
tarball 내부 원문·copyright·NOTICE 전수 확인은 서로 다른 단계이므로 전자는
`VERIFIED`, 후자는 아직 `UNKNOWN`이다.

| ID | SPDX | Count | Scope / relation | Purpose | Distribution | NOTICE / status | Verified |
|---|---:|---:|---|---|---|---|---|
| `NPM-MIT` | `MIT` | 135 | runtime 3 + dev transitives | UI runtime, build, type, lint | initial binary `NOT_DISTRIBUTED`; runtime 3개는 ignored `dist`에서만 관찰 | future `dist`에는 runtime copyright·permission notice 필요; metadata `VERIFIED` | 2026-07-24 |
| `NPM-APACHE` | `Apache-2.0` | 15 | dev | TypeScript·ESLint support | initial binary `NOT_DISTRIBUTED` | future bundle 포함 시 LICENSE·upstream NOTICE 전달; 포함 여부 `UNKNOWN` | 2026-07-24 |
| `NPM-MPL` | `MPL-2.0` | 12 | dev; optional platform binding 11 | Lightning CSS | initial binary `NOT_DISTRIBUTED` | future bundle에 Covered Software가 포함되면 file-level 의무; 포함 여부 `UNKNOWN` | 2026-07-24 |
| `NPM-ISC` | `ISC` | 10 | dev | build·lint transitive | initial binary `NOT_DISTRIBUTED` | future 포함 시 저작권·license 보존; 포함 여부 `UNKNOWN` | 2026-07-24 |
| `NPM-BSD2` | `BSD-2-Clause` | 6 | dev | parser·scope | initial binary `NOT_DISTRIBUTED` | future 포함 시 copyright·조건·disclaimer 보존; 포함 여부 `UNKNOWN` | 2026-07-24 |
| `NPM-BSD3` | `BSD-3-Clause` | 2 | dev | query·source map | initial binary `NOT_DISTRIBUTED` | future 포함 시 copyright·조건·disclaimer 보존; 포함 여부 `UNKNOWN` | 2026-07-24 |
| `NPM-CCBY` | `CC-BY-4.0` | 1 | dev | browser support data | initial binary `NOT_DISTRIBUTED` | future data·adaptation 포함 시 attribution·license link·변경 표시; 포함 여부 `UNKNOWN` | 2026-07-24 |
| `NPM-BLUEOAK` | `BlueOak-1.0.0` | 1 | dev | glob matching | initial binary `NOT_DISTRIBUTED` | future 포함 시 원문 또는 공식 link 제공; 포함 여부 `UNKNOWN` | 2026-07-24 |
| `NPM-0BSD` | `0BSD` | 1 | dev optional | TypeScript helper | initial binary `NOT_DISTRIBUTED` | 추가 조건 없음; future 포함 여부 `UNKNOWN` | 2026-07-24 |

### Direct와 주요 non-MIT component

| ID | Identity | Upstream / evidence | SPDX | Relation·scope | Distribution / status |
|---|---|---|---|---|---|
| `NPM-REACT` | `react@19.2.7`, `react-dom@19.2.7`, `scheduler@0.27.0` | [React](https://github.com/facebook/react), lockfile·official LICENSE | `MIT` | direct 2 + transitive 1, runtime | 현 ignored `dist` 포함 `VERIFIED`; initial source-only 경계에서는 `NOT_DISTRIBUTED`, future `dist` release는 고지 추가 전 `BLOCKED` |
| `NPM-VITE` | `vite@8.1.4`, `@vitejs/plugin-react@6.0.3` | [Vite](https://github.com/vitejs/vite), lockfile | `MIT` | direct dev | build-only 예상; clean bundle 대조 `UNKNOWN` |
| `NPM-TYPESCRIPT` | `typescript@6.0.3` | [TypeScript](https://github.com/microsoft/TypeScript), lockfile | `Apache-2.0` | direct dev | build-only 예상; clean bundle 대조 `UNKNOWN` |
| `NPM-ESLINT` | ESLint `10.7.0`, typescript-eslint `8.63.0`, plugins와 globals direct versions | [ESLint](https://github.com/eslint/eslint), lockfile | 주로 `MIT` | direct dev | lint-only 예상; clean bundle 대조 `UNKNOWN` |
| `NPM-LIGHTNINGCSS` | `lightningcss@1.32.0` + platform binding 11개 | [Lightning CSS LICENSE](https://github.com/parcel-bundler/lightningcss/blob/master/LICENSE), lockfile | `MPL-2.0` | transitive dev; binding optional | final 포함 여부 `UNKNOWN` |
| `NPM-CANIUSE` | `caniuse-lite@1.0.30001803` | [caniuse-lite](https://github.com/browserslist/caniuse-lite), lockfile | `CC-BY-4.0` | transitive dev data | final data·adaptation 포함 여부 `UNKNOWN` |
| `NPM-MINIMATCH` | `minimatch@10.2.5` | [Blue Oak Model License](https://blueoakcouncil.org/license/1.0.0.html), lockfile | `BlueOak-1.0.0` | transitive dev | final 포함 여부 `UNKNOWN` |
| `NPM-TSLIB` | `tslib@2.8.1` | lockfile | `0BSD` | optional dev | final 포함 여부 `UNKNOWN` |
| `NPM-BSD-ISC` | BSD-2 6개, BSD-3 2개, ISC 10개 | lockfile exact versions | BSD·ISC | transitive dev | final 포함 여부 `UNKNOWN` |
| `FONT-PRETENDARD-REFERENCE` | CSS family 이름 `Pretendard`; font version·binary 없음 | [공식 upstream](https://github.com/orioncactus/pretendard), [공식 LICENSE](https://github.com/orioncactus/pretendard/blob/main/LICENSE), [SPDX](https://spdx.org/licenses/OFL-1.1.html) | `OFL-1.1`; Reserved Font Name `Pretendard` | runtime CSS system-font preference | font file·npm package·`@font-face`·`@import`·CDN 요청이 없어 `NOT_DISTRIBUTED`; system fallback 사용. 향후 bundle 시 exact version·hash와 OFL 고지 재감사 |

BSD·ISC exact component는 다음과 같다.

- BSD-2: `eslint-scope@9.1.2`, `espree@11.2.0`, `esrecurse@4.3.0`,
  `estraverse@5.3.0`, `esutils@2.0.3`, `uri-js@4.4.1`
- BSD-3: `esquery@1.7.0`, `source-map-js@1.2.1`
- ISC: `@eslint/plugin-kit` 아래 `semver@7.8.5`,
  `electron-to-chromium@1.5.389`, `flatted@3.4.2`,
  `glob-parent@6.0.2`, `isexe@2.0.0`, `lru-cache@5.1.1`,
  `picocolors@1.1.1`, `semver@6.3.1`, `which@2.0.2`, `yallist@3.1.1`

Apache-2.0 exact component는
`@eslint/config-array@0.23.5`, `@eslint/config-helpers@0.6.0`,
`@eslint/core@1.2.1`, `@eslint/object-schema@3.0.5`,
`@eslint/plugin-kit@0.7.2`, `@humanfs/core@0.19.2`,
`@humanfs/node@0.16.8`, `@humanfs/types@0.15.0`,
`@humanwhocodes/module-importer@1.0.1`, `@humanwhocodes/retry@0.4.3`,
`baseline-browser-mapping@2.10.42`, `detect-libc@2.1.2`,
`eslint-visitor-keys@3.4.3`·`5.0.1`, `typescript@6.0.3`이다.

`.nvmrc`와 package engines는 Node `22.17.0`, package manager는 npm
`10.9.2`다. PRZ-004 최초 후보는 frontend builder를
`node:22.17.0-alpine`으로 바꾸고 개발 전용 transitive dependency 세 항목을
갱신했다. 현재 lockfile과 candidate SBOM은 183개 component로 정적 일관성을
보인다. 다만 이번 교정 단계에서는 final source의 clean `npm ci`, full·production
audit, frontend build, Docker builder identity와 SBOM 재생성을 실행하지 않았다.
따라서 finding 0, builder identity 또는 공급망 최종 `PASS`로 판정하지 않는다.

현재 ignored `frontend/dist`의 JavaScript에는 React·React DOM `19.2.7`
계열이 포함되지만 license/copyright 고지가 없었다. clean build는 이번
범위에서 실행하지 않았다. 따라서:

- G-01 initial source-only 경계에서는 `dist`를 배포하지 않으므로 현재
  배포 충돌은 아니다.
- 향후 `dist`를 배포하면 runtime MIT 고지 누락이 `CONFLICT`가 된다.
- dev-only dependency가 빠졌다고 단정할 수 없어 clean bundle 검증은
  `UNKNOWN`이다.
- `dist`의 future release는 clean bundle·NOTICE 검증 전까지 `BLOCKED`한다.

## Container·database inventory

Tag는 repository 설정에서 확인한 값이다. platform manifest digest와 base
package SBOM을 만들거나 registry에 publish하지 않았다.

| ID | Identity | Upstream / evidence | SPDX | Purpose·scope | Distribution | NOTICE | Status / unresolved | Verified |
|---|---|---|---|---|---|---|---|---|
| `IMG-TEMURIN-JDK` | `eclipse-temurin:17-jdk` | [Temurin image](https://hub.docker.com/_/eclipse-temurin) | Dockerfile Apache-2.0; OpenJDK `GPL-2.0-only WITH Classpath-exception-2.0`; OS composite | backend build | `NOT_DISTRIBUTED`; 사용자가 local build 중 upstream에서 받음 | image를 future 배포하면 package별 필요 | mutable tag, digest·SBOM `UNKNOWN` | 2026-07-24 |
| `IMG-TEMURIN-JRE` | `eclipse-temurin:17-jre` | [Temurin image](https://hub.docker.com/_/eclipse-temurin) | OpenJDK `GPL-2.0-only WITH Classpath-exception-2.0` + OS composite | backend runtime | `NOT_DISTRIBUTED`; 사용자가 local build 중 upstream에서 받음 | image를 future 배포하면 package별 필요 | mutable tag, digest·SBOM `UNKNOWN` | 2026-07-24 |
| `IMG-NODE` | `node:22-alpine` | [Node image](https://hub.docker.com/_/node) | Node `MIT`, Alpine packages composite | frontend build | `NOT_DISTRIBUTED`; 사용자가 local build 중 upstream에서 받음 | image를 future 배포하면 package별 필요 | patch·Alpine·digest·SBOM `UNKNOWN`; PRZ-004 candidate 변경은 위 별도 절에 기록 | 2026-07-24 |
| `IMG-NGINX` | `nginx:1.27-alpine` | [Nginx image](https://hub.docker.com/_/nginx) | Nginx BSD family, Alpine packages composite | frontend runtime | `NOT_DISTRIBUTED`; 사용자가 local build 중 upstream에서 받음 | image를 future 배포하면 package별 필요 | minor·Alpine·digest·SBOM `UNKNOWN` | 2026-07-24 |
| `IMG-PGVECTOR` | `pgvector/pgvector:0.8.2-pg16-bookworm` | [pgvector v0.8.2 Dockerfile](https://github.com/pgvector/pgvector/blob/v0.8.2/Dockerfile), [PostgreSQL license](https://www.postgresql.org/about/licence/) | pgvector·PostgreSQL License; Debian packages composite | database runtime | `NOT_DISTRIBUTED`; 사용자의 Docker가 upstream에서 pull | image를 future 배포하면 package별 필요 | PostgreSQL 16 base patch·digest·SBOM `UNKNOWN` | 2026-07-24 |

Compose는 database image를 pull하고 backend·frontend image를 local build한다.
registry publish 설정은 없다. G-01은 Dockerfile·Compose 정의만 source로
배포하고 image archive나 registry image는 배포하지 않기로 확정했다.

## GitHub Actions·CI inventory

| ID | Identity | Upstream / evidence | SPDX | Purpose·scope | Distribution | Status / unresolved | Verified |
|---|---|---|---|---|---|---|---|
| `CI-RUNNER` | `ubuntu-latest` | GitHub-hosted runner | image package composite | CI | `NOT_DISTRIBUTED` | runner revision floating `UNKNOWN` | 2026-07-24 |
| `CI-CHECKOUT` | `actions/checkout` `v6.1.0`; commit `d23441a48e516b6c34aea4fa41551a30e30af803` | [actions/checkout](https://github.com/actions/checkout) | `MIT` | CI source checkout | `NOT_DISTRIBUTED` | full commit SHA와 version 주석 고정; GitHub 실행 `PASS` | 2026-07-30 |
| `CI-JAVA` | `actions/setup-java` `v5.6.0`; commit `03ad4de0992f5dab5e18fcb136590ce7c4a0ac95`; Temurin Java `17` | [actions/setup-java](https://github.com/actions/setup-java) | `MIT` | CI JDK setup | `NOT_DISTRIBUTED` | Action full SHA 고정; JDK patch는 setup 시점에 결정되는 환경 재현성 제한으로 분리. GitHub 실행 `PASS` | 2026-07-30 |
| `CI-NODE` | `actions/setup-node` `v6.5.0`; commit `249970729cb0ef3589644e2896645e5dc5ba9c38`; `.nvmrc` Node `22.17.0` | [actions/setup-node](https://github.com/actions/setup-node) | `MIT` | CI Node setup | `NOT_DISTRIBUTED` | Action full SHA와 Node exact version 고정; GitHub 실행 `PASS` | 2026-07-30 |
| `CI-OLLAMA-INSTALL` | Ollama `v0.32.3` Linux amd64 archive; SHA-256 `2597d74fbe654ef6a37db56f771cf37d4a85c6bde4018127874e3927d3113800` | [Ollama v0.32.3 release](https://github.com/ollama/ollama/releases/tag/v0.32.3), release asset digest | source `MIT`; binary archive는 외부 제공물 | CI runtime setup | `NOT_DISTRIBUTED` | mutable install script 제거, exact release archive checksum과 runtime API version fail-closed Gate 구현; GitHub 실행 `PASS` | 2026-07-30 |
| `CI-BGE-PULL` | `bge-m3:latest`; registry manifest SHA-256 `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab` | [Ollama model page](https://ollama.com/library/bge-m3:latest), registry manifest | model license는 아래 별도 기록 | CI model pull | model bytes를 GitHub cache·artifact로 배포하지 않음 | pull 전 registry manifest와 pull 후 local manifest를 같은 digest로 검증하고 model·license blob 존재를 검사. mutable alias drift는 fail-closed; GitHub 실행 `PASS` | 2026-07-30 |

PostgreSQL 성공이나 일반 CI 성공은 OpenSQL·OpenProxy·OpenHA 성공이 아니다.
이 component 감사 자체에서는 세 환경을 실행하지 않았다. 별도 PRZ-003
실행에서 실제 OpenSQL single-node SQL Gate만 `PASS`했고 OpenProxy·OpenHA는
`NOT_RUN` 또는 `NOT_VERIFIED`다.

## Ollama·bge-m3·Codex

PRIZM 코드의 outgoing license와 external runtime·model license를 합치지 않는다.

| ID | Identity | Upstream / evidence | License | Purpose·scope | Distribution | NOTICE / status | Verified |
|---|---|---|---|---|---|---|---|
| `AI-OLLAMA-SOURCE` | Ollama source와 CI runtime `v0.32.3`; Linux amd64 archive SHA-256 `2597d74fbe654ef6a37db56f771cf37d4a85c6bde4018127874e3927d3113800` | [Ollama source](https://github.com/ollama/ollama), [v0.32.3 release](https://github.com/ollama/ollama/releases/tag/v0.32.3), official LICENSE·asset digest | source `MIT`; downloaded binary archive는 외부 제공물 | local·CI model runtime | `NOT_DISTRIBUTED`; 사용자가 공식 upstream에서 설치 | CI identity·checksum Gate와 실제 GitHub 실행 `PASS`; bundled dependency 전체 provenance는 future binary 재배포 전 별도 감사 | 2026-07-30 |
| `AI-BGE-M3-UPSTREAM` | BAAI `bge-m3`; 2026-07-25 upstream reference revision `5617a9f61b028005a4858fdac845db406aefb181` | [BAAI/bge-m3](https://huggingface.co/BAAI/bge-m3), model repository metadata | model card `MIT` | 1024-d embedding model | `NOT_DISTRIBUTED`; 사용자가 Ollama registry에서 pull | 이 revision은 감사 시점 upstream reference일 뿐 Ollama 변환 원본임을 증명하지 않음; `UNVERIFIED_LINEAGE` | 2026-07-25 |
| `AI-BGE-M3-OLLAMA` | `bge-m3:latest`; manifest SHA-256 `7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab`; model blob `daec91ffb5dd0c27411bd71f29932917c49cf529a641d0168496c3a501e3062c`; license blob `a406579cd136771c705c521db86ca7d60a6f3de7c9b5460e6193a2df27861bde` | [Ollama model page](https://ollama.com/library/bge-m3:latest), registry manifest | license blob은 MIT 형식이나 저작권자 placeholder 포함 | local·CI embedding | `NOT_DISTRIBUTED`; model/cache를 Git·release에 포함하지 않음 | exact manifest Gate와 GitHub 실행 `PASS`; BAAI revision→Ollama 변환 lineage와 placeholder는 `UNVERIFIED_LINEAGE`로 남아 future model 재배포·정확한 lineage 주장을 차단하지만 PRIZM source distribution의 license 충돌로 간주하지 않음 | 2026-07-30 |
| `AI-CODEX` | Codex; exact 사용 비율 없음 | 사용자 확인과 개발 기록 | runtime component의 license로 분류하지 않음 | authoring assistant | code·model 자체를 PRIZM과 함께 배포하지 않음 | 보조도구 사용 사실만 공개; 저작권자·공동 기여자·runtime dependency로 기록하지 않음 | 2026-07-24 |

모델 파일과 cache는 Git·기본 제출 source에 넣지 않는다. G-01은 Ollama와
모델을 PRIZM이 재배포하지 않도록 확정했다. Ollama manifest/blob identity는
CI에서 fail-closed로 확인하지만, BAAI upstream에서 Ollama artifact로
변환된 lineage는 확인하지 못했다. 이 제한을 숨기지 않으며 future 재배포는
별도 사용자 결정과 재감사 없이는 허용하지 않는다.

## Fixture·sample·asset·binary provenance

| ID | Identity | Upstream / evidence | License | Purpose·scope | Distribution | Status / unresolved | Verified |
|---|---|---|---|---|---|---|---|
| `DATA-SEARCH-CORPUS` | `src/test/resources/search-evaluation/sample/corpus.json`; `prizm-synthetic-dense-pilot-v2`, 11 virtual docs; SHA-256 `0E9981C4BFCEA39ED7DFCA3F156EC9BCBF7E425DE9F29E966BB5F6D7D0494D86` | tracked JSON self-description, Git history와 2026-07-24 사용자 직접·Codex 보조 제작 확인; [자산 감사](2026-asset-provenance-audit.md) | `Apache-2.0` 적용 | search evaluation fixture | source와 함께 배포 후보 | 외부 dataset·실제 문서 비파생, 개인정보·기밀 부재와 공개 권리를 사용자 확인해 `VERIFIED_DIRECT` | 2026-07-30 |
| `DATA-SEARCH-QUESTIONS` | `src/test/resources/search-evaluation/sample/questions.jsonl`; 30 questions; SHA-256 `A42A356628E577722BC62A65C8157EC79A9917CA033C6B6CBD1D7BEE80FA07B5` | tracked JSONL, Git history와 2026-07-24 사용자 직접·Codex 보조 제작 확인; [자산 감사](2026-asset-provenance-audit.md) | `Apache-2.0` 적용 | search evaluation fixture | source와 함께 배포 후보 | corpus와 같은 제3자 비파생·공개 권리 확인으로 `VERIFIED_DIRECT` | 2026-07-30 |
| `DATA-EVALUATION-CONFIG` | `src/searchEvaluation/resources/application-search-evaluation.yml`; SHA-256 `06A36041A79D5DE3AF428C7F9B72017E12815438932BC1672EA3191731DF8CA8` | tracked project configuration | `Apache-2.0` 적용 | search evaluation configuration | source와 함께 배포 후보 | 외부 asset이 아닌 설정임을 `VERIFIED`; root license·source-only NOTICE 적용 | 2026-07-30 |
| `ASSET-TRACKED` | tracked frontend/search 이미지·PDF·office·archive·model 0개 | `git ls-files`와 extension·signature scan | 해당 없음 | asset audit | 해당 없음 | `VERIFIED` | 2026-07-24 |
| `BINARY-WRAPPER` | tracked binary는 `gradle-wrapper.jar` 1개 | 위 Gradle row | `Apache-2.0` | build bootstrap | source와 함께 배포 | `VERIFIED`; Gradle 9.5.1 distribution checksum pin 적용 | 2026-07-25 |
| `REF-SPEC-KIT` | GitHub Spec Kit의 spec·plan·tasks 구조, 확인 commit `4d3a4281bc63bd2af9f2515bb1036fc38da1294e` | [upstream](https://github.com/github/spec-kit), [MIT](https://github.com/github/spec-kit/blob/main/LICENSE), 사용자 제공 화면과 확인 | `MIT`; PRIZM에 upstream 원문·template·code 미포함 | 일반적인 작업 흐름 참고 | `NOT_DISTRIBUTED` | upstream template과 비자명한 동일 문구 0건, PRIZM 문서는 개념만 독자 작성, `VERIFIED_EXTERNAL_REFERENCE` | 2026-07-24 |
| `REF-ROBO-ARCHITECT` | uEngine Robo Architect의 spec별 보조 문서 구조, 확인 commit `bb4b24addc301062e06f983e25c8e5f76877b9cd` | [repository](https://github.com/uengine-oss/robo-architect), [제품 소개](https://www.uengine.org/contents/roboarchitect.html), 사용자 제공 화면과 확인 | root license 파일·GitHub license metadata 없음; README의 `MIT License` 문구만 확인. PRIZM에 upstream 원문·code·asset 미포함 | 일반적인 문서 배치 참고 | `NOT_DISTRIBUTED` | upstream에는 `evidence.md`가 없으며 PRIZM evidence 분리는 독자 적용. future copy 전 license 재확인 | 2026-07-24 |
| `REF-GAMIUM` | Gamium·samples·docs 구조 | PRZ-002 plan/tasks와 2026-07-24 사용자 확인 | 향후 참고 시 exact repository·commit·license 재감사 | 공개 저장소 정리의 미래 참고 후보 | 현재 code·문구·asset 반영 0 | `NOT_DISTRIBUTED`; 현재 provenance Gate 대상 아님 | 2026-07-24 |
| `SOURCE-UI-DESIGN-TOKENS` | [`frontend/src/styles.css`](../../frontend/src/styles.css)의 독립 `--prizm-*` color·spacing·radius·state token | 2026-07-24 external-token 제거 구현과 [자산 감사](2026-asset-provenance-audit.md) | `Apache-2.0` 적용 | frontend visual system | source와 함께 배포 후보 | 외부 token 값·문구·asset 미포함, `VERIFIED_DIRECT` | 2026-07-30 |
| `REF-OH-MY-DESIGN-TOSS` | oh-my-design의 Toss design reference | [reference](https://oh-my-design.kr/design-systems/toss), [tool repository](https://github.com/kwakseongjae/oh-my-design), [Toss TDS 사용 범위](https://developers-apps-in-toss.toss.im/design/components.html) | tool은 `MIT`이나 company reference는 각 회사 소유로 분리되고, 공식 TDS 사용 허가는 앱인토스 범위로 제한됨 | 과거 frontend token 출처를 설명하는 감사 이력 | 외부 file·token·문구 0개 | 재사용 권리를 가정하지 않고 source token을 제거해 `NOT_DISTRIBUTED`; URL은 역사적 근거로만 유지 | 2026-07-24 |

검색 평가의 ignored real-data 경로, `local/`, `outputs/`, model cache는 감사 입력
또는 배포 후보로 사용하지 않았고 수정·삭제하지 않았다.
초기 ZIP·PRIZM 골격과 inline Mermaid diagram은 사용자 확인으로
`VERIFIED_DIRECT`가 됐다. frontend color·spacing·radius token도
oh-my-design의 Toss reference에서 가져온 이력을 확인한 뒤 외부 값과 legacy
token 이름을 제거하고 독립 PRIZM 체계로 교체했다. 따라서
[자산 감사](2026-asset-provenance-audit.md)의 외부 design rights blocker는
해소됐다.

## Machine inventory 경계

패키지별 전체 identity, exact version, PURL, license expression, source URL과
artifact checksum은 사람용 문서에 복제하지 않는다. 다음 machine file을 단일
원본으로 사용한다.

| 기록 | 형식 | 현재 수 |
|---|---|---:|
| [backend runtime SBOM](../../sbom/prizm-backend-runtime.cdx.json) | CycloneDX 1.6 | 169 components |
| [frontend SBOM](../../sbom/prizm-frontend.cdx.json) | CycloneDX 1.6 | 183 components |
| [AI model manifest](../../sbom/prizm-ai-model-manifest.json) | PRIZM manifest 1.0 | 4 records |
| [scope manifest](../../sbom/prizm-scope-manifest.json) | PRIZM manifest 1.0 | 7 scope records |
| [generated-file checksums](../../sbom/SHA256SUMS) | SHA-256 | 위 네 JSON file의 4 checksums |

backend 사람용 감사의 167 module identity에는 물리 JAR이 없는 platform/BOM
2개가 포함된다. 한편 `netty-codec-native-quic` 한 module은 platform classifier
JAR 5개로 해소된다. 따라서 artifact SBOM은
`167 - 2 + (5 - 1) = 169` components다. frontend lockfile과 machine SBOM은
모두 183 entries다.

test·build dependency의 재현 가능한 identity와 checksum은
[Gradle dependency verification metadata](../../gradle/verification-metadata.xml)가
단일 원본이다. 전체 component 목록을 확인하거나 변경할 때는
[SBOM 사용 안내](../../sbom/README.md)에 적힌 생성·검증 절차를 따른다.

사람용 감사에서 확인한 수는 runtime module identity 167개,
`testRuntimeClasspath` 217개(runtime에 test-only 50개 추가)다.
`buildEnvironment`는 고유 coordinate 26개였고 test graph와 겹치지 않는
build-only identity는 20개였다. annotation processor 1개까지 합친 중복 제거
Maven component union은 `217 + 20 + 1 = 238`개다. Gradle Wrapper `9.5.1`은
별도 non-Maven component다.

성공한 현행 tree에 없고 cache·execution history에만 남은 identity는 현행
component로 세지 않는다. 확인된 제외 예시는
`biz.aQute.bnd:biz.aQute.bnd.annotation:7.1.0`,
`com.google.errorprone:error_prone_annotations:2.38.0`,
`com.pgvector:pgvector:0.1.6`, `org.antlr:antlr4-runtime:4.13.1`,
`org.apiguardian:apiguardian-api:1.1.2`, OSGi annotation/service family와
Spring AI pgvector store 5개 module이다. local cache에만 있는
`org.checkerframework:checker-qual`도 같은 이유로 세지 않는다.

## G-01 확정 배포 경계

현재 source는 공개 repository에 두지만, “repository에 있음”, “사용자가 build
중 내려받음”, “release artifact에 포함”, “container/model bytes를 재배포”는
서로 다른 행위다.

**사용자 승인일:** 2026-07-24

> PRIZM은 우선 소스 코드와 실행 설정만 배포한다. PostgreSQL·pgvector,
> Ollama, `bge-m3`는 사용자가 공식 upstream에서 직접 내려받으며, PRIZM은
> 해당 이미지·실행 파일·모델 가중치·캐시를 재배포하지 않는다.

| 배포 후보 | 현재 사실 | future 포함 시 필요한 추가 검증 | G-01 결정 |
|---|---|---|---|
| 공개 Git repository·source ZIP | 대회 필수 범위; source·docs·실행 설정·wrapper JAR·synthetic fixture 포함 | outgoing license, wrapper·fixture 권리, source SBOM | **배포** |
| backend fat JAR | build 가능하지만 release artifact 없음 | JAR 내부 전체 license·NOTICE coverage | **초기 미배포** |
| frontend `dist` | ignored generated output; runtime MIT 고지 누락 | clean build, bundle component map, notice artifact | **초기 미배포** |
| backend image | 사용자가 source에서 local build | base digest·platform SBOM·license/NOTICE | **image 미배포** |
| frontend image | 사용자가 source에서 local build | builder/runtime digest·platform SBOM·license/NOTICE | **image 미배포** |
| database image | 사용자의 Docker가 upstream에서 pull | exact digest·package SBOM | **image 미배포** |
| Ollama binary | 사용자가 공식 upstream에서 설치 | exact version·checksum·bundled dependency·terms | **미배포** |
| `bge-m3` weights·cache | 사용자가 Ollama registry에서 pull; Git ignore | exact manifest/blob·upstream mapping·license notice | **미배포** |

이 결정으로 initial NOTICE·SBOM 범위를 다음처럼 다시 계산한다.

- source distribution에 포함: PRIZM source·문서·실행 설정, Gradle
  Wrapper, synthetic fixture.
- source distribution에서 제외: `bootJar`, frontend `dist`, container
  image/archive, Ollama binary, model weights·cache, DB volume.
- NOTICE는 PRIZM outgoing license와 저작권자, source에 실제 포함되는 Gradle
  Wrapper의 Apache-2.0 고지만 다룬다. Wrapper JAR에는 `META-INF/LICENSE`가
  있고 별도 `NOTICE` entry는 없다. 외부 JAR, npm tarball, 생성된 `dist`,
  image, Ollama binary, model bytes는 현재 source ZIP에 포함되지 않으므로 그
  고지를 현재 `NOTICE`에 대신 넣거나 재배포한다고 주장하지 않는다.
- SBOM은 `included`와 `external/provided`를 분리한다. Java/npm dependency,
  PostgreSQL·pgvector, Ollama와 `bge-m3`는 실행에 필요한 외부 구성요소로
  기록하지만 initial source archive에 포함됐다고 표시하지 않는다.
- 향후 fat JAR·`dist`·image·model을 배포하려면 G-01을 다시 열고 해당
  산출물의 NOTICE·SBOM·license 검증을 재수행한다.

G-01은 `COMPLETE`다. 사용자는 아래 비교를 검토한 뒤 2026-07-24
Apache-2.0을 outgoing license로 승인했다. fixture·asset 권리와 외부 design
token blocker는 해소됐고 build·CI artifact identity Gate도 구현했다.
Ollama 변환 lineage와 binary·image 고지 범위는 source-only 배포 경계 밖의
미래 release 제한으로 분리했다. 이 제한은 현재 source-only의 Apache-2.0
적용을 막지 않지만, 해당 산출물을 실제로 추가하는 release는 막는다.

## MIT와 Apache-2.0 비교와 사용자 선택

두 후보 모두 OSI 승인 permissive license지만 조건이 같지 않다.

| 항목 | MIT | Apache-2.0 |
|---|---|---|
| 공식 근거 | [OSI MIT](https://opensource.org/license/mit) | [OSI Apache-2.0](https://opensource.org/license/apache-2.0), [ASF 원문](https://www.apache.org/licenses/LICENSE-2.0) |
| SPDX | `MIT` | `Apache-2.0` |
| 재배포 기본 의무 | copyright와 license 문구 보존 | license 제공, 수정 파일 표시, 관련 attribution 보존 |
| 특허 | 명시적 patent grant 없음 | contributor의 명시적 patent grant와 특허 소송 시 종료 조항 |
| NOTICE | 표준 NOTICE 체계 없음; third-party 고지는 여전히 필요 | upstream Work에 NOTICE가 있으면 관련 attribution 전달 |
| 기여 기본값 | 별도 contributor 정책으로 명확히 해야 함 | 명시하지 않은 contribution의 동일 license 적용 규칙이 있음 |
| 운영 부담 | 짧고 단순 | NOTICE·수정 표시·patent 조항 운영 부담이 더 큼 |
| PRIZM 관점 | 작은 source 배포에는 단순 | 재사용 가능한 Engine·extension ecosystem의 patent 명확성에 장점 가능 |

**사용자 결정:** PRIZM 직접 작성 source의 outgoing license는
`Apache-2.0`으로 승인됐다. 이 결정 당시에는 아직 표준 원문을 적용하지
않았으며, 이후 T-04에서 루트 `LICENSE`와 `NOTICE`를 적용했다. 다음 조건을
모두 충족해 T-03의 **현재 source-only 범위**를 마감하고 T-04 구현으로
이동했다.

1. 완료: G-01의 배포 후보별 `배포/미배포` 결정
2. 완료: fixture 작성자·제3자 비파생·Apache-2.0 재배포 권리 확인
3. 완료: 외부 Toss reference token 제거와 독립 PRIZM frontend design
   token 교체
4. 완료: dependency-management plugin과 `org.tomlj`의 Apache-2.0 근거 확인
   및 Gradle checksum Gate 구현
5. 완료: 현재 source-only release에는 third-party JAR·npm bundle·container·model
   bytes를 포함하지 않으며, 포함되는 Wrapper는 Apache-2.0과 호환됨
6. future fat JAR·bundle·image·Ollama binary·`bge-m3` 재배포의 exact identity,
   NOTICE 및 compatibility는 해당 release 전 별도 gate로 유지
7. audit snapshot과 결정일 기록

### Apache-2.0 canonical 원문·결정 snapshot

| 항목 | 확인 결과 |
|---|---|
| canonical URL | [Apache License 2.0 text](https://www.apache.org/licenses/LICENSE-2.0.txt) |
| canonical SHA-256 | `CFC7749B96F63BD31C3C42B5C471BF756814053E847C10F3EB003417BC523D30` |
| 원문 적용 방식 | T-04에서 원문을 변형하지 않고 repository root `LICENSE`에 복사 |
| PRIZM 저작권자 | `Jaemin Jeong` |
| 현재 호환성 결론 | 직접 작성 source와 source에 포함되는 Apache-2.0 Gradle Wrapper에 대해 `COMPATIBLE` |
| future 제한 | JAR, frontend bundle, image, Ollama binary/model 재배포에는 적용하지 않으며 각 artifact별 재감사 필요 |

이 snapshot은 현재 source-only 배포 경계의 license 선택 기록이다. Apache-2.0이
모든 미래 산출물·컨테이너·모델에 대한 검증을 대신한다는 뜻은 아니다.

## T-04 source-only `LICENSE`·`NOTICE` 적용

| 항목 | 확인 결과 |
|---|---|
| outgoing license | [repository `LICENSE`](../../LICENSE), Apache License 2.0 원문을 변형 없이 적용 |
| `LICENSE` SHA-256 | `CFC7749B96F63BD31C3C42B5C471BF756814053E847C10F3EB003417BC523D30`; canonical 원문과 일치 |
| 저작권자 | [repository `NOTICE`](../../NOTICE)에 `Copyright 2026 Jaemin Jeong` 기록 |
| 현재 NOTICE 대상 | PRIZM 직접 작성 source와 포함되는 Gradle Wrapper scripts/JAR |
| Wrapper 고지 | JAR의 `META-INF/LICENSE` 확인, 별도 `NOTICE` entry 없음 |
| 제외한 고지 | Java/npm package, generated `dist`, image, Ollama binary, `bge-m3` bytes는 source-only 배포물에 없음 |
| `NOTICE` SHA-256 | `155665012F4D119B5929061150DA6147E77151D29CD1020464800AA8789EE1F6` |

따라서 현재 `NOTICE`에는 포함하지 않는 artifact의 third-party attribution을
추측해 복사하지 않는다. PDFBox를 포함한 runtime JAR, frontend bundle, image와
model을 실제로 배포할 때는 각 artifact의 license·NOTICE·SBOM coverage를
다시 확인하고 해당 release의 `NOTICE`를 확장한다. Codex는 개발 보조도구이며
저작권자·공동 기여자·runtime dependency로 이 파일들에 기록하지 않았다.

## Blocker 요약

### VERIFIED

- 공식 source register의 PDF·ZIP hash와 요구사항 위치
- 실제 Gradle `runtimeClasspath`, `testRuntimeClasspath`, `buildEnvironment`
- npm lock 183개 전부의 exact version, registry tarball, integrity와
  declared license
- tracked model·이미지·문서 asset 부재와 wrapper JAR 단일 binary
- synthetic fixture의 형식·hash, 사용자 직접·Codex 보조 작성, 제3자 비파생,
  개인정보·기밀 부재와 Apache-2.0 공개 동의
- 초기 PRIZM 골격과 inline Mermaid의 직접·Codex 보조 작성
- Spec Kit·Robo Architect의 외부 참고 경계와 외부 file 미포함
- 독립 PRIZM color·spacing·radius token과 외부 Toss reference token 제거
- Pretendard 공식 `OFL-1.1` 확인과 font binary·CDN 미포함 경계
- Gradle 9.5.1 distribution checksum, 377-component dependency verification
  metadata와 dependency-management plugin·`org.tomlj`의 Apache-2.0 근거
- 모든 third-party GitHub Action의 full commit SHA와 version 주석
- Ollama `v0.32.3` archive checksum과 `bge-m3` registry/local manifest
  fail-closed Gate 구현

### UNKNOWN

- future `bootJar`·frontend bundle의 최종 component·NOTICE coverage
- 각 container의 platform digest와 base package SBOM
- Ollama `bge-m3`와 BAAI upstream revision 사이의 변환 lineage. 이 제한은
  model 재배포와 정확한 lineage 주장을 막지만 source-only PRIZM 배포물에는
  model bytes가 포함되지 않는다.

### CONFLICT

- 현재 source-only 배포 범위에서 확인된 license 충돌은 없다. Ollama
  `bge-m3` license blob의 저작권자 placeholder는 위 `UNVERIFIED_LINEAGE`와
  future model 재배포 blocker로 유지한다.

### 다음 release Gate

- 실제 제출 직전 공개 commit·tree·환경·결과 hash 재고정
- fat JAR·`dist`·container image·Ollama binary·model weight의 future release

현재 source-only 범위의 T-02와 T-03 결정은 마감됐다. model lineage 제한은
`NOT_DISTRIBUTED` 경계와 함께 명시하며 PostgreSQL 성공이나 실제 OpenSQL
single-node SQL Gate 결과로 대체하지 않는다. T-09 CI와 T-10 최종 감사까지
통과한 PRZ-002의 현재 source-only 범위는 `VERIFIED`다.

## T-05 SBOM·AI 모델 명세 구현 기록

2026-07-26에 source-only 배포 경계를 위한 기계 판독용 SBOM과 AI model
manifest를 구현했다. 상세 범위·재생성 명령, T-05 당시 `NOT_RUN` 환경과 이후
repository-level 검증 경계는
[SBOM 및 AI 모델 명세](2026-sbom-model-manifest.md)를 기준으로 한다.

| 항목 | 현재 기록 | tool/license 판정 | 배포 경계 |
|---|---|---|---|
| backend runtime | CycloneDX 1.6, Gradle `runtimeClasspath` | first-party `generateBackendSbom` Gradle task, Apache-2.0; existing verification metadata의 resolved artifact SHA-256을 record | resolved JAR은 `EXTERNAL_PROVIDED_NOT_DISTRIBUTED` |
| frontend lockfile | CycloneDX 1.6, 183 versioned `package-lock.json` entries | first-party `scripts/generate-frontend-sbom.mjs`, Apache-2.0, Node standard-library only | npm packages는 `EXTERNAL_PROVIDED_NOT_DISTRIBUTED` |
| model record | Ollama v0.32.3, BAAI `bge-m3` revision, Ollama registry manifest/blob hashes | model bytes와 PRIZM source license 분리; registry lineage `UNVERIFIED_LINEAGE` 유지 | Ollama binary·weights·cache `NOT_DISTRIBUTED` |
| scope·integrity | runtime/test-build/CI/container/model/asset scope manifest와 `SHA256SUMS` | first-party structural verifier가 local path·JDBC URL·credential-shaped data와 checksum drift 검사 | source-only record 자체는 Git tracked |

### T-05 human/machine component 조정

사람용 Java runtime exact set은 Gradle dependency graph의 module identity
167개를 기록한다. 기계 SBOM은 실제로 해소된 runtime artifact JAR을 기록하므로
다음 두 차이를 명시적으로 조정한다.

- `org.hibernate.orm:hibernate-platform:7.4.1.Final`과
  `tools.jackson:jackson-bom:3.1.4`는 dependency constraint를 제공하는
  metadata-only component라 runtime JAR artifact가 없다.
- `io.netty:netty-codec-native-quic:4.2.15.Final` 한 module identity는
  Linux x86_64·aarch64, macOS x86_64·aarch64, Windows x86_64의 classifier
  JAR 5개로 해소된다. 기계 SBOM은 각 classifier를 PURL qualifier에 포함한다.

따라서 machine artifact 수는 `167 - 2 + (5 - 1) = 169`개다. frontend는
사람용·기계용 모두 `package-lock.json`의 183개 versioned entry를 기준으로 한다.
이 기록은 수량 차이를 숨기지 않고 module graph와 physical artifact inventory의
관계를 설명한다. commit `8dd57c4`의 clean-checkout VERIFY와 독립 읽기 전용
AUDIT는 통과했다. AUDIT에서 지적한 문서 gate 현행화와 LF 회귀 검증은 후속
보완 commit에서 반영했으며, 이것은 현재 source-only T-05 판정을 바꾸지 않는다.

`@cyclonedx/cyclonedx-npm`은 frontend generator로 채택하지 않았다. 6.0.0 후보는
당시 full npm audit에서 high finding 10건이 있었고, 4.0.1 후보는 full audit
endpoint가 package tree를 거부하여 신뢰 가능한 전이 취약점 판정을 만들지 못했다.
따라서 외부 npm SBOM tool을 새로 배포·개발 의존성으로 넣지 않고, lockfile만
읽는 first-party generator를 선택했다. 이것은 npm ecosystem 전체 license
판단을 자동으로 완결한다는 주장이 아니며, 누락·충돌 판단은 위 human audit과
후속 독립 대조에서 계속 확인한다.

**T-05 상태:** 현재 source-only 배포 범위에서 `VERIFIED`. 생성기의 고정 LF,
표준 hash algorithm, classifier-aware identity와 위 human/machine 조정 규칙을
구현하고 clean checkout·공식 CycloneDX 1.6 schema·checksum·독립 읽기 전용
AUDIT를 통과했다. 이후 T-09 CI와 T-10 최종 감사도 통과했다. 제출 직전
snapshot 고정과 future binary/image/model 배포는 별도 후속 Gate다.

## T-09 source-only license Gate 자동 검사

2026-07-29에 외부 license 도구를 추가하지 않는 first-party 검증기
`scripts/verify-oss-readiness.mjs`를 구현했다. 검증기는 현재 source-only
배포 경계만 판정하며, JAR·frontend bundle·container image·Ollama binary·model
weights를 향후 재배포할 때 필요한 별도 감사를 통과시킨 것으로 보지 않는다.

- `sbom/prizm-scope-manifest.json`의 Gate가 `PASS`, blocker가 빈 배열인지와
  `UNKNOWN`·`CONFLICT`·`BLOCKED`가 차단 상태로 등록됐는지 검사한다.
- frontend lockfile 183개 entry의 license가 누락됐거나 `NONE`,
  `NOASSERTION`, `UNLICENSED`, `UNKNOWN`이면 실패한다.
- backend runtime의 사람용 license inventory는 이 문서의 T-05 조정 결과와
  machine SBOM 169개 artifact 관계를 유지한다. 이 검증기는 POM에서 license를
  새로 추론하거나 미확인 항목을 임의로 `VERIFIED`로 바꾸지 않는다.
- 기존 dependency verification metadata를 strict 모드로 적용하면서 backend
  SBOM을 재생성하고, frontend lockfile SBOM도 재생성해 committed checksum과
  달라지면 실패한다.
- Git tracked path와 text를 검사해 model cache, 업로드 원본, credential,
  private key와 사용자 로컬 절대 경로가 source 배포물에 들어오는 것을
  차단한다.

corrective commit `192295227f566815fa026259d2053b1c73e641f2`에서 Windows local,
Linux clean clone과 GitHub
[`OSS Readiness` push run](https://github.com/jaemin-devlog/PRIZM/actions/runs/30443185952)이
같은 단일 명령으로 통과했다. 이후 현재 `main`의 동일 OSS Readiness·CI와 T-10
최종 감사까지 통과해 PRZ-002의 source-only 범위를 닫았다. 이 결과는 future
binary/image/model 배포 감사나 OpenSQL 공급 bundle의 재배포 권리를 뜻하지
않으며, 실제 OpenSQL single-node SQL Gate는 PRZ-003의 별도 실행 근거다.

## OpenSQL 대회용 외부 runtime 경계

PRZ-003 검증 환경에는 공급사가 별도로 제공한 OpenSQL 대회용 테스트 runtime과
라이선스를 사용한다. 이 자산은 PRIZM의 Apache-2.0 source 배포물이나 SBOM의
`included` 범위가 아니다.

| 항목 | 판정 |
|---|---|
| 사용 권한 | 별도로 보존한 공급사 승인과 테스트 라이선스의 허가 범위에서만 사용 |
| 저장소 포함 | archive, 추출물, license, 설정, credential, key, screenshot, log 모두 `NOT_DISTRIBUTED` |
| 공개 근거 | Rocky Linux 9.7 single-node 설치 여부와 비식별 test 결과만 기록 |
| 재배포 권리 | 확인하거나 가정하지 않음. bundled OSS의 개별 license가 공급사 전용 bundle 전체의 재배포 권한을 부여한다고 해석하지 않음 |
| 향후 Gate | OpenSQL binary·설치 image·license를 release 또는 container에 포함하려면 공급사의 명시적 허가와 별도 license·NOTICE·SBOM 감사를 먼저 수행 |
| 현재 상태 | `EXTERNAL_PROVIDED_NOT_DISTRIBUTED` |

이 경계는 OpenSQL 설치 또는 호환성 결과를 대신하지 않는다. 설치 전용 결과와
PRIZM 실행 Gate는 [PRZ-003 Evidence](../../specs/PRZ-003-opensql-single-node-gate/evidence.md)에서
분리해 관리한다.
