#!/usr/bin/env python3
"""Run Judge v3 over frozen P7-B claim-aware candidate windows without ground truth."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
import statistics
import sys
from pathlib import Path
from typing import Any

from run_semantic_support_judge import (
    DEFAULT_ENDPOINT,
    DEFAULT_MODEL,
    LABELS,
    RUNNER_VERSION as JUDGE_VERSION,
    append_checkpoint,
    call_ollama,
    initialize_checkpoint,
    load_checkpoint,
    prompt_sha256,
    resolve_evidence_sentences,
    schema_sha256,
    segment_evidence,
    validate_decision,
)


ADAPTER_VERSION = "PRZ-016-P7-FULL-CLAIM-AWARE-JUDGE-v1"
PAIR_KEYS = {"id", "queryId", "originalRank", "candidateRank", "premise", "hypothesis"}


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def percentile_95(values: list[float]) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    return ordered[math.ceil(len(ordered) * 0.95) - 1]


def load_pairs(path: Path, expected_sha256: str) -> tuple[list[dict[str, Any]], str]:
    raw = path.read_bytes()
    actual_sha256 = sha256_bytes(raw)
    if actual_sha256 != expected_sha256.lower():
        raise ValueError(f"pair SHA-256 mismatch: expected {expected_sha256.lower()}, got {actual_sha256}")
    document = json.loads(raw.decode("utf-8"))
    pairs = document.get("pairs")
    if not isinstance(pairs, list) or len(pairs) != 430:
        raise ValueError("frozen runner dataset must contain exactly 430 pairs")
    pair_ids: set[str] = set()
    result_candidates: dict[tuple[str, int], list[int]] = {}
    for pair in pairs:
        if not isinstance(pair, dict) or set(pair) != PAIR_KEYS:
            raise ValueError(f"invalid pair shape: {pair!r}")
        for key in ("id", "queryId", "premise", "hypothesis"):
            if not isinstance(pair[key], str) or not pair[key]:
                raise ValueError(f"pair field must be a non-empty string: {pair!r}")
        if not isinstance(pair["originalRank"], int) or pair["originalRank"] <= 0:
            raise ValueError(f"invalid originalRank: {pair!r}")
        if not isinstance(pair["candidateRank"], int) or not 1 <= pair["candidateRank"] <= 5:
            raise ValueError(f"invalid candidateRank: {pair!r}")
        expected_id = f'{pair["queryId"]}#r{pair["originalRank"]}#w{pair["candidateRank"]}'
        if pair["id"] != expected_id:
            raise ValueError(f"pair ID does not match metadata: {pair['id']}")
        if pair["id"] in pair_ids:
            raise ValueError(f"duplicate pair ID: {pair['id']}")
        pair_ids.add(pair["id"])
        result_candidates.setdefault((pair["queryId"], pair["originalRank"]), []).append(pair["candidateRank"])
    if len(result_candidates) != 86:
        raise ValueError(f"expected 86 original results, got {len(result_candidates)}")
    if any(ranks != [1, 2, 3, 4, 5] for ranks in result_candidates.values()):
        raise ValueError("each original result must have candidates 1 through 5 in frozen order")
    return pairs, actual_sha256


def result_from_envelope(pair: dict[str, Any], envelope: dict[str, Any], latency_ms: float) -> dict[str, Any]:
    evidence_sentences = segment_evidence(pair["premise"])
    message = envelope.get("message")
    content = message.get("content") if isinstance(message, dict) else None
    validation_errors: list[str] = []
    parsed: Any = None
    if not isinstance(content, str) or not content:
        validation_errors.append("response message.content is missing or empty")
    else:
        try:
            parsed = json.loads(content)
        except json.JSONDecodeError:
            validation_errors.append("response message.content is not valid JSON")
    if not validation_errors:
        validation_errors.extend(validate_decision(parsed, evidence_sentences))
    status = "VALID" if not validation_errors else "MODEL_OUTPUT_INVALID"
    grounded_evidence = (
        resolve_evidence_sentences(parsed, evidence_sentences)
        if status == "VALID" and isinstance(parsed, dict)
        else []
    )
    return {
        "id": pair["id"],
        "queryId": pair["queryId"],
        "originalRank": pair["originalRank"],
        "candidateRank": pair["candidateRank"],
        "status": status,
        "decision": parsed if isinstance(parsed, dict) else None,
        "groundedEvidence": grounded_evidence,
        "validationErrors": validation_errors,
        "rawMessageContent": content,
        "latencyMs": latency_ms,
        "ollama": {
            "model": envelope.get("model"),
            "createdAt": envelope.get("created_at"),
            "done": envelope.get("done"),
            "doneReason": envelope.get("done_reason"),
            "totalDurationNs": envelope.get("total_duration"),
            "loadDurationNs": envelope.get("load_duration"),
            "promptEvalCount": envelope.get("prompt_eval_count"),
            "promptEvalDurationNs": envelope.get("prompt_eval_duration"),
            "evalCount": envelope.get("eval_count"),
            "evalDurationNs": envelope.get("eval_duration"),
        },
    }


def build_summary(records: list[dict[str, Any]], complete: bool) -> dict[str, Any]:
    distribution = {label: 0 for label in LABELS}
    invalid = 0
    latencies: list[float] = []
    for record in records:
        latencies.append(float(record["latencyMs"]))
        if record["status"] == "MODEL_OUTPUT_INVALID":
            invalid += 1
        else:
            distribution[record["decision"]["label"]] += 1
    return {
        "complete": complete,
        "completedPairCount": len(records),
        "modelOutputInvalid": invalid,
        "labelDistribution": distribution,
        "latencyMs": {
            "average": statistics.fmean(latencies) if latencies else 0.0,
            "p95": percentile_95(latencies),
            "total": sum(latencies),
        },
        "groundTruthScoring": "NOT_RUN",
    }


def write_aggregate(
    output_path: Path,
    pairs_path: Path,
    dataset_sha256: str,
    model: str,
    endpoint: str,
    checkpoint_path: Path,
    records: list[dict[str, Any]],
    complete: bool,
) -> None:
    output = {
        "schemaVersion": 1,
        "phase": "PRZ-016-P7-B-FULL-CLAIM-AWARE-JUDGE-V3-INFERENCE",
        "adapterVersion": ADAPTER_VERSION,
        "judgeVersion": JUDGE_VERSION,
        "dataset": {"path": str(pairs_path), "sha256": dataset_sha256},
        "model": model,
        "endpoint": endpoint,
        "requestContract": {
            "stream": False,
            "think": False,
            "temperature": 0,
            "schemaSha256": schema_sha256(),
            "promptSha256": prompt_sha256(),
        },
        "checkpoint": str(checkpoint_path),
        "results": records,
        "summary": build_summary(records, complete),
    }
    output_path.parent.mkdir(parents=True, exist_ok=True)
    temporary = output_path.with_name(output_path.name + ".tmp")
    if temporary.exists():
        raise FileExistsError(f"temporary output already exists: {temporary}")
    temporary.write_text(json.dumps(output, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    os.replace(temporary, output_path)


def run(args: argparse.Namespace) -> None:
    pairs_path = args.pairs.resolve()
    pairs, dataset_sha256 = load_pairs(pairs_path, args.expected_sha256)
    if args.check:
        print(
            json.dumps(
                {
                    "status": "CHECK_PASS",
                    "datasetSha256": dataset_sha256,
                    "pairCount": len(pairs),
                    "originalResultCount": 86,
                    "queryCountWithResults": len({pair["queryId"] for pair in pairs}),
                    "judgeVersion": JUDGE_VERSION,
                    "schemaSha256": schema_sha256(),
                    "promptSha256": prompt_sha256(),
                },
                ensure_ascii=False,
            )
        )
        return
    if args.output is None or args.checkpoint is None:
        raise ValueError("--output and --checkpoint are required unless --check is used")
    if args.limit is not None and args.limit <= 0:
        raise ValueError("--limit must be positive")

    endpoint = args.endpoint.rstrip("/")
    output_path = args.output.resolve()
    checkpoint_path = args.checkpoint.resolve()
    if len({pairs_path, output_path, checkpoint_path}) != 3:
        raise ValueError("pairs, output, and checkpoint paths must be distinct")
    if output_path.exists() and not checkpoint_path.exists():
        raise FileExistsError("output exists without checkpoint; refusing to overwrite")

    header = {
        "type": "header",
        "adapterVersion": ADAPTER_VERSION,
        "judgeVersion": JUDGE_VERSION,
        "datasetSha256": dataset_sha256,
        "pairCount": len(pairs),
        "model": args.model,
        "endpoint": endpoint,
        "schemaSha256": schema_sha256(),
        "promptSha256": prompt_sha256(),
    }
    completed = load_checkpoint(checkpoint_path, header, {pair["id"] for pair in pairs})
    initialize_checkpoint(checkpoint_path, header)
    if len(completed) == len(pairs) and output_path.exists():
        print(json.dumps({"status": "ALREADY_COMPLETE", "output": str(output_path)}, ensure_ascii=False))
        return

    new_requests = 0
    for pair in pairs:
        if pair["id"] in completed:
            continue
        if args.limit is not None and new_requests >= args.limit:
            break
        envelope, latency_ms = call_ollama(endpoint, args.model, pair, args.timeout_seconds)
        record = result_from_envelope(pair, envelope, latency_ms)
        append_checkpoint(checkpoint_path, record)
        completed[pair["id"]] = record
        new_requests += 1
        print(
            json.dumps(
                {
                    "id": pair["id"],
                    "status": record["status"],
                    "label": record["decision"].get("label") if record["decision"] else None,
                    "latencyMs": record["latencyMs"],
                },
                ensure_ascii=False,
            ),
            flush=True,
        )

    ordered_records = [completed[pair["id"]] for pair in pairs if pair["id"] in completed]
    complete = len(ordered_records) == len(pairs)
    write_aggregate(
        output_path,
        pairs_path,
        dataset_sha256,
        args.model,
        endpoint,
        checkpoint_path,
        ordered_records,
        complete,
    )
    print(
        json.dumps(
            {
                "status": "COMPLETE" if complete else "PARTIAL",
                "completedPairCount": len(ordered_records),
                "newRequests": new_requests,
                "output": str(output_path),
                "checkpoint": str(checkpoint_path),
            },
            ensure_ascii=False,
        )
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--pairs", type=Path, required=True)
    parser.add_argument("--expected-sha256", required=True)
    parser.add_argument("--model", default=DEFAULT_MODEL)
    parser.add_argument("--endpoint", default=DEFAULT_ENDPOINT)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--checkpoint", type=Path)
    parser.add_argument("--timeout-seconds", type=float, default=180.0)
    parser.add_argument("--limit", type=int)
    parser.add_argument("--check", action="store_true")
    return parser.parse_args()


if __name__ == "__main__":
    try:
        run(parse_args())
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1) from exc
