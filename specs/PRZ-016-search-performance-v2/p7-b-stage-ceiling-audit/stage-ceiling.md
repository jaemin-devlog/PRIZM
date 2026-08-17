# PRZ-016 P7-B Search Stage Ceiling Audit

## Integrity

- P7-B raw SHA-256: `defc5e35dbf26f48a640f3df673e2247c14437b0cb65e8c8c05a0bd3b6e2cb2e`
- Ground Truth SHA-256: `fd7525da3a00df4d7eccf42022b54a63cb2571be9f20111d2d6de740aa5f9680`
- Active corpus aggregate SHA-256: `fef6cb0b38fea658b03dfd06a43212acb84b57922acec764c49a5032fd795498`
- Active document hashes: 8/8 PASS.
- Owner/ACTIVE/version processing: 8/8 ACTIVE documents completed, 30 searchable chunks; inactive V0 is not the final active version.
- Search behavior changes: 0. Search/model inference reruns: NOT_RUN.

The original `prizm-p7b-20260816` Docker project and volume are no longer present. The frozen artifacts retain final results and content, but not pre-filter or post-filter candidate IDs. Therefore S2/S3 losses cannot be reconstructed without a new run and are reported as unknown rather than inferred.

## Positive ceiling

| Stage | Found | Lost at this stage | Unknown |
|---|---:|---:|---:|
| S0 Corpus Oracle | 36 | 0 | 0 |
| S1 Chunk Oracle | 36 | 0 | 0 |
| S2 Pre-filter Candidate | at least 22 | 0 proven | 14 |
| S3 Post-filter | at least 22 | 0 proven | 14 |
| S4 Final Top5 | 22 | 0 proven | 14 absent, prior loss unknown |
| S5 Result Content | 22 | 0 | 0 among S4 |
| S6 Localization | 19 | 3 | 0 among S5 |

S1 is supported directly for 34 queries by actual frozen `result.content` chunks observed across the complete raw result artifact. The two remaining anchors, `V2-U04-D01` and `V2-U04-CN02`, are in the first fixed 800/120 chunk of their frozen PDF page; the P7-B owner snapshot records all four U04 resume chunks completed.

S4 uses the original result chunk identity and `result.content`, not the separately localized production snippet. This exposes `V2-U03-IP01` as a correct rank-1 result whose content contains the GT anchor even though the existing localized evidence points elsewhere.

## First-failure taxonomy

- CORPUS_MISSING: 0
- CHUNKING: 0
- CANDIDATE_RECALL: 0 proven
- FILTERING: 0 proven
- RANKING: 0 proven
- RESULT_CONTENT: 0
- LOCALIZATION: 3
- UNKNOWN: 14
- PASSED_ALL_OBSERVED_STAGES: 19

Zero proven losses at S2-S4 do not exonerate those stages. Fourteen queries have correct S1 chunks but no correct final Top5 result, while the frozen artifacts omit the intermediate IDs needed to distinguish candidate recall, filtering, and ranking.

## Failed Positive queries

### UNKNOWN_STAGE_TRACE_REQUIRED

- `V2-U01-D01`: expected `골재·아스콘 주문을 출하 거점에 배정하고 계근 완료 중량을 운송 전표로 확정하는 서비스`; S1 chunk 7 found, correct S4 result absent.
- `V2-U01-NV02`: expected subscription filter/owner routing evidence; S1 chunk 11 found, correct S4 result absent.
- `V2-U02-D02`: expected Airflow/Spark operation evidence; S1 chunk 15 found, correct S4 result absent.
- `V2-U02-NV02`: expected scene/revision retransmission evidence; S1 chunk 20 found, correct S4 result absent.
- `V2-U02-IP02`: expected damaged GeoTIFF isolation evidence; S1 chunk 20 found, correct S4 result absent.
- `V2-U02-NI01`: expected 6.4TB and 138-to-41-minute evidence; S1 chunk 15 found, correct S4 result absent.
- `V2-U02-CN01`: expected backfill, zero-duplicate, and checkpoint evidence; S1 chunks 20/21 found, correct S4 result absent.
- `V2-U02-CN02`: expected corrupted-input exclusion evidence; S1 chunk 20 found, correct S4 result absent.
- `V2-U03-NI01`: expected 12,800 trains/16 zones evidence; S1 chunk 3 found, correct S4 result absent.
- `V2-U04-D01`: expected NestJS catalog ingestion/search API evidence; S1 active PDF chunk 25 found, correct S4 result absent.
- `V2-U04-D02`: expected 2,300,000 records/Meilisearch evidence; S1 chunk 26 found, correct S4 result absent.
- `V2-U04-IP01`: expected conflicting territory-share review evidence; S1 chunk 30 found, correct S4 result absent.
- `V2-U04-NI01`: expected search P95 1.6 seconds to 240 milliseconds evidence; S1 chunk 26 found, correct S4 result absent.
- `V2-U04-CN02`: expected catalog snapshot reproducibility evidence; S1 active PDF chunk 27 found, correct S4 result absent.

For all 14, S2/S3 candidate IDs are unavailable, so the first failure cannot be assigned to candidate recall, filtering, or ranking.

### LOCALIZATION

- `V2-U03-NV01`: correct chunk 22 is present at final rank 3, but none of its five frozen claim-aware windows contains `permit_epoch가 포함됐다.` or the accepted stale-epoch rejection anchor.
- `V2-U03-IP01`: correct chunk 3 is present at final rank 1, but none of its five frozen claim-aware windows contains the accepted 9.6-to-2.7-second failover anchor.
- `V2-U03-CN01`: correct chunk 22 is present at final rank 2, but none of its five frozen claim-aware windows contains the accepted permit-epoch or signed-package anchor.

## Important finding

The corpus and chunk ceiling is 36/36, while correct result content reaches final Top5 for 22/36. Claim-aware localization preserves acceptable evidence for 19/22 of those results and loses three. The largest resolved loss is therefore LOCALIZATION, but the larger 14-query gap lies somewhere across S2 candidate recall, S3 filtering, or S4 ranking and cannot be split from the frozen evidence.

`PRIMARY BOTTLENECK = UNKNOWN_STAGE_TRACE_REQUIRED`

`NEXT = INSUFFICIENT_TRACE_DATA`

No redesign is selected from this audit because doing so would require guessing which search stage caused the 14-query loss.
