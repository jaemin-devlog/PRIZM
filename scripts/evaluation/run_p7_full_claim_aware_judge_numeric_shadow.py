#!/usr/bin/env python3
"""Apply the existing deterministic numeric verifier to frozen P7-B Judge v3 output."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
from typing import Any

from run_p7_full_claim_aware_judge import load_pairs
from run_semantic_support_judge_numeric_shadow import combined_label, evaluate_numeric_claim


RUNNER_VERSION = "PRZ-016-P7-FULL-CLAIM-AWARE-JUDGE-NUMERIC-SHADOW-v1"
JUDGE_LABELS = {"SUPPORTED", "REFUTED", "INSUFFICIENT"}


def sha256_file(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def verify_sha256(path: Path, expected: str) -> str:
    actual = sha256_file(path)
    if actual != expected.lower():
        raise ValueError(f"SHA-256 mismatch for {path}: expected {expected.lower()}, got {actual}")
    return actual


def write_new_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    if path.exists() or temporary.exists():
        raise FileExistsError(f"refusing to overwrite output: {path}")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    os.replace(temporary, path)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--pairs", type=Path, required=True)
    parser.add_argument("--expected-pairs-sha256", required=True)
    parser.add_argument("--judge-results", type=Path, required=True)
    parser.add_argument("--expected-judge-results-sha256", required=True)
    parser.add_argument("--judge-checkpoint", type=Path, required=True)
    parser.add_argument("--expected-judge-checkpoint-sha256", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    pairs, pairs_sha256 = load_pairs(args.pairs, args.expected_pairs_sha256)
    judge_results_sha256 = verify_sha256(args.judge_results, args.expected_judge_results_sha256)
    judge_checkpoint_sha256 = verify_sha256(args.judge_checkpoint, args.expected_judge_checkpoint_sha256)
    judge_document = json.loads(args.judge_results.read_text(encoding="utf-8"))
    if judge_document.get("summary", {}).get("complete") is not True:
        raise ValueError("Judge result is not a complete 430-pair run")
    judge_results = judge_document.get("results")
    if not isinstance(judge_results, list) or len(judge_results) != len(pairs):
        raise ValueError("Judge result count does not match frozen pair count")

    pair_by_id = {pair["id"]: pair for pair in pairs}
    seen: set[str] = set()
    rows: list[dict[str, Any]] = []
    for result in judge_results:
        pair_id = result.get("id")
        if pair_id in seen:
            raise ValueError(f"duplicate Judge result ID: {pair_id}")
        seen.add(pair_id)
        pair = pair_by_id.get(pair_id)
        if pair is None:
            raise ValueError(f"Judge result has no frozen pair: {pair_id}")
        for field in ("queryId", "originalRank", "candidateRank"):
            if result.get(field) != pair[field]:
                raise ValueError(f"Judge result metadata mismatch for {field}: {pair_id}")

        decision = result.get("decision")
        judge_label = decision.get("label") if isinstance(decision, dict) else None
        if result.get("status") == "VALID" and judge_label not in JUDGE_LABELS:
            raise ValueError(f"valid Judge result has invalid label: {pair_id}")
        numeric = (
            evaluate_numeric_claim(pair["premise"], pair["hypothesis"])
            if result.get("status") == "VALID" and judge_label == "SUPPORTED"
            else {
                "status": "NOT_EVALUATED",
                "reason": "JUDGE_NON_SUPPORT_OR_INVALID",
                "expectedValues": [],
                "metricTokens": [],
                "numericVeto": None,
            }
        )
        final_label = (
            combined_label(judge_label, numeric)
            if result.get("status") == "VALID"
            else "MODEL_OUTPUT_INVALID"
        )
        rows.append(
            {
                "id": pair_id,
                "queryId": pair["queryId"],
                "originalRank": pair["originalRank"],
                "candidateRank": pair["candidateRank"],
                "judgeStatus": result["status"],
                "judgeLabel": judge_label,
                "numeric": numeric,
                "combinedLabel": final_label,
                "finalSupport": final_label == "SUPPORTED",
            }
        )
    if seen != set(pair_by_id):
        raise ValueError("frozen pair and Judge result IDs differ")

    label_distribution: dict[str, int] = {}
    for row in rows:
        label_distribution[row["combinedLabel"]] = label_distribution.get(row["combinedLabel"], 0) + 1
    numeric_verifier = Path(__file__).with_name("run_semantic_nli_numeric_shadow.py")
    output = {
        "schemaVersion": 1,
        "phase": "PRZ-016-P7-B-FULL-CLAIM-AWARE-JUDGE-V3-NUMERIC-SHADOW",
        "runnerVersion": RUNNER_VERSION,
        "sources": {
            "pairs": {"path": str(args.pairs), "sha256": pairs_sha256},
            "judgeResults": {"path": str(args.judge_results), "sha256": judge_results_sha256},
            "judgeCheckpoint": {"path": str(args.judge_checkpoint), "sha256": judge_checkpoint_sha256},
            "numericVerifier": {"path": str(numeric_verifier), "sha256": sha256_file(numeric_verifier)},
        },
        "policy": {
            "numericContradiction": "REFUTED",
            "numericConsistentOrNotApplicable": "PRESERVE_JUDGE_LABEL",
            "numericUnresolved": "FAIL_CLOSED_NON_SUPPORT",
            "groundTruthScoring": "NOT_RUN",
        },
        "results": rows,
        "summary": {
            "pairCount": len(rows),
            "originalResultCount": len({(row["queryId"], row["originalRank"]) for row in rows}),
            "queryCountWithResults": len({row["queryId"] for row in rows}),
            "labelDistribution": label_distribution,
            "modelOutputInvalid": sum(row["judgeStatus"] == "MODEL_OUTPUT_INVALID" for row in rows),
            "numericUnresolved": sum(row["combinedLabel"] == "NUMERIC_UNRESOLVED" for row in rows),
            "numericBlocked": sum(row["numeric"]["status"] == "CONTRADICTION" for row in rows),
            "groundTruthScoring": "NOT_RUN",
        },
    }
    write_new_json(args.output, output)
    print(json.dumps(output["summary"], ensure_ascii=False))


if __name__ == "__main__":
    main()
