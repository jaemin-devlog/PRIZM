#!/usr/bin/env python3
"""Run the frozen PRZ-031 output-protocol-v2 conformance and official inference."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
import statistics
import subprocess
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


RUNNER_VERSION = "PRZ-031-SEMANTIC-DIRECTNESS-PROTOCOL-V2-v1"
PROTOCOL = "SEMANTIC_DIRECTNESS_PROTOCOL_V2"
CONTRACT_ARTIFACT = "PRZ031_SEMANTIC_DIRECTNESS_EXECUTION_CONTRACT_V2"
INPUT_ARTIFACT = "PRZ031_SEMANTIC_DIRECTNESS_PROTOCOL_V2_INPUT"
CONFORMANCE_FIXTURE_ARTIFACT = (
    "PRZ031_SEMANTIC_DIRECTNESS_PROTOCOL_V2_CONFORMANCE_FIXTURES"
)
CONFORMANCE_OUTPUT_ARTIFACT = (
    "PRZ031_SEMANTIC_DIRECTNESS_PROTOCOL_V2_CONFORMANCE_OUTPUT"
)
OFFICIAL_OUTPUT_ARTIFACT = "PRZ031_SEMANTIC_DIRECTNESS_OUTPUT_PROTOCOL_V2"
CONFORMANCE_MARKER_ARTIFACT = "PRZ031_OUTPUT_PROTOCOL_V2_CONFORMANCE_STARTED"
OFFICIAL_MARKER_ARTIFACT = "PRZ031_OUTPUT_PROTOCOL_V2_OFFICIAL_RUN_STARTED"
CONFORMANCE_OUTPUT = Path(
    "local/search-v3-evaluation/prz031/protocol-v2-conformance-output.json"
)
OFFICIAL_OUTPUT = Path(
    "local/search-v3-evaluation/prz031/semantic-directness-output-protocol-v2.json"
)
RELATIONS = (
    "DIRECT_MATCH",
    "RELATED_CONTEXT",
    "QUERY_CONFLICT",
    "INSUFFICIENT",
)
EXPECTED_D1_PARITY = {
    "d1ContractFileSha256": "aa683f4cecb21c90d91d43c7b77bb31cb2f98fe0cd8c7a2c916962eef620d77e",
    "modelCanonicalSha256": "fb9f51d356a3e4223149ccafc28a2450a13219eba70ea69dd314d13fbea2144f",
    "instructionSha256": "3b76fc147b2c8cb3ac0baab4b01a2611aebaadfd77b051b4185be0baa1fc5a55",
    "inferenceConfigSha256": "c63e74cb4e7d79453973d747819eef0a0d9ea0420f0ae95dfb1cfc57938b6c32",
    "rankingPolicySha256": "25e484a0d5f2c450cd63288160c2ab334e71e398bffc6ccf3c94867614602d88",
}
EXPECTED_D1_SOURCE = {
    "artifactType": "PRZ031_SEMANTIC_DIRECTNESS_INPUT",
    "contractFileSha256": EXPECTED_D1_PARITY["d1ContractFileSha256"],
    "candidateFreezeFileSha256": "708f8f647a57a3b42a55a9c11ac76d925646491d5bee1997e052f6690e77107a",
    "inputFileSha256": "b91c6864f809560ee486cd00cad2a21ec7aae02844fa51a902a842e909943671",
    "inputCanonicalSha256": "4242e751831cb59d1a2c9849a1063f6a6044bae87f2a6cbdbce168acedfd6359",
    "guardContractSha256": "237537ffb08179e10f579203b0681cf9c4040791b059cb9152b5ced1e6442d20",
    "candidatePayloadSha256": "5e4863f245f258dcdc96eed755bf17159ae55c5711ec2b967b6169ee000b885f",
    "semanticQueryCount": 79,
    "candidateCount": 670,
    "inferencePairCount": 578,
    "typedQueryCount": 0,
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


class OutputProtocolViolation(ValueError):
    def __init__(self, category: str, message: str, parse_succeeded: bool) -> None:
        super().__init__(message)
        self.category = category
        self.parse_succeeded = parse_succeeded


def canonical_json(value: Any) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def _reject_duplicate_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate JSON key is forbidden: {key}")
        result[key] = value
    return result


def _reject_json_constant(value: str) -> None:
    raise ValueError(f"non-standard JSON constant is forbidden: {value}")


def parse_strict_json(content: str) -> Any:
    return json.loads(
        content,
        object_pairs_hook=_reject_duplicate_keys,
        parse_constant=_reject_json_constant,
    )


def read_verified_json(path: Path, expected_sha256: str) -> tuple[dict[str, Any], str]:
    raw = path.read_bytes()
    actual = sha256_bytes(raw)
    if actual != expected_sha256.lower():
        raise ValueError(f"SHA-256 mismatch for {path}: expected {expected_sha256.lower()}, got {actual}")
    parsed = parse_strict_json(raw.decode("utf-8"))
    if not isinstance(parsed, dict):
        raise ValueError(f"JSON root must be an object: {path}")
    return parsed, actual


def verify_contract(contract: dict[str, Any]) -> None:
    if (
        contract.get("schemaVersion") != 2
        or contract.get("artifactType") != CONTRACT_ARTIFACT
        or contract.get("protocol") != PROTOCOL
    ):
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
    if contract.get("parity") != EXPECTED_D1_PARITY:
        raise ValueError("D1 parity declaration drifted")
    if sha256_bytes(canonical_json(contract.get("model"))) != EXPECTED_D1_PARITY["modelCanonicalSha256"]:
        raise ValueError("model identity differs from frozen D1")
    if actual["instructionSha256"] != EXPECTED_D1_PARITY["instructionSha256"]:
        raise ValueError("instruction differs from frozen D1")
    if actual["inferenceConfigSha256"] != EXPECTED_D1_PARITY["inferenceConfigSha256"]:
        raise ValueError("inference config differs from frozen D1")
    if actual["rankingPolicySha256"] != EXPECTED_D1_PARITY["rankingPolicySha256"]:
        raise ValueError("ranking policy differs from frozen D1")
    expected_schema = {
        "type": "object",
        "additionalProperties": False,
        "properties": {"relation": {"type": "string", "enum": list(RELATIONS)}},
        "required": ["relation"],
    }
    if contract.get("outputSchema") != expected_schema:
        raise ValueError("protocol-v2 output schema must contain only relation")
    if contract.get("sourceD1Input") != EXPECTED_D1_SOURCE:
        raise ValueError("immutable D1 source lineage drifted")
    conformance = contract.get("conformance")
    if not isinstance(conformance, dict) or conformance != {
        "fixtureArtifactType": CONFORMANCE_FIXTURE_ARTIFACT,
        "fixturePath": "specs/PRZ-031-semantic-evidence-directness/protocol-v2-conformance-fixtures.json",
        "fixtureFileSha256": "0f7625b2119f6ad0de9957803fa8d13c2ac4151c11c4b2161b669a4f816c1007",
        "caseCount": 16,
        "gate": "ALL_OUTPUTS_STRICTLY_VALID",
    }:
        raise ValueError("protocol-v2 conformance contract drifted")
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


def verify_input(
    document: dict[str, Any], contract_sha256: str, contract: dict[str, Any]
) -> list[dict[str, Any]]:
    if (
        document.get("artifactType") != INPUT_ARTIFACT
        or document.get("schemaVersion") != 2
        or document.get("protocol") != PROTOCOL
    ):
        raise ValueError("unexpected candidate input artifact")
    if document.get("contractSha256") != contract_sha256:
        raise ValueError("candidate input execution-contract identity mismatch")
    source = contract["sourceD1Input"]
    lineage_fields = {
        "candidateFreezeFileSha256": source["candidateFreezeFileSha256"],
        "sourceD1InputFileSha256": source["inputFileSha256"],
        "sourceD1InputCanonicalSha256": source["inputCanonicalSha256"],
        "sourceD1GuardContractSha256": source["guardContractSha256"],
        "candidatePayloadSha256": source["candidatePayloadSha256"],
    }
    for field, expected in lineage_fields.items():
        if document.get(field) != expected:
            raise ValueError(f"protocol-v2 input lineage drifted: {field}")
    reject_forbidden_input_keys(document)
    queries = document.get("queries")
    if not isinstance(queries, list) or len(queries) != 79:
        raise ValueError("semantic input must contain exactly 79 queries")
    if document.get("semanticQueryCount") != 79 or document.get("candidateCount") != 670:
        raise ValueError("semantic query/candidate inventory drifted")
    if document.get("inferencePairCount") != 578 or document.get("typedQueryCount") != 0:
        raise ValueError("Top10 pair or typed-query inventory drifted")
    if sha256_bytes(canonical_json(queries)) != source["candidatePayloadSha256"]:
        raise ValueError("protocol-v2 candidate payload SHA-256 drifted")

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


def verify_conformance_fixtures(
    document: dict[str, Any], fixture_file_sha256: str, contract: dict[str, Any]
) -> list[dict[str, Any]]:
    expected = contract["conformance"]
    if (
        document.get("schemaVersion") != 1
        or document.get("artifactType") != CONFORMANCE_FIXTURE_ARTIFACT
        or document.get("protocol") != PROTOCOL
        or fixture_file_sha256 != expected["fixtureFileSha256"]
        or document.get("caseCount") != expected["caseCount"]
    ):
        raise ValueError("protocol-v2 conformance fixture identity drifted")
    if set(document) != {"schemaVersion", "artifactType", "protocol", "caseCount", "cases"}:
        raise ValueError("protocol-v2 conformance fixture contains an unapproved field")
    cases = document.get("cases")
    if not isinstance(cases, list) or len(cases) != 16:
        raise ValueError("protocol-v2 requires exactly 16 generic conformance cases")
    identifiers: set[str] = set()
    allowed_languages = {"KO", "EN", "KO_EN_MIXED"}
    for case in cases:
        if not isinstance(case, dict) or set(case) != {
            "caseId", "language", "queryText", "sourceText"
        }:
            raise ValueError("conformance case shape drifted")
        case_id = case["caseId"]
        if not isinstance(case_id, str) or not case_id or case_id in identifiers:
            raise ValueError("duplicate or invalid conformance case ID")
        if case["language"] not in allowed_languages:
            raise ValueError(f"invalid conformance language: {case_id}")
        if not isinstance(case["queryText"], str) or not case["queryText"].strip():
            raise ValueError(f"empty conformance query: {case_id}")
        if not isinstance(case["sourceText"], str) or not case["sourceText"].strip():
            raise ValueError(f"empty conformance source: {case_id}")
        identifiers.add(case_id)
    return cases


def verify_canonical_output(document: dict[str, Any]) -> None:
    declared = document.get("outputCanonicalSha256")
    if not isinstance(declared, str) or len(declared) != 64:
        raise ValueError("output canonical SHA-256 is missing")
    core = dict(document)
    core.pop("outputCanonicalSha256", None)
    if sha256_bytes(canonical_json(core)) != declared:
        raise ValueError("output canonical SHA-256 drifted")


def verify_conformance_pass(
    document: dict[str, Any],
    contract: dict[str, Any],
    contract_sha256: str,
    output_file_sha256: str,
    fixtures: list[dict[str, Any]],
) -> None:
    if (
        document.get("schemaVersion") != 2
        or document.get("artifactType") != CONFORMANCE_OUTPUT_ARTIFACT
        or document.get("protocol") != PROTOCOL
        or document.get("status") != "PASS"
        or document.get("contractSha256") != contract_sha256
        or document.get("fixtureFileSha256")
        != contract["conformance"]["fixtureFileSha256"]
        or document.get("caseCount") != 16
        or document.get("parseSuccessCount") != 16
        or document.get("schemaSuccessCount") != 16
        or document.get("enumViolationCount") != 0
        or document.get("extraFieldCount") != 0
        or document.get("malformedCount") != 0
        or document.get("attemptedCaseCount") != 16
        or document.get("failure") is not None
    ):
        raise ValueError("protocol-v2 conformance PASS identity drifted")
    rows = document.get("rows")
    if not isinstance(rows, list) or len(rows) != 16:
        raise ValueError("protocol-v2 conformance PASS row count drifted")
    case_ids: set[str] = set()
    for row, fixture in zip(rows, fixtures, strict=True):
        if not isinstance(row, dict) or set(row) != {
            "caseId",
            "language",
            "relation",
            "queryTextSha256",
            "sourceTextSha256",
            "rawMessageContentSha256",
            "responseEnvelopeSha256",
            "latencyMs",
            "ollama",
        }:
            raise ValueError("protocol-v2 conformance row shape drifted")
        if (
            row["caseId"] in case_ids
            or row["caseId"] != fixture["caseId"]
            or row["language"] != fixture["language"]
            or row["queryTextSha256"]
            != sha256_bytes(fixture["queryText"].encode("utf-8"))
            or row["sourceTextSha256"]
            != sha256_bytes(fixture["sourceText"].encode("utf-8"))
            or row["relation"] not in RELATIONS
        ):
            raise ValueError("protocol-v2 conformance row identity/relation drifted")
        case_ids.add(row["caseId"])
    marker = CONFORMANCE_OUTPUT.with_suffix(
        CONFORMANCE_OUTPUT.suffix + ".official-run-started.json"
    )
    marker_raw = marker.read_bytes()
    if sha256_bytes(marker_raw) != document.get("runMarkerFileSha256"):
        raise ValueError("protocol-v2 conformance marker hash drifted")
    marker_document = parse_strict_json(marker_raw.decode("utf-8"))
    if (
        not isinstance(marker_document, dict)
        or marker_document.get("artifactType") != CONFORMANCE_MARKER_ARTIFACT
        or marker_document.get("protocol") != PROTOCOL
        or marker_document.get("contractSha256") != contract_sha256
        or marker_document.get("fixtureFileSha256")
        != contract["conformance"]["fixtureFileSha256"]
        or marker_document.get("startedAtUtc") != document.get("startedAtUtc")
    ):
        raise ValueError("protocol-v2 conformance marker identity drifted")
    verify_canonical_output(document)
    if not output_file_sha256 or len(output_file_sha256) != 64:
        raise ValueError("protocol-v2 conformance output file SHA-256 is invalid")


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
    try:
        value = parse_strict_json(content)
    except (json.JSONDecodeError, ValueError) as exc:
        raise OutputProtocolViolation(
            "MALFORMED", "model output is not strict duplicate-free JSON", False
        ) from exc
    if not isinstance(value, dict):
        raise OutputProtocolViolation(
            "SCHEMA", "model output must be one JSON object", True
        )
    extra = set(value) - {"relation"}
    if extra:
        raise OutputProtocolViolation(
            "EXTRA_FIELD", f"model output contains extra fields: {sorted(extra)}", True
        )
    if set(value) != {"relation"}:
        raise OutputProtocolViolation(
            "SCHEMA", "model output keys must be exactly relation", True
        )
    relation = value.get("relation")
    if not isinstance(relation, str) or relation not in RELATIONS:
        raise OutputProtocolViolation("ENUM", "model relation enum is invalid", True)
    return {"relation": relation}


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


def marker_path(output: Path) -> Path:
    return output.with_suffix(output.suffix + ".official-run-started.json")


def require_output_path(actual: Path, expected: Path) -> None:
    if actual.resolve() != expected.resolve():
        raise ValueError(f"output path must be the frozen protocol-v2 path: {expected}")


def write_json_create_new(path: Path, document: dict[str, Any]) -> str:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("x", encoding="utf-8", newline="\n") as stream:
        json.dump(document, stream, ensure_ascii=False, sort_keys=True, indent=2)
        stream.write("\n")
        stream.flush()
        os.fsync(stream.fileno())
    return sha256_bytes(path.read_bytes())


def relation_inference(
    contract: dict[str, Any], query: str, source: str
) -> tuple[str, float, str, str, dict[str, Any]]:
    payload = build_payload(contract, query, source)
    envelope, latency_ms, raw_envelope = ollama_json(
        contract["inferenceConfig"]["endpoint"],
        payload,
        float(contract["inferenceConfig"]["requestTimeoutSeconds"]),
    )
    message = envelope.get("message")
    content = message.get("content") if isinstance(message, dict) else None
    if (
        not isinstance(content, str)
        or not content
        or envelope.get("done") is not True
        or envelope.get("done_reason") != "stop"
    ):
        raise RuntimeError("model returned an incomplete protocol-v2 response")
    decision = validate_decision(content)
    diagnostics = {
        "model": envelope.get("model"),
        "done": envelope.get("done"),
        "doneReason": envelope.get("done_reason"),
        "totalDurationNs": envelope.get("total_duration"),
        "loadDurationNs": envelope.get("load_duration"),
        "promptEvalCount": envelope.get("prompt_eval_count"),
        "promptEvalDurationNs": envelope.get("prompt_eval_duration"),
        "evalCount": envelope.get("eval_count"),
        "evalDurationNs": envelope.get("eval_duration"),
    }
    return (
        decision["relation"],
        latency_ms,
        sha256_bytes(content.encode("utf-8")),
        sha256_bytes(raw_envelope),
        diagnostics,
    )


def output_with_canonical(core: dict[str, Any]) -> dict[str, Any]:
    result = dict(core)
    result["outputCanonicalSha256"] = sha256_bytes(canonical_json(core))
    return result


def run_conformance(args: argparse.Namespace) -> None:
    require_output_path(args.output, CONFORMANCE_OUTPUT)
    contract, contract_sha = read_verified_json(args.contract, args.contract_sha256)
    verify_contract(contract)
    fixture, fixture_sha = read_verified_json(args.fixtures, args.fixtures_sha256)
    cases = verify_conformance_fixtures(fixture, fixture_sha, contract)
    model_identity = verify_local_model(contract)

    marker = marker_path(args.output)
    if args.output.exists() or marker.exists():
        raise FileExistsError("protocol-v2 conformance output/marker already exists")
    started_at = datetime.now(timezone.utc).isoformat()
    marker_document = {
        "artifactType": CONFORMANCE_MARKER_ARTIFACT,
        "protocol": PROTOCOL,
        "runnerVersion": RUNNER_VERSION,
        "startedAtUtc": started_at,
        "contractSha256": contract_sha,
        "fixtureFileSha256": fixture_sha,
        "modelManifestDigest": contract["model"]["ollamaManifestDigest"],
    }
    marker_sha = write_json_create_new(marker, marker_document)

    rows: list[dict[str, Any]] = []
    latencies: list[float] = []
    parse_success_count = 0
    schema_success_count = 0
    enum_violation_count = 0
    extra_field_count = 0
    malformed_count = 0
    failure: dict[str, Any] | None = None
    started = time.perf_counter()
    for case in cases:
        try:
            relation, latency, content_sha, envelope_sha, diagnostics = relation_inference(
                contract, case["queryText"], case["sourceText"]
            )
        except OutputProtocolViolation as exc:
            if exc.parse_succeeded:
                parse_success_count += 1
            if exc.category == "ENUM":
                enum_violation_count += 1
            elif exc.category == "EXTRA_FIELD":
                extra_field_count += 1
            elif exc.category == "MALFORMED":
                malformed_count += 1
            failure = {
                "caseId": case["caseId"],
                "category": exc.category,
                "errorType": type(exc).__name__,
            }
            break
        except Exception as exc:
            failure = {
                "caseId": case["caseId"],
                "category": "RUNTIME",
                "errorType": type(exc).__name__,
            }
            break
        parse_success_count += 1
        schema_success_count += 1
        rows.append({
            "caseId": case["caseId"],
            "language": case["language"],
            "relation": relation,
            "queryTextSha256": sha256_bytes(case["queryText"].encode("utf-8")),
            "sourceTextSha256": sha256_bytes(case["sourceText"].encode("utf-8")),
            "rawMessageContentSha256": content_sha,
            "responseEnvelopeSha256": envelope_sha,
            "latencyMs": latency,
            "ollama": diagnostics,
        })
        latencies.append(latency)
    status = "PASS" if len(rows) == 16 and failure is None else "NO_GO"
    output = output_with_canonical({
        "schemaVersion": 2,
        "artifactType": CONFORMANCE_OUTPUT_ARTIFACT,
        "protocol": PROTOCOL,
        "status": status,
        "runnerVersion": RUNNER_VERSION,
        "startedAtUtc": started_at,
        "completedAtUtc": datetime.now(timezone.utc).isoformat(),
        "contractSha256": contract_sha,
        "fixtureFileSha256": fixture_sha,
        "runMarkerFileSha256": marker_sha,
        "modelIdentity": model_identity,
        "caseCount": 16,
        "attemptedCaseCount": len(rows) + (1 if failure is not None else 0),
        "parseSuccessCount": parse_success_count,
        "schemaSuccessCount": schema_success_count,
        "enumViolationCount": enum_violation_count,
        "extraFieldCount": extra_field_count,
        "malformedCount": malformed_count,
        "failure": failure,
        "rows": rows,
        "cost": {
            "wallMs": (time.perf_counter() - started) * 1000.0,
            "pairLatencyAverageMs": statistics.fmean(latencies) if latencies else None,
            "pairLatencyP50Ms": statistics.median(latencies) if latencies else None,
            "pairLatencyP95Ms": percentile(latencies, 0.95) if latencies else None,
            "pairLatencyMaxMs": max(latencies) if latencies else None,
            "processRssBytesAfter": process_rss_bytes(),
            "gpuUsedMiBAfter": gpu_used_mib(),
            "ollamaPsAfter": ps_snapshot(),
        },
    })
    output_sha = write_json_create_new(args.output, output)
    print(f"PRZ031_PROTOCOL_V2_CONFORMANCE={args.output.resolve()}")
    print(f"PRZ031_PROTOCOL_V2_CONFORMANCE_FILE_SHA256={output_sha}")
    print(f"PRZ031_PROTOCOL_V2_CONFORMANCE_CANONICAL_SHA256={output['outputCanonicalSha256']}")
    if status != "PASS":
        raise RuntimeError("protocol-v2 conformance failed closed; NO_GO report preserved")


def validate_repository_freeze(
    candidate_input: dict[str, Any], current_head: str, porcelain_status: str
) -> None:
    code_freeze = candidate_input.get("codeFreezeCommit")
    if (
        not isinstance(code_freeze, str)
        or len(code_freeze) != 40
        or any(character not in "0123456789abcdef" for character in code_freeze)
        or code_freeze != current_head
    ):
        raise ValueError("protocol-v2 input code-freeze commit differs from current HEAD")
    if porcelain_status:
        raise ValueError("official protocol-v2 run requires a clean tracked/untracked worktree")


def verify_repository_freeze(candidate_input: dict[str, Any]) -> None:
    head = subprocess.run(
        ["git", "rev-parse", "HEAD"],
        capture_output=True,
        text=True,
        timeout=30,
        check=True,
    ).stdout.strip()
    status = subprocess.run(
        ["git", "status", "--porcelain", "--untracked-files=all"],
        capture_output=True,
        text=True,
        timeout=30,
        check=True,
    ).stdout.strip()
    validate_repository_freeze(candidate_input, head, status)


def run_official(args: argparse.Namespace) -> None:
    require_output_path(args.output, OFFICIAL_OUTPUT)
    contract, contract_sha = read_verified_json(args.contract, args.contract_sha256)
    verify_contract(contract)
    conformance, conformance_sha = read_verified_json(
        args.conformance_output, args.conformance_output_sha256
    )
    fixture_path = Path(contract["conformance"]["fixturePath"])
    fixture, fixture_sha = read_verified_json(
        fixture_path, contract["conformance"]["fixtureFileSha256"]
    )
    fixtures = verify_conformance_fixtures(fixture, fixture_sha, contract)
    verify_conformance_pass(
        conformance, contract, contract_sha, conformance_sha, fixtures
    )
    candidate_input, input_file_sha = read_verified_json(args.input, args.input_sha256)
    queries = verify_input(candidate_input, contract_sha, contract)
    verify_repository_freeze(candidate_input)
    model_identity = verify_local_model(contract)

    marker = marker_path(args.output)
    if args.output.exists() or marker.exists():
        raise FileExistsError("protocol-v2 official output/marker already exists")
    started_at = datetime.now(timezone.utc).isoformat()
    marker_document = {
        "artifactType": OFFICIAL_MARKER_ARTIFACT,
        "protocol": PROTOCOL,
        "runnerVersion": RUNNER_VERSION,
        "startedAtUtc": started_at,
        "contractSha256": contract_sha,
        "conformanceOutputFileSha256": conformance_sha,
        "candidateInputFileSha256": input_file_sha,
        "candidatePayloadSha256": candidate_input["candidatePayloadSha256"],
        "candidateFreezeFileSha256": candidate_input["candidateFreezeFileSha256"],
        "modelManifestDigest": contract["model"]["ollamaManifestDigest"],
    }
    marker_sha = write_json_create_new(marker, marker_document)

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
            relation, latency, content_sha, envelope_sha, diagnostics = relation_inference(
                contract, query["queryText"], candidate["sourceText"]
            )
            rows.append({
                "pairId": f"{query['queryId']}::{candidate['rank']:02d}::{candidate['candidateId']}",
                "suite": query["suite"],
                "queryId": query["queryId"],
                "userBundleId": query["userBundleId"],
                "denseRank": candidate["rank"],
                "candidateId": candidate["candidateId"],
                "relation": relation,
                "rawMessageContentSha256": content_sha,
                "responseEnvelopeSha256": envelope_sha,
                "latencyMs": latency,
                "ollama": diagnostics,
            })
            pair_latencies.append(latency)
            query_latency += latency
        query_latencies.append(query_latency)
        rss = process_rss_bytes()
        gpu = gpu_used_mib()
        if rss is not None:
            peak_rss = rss if peak_rss is None else max(peak_rss, rss)
        if gpu is not None:
            peak_gpu = gpu if peak_gpu is None else max(peak_gpu, gpu)
        print(f"PRZ031_PROTOCOL_V2_PROGRESS={query_index}/79 pairs={len(rows)}/578", flush=True)

    if len(rows) != 578:
        raise RuntimeError(f"protocol-v2 official pair count drifted: {len(rows)}")
    output = output_with_canonical({
        "schemaVersion": 2,
        "artifactType": OFFICIAL_OUTPUT_ARTIFACT,
        "protocol": PROTOCOL,
        "runnerVersion": RUNNER_VERSION,
        "startedAtUtc": started_at,
        "completedAtUtc": datetime.now(timezone.utc).isoformat(),
        "contractSha256": contract_sha,
        "conformanceOutputFileSha256": conformance_sha,
        "runMarkerFileSha256": marker_sha,
        "candidateInputFileSha256": input_file_sha,
        "candidatePayloadSha256": candidate_input["candidatePayloadSha256"],
        "candidateFreezeFileSha256": candidate_input["candidateFreezeFileSha256"],
        "modelIdentity": model_identity,
        "instructionSha256": contract["frozenHashes"]["instructionSha256"],
        "outputSchemaSha256": contract["frozenHashes"]["outputSchemaSha256"],
        "inferenceConfigSha256": contract["frozenHashes"]["inferenceConfigSha256"],
        "rankingPolicySha256": contract["frozenHashes"]["rankingPolicySha256"],
        "queryCount": 79,
        "pairCount": 578,
        "rows": rows,
        "cost": {
            "officialWallMs": (time.perf_counter() - official_started) * 1000.0,
            "pairLatencyAverageMs": statistics.fmean(pair_latencies),
            "pairLatencyP50Ms": statistics.median(pair_latencies),
            "pairLatencyP95Ms": percentile(pair_latencies, 0.95),
            "pairLatencyMaxMs": max(pair_latencies),
            "queryTop10LatencyP50Ms": statistics.median(query_latencies),
            "queryTop10LatencyP95Ms": percentile(query_latencies, 0.95),
            "queryTop10LatencyMaxMs": max(query_latencies),
            "processRssBytes": {
                "before": initial_rss,
                "peak": peak_rss,
                "after": process_rss_bytes(),
            },
            "gpuUsedMiB": {
                "before": initial_gpu,
                "peak": peak_gpu,
                "after": gpu_used_mib(),
            },
            "ollamaPsAfter": ps_snapshot(),
        },
    })
    output_sha = write_json_create_new(args.output, output)
    print(f"PRZ031_PROTOCOL_V2_OUTPUT={args.output.resolve()}")
    print(f"PRZ031_PROTOCOL_V2_OUTPUT_FILE_SHA256={output_sha}")
    print(f"PRZ031_PROTOCOL_V2_OUTPUT_CANONICAL_SHA256={output['outputCanonicalSha256']}")


def self_test() -> None:
    for relation in RELATIONS:
        value = json.dumps({"relation": relation}, separators=(",", ":"))
        assert validate_decision(value) == {"relation": relation}
    invalid_values = (
        '{"relation":"DIRECT_MATCH","explanation":"x"}',
        '{"relation":"OTHER"}',
        '{}',
        '{"reasonCode":"DIRECT_ANSWER"}',
        '{"relation":1}',
        '[{"relation":"DIRECT_MATCH"}]',
        '"DIRECT_MATCH"',
        'null',
        '{"relation":"DIRECT_MATCH"',
        '{"relation":"DIRECT_MATCH"} trailing',
        '{"relation":"DIRECT_MATCH","relation":"INSUFFICIENT"}',
        '{"relation":NaN}',
    )
    for invalid in invalid_values:
        try:
            validate_decision(invalid)
        except ValueError:
            pass
        else:
            raise AssertionError("invalid decision was accepted")
    category_cases = {
        '{"relation":"DIRECT_MATCH","extra":true}': "EXTRA_FIELD",
        '{"relation":"OTHER"}': "ENUM",
        '{"relation":"DIRECT_MATCH"': "MALFORMED",
        '{}': "SCHEMA",
    }
    for invalid, expected_category in category_cases.items():
        try:
            validate_decision(invalid)
        except OutputProtocolViolation as exc:
            assert exc.category == expected_category
        else:
            raise AssertionError("protocol violation category was not detected")
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
    contract_path = Path(
        "specs/PRZ-031-semantic-evidence-directness/execution-contract-v2.json"
    )
    fixture_path = Path(
        "specs/PRZ-031-semantic-evidence-directness/protocol-v2-conformance-fixtures.json"
    )
    contract = parse_strict_json(contract_path.read_text(encoding="utf-8"))
    fixture = parse_strict_json(fixture_path.read_text(encoding="utf-8"))
    assert isinstance(contract, dict) and isinstance(fixture, dict)
    verify_contract(contract)
    cases = verify_conformance_fixtures(
        fixture, sha256_bytes(fixture_path.read_bytes()), contract
    )
    assert len(cases) == 16
    validate_repository_freeze({"codeFreezeCommit": "a" * 40}, "a" * 40, "")
    for invalid_input, head, status in (
        ({"codeFreezeCommit": "b" * 40}, "a" * 40, ""),
        ({"codeFreezeCommit": "a" * 40}, "a" * 40, "?? untracked.txt"),
        ({"codeFreezeCommit": "invalid"}, "a" * 40, ""),
    ):
        try:
            validate_repository_freeze(invalid_input, head, status)
        except ValueError:
            pass
        else:
            raise AssertionError("invalid repository freeze was accepted")
    print(json.dumps({
        "status": "SELF_TEST_PASS",
        "protocol": PROTOCOL,
        "validEnums": len(RELATIONS),
        "rejectedInvalidForms": len(invalid_values),
        "violationCategories": len(category_cases),
        "conformanceCases": len(cases),
        "repositoryFreezeChecks": 4,
    }, ensure_ascii=False))


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--mode", choices=("conformance", "official"))
    parser.add_argument(
        "--contract",
        type=Path,
        default=Path("specs/PRZ-031-semantic-evidence-directness/execution-contract-v2.json"),
    )
    parser.add_argument("--contract-sha256")
    parser.add_argument("--fixtures", type=Path)
    parser.add_argument("--fixtures-sha256")
    parser.add_argument("--input", type=Path)
    parser.add_argument("--input-sha256")
    parser.add_argument("--conformance-output", type=Path)
    parser.add_argument("--conformance-output-sha256")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        return args
    if args.mode is None or args.contract_sha256 is None:
        parser.error("a protocol-v2 mode and frozen contract SHA-256 are required")
    if args.mode == "conformance":
        if args.fixtures is None or args.fixtures_sha256 is None:
            parser.error("conformance mode requires fixture path and frozen SHA-256")
        if args.output is None:
            args.output = CONFORMANCE_OUTPUT
    else:
        required = (
            args.input,
            args.input_sha256,
            args.conformance_output,
            args.conformance_output_sha256,
        )
        if any(value is None for value in required):
            parser.error("official mode requires frozen input and conformance output paths/hashes")
        if args.output is None:
            args.output = OFFICIAL_OUTPUT
    return args


if __name__ == "__main__":
    arguments = parse_args()
    if arguments.self_test:
        self_test()
    elif arguments.mode == "conformance":
        run_conformance(arguments)
    else:
        run_official(arguments)
