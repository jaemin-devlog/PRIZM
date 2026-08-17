#!/usr/bin/env python3
"""Freeze P7-B's existing five claim-aware windows per original result as one Judge input."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from run_p7_result_level_evidence_set_judge import (
    ADAPTER_VERSION,
    prompt_sha256,
    schema_sha256,
)


BUILDER_VERSION = "PRZ-016-P7-RESULT-LEVEL-EVIDENCE-SET-BUILDER-v1"
SOURCE_PAIR_KEYS = {"id", "queryId", "originalRank", "candidateRank", "premise", "hypothesis"}
RESULT_KEYS = {"id", "queryId", "originalRank", "resultIdentity", "hypothesis", "evidenceWindows"}
WINDOW_KEYS = {"id", "sourceCandidateId", "candidateRank", "text"}


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def verify_sha256(path: Path, expected: str) -> str:
    actual = sha256_bytes(path.read_bytes())
    if actual != expected.lower():
        raise ValueError(f"SHA-256 mismatch for {path}: expected {expected.lower()}, got {actual}")
    return actual


def load_object(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"JSON root must be an object: {path}")
    return value


def write_new_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    if path.exists() or temporary.exists():
        raise FileExistsError(f"refusing to overwrite frozen output: {path}")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    os.replace(temporary, path)


def validate_source_pairs(document: dict[str, Any]) -> tuple[list[dict[str, Any]], dict[tuple[str, int], list[dict[str, Any]]]]:
    pairs = document.get("pairs")
    if not isinstance(pairs, list) or len(pairs) != 430:
        raise ValueError("source candidate pair artifact must contain exactly 430 pairs")
    grouped: dict[tuple[str, int], list[dict[str, Any]]] = {}
    seen_ids: set[str] = set()
    for pair in pairs:
        if not isinstance(pair, dict) or set(pair) != SOURCE_PAIR_KEYS:
            raise ValueError(f"invalid source pair shape: {pair!r}")
        if not all(isinstance(pair[key], str) and pair[key] for key in ("id", "queryId", "premise", "hypothesis")):
            raise ValueError(f"source pair string field is empty: {pair!r}")
        if not isinstance(pair["originalRank"], int) or pair["originalRank"] <= 0:
            raise ValueError(f"invalid original rank: {pair!r}")
        if not isinstance(pair["candidateRank"], int) or not 1 <= pair["candidateRank"] <= 5:
            raise ValueError(f"invalid candidate rank: {pair!r}")
        expected_id = f'{pair["queryId"]}#r{pair["originalRank"]}#w{pair["candidateRank"]}'
        if pair["id"] != expected_id:
            raise ValueError(f"source pair ID does not match metadata: {pair['id']}")
        if pair["id"] in seen_ids:
            raise ValueError(f"duplicate source pair ID: {pair['id']}")
        seen_ids.add(pair["id"])
        grouped.setdefault((pair["queryId"], pair["originalRank"]), []).append(pair)
    if len(grouped) != 86:
        raise ValueError(f"expected 86 original results, got {len(grouped)}")
    return pairs, grouped


def validate_candidate_rows(document: dict[str, Any]) -> dict[tuple[str, int], dict[str, Any]]:
    rows = document.get("pairs")
    if not isinstance(rows, list) or len(rows) != 86:
        raise ValueError("claim-aware candidate artifact must contain exactly 86 result rows")
    indexed: dict[tuple[str, int], dict[str, Any]] = {}
    for row in rows:
        if not isinstance(row, dict):
            raise ValueError("claim-aware result row must be an object")
        key = (row.get("queryId"), row.get("rank"))
        if not isinstance(key[0], str) or not key[0] or not isinstance(key[1], int) or key[1] <= 0:
            raise ValueError(f"invalid claim-aware result identity: {key}")
        if key in indexed:
            raise ValueError(f"duplicate claim-aware result key: {key}")
        if not isinstance(row.get("resultIdentity"), dict) or not row["resultIdentity"]:
            raise ValueError(f"missing result identity: {key}")
        indexed[key] = row
    return indexed


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--candidate-pairs", type=Path, required=True)
    parser.add_argument("--expected-candidate-pairs-sha256", required=True)
    parser.add_argument("--claim-aware-candidates", type=Path, required=True)
    parser.add_argument("--expected-claim-aware-candidates-sha256", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--freeze", type=Path, required=True)
    args = parser.parse_args()

    source_pairs_sha256 = verify_sha256(args.candidate_pairs, args.expected_candidate_pairs_sha256)
    candidates_sha256 = verify_sha256(args.claim_aware_candidates, args.expected_claim_aware_candidates_sha256)
    _, grouped_pairs = validate_source_pairs(load_object(args.candidate_pairs))
    candidate_rows = validate_candidate_rows(load_object(args.claim_aware_candidates))
    if set(grouped_pairs) != set(candidate_rows):
        raise ValueError("source candidate pairs and claim-aware result keys differ")

    results: list[dict[str, Any]] = []
    source_candidate_ids: set[str] = set()
    hypothesis_changed = 0
    identity_mismatch = 0
    for key, source_group in grouped_pairs.items():
        query_id, original_rank = key
        candidate_row = candidate_rows[key]
        source_ranks = [pair["candidateRank"] for pair in source_group]
        if source_ranks != [1, 2, 3, 4, 5]:
            raise ValueError(f"source candidate order is not frozen 1 through 5: {key}")
        hypotheses = {pair["hypothesis"] for pair in source_group}
        if len(hypotheses) != 1 or candidate_row.get("hypothesis") not in hypotheses:
            hypothesis_changed += 1

        candidates = candidate_row.get("candidates")
        if not isinstance(candidates, list) or len(candidates) != 5:
            raise ValueError(f"expected five claim-aware candidates: {key}")
        if [candidate.get("order") for candidate in candidates] != [1, 2, 3, 4, 5]:
            raise ValueError(f"claim-aware candidate order changed: {key}")

        windows: list[dict[str, Any]] = []
        for index, (source_pair, candidate) in enumerate(zip(source_group, candidates, strict=True), start=1):
            if source_pair["id"] != candidate.get("id") or source_pair["premise"] != candidate.get("window"):
                raise ValueError(f"source pair and claim-aware window differ: {source_pair['id']}")
            if source_pair["id"] in source_candidate_ids:
                raise ValueError(f"duplicate source candidate ID: {source_pair['id']}")
            source_candidate_ids.add(source_pair["id"])
            window = {
                "id": f"W{index}",
                "sourceCandidateId": source_pair["id"],
                "candidateRank": source_pair["candidateRank"],
                "text": source_pair["premise"],
            }
            if set(window) != WINDOW_KEYS:
                raise AssertionError("internal window shape error")
            windows.append(window)

        result = {
            "id": f"{query_id}#r{original_rank}",
            "queryId": query_id,
            "originalRank": original_rank,
            "resultIdentity": candidate_row["resultIdentity"],
            "hypothesis": source_group[0]["hypothesis"],
            "evidenceWindows": windows,
        }
        if set(result) != RESULT_KEYS:
            raise AssertionError("internal result shape error")
        results.append(result)

    if hypothesis_changed or identity_mismatch:
        raise ValueError(
            f"freeze integrity failed: hypothesisChanged={hypothesis_changed}, identityMismatch={identity_mismatch}"
        )
    if len(results) != 86 or len(source_candidate_ids) != 430:
        raise ValueError("result-level coverage differs from the frozen 86-result/430-window contract")

    output_document = {
        "schemaVersion": 1,
        "phase": "PRZ-016-P7-B-RESULT-LEVEL-EVIDENCE-SET-JUDGE-INPUT",
        "datasetRole": "SHADOW_INFERENCE_INPUT_NO_GROUND_TRUTH",
        "sourceCandidatePairsSha256": source_pairs_sha256,
        "sourceClaimAwareCandidatesSha256": candidates_sha256,
        "results": results,
    }
    write_new_json(args.output, output_document)
    dataset_sha256 = sha256_bytes(args.output.read_bytes())

    script_directory = Path(__file__).resolve().parent
    judge_adapter = script_directory / "run_p7_result_level_evidence_set_judge.py"
    numeric_adapter = script_directory / "run_p7_result_level_evidence_set_numeric_shadow.py"
    numeric_verifier = script_directory / "run_semantic_nli_numeric_shadow.py"
    for implementation in (judge_adapter, numeric_adapter, numeric_verifier):
        if not implementation.is_file():
            raise FileNotFoundError(f"required evaluation implementation is missing: {implementation}")

    freeze_document = {
        "schemaVersion": 1,
        "phase": "PRZ-016-P7-B-RESULT-LEVEL-EVIDENCE-SET-FREEZE",
        "frozenAt": datetime.now(timezone.utc).isoformat(),
        "groundTruthUsedBeforeFreeze": False,
        "sources": {
            "candidatePairs": {"path": str(args.candidate_pairs), "sha256": source_pairs_sha256},
            "claimAwareCandidates": {"path": str(args.claim_aware_candidates), "sha256": candidates_sha256},
        },
        "resultLevelDataset": {
            "path": str(args.output),
            "sha256": dataset_sha256,
            "resultCount": len(results),
            "queryCountWithResults": len({result["queryId"] for result in results}),
            "windowCount": sum(len(result["evidenceWindows"]) for result in results),
            "windowsPerResult": {"min": 5, "max": 5},
        },
        "judge": {
            "version": ADAPTER_VERSION,
            "model": "qwen3:4b-instruct",
            "schemaSha256": schema_sha256(),
            "promptSha256": prompt_sha256(),
            "adapter": {"path": str(judge_adapter), "sha256": sha256_bytes(judge_adapter.read_bytes())},
        },
        "numeric": {
            "adapter": {"path": str(numeric_adapter), "sha256": sha256_bytes(numeric_adapter.read_bytes())},
            "verifier": {"path": str(numeric_verifier), "sha256": sha256_bytes(numeric_verifier.read_bytes())},
            "unresolvedPolicy": "FAIL_CLOSED_NON_SUPPORT",
        },
        "builder": {
            "version": BUILDER_VERSION,
            "path": str(Path(__file__).resolve()),
            "sha256": sha256_bytes(Path(__file__).read_bytes()),
        },
        "integrity": {
            "sourceResultKeysMatch": True,
            "sourceWindowTextMatch": True,
            "candidateOrderPreserved": True,
            "hypothesisChanged": hypothesis_changed,
            "identityMismatch": identity_mismatch,
            "duplicateSourceCandidateIds": 0,
        },
        "inference": "NOT_RUN",
        "groundTruthScoring": "NOT_RUN",
    }
    write_new_json(args.freeze, freeze_document)
    print(
        json.dumps(
            {
                "status": "RESULT_LEVEL_EVIDENCE_SET_PAIRS_FROZEN",
                "datasetSha256": dataset_sha256,
                "resultCount": len(results),
                "windowCount": len(source_candidate_ids),
                "groundTruthUsedBeforeFreeze": False,
            },
            ensure_ascii=False,
        )
    )


if __name__ == "__main__":
    main()
