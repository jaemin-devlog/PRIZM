#!/usr/bin/env python3
"""Run the single frozen PRZ-031 local directness inference exactly once."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
import statistics
import subprocess
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


RUNNER_VERSION = "PRZ-031-SEMANTIC-DIRECTNESS-v1"
INPUT_ARTIFACT = "PRZ031_SEMANTIC_DIRECTNESS_INPUT"
OUTPUT_ARTIFACT = "PRZ031_SEMANTIC_DIRECTNESS_OUTPUT"
RELATION_REASON = {
    "DIRECT_MATCH": "DIRECT_ANSWER",
    "RELATED_CONTEXT": "RELATED_NOT_DIRECT",
    "QUERY_CONFLICT": "QUERY_MEANING_MISMATCH",
    "INSUFFICIENT": "INSUFFICIENT_INFORMATION",
}
FORBIDDEN_INPUT_KEYS = {
    "answerability",
    "categories",
    "category",
    "expectedAnswer",
    "expectedEvidence",
    "gold",
    "goldParent",
    "goldRelation",
    "oracle",
    "relation",
    "supportRelation",
}


def canonical_json(value: Any) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def read_verified_json(path: Path, expected_sha256: str) -> tuple[dict[str, Any], str]:
    raw = path.read_bytes()
    actual = sha256_bytes(raw)
    if actual != expected_sha256.lower():
        raise ValueError(f"SHA-256 mismatch for {path}: expected {expected_sha256.lower()}, got {actual}")
    parsed = json.loads(raw.decode("utf-8"))
    if not isinstance(parsed, dict):
        raise ValueError(f"JSON root must be an object: {path}")
    return parsed, actual


def verify_contract(contract: dict[str, Any]) -> None:
    if contract.get("artifactType") != "PRZ031_SEMANTIC_DIRECTNESS_EXECUTION_CONTRACT":
        raise ValueError("unexpected execution contract")
    frozen = contract.get("frozenHashes")
    if not isinstance(frozen, dict):
        raise ValueError("execution contract lacks frozen hashes")
    actual = {
        "instructionSha256": sha256_bytes(contract["instruction"].encode("utf-8")),
        "outputSchemaSha256": sha256_bytes(canonical_json(contract["outputSchema"])),
        "inferenceConfigSha256": sha256_bytes(canonical_json(contract["inferenceConfig"])),
        "rankingPolicySha256": sha256_bytes(canonical_json(contract["rankingPolicy"])),
    }
    if actual != frozen:
        raise ValueError(f"execution contract component hash mismatch: {actual}")
    if contract.get("relationReasonCode") != RELATION_REASON:
        raise ValueError("relation/reason-code mapping drifted")
    config = contract["inferenceConfig"]
    required_config = {
        "stream": False,
        "think": False,
        "temperature": 0.0,
        "seed": 31031,
        "numPredict": 64,
        "numContext": 4096,
        "topK": 1,
        "topP": 1.0,
        "repeatPenalty": 1.0,
        "topKCandidatesPerQuery": 10,
    }
    for key, expected in required_config.items():
        if config.get(key) != expected:
            raise ValueError(f"inference config drifted: {key}")


def reject_forbidden_input_keys(value: Any, path: str = "$") -> None:
    if isinstance(value, dict):
        for key, child in value.items():
            if key in FORBIDDEN_INPUT_KEYS:
                raise ValueError(f"Gold/diagnostic field is forbidden in model input: {path}.{key}")
            reject_forbidden_input_keys(child, f"{path}.{key}")
    elif isinstance(value, list):
        for index, child in enumerate(value):
            reject_forbidden_input_keys(child, f"{path}[{index}]")


def verify_input(document: dict[str, Any], contract_sha256: str) -> list[dict[str, Any]]:
    if document.get("artifactType") != INPUT_ARTIFACT or document.get("schemaVersion") != 1:
        raise ValueError("unexpected candidate input artifact")
    if document.get("contractSha256") != contract_sha256:
        raise ValueError("candidate input execution-contract identity mismatch")
    reject_forbidden_input_keys(document)
    queries = document.get("queries")
    if not isinstance(queries, list) or len(queries) != 79:
        raise ValueError("semantic input must contain exactly 79 queries")
    if document.get("semanticQueryCount") != 79 or document.get("candidateCount") != 670:
        raise ValueError("semantic query/candidate inventory drifted")
    if document.get("inferencePairCount") != 578 or document.get("typedQueryCount") != 0:
        raise ValueError("Top10 pair or typed-query inventory drifted")

    query_ids: set[str] = set()
    pair_ids: set[str] = set()
    candidate_total = 0
    pair_total = 0
    for query in queries:
        required_query = {
            "suite", "datasetVersion", "queryId", "userBundleId", "split",
            "queryText", "queryTextSha256", "candidates",
        }
        if not isinstance(query, dict) or not required_query.issubset(query):
            raise ValueError("candidate query shape is incomplete")
        query_id = query["queryId"]
        query_text = query["queryText"]
        if not isinstance(query_id, str) or not query_id or query_id in query_ids:
            raise ValueError(f"duplicate/invalid query ID: {query_id}")
        if not isinstance(query_text, str) or not query_text:
            raise ValueError(f"query text is empty: {query_id}")
        if sha256_bytes(query_text.encode("utf-8")) != query["queryTextSha256"]:
            raise ValueError(f"query text hash mismatch: {query_id}")
        query_ids.add(query_id)
        candidates = query["candidates"]
        if not isinstance(candidates, list) or not 1 <= len(candidates) <= 20:
            raise ValueError(f"candidate count is invalid: {query_id}")
        candidate_ids: set[str] = set()
        for index, candidate in enumerate(candidates, start=1):
            if not isinstance(candidate, dict) or candidate.get("rank") != index:
                raise ValueError(f"Dense rank/order mismatch: {query_id}")
            candidate_id = candidate.get("candidateId")
            source = candidate.get("sourceText")
            if not isinstance(candidate_id, str) or not candidate_id or candidate_id in candidate_ids:
                raise ValueError(f"duplicate/invalid candidate ID: {query_id}/{candidate_id}")
            if not isinstance(source, str) or not source:
                raise ValueError(f"candidate sourceText is empty: {query_id}/{candidate_id}")
            if sha256_bytes(source.encode("utf-8")) != candidate.get("sourceTextSha256"):
                raise ValueError(f"candidate sourceText hash mismatch: {query_id}/{candidate_id}")
            candidate_ids.add(candidate_id)
            if index <= 10:
                pair_id = f"{query_id}::{index:02d}::{candidate_id}"
                if pair_id in pair_ids:
                    raise ValueError(f"duplicate inference pair: {pair_id}")
                pair_ids.add(pair_id)
                pair_total += 1
        candidate_total += len(candidates)
    if candidate_total != 670 or pair_total != 578:
        raise ValueError(f"candidate/pair total drifted: {candidate_total}/{pair_total}")
    return queries


def ollama_json(endpoint: str, payload: dict[str, Any], timeout: float) -> tuple[dict[str, Any], float, bytes]:
    request = urllib.request.Request(
        endpoint,
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            body = response.read()
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Ollama HTTP {exc.code}: {detail}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"Ollama request failed: {exc.reason}") from exc
    latency_ms = (time.perf_counter() - started) * 1000.0
    envelope = json.loads(body.decode("utf-8"))
    if not isinstance(envelope, dict):
        raise RuntimeError("Ollama response envelope is not an object")
    return envelope, latency_ms, body


def ollama_get(endpoint: str, timeout: float = 30.0) -> dict[str, Any]:
    with urllib.request.urlopen(endpoint, timeout=timeout) as response:
        value = json.loads(response.read().decode("utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"unexpected Ollama response: {endpoint}")
    return value


def verify_runtime_executable(model: dict[str, Any]) -> dict[str, Any]:
    try:
        completed = subprocess.run(
            [
                "powershell.exe", "-NoProfile", "-Command",
                "Get-Process -Name ollama -ErrorAction Stop | "
                "Where-Object Path | Select-Object -ExpandProperty Path -Unique",
            ],
            capture_output=True,
            text=True,
            timeout=30,
            check=False,
        )
    except (OSError, subprocess.SubprocessError) as exc:
        raise ValueError("cannot inspect the frozen Ollama runtime executable") from exc
    if completed.returncode != 0:
        raise ValueError("cannot inspect the frozen Ollama runtime executable")
    expected_hash = model["runtimeExecutableSha256"]
    expected_bytes = model["runtimeExecutableBytes"]
    observed: list[dict[str, Any]] = []
    for raw_path in completed.stdout.splitlines():
        path = Path(raw_path.strip())
        if not raw_path.strip() or not path.is_file():
            continue
        digest = hashlib.sha256()
        with path.open("rb") as stream:
            for chunk in iter(lambda: stream.read(8 * 1024 * 1024), b""):
                digest.update(chunk)
        identity = {"sha256": digest.hexdigest(), "bytes": path.stat().st_size}
        observed.append(identity)
        if identity == {"sha256": expected_hash, "bytes": expected_bytes}:
            return identity
    raise ValueError(f"Ollama runtime executable identity drifted: {observed}")


def verify_local_model(contract: dict[str, Any]) -> dict[str, Any]:
    model = contract["model"]
    model_name = model["ollamaModel"]
    version = ollama_get("http://localhost:11434/api/version").get("version")
    if version != "0.33.2" or model.get("runtime") != "Ollama 0.33.2":
        raise ValueError("Ollama runtime version drifted")
    runtime_identity = verify_runtime_executable(model)
    tags = ollama_get("http://localhost:11434/api/tags")
    matches = [item for item in tags.get("models", []) if item.get("name") == model_name]
    if (
        len(matches) != 1
        or matches[0].get("digest") != model["ollamaManifestDigest"]
        or matches[0].get("size") != model["ollamaAggregateBytes"]
    ):
        raise ValueError("local Ollama tag/manifest digest does not match the frozen model")
    show, _, _ = ollama_json(
        "http://localhost:11434/api/show", {"model": model_name, "verbose": False}, 30.0
    )
    details = show.get("details", {})
    info = show.get("model_info", {})
    if details.get("family") != "qwen3" or details.get("parameter_size") != "4.02B":
        raise ValueError("local model family/parameter metadata drifted")
    if details.get("quantization_level") != model["quantization"]:
        raise ValueError("local model quantization drifted")
    if info.get("general.parameter_count") != model["parameterCount"]:
        raise ValueError("local model parameter count drifted")
    if "Apache License" not in show.get("license", ""):
        raise ValueError("local model license metadata drifted")

    manifest_path = Path.home() / ".ollama" / "models" / "manifests" / "hf.co" / "Qwen" / "Qwen3-4B-GGUF" / "Q4_K_M"
    manifest_raw = manifest_path.read_bytes()
    if (
        sha256_bytes(manifest_raw) != model["ollamaManifestDigest"]
        or len(manifest_raw) != model["ollamaManifestBytes"]
    ):
        raise ValueError("local Ollama manifest file digest drifted")
    manifest = json.loads(manifest_raw.decode("utf-8"))
    model_layers = [
        layer for layer in manifest.get("layers", [])
        if layer.get("mediaType") == "application/vnd.ollama.image.model"
    ]
    if len(model_layers) != 1:
        raise ValueError("local Ollama manifest must contain one model layer")
    layer = model_layers[0]
    layer_sha = layer["digest"].removeprefix("sha256:")
    blob_path = Path.home() / ".ollama" / "models" / "blobs" / f"sha256-{layer_sha}"
    blob_raw_hash = hashlib.sha256()
    with blob_path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(8 * 1024 * 1024), b""):
            blob_raw_hash.update(chunk)
    if (
        layer_sha != model["localModelBlobSha256"]
        or blob_raw_hash.hexdigest() != model["localModelBlobSha256"]
        or blob_path.stat().st_size != model["upstreamFileBytes"]
        or layer.get("size") != model["upstreamFileBytes"]
    ):
        raise ValueError("local model blob identity/size drifted")
    license_layers = [
        layer for layer in manifest.get("layers", [])
        if layer.get("mediaType") == "application/vnd.ollama.image.license"
    ]
    if len(license_layers) != 1:
        raise ValueError("local Ollama manifest must contain one license layer")
    license_layer = license_layers[0]
    license_sha = license_layer["digest"].removeprefix("sha256:")
    license_path = Path.home() / ".ollama" / "models" / "blobs" / f"sha256-{license_sha}"
    license_raw = license_path.read_bytes()
    if (
        license_sha != model["localLicenseBlobSha256"]
        or sha256_bytes(license_raw) != model["localLicenseBlobSha256"]
        or len(license_raw) != model["localLicenseBlobBytes"]
        or license_layer.get("size") != model["localLicenseBlobBytes"]
    ):
        raise ValueError("local model license blob identity/size drifted")
    return {
        "ollamaTagDigest": matches[0]["digest"],
        "ollamaManifestSha256": sha256_bytes(manifest_raw),
        "localModelBlobSha256": blob_raw_hash.hexdigest(),
        "localModelBlobBytes": blob_path.stat().st_size,
        "localLicenseBlobSha256": sha256_bytes(license_raw),
        "localLicenseBlobBytes": len(license_raw),
        "runtimeVersion": version,
        "runtimeExecutable": runtime_identity,
        "details": details,
        "capabilities": show.get("capabilities", []),
    }


def validate_decision(content: str) -> dict[str, str]:
    value = json.loads(content)
    if not isinstance(value, dict) or set(value) != {"relation", "reasonCode"}:
        raise ValueError("model output keys must be exactly relation and reasonCode")
    relation = value.get("relation")
    reason = value.get("reasonCode")
    if relation not in RELATION_REASON or RELATION_REASON[relation] != reason:
        raise ValueError("model relation/reasonCode is invalid or inconsistent")
    return {"relation": relation, "reasonCode": reason}


def percentile(values: list[float], quantile: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    return ordered[max(0, math.ceil(len(ordered) * quantile) - 1)]


def process_rss_bytes() -> int | None:
    command = [
        "powershell.exe", "-NoProfile", "-Command",
        "($p=Get-Process -Name ollama -ErrorAction SilentlyContinue | Measure-Object WorkingSet64 -Sum).Sum",
    ]
    try:
        output = subprocess.run(command, capture_output=True, text=True, timeout=15, check=False).stdout.strip()
        return int(output) if output else None
    except (OSError, ValueError, subprocess.SubprocessError):
        return None


def gpu_used_mib() -> int | None:
    try:
        output = subprocess.run(
            ["nvidia-smi", "--query-gpu=memory.used", "--format=csv,noheader,nounits"],
            capture_output=True, text=True, timeout=15, check=False,
        ).stdout.strip().splitlines()
        return sum(int(line.strip()) for line in output if line.strip()) if output else None
    except (OSError, ValueError, subprocess.SubprocessError):
        return None


def ps_snapshot() -> dict[str, Any]:
    try:
        value = ollama_get("http://localhost:11434/api/ps")
        return {"models": value.get("models", [])}
    except Exception as exc:  # resource diagnostic must not alter classification
        return {"error": type(exc).__name__}


def build_payload(contract: dict[str, Any], query: str, source: str) -> dict[str, Any]:
    config = contract["inferenceConfig"]
    user_value = json.dumps({"query": query, "sourceText": source}, ensure_ascii=False, separators=(",", ":"))
    return {
        "model": contract["model"]["ollamaModel"],
        "messages": [
            {"role": "system", "content": contract["instruction"]},
            {"role": "user", "content": user_value},
        ],
        "stream": config["stream"],
        "think": config["think"],
        "format": contract["outputSchema"],
        "keep_alive": "30m",
        "options": {
            "temperature": config["temperature"],
            "seed": config["seed"],
            "num_predict": config["numPredict"],
            "num_ctx": config["numContext"],
            "top_k": config["topK"],
            "top_p": config["topP"],
            "repeat_penalty": config["repeatPenalty"],
        },
    }


def run(args: argparse.Namespace) -> None:
    contract, contract_sha = read_verified_json(args.contract, args.contract_sha256)
    verify_contract(contract)
    model_identity = verify_local_model(contract)
    candidate_input, input_file_sha = read_verified_json(args.input, args.input_sha256)
    queries = verify_input(candidate_input, contract_sha)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    marker = args.output.with_suffix(args.output.suffix + ".official-run-started.json")
    if args.output.exists() or marker.exists():
        raise FileExistsError("official output or one-run marker already exists; rerun is forbidden")
    started_at = datetime.now(timezone.utc).isoformat()
    marker_document = {
        "artifactType": "PRZ031_OFFICIAL_RUN_STARTED",
        "runnerVersion": RUNNER_VERSION,
        "startedAtUtc": started_at,
        "contractSha256": contract_sha,
        "candidateInputFileSha256": input_file_sha,
        "candidateInputCanonicalSha256": candidate_input["inputCanonicalSha256"],
        "modelManifestDigest": contract["model"]["ollamaManifestDigest"],
    }
    with marker.open("x", encoding="utf-8", newline="\n") as stream:
        stream.write(json.dumps(marker_document, ensure_ascii=False, sort_keys=True, indent=2) + "\n")
        stream.flush()
        os.fsync(stream.fileno())

    initial_rss = process_rss_bytes()
    initial_gpu = gpu_used_mib()
    peak_rss = initial_rss
    peak_gpu = initial_gpu
    pair_latencies: list[float] = []
    query_latencies: list[float] = []
    rows: list[dict[str, Any]] = []
    official_started = time.perf_counter()

    for query_index, query in enumerate(queries, start=1):
        query_latency = 0.0
        for candidate in query["candidates"][:10]:
            payload = build_payload(contract, query["queryText"], candidate["sourceText"])
            envelope, latency_ms, raw_envelope = ollama_json(
                contract["inferenceConfig"]["endpoint"],
                payload,
                float(contract["inferenceConfig"]["requestTimeoutSeconds"]),
            )
            message = envelope.get("message")
            content = message.get("content") if isinstance(message, dict) else None
            if not isinstance(content, str) or not content:
                raise RuntimeError(f"model returned empty content for {query['queryId']}/{candidate['rank']}")
            decision = validate_decision(content)
            pair_id = f"{query['queryId']}::{candidate['rank']:02d}::{candidate['candidateId']}"
            rows.append({
                "pairId": pair_id,
                "suite": query["suite"],
                "queryId": query["queryId"],
                "userBundleId": query["userBundleId"],
                "denseRank": candidate["rank"],
                "candidateId": candidate["candidateId"],
                "relation": decision["relation"],
                "reasonCode": decision["reasonCode"],
                "rawMessageContentSha256": sha256_bytes(content.encode("utf-8")),
                "responseEnvelopeSha256": sha256_bytes(raw_envelope),
                "latencyMs": latency_ms,
                "ollama": {
                    "model": envelope.get("model"),
                    "done": envelope.get("done"),
                    "doneReason": envelope.get("done_reason"),
                    "totalDurationNs": envelope.get("total_duration"),
                    "loadDurationNs": envelope.get("load_duration"),
                    "promptEvalCount": envelope.get("prompt_eval_count"),
                    "promptEvalDurationNs": envelope.get("prompt_eval_duration"),
                    "evalCount": envelope.get("eval_count"),
                    "evalDurationNs": envelope.get("eval_duration"),
                },
            })
            pair_latencies.append(latency_ms)
            query_latency += latency_ms
        query_latencies.append(query_latency)
        rss = process_rss_bytes()
        gpu = gpu_used_mib()
        if rss is not None:
            peak_rss = rss if peak_rss is None else max(peak_rss, rss)
        if gpu is not None:
            peak_gpu = gpu if peak_gpu is None else max(peak_gpu, gpu)
        print(f"PRZ031_PROGRESS={query_index}/79 pairs={len(rows)}/578", flush=True)

    official_wall_ms = (time.perf_counter() - official_started) * 1000.0
    if len(rows) != 578:
        raise RuntimeError(f"official inference pair count drifted: {len(rows)}")
    final_rss = process_rss_bytes()
    final_gpu = gpu_used_mib()
    output_core = {
        "schemaVersion": 1,
        "artifactType": OUTPUT_ARTIFACT,
        "runnerVersion": RUNNER_VERSION,
        "startedAtUtc": started_at,
        "completedAtUtc": datetime.now(timezone.utc).isoformat(),
        "contractSha256": contract_sha,
        "candidateInputFileSha256": input_file_sha,
        "candidateInputCanonicalSha256": candidate_input["inputCanonicalSha256"],
        "modelIdentity": model_identity,
        "instructionSha256": contract["frozenHashes"]["instructionSha256"],
        "outputSchemaSha256": contract["frozenHashes"]["outputSchemaSha256"],
        "inferenceConfigSha256": contract["frozenHashes"]["inferenceConfigSha256"],
        "rankingPolicySha256": contract["frozenHashes"]["rankingPolicySha256"],
        "queryCount": 79,
        "pairCount": 578,
        "rows": rows,
        "cost": {
            "officialWallMs": official_wall_ms,
            "pairLatencyAverageMs": statistics.fmean(pair_latencies),
            "pairLatencyP50Ms": statistics.median(pair_latencies),
            "pairLatencyP95Ms": percentile(pair_latencies, 0.95),
            "pairLatencyMaxMs": max(pair_latencies),
            "queryTop10LatencyP50Ms": statistics.median(query_latencies),
            "queryTop10LatencyP95Ms": percentile(query_latencies, 0.95),
            "queryTop10LatencyMaxMs": max(query_latencies),
            "processRssBytes": {"before": initial_rss, "peak": peak_rss, "after": final_rss},
            "gpuUsedMiB": {"before": initial_gpu, "peak": peak_gpu, "after": final_gpu},
            "ollamaPsAfter": ps_snapshot(),
        },
    }
    output_document = dict(output_core)
    output_document["outputCanonicalSha256"] = sha256_bytes(canonical_json(output_core))
    with args.output.open("x", encoding="utf-8", newline="\n") as stream:
        json.dump(output_document, stream, ensure_ascii=False, sort_keys=True, indent=2)
        stream.write("\n")
        stream.flush()
        os.fsync(stream.fileno())
    print(f"PRZ031_OUTPUT={args.output.resolve()}")
    print(f"PRZ031_OUTPUT_FILE_SHA256={sha256_bytes(args.output.read_bytes())}")
    print(f"PRZ031_OUTPUT_CANONICAL_SHA256={output_document['outputCanonicalSha256']}")


def self_test() -> None:
    valid = '{"relation":"DIRECT_MATCH","reasonCode":"DIRECT_ANSWER"}'
    assert validate_decision(valid) == {"relation": "DIRECT_MATCH", "reasonCode": "DIRECT_ANSWER"}
    for invalid in (
        '{"relation":"DIRECT_MATCH","reasonCode":"RELATED_NOT_DIRECT"}',
        '{"relation":"OTHER","reasonCode":"DIRECT_ANSWER"}',
        '{"relation":"DIRECT_MATCH","reasonCode":"DIRECT_ANSWER","explanation":"x"}',
    ):
        try:
            validate_decision(invalid)
        except ValueError:
            pass
        else:
            raise AssertionError("invalid decision was accepted")
    try:
        reject_forbidden_input_keys({"queries": [{"gold": "forbidden"}]})
    except ValueError:
        pass
    else:
        raise AssertionError("Gold-bearing input was accepted")
    contract = {
        "model": {"ollamaModel": "frozen-model"},
        "instruction": "frozen instruction",
        "outputSchema": {"type": "object"},
        "inferenceConfig": {
            "stream": False,
            "think": False,
            "temperature": 0.0,
            "seed": 31031,
            "numPredict": 64,
            "numContext": 4096,
            "topK": 1,
            "topP": 1.0,
            "repeatPenalty": 1.0,
        },
    }
    payload = build_payload(contract, "original query", "source excerpt")
    user_input = json.loads(payload["messages"][1]["content"])
    assert user_input == {"query": "original query", "sourceText": "source excerpt"}
    assert set(user_input) == {"query", "sourceText"}
    assert payload["think"] is False and payload["options"]["temperature"] == 0.0
    print(json.dumps({"status": "SELF_TEST_PASS", "checks": 8}, ensure_ascii=False))


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--contract", type=Path)
    parser.add_argument("--contract-sha256")
    parser.add_argument("--input", type=Path)
    parser.add_argument("--input-sha256")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if not args.self_test and any(
        value is None for value in (
            args.contract, args.contract_sha256, args.input, args.input_sha256, args.output
        )
    ):
        parser.error("official run requires contract/input/output paths and both expected SHA-256 values")
    return args


if __name__ == "__main__":
    arguments = parse_args()
    if arguments.self_test:
        self_test()
    else:
        run(arguments)
