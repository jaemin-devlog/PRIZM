#!/usr/bin/env python3
"""Combine frozen Semantic Support Judge output with the existing numeric verifier."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
from typing import Any

from run_semantic_nli_numeric_shadow import (
    NUMERIC_DENIAL,
    SENTENCE,
    metric_tokens,
    numeric_values,
    numeric_veto,
)


RUNNER_VERSION = "PRZ-016-SEMANTIC-NUMERIC-JUDGE-SHADOW-v1"
JUDGE_LABELS = {"SUPPORTED", "REFUTED", "INSUFFICIENT"}


def sha256_file(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def verify_sha256(path: Path, expected: str) -> str:
    actual = sha256_file(path)
    if actual != expected.lower():
        raise ValueError(f"SHA-256 mismatch for {path}: expected {expected.lower()}, got {actual}")
    return actual


def sorted_numeric_values(values: set[tuple[str, str]]) -> list[dict[str, str]]:
    return [{"value": value, "unit": unit} for value, unit in sorted(values)]


def evaluate_numeric_claim(premise: str, hypothesis: str) -> dict[str, Any]:
    expected = numeric_values(hypothesis)
    if not expected:
        return {
            "status": "NOT_APPLICABLE",
            "reason": "NO_NUMERIC_CLAIM",
            "expectedValues": [],
            "metricTokens": [],
            "numericVeto": None,
        }

    metrics = metric_tokens(hypothesis)
    if not metrics:
        return {
            "status": "NUMERIC_UNRESOLVED",
            "reason": "HYPOTHESIS_METRIC_MISSING",
            "expectedValues": sorted_numeric_values(expected),
            "metricTokens": [],
            "numericVeto": None,
        }

    sentences = [item.strip() for item in SENTENCE.split(premise) if item.strip()]
    relevant: list[str] = []
    for index, sentence in enumerate(sentences):
        if metrics & metric_tokens(sentence):
            relevant.append(sentence)
            if index + 1 < len(sentences):
                relevant.append(sentence + " " + sentences[index + 1])
    if not relevant:
        return {
            "status": "NUMERIC_UNRESOLVED",
            "reason": "PREMISE_METRIC_MISSING",
            "expectedValues": sorted_numeric_values(expected),
            "metricTokens": sorted(metrics),
            "numericVeto": None,
        }

    veto = numeric_veto(premise, hypothesis)
    if veto:
        return {
            "status": "CONTRADICTION",
            "reason": veto,
            "expectedValues": sorted_numeric_values(expected),
            "metricTokens": sorted(metrics),
            "numericVeto": veto,
        }

    consistent = any(
        expected & numeric_values(claim) and not NUMERIC_DENIAL.search(claim)
        for claim in relevant
    )
    if consistent:
        return {
            "status": "CONSISTENT",
            "reason": "EXACT_VALUE_UNIT_METRIC_MATCH",
            "expectedValues": sorted_numeric_values(expected),
            "metricTokens": sorted(metrics),
            "numericVeto": None,
        }

    return {
        "status": "NUMERIC_UNRESOLVED",
        "reason": "VALUE_UNIT_BINDING_UNRESOLVED",
        "expectedValues": sorted_numeric_values(expected),
        "metricTokens": sorted(metrics),
        "numericVeto": None,
    }


def combined_label(judge_label: str, numeric: dict[str, Any]) -> str:
    if judge_label != "SUPPORTED":
        return judge_label
    if numeric["status"] == "CONTRADICTION":
        return "REFUTED"
    if numeric["status"] == "NUMERIC_UNRESOLVED":
        return "NUMERIC_UNRESOLVED"
    return "SUPPORTED"


def build_judge_summary(rows: list[dict[str, Any]]) -> dict[str, Any]:
    core = [row for row in rows if row["group"] == "C"]
    group_ab = [row for row in rows if row["group"] in {"A", "B"}]
    return {
        "groupCSupported": f'{sum(row["judgeLabel"] == "SUPPORTED" for row in core)}/5',
        "groupABSupportSupported": (
            f'{sum(row["groundTruth"] == "SUPPORT" and row["judgeLabel"] == "SUPPORTED" for row in group_ab)}/40'
        ),
        "groupABContradictFalseSupport": (
            f'{sum(row["groundTruth"] == "CONTRADICT" and row["judgeLabel"] == "SUPPORTED" for row in group_ab)}/52'
        ),
        "groupABUnknownFalseSupport": (
            f'{sum(row["groundTruth"] == "UNKNOWN" and row["judgeLabel"] == "SUPPORTED" for row in group_ab)}/16'
        ),
        "modelOutputInvalid": sum(row["judgeStatus"] == "MODEL_OUTPUT_INVALID" for row in rows),
    }


def build_combined_summary(rows: list[dict[str, Any]]) -> dict[str, Any]:
    core = [row for row in rows if row["group"] == "C"]
    group_ab = [row for row in rows if row["group"] in {"A", "B"}]
    core_supported = sum(row["combinedLabel"] == "SUPPORTED" for row in core)
    positive_supported = sum(
        row["groundTruth"] == "SUPPORT" and row["combinedLabel"] == "SUPPORTED"
        for row in group_ab
    )
    negative_false_support = sum(
        row["groundTruth"] == "CONTRADICT" and row["combinedLabel"] == "SUPPORTED"
        for row in group_ab
    )
    unknown_false_support = sum(
        row["groundTruth"] == "UNKNOWN" and row["combinedLabel"] == "SUPPORTED"
        for row in group_ab
    )
    invalid = sum(row["judgeStatus"] == "MODEL_OUTPUT_INVALID" for row in rows)
    numeric_unresolved = sum(row["combinedLabel"] == "NUMERIC_UNRESOLVED" for row in rows)
    checks = {
        "groupCSupportedAtLeastFourOfFive": {"passed": core_supported >= 4, "actual": f"{core_supported}/5"},
        "groupABSupportAtLeastThirtyNineOfForty": {
            "passed": positive_supported >= 39,
            "actual": f"{positive_supported}/40",
        },
        "groupABContradictFalseSupportAtMostOne": {
            "passed": negative_false_support <= 1,
            "actual": f"{negative_false_support}/52",
        },
        "groupABUnknownFalseSupportAtMostOne": {
            "passed": unknown_false_support <= 1,
            "actual": f"{unknown_false_support}/16",
        },
        "modelOutputInvalidZero": {"passed": invalid == 0, "actual": invalid},
    }
    all_passed = all(check["passed"] for check in checks.values())
    return {
        "groupCSupported": f"{core_supported}/5",
        "groupABSupportSupported": f"{positive_supported}/40",
        "groupABContradictFalseSupport": f"{negative_false_support}/52",
        "groupABUnknownFalseSupport": f"{unknown_false_support}/16",
        "modelOutputInvalid": invalid,
        "numericUnresolved": numeric_unresolved,
        "numericBlockedIds": [row["id"] for row in rows if row["numeric"]["status"] == "CONTRADICTION"],
        "remainingFalseSupportIds": [
            row["id"]
            for row in group_ab
            if row["groundTruth"] == "CONTRADICT" and row["combinedLabel"] == "SUPPORTED"
        ],
        "gate": {
            "checks": checks,
            "allPassed": all_passed,
            "final": "SEMANTIC_NUMERIC_JUDGE_PROMISING" if all_passed else "SEMANTIC_NUMERIC_JUDGE_NO_GO",
        },
    }


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

    pairs_sha256 = verify_sha256(args.pairs, args.expected_pairs_sha256)
    judge_sha256 = verify_sha256(args.judge_results, args.expected_judge_results_sha256)
    checkpoint_sha256 = verify_sha256(args.judge_checkpoint, args.expected_judge_checkpoint_sha256)
    pairs_document = json.loads(args.pairs.read_text(encoding="utf-8"))
    judge_document = json.loads(args.judge_results.read_text(encoding="utf-8"))
    pairs = {pair["id"]: pair for pair in pairs_document["pairs"]}
    if len(pairs) != len(pairs_document["pairs"]):
        raise ValueError("duplicate pair ID in frozen dataset")

    judge_results = judge_document.get("results")
    if not isinstance(judge_results, list) or len(judge_results) != len(pairs):
        raise ValueError("judge result count does not match frozen pair count")

    rows: list[dict[str, Any]] = []
    seen: set[str] = set()
    for result in judge_results:
        pair_id = result.get("id")
        if pair_id in seen:
            raise ValueError(f"duplicate judge result ID: {pair_id}")
        seen.add(pair_id)
        pair = pairs.get(pair_id)
        if pair is None:
            raise ValueError(f"judge result has no frozen pair: {pair_id}")
        if result.get("groundTruth") != pair["groundTruth"]:
            raise ValueError(f"ground truth mismatch: {pair_id}")
        decision = result.get("decision")
        judge_label = decision.get("label") if isinstance(decision, dict) else None
        if result.get("status") == "VALID" and judge_label not in JUDGE_LABELS:
            raise ValueError(f"valid judge result has invalid label: {pair_id}")

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
                "group": result["group"],
                "groundTruth": pair["groundTruth"],
                "hypothesis": pair["hypothesis"],
                "judgeStatus": result["status"],
                "judgeLabel": judge_label,
                "numericClaim": bool(numeric_values(pair["hypothesis"])),
                "numeric": numeric,
                "combinedLabel": final_label,
            }
        )

    if seen != set(pairs):
        raise ValueError("frozen pairs and judge result IDs differ")

    numeric_verifier_path = Path(__file__).with_name("run_semantic_nli_numeric_shadow.py")
    output = {
        "schemaVersion": 1,
        "mode": "SEMANTIC_SUPPORT_JUDGE_V3_PLUS_NUMERIC_SHADOW",
        "runnerVersion": RUNNER_VERSION,
        "sources": {
            "pairs": {"path": str(args.pairs), "sha256": pairs_sha256},
            "judgeResults": {"path": str(args.judge_results), "sha256": judge_sha256},
            "judgeCheckpoint": {"path": str(args.judge_checkpoint), "sha256": checkpoint_sha256},
            "numericVerifier": {
                "path": str(numeric_verifier_path),
                "sha256": sha256_file(numeric_verifier_path),
            },
        },
        "results": rows,
        "summary": {
            "judgeV3": build_judge_summary(rows),
            "combined": build_combined_summary(rows),
        },
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    temporary = args.output.with_name(args.output.name + ".tmp")
    if args.output.exists() or temporary.exists():
        raise FileExistsError("combined output or its temporary file already exists")
    temporary.write_text(json.dumps(output, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    os.replace(temporary, args.output)
    print(json.dumps(output["summary"], ensure_ascii=False))


if __name__ == "__main__":
    main()
