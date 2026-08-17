# PRZ-016 P7-B Unknown 14 Query Stage Trace

- Executed at: `2026-08-16T21:57:11.410845200Z`
- Baseline reproduction: `14/14`
- Search behavior changed: `NO`

| ID | Correct chunk | S2 | S3 | S4 | First failure |
|---|---:|---|---|---|---|
| V2-U01-D01 | [7] | YES ORIGINAL r2 score=0.476716 | NO | NO | FILTERING |
| V2-U01-NV02 | [11] | YES ORIGINAL r1 score=0.552322 | NO | NO | FILTERING |
| V2-U02-D02 | [15] | YES ORIGINAL r3 score=0.526516 | NO | NO | FILTERING |
| V2-U02-NV02 | [20] | YES ORIGINAL r3 score=0.471640 | NO | NO | FILTERING |
| V2-U02-IP02 | [20] | YES ORIGINAL r2 score=0.555912 | NO | NO | FILTERING |
| V2-U02-NI01 | [15] | YES ORIGINAL r2 score=0.508029 | NO | NO | FILTERING |
| V2-U02-CN01 | [20,21] | YES ORIGINAL r3 score=0.554588 | NO | NO | FILTERING |
| V2-U02-CN02 | [20] | YES ORIGINAL r5 score=0.436648 | NO | NO | FILTERING |
| V2-U03-NI01 | [3] | YES ORIGINAL r2 score=0.524330 | NO | NO | FILTERING |
| V2-U04-D01 | [25] | YES ORIGINAL r3 score=0.480799 | NO | NO | FILTERING |
| V2-U04-D02 | [26] | YES ORIGINAL r4 score=0.449125 | NO | NO | FILTERING |
| V2-U04-IP01 | [30] | YES ORIGINAL r3 score=0.438238 | NO | NO | FILTERING |
| V2-U04-NI01 | [26] | YES ORIGINAL r2 score=0.524862 | NO | NO | FILTERING |
| V2-U04-CN02 | [27] | YES ORIGINAL r4 score=0.495763 | NO | NO | FILTERING |
