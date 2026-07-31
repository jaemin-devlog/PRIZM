# 2026 오픈소스 개발자대회 공식 근거 등록부

> 이 문서는 공식 출처의 URL, artifact identity와 재배포 경계의 단일
> 원본이다. 요구사항 대응은 [추적표](2026-requirements-traceability.md),
> source-only 배포 결론은 [2026 compliance](2026-compliance.md)를 따른다.

## 문서 상태

| 항목 | 값 |
|---|---|
| PRZ 작업 | [`PRZ-002-open-source-readiness`](../../specs/PRZ-002-open-source-readiness/spec.md) |
| 작업 범위 | T-01 공식 source register |
| 기준일 | 2026-07-24 |
| 상태 | `COMPLETE` |
| 원문 보관 정책 | 공식 PDF·ZIP·이미지와 사용자 제공 캡처를 저장소에 복사하지 않음 |

이 문서는 PRIZM의 대회 요구사항을 어떤 공식 자료에서 확인했는지 추적한다.
공식 원문을 대신하지 않으며 법률 자문도 아니다. 원문이 바뀌면 URL만 믿지
말고 아래 hash·크기와 다시 대조한다.

## 상태와 근거 등급

| 값 | 의미 |
|---|---|
| `VERIFIED_OFFICIAL_WEB` | 공식 도메인의 웹 문서임을 확인했으나 내용이 동적으로 바뀔 수 있음 |
| `VERIFIED_OFFICIAL_ARTIFACT` | 공식 페이지가 연결한 artifact의 media type·크기·SHA-256을 확인함 |
| `OT_AUXILIARY_USER_PROVIDED` | 사용자가 제공했지만 공개 원본 artifact URL이 없는 보조 자료 |
| `UNKNOWN_DO_NOT_COMMIT` | 재배포 권리 또는 원본 동일성을 확정하지 못해 저장소에 넣지 않음 |
| `SUPERSEDED` | 더 새로운 공식 근거가 확인되어 더 이상 현재 판단에 사용하지 않음 |

## Source 목록

| Source ID | 발행·운영 주체 | 제목 | Canonical / artifact URL | 발행·수집 정보 | Hash·크기 | 적용 요구사항 | 저작권·재배포 | 검증 상태 |
|---|---|---|---|---|---|---|---|---|
| `SRC-CONTEST-HOME` | 오픈소스 개발자대회 운영사무국·한국오픈소스협회 | 오픈소스 개발자대회 | [공식 홈페이지](https://osscontest.kr/) | 게시일 미제공; 2026-07-24 KST 수집 | 동적 HTML 관찰값은 artifact hash로 사용하지 않음 | 대회 주체와 공식 도메인 식별 | Footer의 권리 고지를 존중해 링크와 최소 요약만 기록 | `VERIFIED_OFFICIAL_WEB` |
| `SRC-CONTEST-OVERVIEW` | 오픈소스 개발자대회 운영사무국·한국오픈소스협회 | 대회 개요 | [공식 개요](https://osscontest.kr/overview) | 게시일 미제공; 2026-07-24 KST 수집 | 동적 HTML 관찰값은 artifact hash로 사용하지 않음 | 일정, 제출물, 운영규정·결과보고서 공식 연결 | 링크와 최소 요약만 기록 | `VERIFIED_OFFICIAL_WEB` |
| `SRC-TMAX-TASK` | 한국오픈소스협회·티맥스티베로 | 2026 오픈소스 개발자대회 티맥스티베로 지정과제 | [지정과제 원문](https://www.kossa.kr/materials/2026/ossp/tasks-tmax.html) | 게시일 미제공; 2026-07-24 KST URL·내용 확인 | 동적 HTML 관찰값은 artifact hash로 사용하지 않음 | OpenSQL 기반 AI 검색·벡터 데이터 플랫폼 미션과 문서 업로드·자동 임베딩·메타데이터·버전 관리·변경 로그 동기화·MCP 검색 개발과제 예시 | 링크와 최소 요약만 기록하고 페이지를 저장소에 복사하지 않음 | `VERIFIED_OFFICIAL_WEB` |
| `SRC-CONTEST-RULES` | 오픈소스 개발자대회 운영사무국·한국오픈소스협회 | 2026년 오픈소스 개발자대회 운영규정 | [공식 PDF](https://api.osscontest.kr/static/uploads/b3b4491a-3bbe-454e-a1d8-6ed475b01b14.pdf) | 문서 2026.06; 시행 2026-06-15; 2026-07-24 KST 수집 | `application/pdf`; 15쪽; 170,020 bytes; `5C129ED9F389ECC04B6F7BA8B97F719A313EFAF32AEA9178E635500023AE1DA1` | 직접 작성 코드의 OSI 라이선스, 외부 구성요소·모델 출처와 라이선스, AI 모델 조건, 전체 source·공개 저장소 | 출처 표시를 전제로 필요한 최소 인용·활용은 가능하지만 무단 수정·변형·복제·배포 제한이 있어 원문은 커밋하지 않음 | `VERIFIED_OFFICIAL_ARTIFACT` |
| `SRC-CONTEST-REPORT` | 운영사무국을 통한 공식 배포; artifact 내부 발행자 표기는 없음 | 2026 오픈소스 개발자대회 결과보고서 양식 | [공식 ZIP](https://api.osscontest.kr/static/uploads/46414fba-c473-4dae-b595-7214d635b494.zip) | 게시일 미제공; 2026-07-24 KST 수집 | `application/zip`; 142,434 bytes; `9A5D2968D48FF8A8FD85CE991DC72DC2B0818D7E8C06EBB871CC97CE5CC62D95` | 결과보고서, SBOM, AI 모델 활용·라이선스 명세 필드 | ZIP과 내부 양식에 별도 재배포 허락이 없어 원본·작성본 모두 커밋하지 않음 | `VERIFIED_OFFICIAL_ARTIFACT`, 권리 `UNKNOWN_DO_NOT_COMMIT` |
| `SRC-CONTEST-OT-NOTICE` | 오픈소스 개발자대회 운영사무국·한국오픈소스협회 | 「2026 오픈소스 개발자대회」 오리엔테이션 안내(7/23(목)) | [공식 공지](https://osscontest.kr/notice/31), [공지 이미지](https://api.osscontest.kr/static/uploads/8d98fbbb-d256-4fa1-9521-1ff689f0c885.png) | 공지 UI 게시일 2026-07-22; 2026-07-24 KST 수집 | 이미지 1,836,176 bytes; `808E6B3C6830A6B05431795DFE1FA4EA325CBEE81AC5FEAF450985B0153A77D7` | OT 개최와 평가기준 안내 세션의 존재 | 공개 이미지는 행사 안내일 뿐 세부 평가 슬라이드 원본이 아님; 링크와 최소 요약만 기록 | `VERIFIED_OFFICIAL_WEB` |
| `SRC-CONTEST-OT-AUX-02` | 사용자 제공; 발행 주체는 캡처 표기 범위만 확인 | OT `프로그램(2/4)` 캡처 | 공개 원본 URL 없음 | 2026-07-24 사용자 제공·검증 | `image/png`; 2150×1401; 1,320,448 bytes; `6446410FC85288DC53BD1E60C4D3B7E8977262631DF1358FB73DD7DB346E79D2` | 1차 평가 항목·배점의 보조 확인 | 공개 원본과 재배포 허락을 확인하지 못해 캡처를 커밋하지 않음 | `OT_AUXILIARY_USER_PROVIDED`, `UNKNOWN_DO_NOT_COMMIT` |
| `SRC-CONTEST-OT-AUX-03` | 사용자 제공; 발행 주체는 캡처 표기 범위만 확인 | OT `프로그램(3/4)` 캡처 | 공개 원본 URL 없음 | 2026-07-24 사용자 제공·검증 | `image/png`; 1992×1359; 1,351,693 bytes; `29A12F15D08FAF3BC4878931FDB0C669C2173329FCA7C22B0F3A46EB1EADF21B` | 멘토링·기능·라이선스 검증 항목의 보조 확인 | 공개 원본과 재배포 허락을 확인하지 못해 캡처를 커밋하지 않음 | `OT_AUXILIARY_USER_PROVIDED`, `UNKNOWN_DO_NOT_COMMIT` |
| `SRC-CONTEST-OT-AUX-04` | 사용자 제공; 발행 주체는 캡처 표기 범위만 확인 | OT `프로그램(4/4)` 캡처 | 공개 원본 URL 없음 | 2026-07-24 사용자 제공·검증 | `image/png`; 1999×1357; 1,214,243 bytes; `756BEFFC71746B323357FAC970308784319AF03B0D25E08026F1724A90F7D061` | 2차 발표 평가 항목·배점의 보조 확인 | 공개 원본과 재배포 허락을 확인하지 못해 캡처를 커밋하지 않음 | `OT_AUXILIARY_USER_PROVIDED`, `UNKNOWN_DO_NOT_COMMIT` |

## 운영규정 요구사항 연결

아래는 원문을 길게 복제하지 않고 PRZ-002에 필요한 의미만 요약한 것이다.
쪽 번호는 PDF viewer 기준이다.

| Claim ID | 근거 | 필요한 조치 | PRZ-002 연결 |
|---|---|---|---|
| `CLAIM-RULES-01` | 5쪽 제7조 | 참가자가 제출물의 권리와 책임을 확인하고 주최 측 이용 조건을 인지 | 저작권자·기여자·외부 자산 provenance 감사 |
| `CLAIM-RULES-02` | 5~6쪽 제8조 | 직접 작성 코드는 OSI 승인 라이선스를 사용하고 외부 라이브러리·프레임워크·모델의 출처와 라이선스를 공개 | T-02 license audit, G-01 배포 경계, T-03 outgoing license |
| `CLAIM-RULES-03` | 6~7쪽 제9조 | AI 모델의 가중치 공개 수준, 라이선스·약관, 직접 작성 코드와 모델 정보를 분리해 확인 | Ollama·`bge-m3`·Codex 분리 기록, T-05 AI 명세 |
| `CLAIM-RULES-04` | 7쪽 제10조 | 전체 source 제출과 공개 저장소 운영 | 공개 repository·clean-clone·제출 commit 검증 |
| `CLAIM-RULES-05` | 14쪽 별표 2 | 가중치 접근·다운로드·독립 구동·재배포 충돌을 확인 | `bge-m3` revision·manifest·blob·재배포 Gate |
| `CLAIM-RULES-06` | 15쪽 | 출처 표시는 하되 원문 수정·변형과 무단 복제·배포를 피함 | 이 등록부만 공개하고 PDF 원문은 커밋하지 않음 |

이 연결은
[`2026-requirements-traceability.md`](2026-requirements-traceability.md)의
`OR-*` 추적을 보완한다. 운영규정이 최종 권위이며 이 요약과 충돌하면 원문을
따른다.

## 결과보고서 필드 연결

| 위치 | 공식 양식이 요구하는 정보 | PRZ-002 산출물 |
|---|---|---|
| 안내 1쪽 | 2026-08-27 18:00까지 원본 형식과 PDF 결과보고서 제출 | 제출 시점 source commit·산출물 hash 고정 |
| 본문 2~3쪽 | 공개 저장소, 시연 영상, 개발환경, architecture, 기능, 구동·테스트, 확장성과 한계 | README·Quickstart·architecture·검증 evidence |
| 붙임 1 | 구성요소 이름, version, license, 공식 repository URL, 사용 목적을 포함한 SBOM | T-02 사람용 audit와 T-05 machine-readable SBOM |
| 붙임 2 | 적용 유형별 기반 모델, 개발사, license, dataset·가중치 공개 정보, 직접 작성 코드 license와 repository | Ollama·`bge-m3`·Codex를 분리한 AI 모델 명세 |

양식 안의 AI 보조도구 사용 비율은 작성 예시이지 필수 측정값이 아니다.
PRIZM은 Codex를 개발 보조도구로 공개하되 근거 없는 비율은 만들지 않는다.

## 원문 비포함·갱신 정책

- 공식 PDF·ZIP·공지 이미지·OT 캡처를 Git 저장소, source archive,
  release asset에 포함하지 않는다.
- 문서에는 공식 URL, 파일명, 검증일, media type, bytes와 SHA-256,
  적용에 필요한 최소 요약만 남긴다.
- 공식 artifact의 bytes 또는 hash가 달라지면 기존 row를 즉시 덮어쓰지
  않고 변경 시각과 새 hash를 비교한 뒤 supersession을 기록한다.
- 공개 원본이 확보되기 전에는 사용자 제공 OT 캡처를 공식 artifact나
  primary evidence로 승격하지 않는다.
- 이 문서의 상태가 `COMPLETE`여도 대회 요구사항 전체가 완료됐다는 뜻은
  아니다. 라이선스·배포 경계의 현재 blocker는
  [`2026-license-audit.md`](2026-license-audit.md)에 기록한다.
