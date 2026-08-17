#!/usr/bin/env python3
"""Freeze the existing P7-B claim-aware candidate windows as Judge v3 input pairs."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from run_semantic_support_judge import RUNNER_VERSION as JUDGE_VERSION
from run_semantic_support_judge import prompt_sha256, schema_sha256


BUILDER_VERSION = "PRZ-016-P7-FULL-JUDGE-COVERAGE-BUILDER-v1"
PAIR_KEYS = {"id", "queryId", "originalRank", "candidateRank", "premise", "hypothesis"}


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def verify_sha256(path: Path, expected: str) -> str:
    actual = sha256_bytes(path.read_bytes())
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


def load_json(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"JSON root must be an object: {path}")
    return value


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--raw-results", type=Path, required=True)
    parser.add_argument("--expected-raw-sha256", required=True)
    parser.add_argument("--semantic-pairs", type=Path, required=True)
    parser.add_argument("--expected-semantic-pairs-sha256", required=True)
    parser.add_argument("--candidates", type=Path, required=True)
    parser.add_argument("--expected-candidates-sha256", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--freeze", type=Path, required=True)
    args = parser.parse_args()

    raw_sha256 = verify_sha256(args.raw_results, args.expected_raw_sha256)
    semantic_pairs_sha256 = verify_sha256(args.semantic_pairs, args.expected_semantic_pairs_sha256)
    candidates_sha256 = verify_sha256(args.candidates, args.expected_candidates_sha256)
    raw_document = load_json(args.raw_results)
    semantic_document = load_json(args.semantic_pairs)
    candidate_document = load_json(args.candidates)

    accounts = {account["userKey"]: str(account["userId"]) for account in raw_document.get("accounts", [])}
    raw_results: dict[tuple[str, int], dict[str, Any]] = {}
    query_users: dict[str, str] = {}
    for query in raw_document.get("queries", []):
        query_id = query["id"]
        query_users[query_id] = accounts[query["userKey"]]
        for rank, result in enumerate(query["response"].get("results", []), start=1):
            key = (query_id, rank)
            if key in raw_results:
                raise ValueError(f"duplicate raw result key: {key}")
            raw_results[key] = result

    semantic_pairs: dict[tuple[str, int], dict[str, Any]] = {}
    for pair in semantic_document.get("pairs", []):
        key = (pair["queryId"], pair["rank"])
        if key in semantic_pairs:
            raise ValueError(f"duplicate semantic pair key: {key}")
        semantic_pairs[key] = pair

    candidate_rows: dict[tuple[str, int], dict[str, Any]] = {}
    for row in candidate_document.get("pairs", []):
        key = (row["queryId"], row["rank"])
        if key in candidate_rows:
            raise ValueError(f"duplicate candidate result key: {key}")
        candidate_rows[key] = row

    if not raw_results or set(raw_results) != set(semantic_pairs) or set(raw_results) != set(candidate_rows):
        raise ValueError("raw results, semantic pairs, and candidate original-result keys differ")

    pairs: list[dict[str, Any]] = []
    pair_ids: set[str] = set()
    hypothesis_changed = 0
    premise_mismatch = 0
    for key in raw_results:
        query_id, original_rank = key
        raw_result = raw_results[key]
        semantic_pair = semantic_pairs[key]
        candidate_row = candidate_rows[key]
        if candidate_row["hypothesis"] != semantic_pair["hypothesis"]:
            hypothesis_changed += 1
        if candidate_row["originalSnippet"] != semantic_pair["premise"]:
            raise ValueError(f"candidate original snippet differs from semantic pair: {key}")
        if candidate_row["originalSnippet"] != raw_result["snippet"]:
            raise ValueError(f"candidate original snippet differs from raw result: {key}")
        if str(candidate_row["userId"]) != str(semantic_pair["userId"]):
            raise ValueError(f"candidate user differs from semantic pair: {key}")
        if str(candidate_row["userId"]) != query_users[query_id]:
            raise ValueError(f"candidate user differs from raw query owner: {key}")

        identity = candidate_row["resultIdentity"]
        for field in ("chunkId", "documentId", "documentVersionId", "sourceType", "sourceIndex", "score", "distance"):
            if identity[field] != raw_result[field]:
                raise ValueError(f"result identity mismatch for {field}: {key}")
        if identity["contentSha256"] != sha256_bytes(raw_result["content"].encode("utf-8")):
            raise ValueError(f"result content SHA mismatch: {key}")

        candidates = candidate_row.get("candidates")
        if not isinstance(candidates, list) or len(candidates) != 5:
            raise ValueError(f"expected exactly five frozen candidates: {key}")
        orders = [candidate["order"] for candidate in candidates]
        if orders != [1, 2, 3, 4, 5]:
            raise ValueError(f"candidate order changed: {key}")
        for candidate in candidates:
            candidate_rank = candidate["order"]
            expected_id = f"{query_id}#r{original_rank}#w{candidate_rank}"
            if candidate["id"] != expected_id:
                raise ValueError(f"candidate ID does not match its result/rank: {candidate['id']}")
            if candidate["id"] in pair_ids:
                raise ValueError(f"duplicate candidate ID: {candidate['id']}")
            pair = {
                "id": candidate["id"],
                "queryId": query_id,
                "originalRank": original_rank,
                "candidateRank": candidate_rank,
                "premise": candidate["window"],
                "hypothesis": candidate_row["hypothesis"],
            }
            if set(pair) != PAIR_KEYS or pair["premise"] != candidate["window"]:
                premise_mismatch += 1
            pairs.append(pair)
            pair_ids.add(candidate["id"])

    if hypothesis_changed or premise_mismatch:
        raise ValueError(
            f"freeze integrity failed: hypothesisChanged={hypothesis_changed}, premiseMismatch={premise_mismatch}"
        )
    if len(raw_results) != 86 or len(pairs) != 430:
        raise ValueError(f"unexpected coverage: originalResults={len(raw_results)}, candidatePairs={len(pairs)}")

    output_document = {
        "schemaVersion": 1,
        "phase": "PRZ-016-P7-B-FULL-CLAIM-AWARE-JUDGE-V3-COVERAGE",
        "datasetRole": "SHADOW_INFERENCE_INPUT_NO_GROUND_TRUTH",
        "sourceCandidatesSha256": candidates_sha256,
        "judgeVersion": JUDGE_VERSION,
        "pairs": pairs,
    }
    write_new_json(args.output, output_document)
    pairs_sha256 = sha256_bytes(args.output.read_bytes())

    script_directory = Path(__file__).resolve().parent
    judge_adapter = script_directory / "run_p7_full_claim_aware_judge.py"
    numeric_adapter = script_directory / "run_p7_full_claim_aware_judge_numeric_shadow.py"
    numeric_verifier = script_directory / "run_semantic_nli_numeric_shadow.py"
    for implementation in (judge_adapter, numeric_adapter, numeric_verifier):
        if not implementation.is_file():
            raise FileNotFoundError(f"required evaluation implementation is missing: {implementation}")

    freeze_document = {
        "schemaVersion": 1,
        "phase": "PRZ-016-P7-B-FULL-CLAIM-AWARE-JUDGE-V3-FREEZE",
        "frozenAt": datetime.now(timezone.utc).isoformat(),
        "groundTruthUsedBeforeFreeze": False,
        "sources": {
            "rawResults": {"path": str(args.raw_results), "sha256": raw_sha256},
            "semanticPairs": {"path": str(args.semantic_pairs), "sha256": semantic_pairs_sha256},
            "claimAwareCandidates": {"path": str(args.candidates), "sha256": candidates_sha256},
        },
        "runnerPairs": {
            "path": str(args.output),
            "sha256": pairs_sha256,
            "pairCount": len(pairs),
            "queryCountWithResults": len({pair["queryId"] for pair in pairs}),
            "originalResultCount": len(raw_results),
            "candidatesPerResult": {"min": 5, "max": 5},
        },
        "judge": {
            "version": JUDGE_VERSION,
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
        "builder": {"version": BUILDER_VERSION, "path": str(Path(__file__).resolve()), "sha256": sha256_bytes(Path(__file__).read_bytes())},
        "integrity": {
            "rawSemanticCandidateResultKeysMatch": True,
            "resultIdentityPreserved": True,
            "resultContentSha256Match": True,
            "hypothesisChanged": hypothesis_changed,
            "premiseMismatch": premise_mismatch,
            "duplicateCandidateIds": 0,
            "missingOriginalResults": 0,
        },
        "inference": "NOT_RUN",
        "groundTruthScoring": "NOT_RUN",
    }
    write_new_json(args.freeze, freeze_document)
    print(
        json.dumps(
            {
                "status": "P7_FULL_JUDGE_PAIRS_FROZEN",
                "pairsSha256": pairs_sha256,
                "pairCount": len(pairs),
                "originalResultCount": len(raw_results),
                "queryCountWithResults": len({pair["queryId"] for pair in pairs}),
            },
            ensure_ascii=False,
        )
    )


if __name__ == "__main__":
    main()
