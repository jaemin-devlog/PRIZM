# PRZ-016 P7-A Dataset Freeze Plan

## 생성 구조

```text
p7-cross-document-generalization/
  spec.md
  plan.md
  tasks.md
  evidence.md
  baseline.json
  dataset/
    corpus-manifest.json
    documents/
    inactive-versions/
    pdf-sources/
    questions.json
    ground-truth.json
  tools/
    generate-pdfs.py
    validate-p7.py
  rendered/
  freeze-manifest.json
```

PDF 최종 산출물은 사용자가 지정한 frozen dataset 경계 안에 있어야 하므로 일반 `output/pdf/`가
아니라 `dataset/documents/`에 둔다. `rendered/` PNG는 P7-A 시각 검증 근거로 함께 동결한다.

## 사용자·문서 설계

| User | Domain / stack | Resume | Portfolio | Style |
|---|---|---|---|---|
| SYN-U01 | 냉장 물류 / C#·.NET 8·Azure Service Bus·SQL Server | TXT | PDF | 짧은 bullet + 긴 서술형 |
| SYN-U02 | 유전체 batch pipeline / Python·FastAPI·Airflow·Spark·Delta Lake | PDF | TXT | 숫자 중심 + incident log |
| SYN-U03 | 해양 제어 / Go·ConnectRPC·NATS JetStream·CockroachDB | TXT | PDF | 한/영 혼합 + 설계 case study |
| SYN-U04 | 박물관 아카이브 / Node.js·NestJS·Meilisearch·Cloudflare R2 | PDF | TXT | compact card + chronology |

SYN-U03 resume에는 같은 logical document의 inactive v0 TXT fixture를 먼저 정의한다. P7-B는 이를
등록한 뒤 ACTIVE v1으로 교체해 과거-version negative를 검증할 수 있지만, P7-A에서는 DB나 검색을
사용하지 않는다.

## 작성 순서

1. 기존 P0/P5 query와 project/fact 금지 목록을 고정한다.
2. 4명의 active document 사실과 negative-only 사실을 서로 겹치지 않게 설계한다.
3. TXT와 PDF source를 작성하고 PDF를 생성·렌더링·시각 검사한다.
4. 문서를 보면서 검색 전에 48개 질문과 ground truth를 작성한다.
5. validation tool로 count, category, positive anchor, negative absence, owner/version, 누출을 검사한다.
6. 모든 검증이 통과한 뒤에만 hash와 freeze manifest를 생성한다.
7. freeze 이후에는 read-only audit만 수행하고 파일 내용을 수정하지 않는다.

## 누출 검사

- raw exact duplicate
- Unicode NFKC·lowercase·문장부호/공백 제거 normalized duplicate
- 문자 bigram Dice와 token Jaccard 후보 검사 후 수동 검토
- P0/P5 project·fact identifier 금지 목록 검사
- 새 질문끼리의 exact/normalized duplicate 검사

near-duplicate 자동 threshold는 후보 탐지용이며 최종 판정은 질문 목적·핵심 사실·표현 구조를 함께
검토한다. threshold 아래라고 자동 PASS 처리하지 않는다.

## Ground truth 검증

- TXT는 UTF-8 line과 section anchor를 검증한다.
- PDF는 `pdfplumber`로 page별 text를 추출해 지정 anchor를 검증한다.
- positive anchor는 해당 owner ACTIVE version에 존재해야 한다.
- negative forbidden anchor는 해당 owner의 모든 ACTIVE 문서에 없어야 한다.
- cross-user negative는 다른 owner 문서에만 존재하는지 별도로 확인한다.
- inactive-version negative는 v0 fixture에는 있고 ACTIVE v1에는 없는지 확인한다.

## 중단·rollback

- 검색 또는 embedding 호출이 발생하면 P7-A를 동결하지 않고 중단한다.
- production diff가 생기면 해당 변경을 임의로 되돌리지 않고 사용자 변경 여부를 확인한다.
- duplicate·anchor·PDF 시각 검증이 실패하면 freeze 전 자산만 수정하고 다시 검증한다.
- freeze 이후 오류를 발견하면 manifest나 자산을 덮어쓰지 않고 P7-A 실패로 기록한다.
- commit/push/PR은 수행하지 않는다.

