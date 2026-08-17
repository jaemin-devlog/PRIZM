#!/usr/bin/env python3
"""Run the evaluation-only semantic Judge once per frozen P7-B original result."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
import statistics
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

from run_semantic_support_judge import (
    DEFAULT_ENDPOINT,
    DEFAULT_MODEL,
    LABELS,
    SYSTEM_PROMPT as V3_SYSTEM_PROMPT,
    append_checkpoint,
    initialize_checkpoint,
    load_checkpoint,
)


ADAPTER_VERSION = "PRZ-016-RESULT-LEVEL-EVIDENCE-SET-JUDGE-v1"
SEMANTIC_POLICY = V3_SYSTEM_PROMPT.split("\nFor SUPPORTED or REFUTED", 1)[0]
SYSTEM_PROMPT = SEMANTIC_POLICY + """
Evaluate all supplied evidenceWindows together as one evidence set for the hypothesis.
Do not ignore explicit counter-evidence about the same claim merely because another window appears supportive. Consider the claim's action, adoption, currentness, and actor across the supplied set. Topic or keyword similarity is not support.
For SUPPORTED or REFUTED, evidenceWindowIds must contain one or more IDs copied from the supplied evidenceWindows. For INSUFFICIENT, evidenceWindowIds may be empty. Do not output reasoning steps or chain-of-thought. Output only the JSON object required by the schema."""
USER_PROMPT_PREFIX = "Evaluate this evidence set. Output only the schema-conforming JSON object.\n"
OUTPUT_SCHEMA: dict[str, Any] = {
    "type": "object",
    "additionalProperties": False,
    "properties": {
        "label": {"type": "string", "enum": list(LABELS)},
        "evidenceWindowIds": {"type": "array", "items": {"type": "string"}},
    },
    "required": ["label", "evidenceWindowIds"],
}
RESULT_KEYS = {"id", "queryId", "originalRank", "resultIdentity", "hypothesis", "evidenceWindows"}
WINDOW_KEYS = {"id", "sourceCandidateId", "candidateRank", "text"}


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def canonical_json(value: Any) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")


def schema_sha256() -> str:
    return sha256_bytes(canonical_json(OUTPUT_SCHEMA))


def prompt_sha256() -> str:
    return sha256_bytes((SYSTEM_PROMPT + "\n" + USER_PROMPT_PREFIX).encode("utf-8"))


def percentile_95(values: list[float]) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    return ordered[math.ceil(len(ordered) * 0.95) - 1]


def load_dataset(path: Path, expected_sha256: str) -> tuple[list[dict[str, Any]], str]:
    raw = path.read_bytes()
    actual_sha256 = sha256_bytes(raw)
    if actual_sha256 != expected_sha256.lower():
        raise ValueError(f"dataset SHA-256 mismatch: expected {expected_sha256.lower()}, got {actual_sha256}")
    document = json.loads(raw.decode("utf-8"))
    results = document.get("results") if isinstance(document, dict) else None
    if not isinstance(results, list) or len(results) != 86:
        raise ValueError("frozen result-level dataset must contain exactly 86 results")

    result_ids: set[str] = set()
    source_candidate_ids: set[str] = set()
    for result in results:
        if not isinstance(result, dict) or set(result) != RESULT_KEYS:
            raise ValueError(f"invalid result shape: {result!r}")
        if not all(isinstance(result[key], str) and result[key] for key in ("id", "queryId", "hypothesis")):
            raise ValueError(f"result string field is empty: {result!r}")
        if not isinstance(result["originalRank"], int) or result["originalRank"] <= 0:
            raise ValueError(f"invalid original rank: {result!r}")
        if result["id"] != f'{result["queryId"]}#r{result["originalRank"]}':
            raise ValueError(f"result ID does not match metadata: {result['id']}")
        if result["id"] in result_ids:
            raise ValueError(f"duplicate result ID: {result['id']}")
        result_ids.add(result["id"])
        if not isinstance(result["resultIdentity"], dict) or not result["resultIdentity"]:
            raise ValueError(f"missing result identity: {result['id']}")

        windows = result["evidenceWindows"]
        if not isinstance(windows, list) or len(windows) != 5:
            raise ValueError(f"result must contain exactly five windows: {result['id']}")
        for index, window in enumerate(windows, start=1):
            if not isinstance(window, dict) or set(window) != WINDOW_KEYS:
                raise ValueError(f"invalid evidence window shape: {window!r}")
            if window["id"] != f"W{index}" or window["candidateRank"] != index:
                raise ValueError(f"evidence window order changed: {result['id']}")
            expected_source_id = f'{result["queryId"]}#r{result["originalRank"]}#w{index}'
            if window["sourceCandidateId"] != expected_source_id:
                raise ValueError(f"source candidate identity changed: {result['id']} {window['id']}")
            if not isinstance(window["text"], str) or not window["text"]:
                raise ValueError(f"empty evidence window text: {result['id']} {window['id']}")
            if expected_source_id in source_candidate_ids:
                raise ValueError(f"duplicate source candidate ID: {expected_source_id}")
            source_candidate_ids.add(expected_source_id)
    if len(source_candidate_ids) != 430:
        raise ValueError(f"expected 430 source windows, got {len(source_candidate_ids)}")
    return results, actual_sha256


def judge_windows(result: dict[str, Any]) -> list[dict[str, str]]:
    return [{"id": window["id"], "text": window["text"]} for window in result["evidenceWindows"]]


def validate_decision(decision: Any, evidence_windows: list[dict[str, str]]) -> list[str]:
    if not isinstance(decision, dict):
        return ["output is not a JSON object"]
    errors: list[str] = []
    required_keys = {"label", "evidenceWindowIds"}
    if set(decision) != required_keys:
        errors.append(f"output keys must be exactly {sorted(required_keys)}")
    label = decision.get("label")
    window_ids = decision.get("evidenceWindowIds")
    valid_ids = {window["id"] for window in evidence_windows}
    if label not in LABELS:
        errors.append("invalid label")
    if not isinstance(window_ids, list) or not all(isinstance(window_id, str) for window_id in window_ids):
        errors.append("evidenceWindowIds must be an array of strings")
        window_ids = []
    else:
        if len(window_ids) != len(set(window_ids)):
            errors.append("evidenceWindowIds must not contain duplicates")
        for index, window_id in enumerate(window_ids):
            if window_id not in valid_ids:
                errors.append(f"evidenceWindowIds[{index}] does not exist in this result")
    if label in {"SUPPORTED", "REFUTED"} and not window_ids:
        errors.append(f"{label} requires at least one evidence window ID")
    return errors


def resolve_windows(decision: dict[str, Any], evidence_windows: list[dict[str, str]]) -> list[dict[str, str]]:
    by_id = {window["id"]: window for window in evidence_windows}
    return [by_id[window_id] for window_id in decision["evidenceWindowIds"]]


def run_self_test() -> None:
    frozen = [
        {"id": "W1", "sourceCandidateId": "sample#r1#w1", "candidateRank": 1, "text": "구체 조치를 완료했다."},
        {"id": "W2", "sourceCandidateId": "sample#r1#w2", "candidateRank": 2, "text": "검증 결과를 기록했다."},
        {"id": "W3", "sourceCandidateId": "sample#r1#w3", "candidateRank": 3, "text": "추가 정보가 있다."},
        {"id": "W4", "sourceCandidateId": "sample#r1#w4", "candidateRank": 4, "text": "운영 기록이 있다."},
        {"id": "W5", "sourceCandidateId": "sample#r1#w5", "candidateRank": 5, "text": "마지막 문장이다."},
    ]
    result = {"evidenceWindows": frozen}
    windows = judge_windows(result)
    assert windows == [{"id": f"W{i}", "text": frozen[i - 1]["text"]} for i in range(1, 6)]
    assert judge_windows(result) == windows
    supported = {"label": "SUPPORTED", "evidenceWindowIds": ["W1"]}
    refuted = {"label": "REFUTED", "evidenceWindowIds": ["W2"]}
    insufficient = {"label": "INSUFFICIENT", "evidenceWindowIds": []}
    assert validate_decision(supported, windows) == []
    assert validate_decision(refuted, windows) == []
    assert validate_decision(insufficient, windows) == []
    assert validate_decision({"label": "SUPPORTED", "evidenceWindowIds": ["W9"]}, windows)
    assert validate_decision({"label": "SUPPORTED", "evidenceWindowIds": ["W1", "W1"]}, windows)
    assert validate_decision({"label": "SUPPORTED", "evidenceWindowIds": ["W1"], "reason": "extra"}, windows)
    assert resolve_windows(supported, windows) == [windows[0]]
    print(json.dumps({"status": "SELF_TEST_PASS", "checks": 8}, ensure_ascii=False))


def build_user_message(result: dict[str, Any]) -> str:
    data = {"hypothesis": result["hypothesis"], "evidenceWindows": judge_windows(result)}
    return USER_PROMPT_PREFIX + json.dumps(data, ensure_ascii=False, separators=(",", ":"))


def call_ollama(
    endpoint: str, model: str, result: dict[str, Any], timeout_seconds: float
) -> tuple[dict[str, Any], float]:
    payload = {
        "model": model,
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": build_user_message(result)},
        ],
        "stream": False,
        "think": False,
        "format": OUTPUT_SCHEMA,
        "options": {"temperature": 0},
    }
    request = urllib.request.Request(
        endpoint,
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
            body = response.read()
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Ollama HTTP {exc.code}: {detail}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"Ollama request failed: {exc.reason}") from exc
    latency_ms = (time.perf_counter() - started) * 1000
    try:
        envelope = json.loads(body.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise RuntimeError("Ollama returned a non-JSON response envelope") from exc
    if not isinstance(envelope, dict):
        raise RuntimeError("Ollama response envelope is not an object")
    return envelope, latency_ms


def result_from_envelope(result: dict[str, Any], envelope: dict[str, Any], latency_ms: float) -> dict[str, Any]:
    windows = judge_windows(result)
    message = envelope.get("message")
    content = message.get("content") if isinstance(message, dict) else None
    errors: list[str] = []
    parsed: Any = None
    if not isinstance(content, str) or not content:
        errors.append("response message.content is missing or empty")
    else:
        try:
            parsed = json.loads(content)
        except json.JSONDecodeError:
            errors.append("response message.content is not valid JSON")
    if not errors:
        errors.extend(validate_decision(parsed, windows))
    status = "VALID" if not errors else "MODEL_OUTPUT_INVALID"
    grounded = resolve_windows(parsed, windows) if status == "VALID" and isinstance(parsed, dict) else []
    return {
        "id": result["id"],
        "queryId": result["queryId"],
        "originalRank": result["originalRank"],
        "resultIdentity": result["resultIdentity"],
        "status": status,
        "decision": parsed if isinstance(parsed, dict) else None,
        "groundedEvidenceWindows": grounded,
        "validationErrors": errors,
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
        "completedResultCount": len(records),
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
    output: Path,
    dataset: Path,
    dataset_sha256: str,
    model: str,
    endpoint: str,
    checkpoint: Path,
    records: list[dict[str, Any]],
    complete: bool,
) -> None:
    value = {
        "schemaVersion": 1,
        "phase": "PRZ-016-P7-B-RESULT-LEVEL-EVIDENCE-SET-JUDGE-INFERENCE",
        "adapterVersion": ADAPTER_VERSION,
        "semanticPolicyBaseVersion": "PRZ-016-SEMANTIC-SUPPORT-JUDGE-SPIKE-v3",
        "dataset": {"path": str(dataset), "sha256": dataset_sha256},
        "model": model,
        "endpoint": endpoint,
        "requestContract": {
            "stream": False,
            "think": False,
            "temperature": 0,
            "schemaSha256": schema_sha256(),
            "promptSha256": prompt_sha256(),
        },
        "checkpoint": str(checkpoint),
        "results": records,
        "summary": build_summary(records, complete),
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = output.with_name(output.name + ".tmp")
    if temporary.exists():
        raise FileExistsError(f"temporary output already exists: {temporary}")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    os.replace(temporary, output)


def run(args: argparse.Namespace) -> None:
    if args.self_test:
        run_self_test()
        return
    if args.pairs is None or args.expected_sha256 is None:
        raise ValueError("--pairs and --expected-sha256 are required")
    pairs_path = args.pairs.resolve()
    results, dataset_sha256 = load_dataset(pairs_path, args.expected_sha256)
    if args.check:
        print(
            json.dumps(
                {
                    "status": "CHECK_PASS",
                    "datasetSha256": dataset_sha256,
                    "resultCount": len(results),
                    "windowCount": sum(len(result["evidenceWindows"]) for result in results),
                    "queryCountWithResults": len({result["queryId"] for result in results}),
                    "schemaSha256": schema_sha256(),
                    "promptSha256": prompt_sha256(),
                    "groundTruthScoring": "NOT_RUN",
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
        "datasetSha256": dataset_sha256,
        "resultCount": len(results),
        "model": args.model,
        "endpoint": endpoint,
        "schemaSha256": schema_sha256(),
        "promptSha256": prompt_sha256(),
    }
    completed = load_checkpoint(checkpoint_path, header, {result["id"] for result in results})
    initialize_checkpoint(checkpoint_path, header)
    if len(completed) == len(results) and output_path.exists():
        print(json.dumps({"status": "ALREADY_COMPLETE", "output": str(output_path)}, ensure_ascii=False))
        return

    new_requests = 0
    for result in results:
        if result["id"] in completed:
            continue
        if args.limit is not None and new_requests >= args.limit:
            break
        envelope, latency_ms = call_ollama(endpoint, args.model, result, args.timeout_seconds)
        record = result_from_envelope(result, envelope, latency_ms)
        append_checkpoint(checkpoint_path, record)
        completed[result["id"]] = record
        new_requests += 1
        print(
            json.dumps(
                {
                    "id": result["id"],
                    "status": record["status"],
                    "label": record["decision"].get("label") if record["decision"] else None,
                    "latencyMs": record["latencyMs"],
                },
                ensure_ascii=False,
            ),
            flush=True,
        )

    ordered = [completed[result["id"]] for result in results if result["id"] in completed]
    complete = len(ordered) == len(results)
    write_aggregate(
        output_path,
        pairs_path,
        dataset_sha256,
        args.model,
        endpoint,
        checkpoint_path,
        ordered,
        complete,
    )
    print(
        json.dumps(
            {
                "status": "COMPLETE" if complete else "PARTIAL",
                "completedResultCount": len(ordered),
                "newRequests": new_requests,
                "output": str(output_path),
                "checkpoint": str(checkpoint_path),
            },
            ensure_ascii=False,
        )
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--pairs", type=Path)
    parser.add_argument("--expected-sha256")
    parser.add_argument("--model", default=DEFAULT_MODEL)
    parser.add_argument("--endpoint", default=DEFAULT_ENDPOINT)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--checkpoint", type=Path)
    parser.add_argument("--timeout-seconds", type=float, default=180.0)
    parser.add_argument("--limit", type=int)
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--self-test", action="store_true")
    return parser.parse_args()


if __name__ == "__main__":
    try:
        run(parse_args())
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1) from exc
