#!/usr/bin/env python3
"""Score only frozen PRZ-027 B3 Dense Top20 query/source pairs."""

from __future__ import annotations

import argparse
import hashlib
import importlib.util
import importlib.metadata
import json
import math
import os
import platform
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import psutil


SCHEMA_VERSION = 1
PROFILE = "PRZ027_B3_TOP20_GTE_MULTILINGUAL_RERANKER_BASE"
MODEL = "Alibaba-NLP/gte-multilingual-reranker-base"
MODEL_REVISION = "8215cf04918ba6f7b6a62bb44238ce2953d8831c"
CODE_REPOSITORY = "Alibaba-NLP/new-impl"
CODE_REVISION = "40ced75c3017eb27626c9d4ea981bde21a2662f4"
LICENSE = "apache-2.0"
TRANSFORMERS_VERSION = "4.39.1"
PYTHON_VERSION = "3.12.13"
TORCH_VERSION = "2.9.0+cpu"
PSUTIL_VERSION = "5.9.8"
TOP_K = 20
MAX_LENGTH = 512
BATCH_SIZE = 8
CPU_THREADS = 8
MODEL_PARAMETERS = 305_959_681
MODEL_WEIGHT_BYTES = 611_934_706
MODEL_WEIGHT_SHA256 = "10ebaa49322dd7e01a13a91c49810939e3f91f231aceaa47fdf0cab3083954f6"
CONFIG_SHA256 = "995730781d157e147c13ccdfe0eb20a0875c486b6c4de8c97f0bbd845549dbc0"
REMOTE_CONFIGURATION_SHA256 = "3411088045ffb8a9a0aa9936eae275896b39983a2ee5b08f091b44e6289e4fe4"
REMOTE_MODELING_SHA256 = "374670b416fcc82f081c9cd28b5fd61c2bd91bbe18eb4798fcc48a81f9c250a0"
FORBIDDEN_KEY_PARTS = (
    "gold",
    "expected",
    "answerability",
    "category",
    "covered",
    "supportrelation",
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--cache-dir", required=True, type=Path)
    parser.add_argument("--module-cache", required=True, type=Path)
    return parser.parse_args()


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_text(value: str) -> str:
    return sha256_bytes(value.encode("utf-8"))


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def read_json(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"Expected JSON object: {path}")
    return value


def walk_keys(value: Any) -> list[str]:
    keys: list[str] = []
    if isinstance(value, dict):
        for key, child in value.items():
            keys.append(str(key))
            keys.extend(walk_keys(child))
    elif isinstance(value, list):
        for child in value:
            keys.extend(walk_keys(child))
    return keys


def validate_input(value: dict[str, Any]) -> None:
    required = {
        "schemaVersion": SCHEMA_VERSION,
        "profile": PROFILE,
        "topK": TOP_K,
        "maxLength": MAX_LENGTH,
        "batchSize": BATCH_SIZE,
        "cpuThreads": CPU_THREADS,
        "model": MODEL,
        "modelRevision": MODEL_REVISION,
        "codeRepository": CODE_REPOSITORY,
        "codeRevision": CODE_REVISION,
        "license": LICENSE,
        "transformersVersion": TRANSFORMERS_VERSION,
        "pairPolicy": "ORIGINAL_QUERY_AND_B3_SOURCE_TEXT_NO_INSTRUCTION",
        "goldPolicy": "GOLD_NOT_PRESENT",
    }
    for key, expected in required.items():
        if value.get(key) != expected:
            raise ValueError(f"Frozen input metadata mismatch: {key}")
    forbidden = [
        key for key in walk_keys(value)
        if any(part in key.lower().replace("_", "") for part in FORBIDDEN_KEY_PARTS)
        and key != "goldPolicy"
    ]
    if forbidden:
        raise ValueError(f"Inference input contains forbidden Gold/evaluation fields: {forbidden}")
    datasets = value.get("datasets")
    if not isinstance(datasets, list) or not datasets:
        raise ValueError("Prepared input requires datasets")
    seen_questions: set[str] = set()
    seen_pairs: set[str] = set()
    digest_lines: list[str] = []
    for dataset in datasets:
        dataset_version = dataset.get("datasetVersion")
        questions = dataset.get("questions")
        if not isinstance(dataset_version, str) or not dataset_version:
            raise ValueError("Dataset version is required")
        if not isinstance(questions, list) or not questions:
            raise ValueError("Dataset questions are required")
        for question in questions:
            question_id = question.get("questionId")
            split = question.get("split")
            query = question.get("query")
            query_sha = question.get("querySha256")
            key = f"{dataset_version}:{question_id}"
            if key in seen_questions or not isinstance(query, str) or not query:
                raise ValueError("Duplicate or invalid prepared question")
            seen_questions.add(key)
            if query_sha != sha256_text(query):
                raise ValueError(f"Query SHA-256 mismatch: {question_id}")
            pairs = question.get("pairs")
            expected_count = min(TOP_K, int(question.get("fullCandidateCount", 0)))
            if (
                not isinstance(pairs, list)
                or len(pairs) != expected_count
                or question.get("pairCount") != expected_count
            ):
                raise ValueError(f"Dense Top20 cutoff mismatch: {question_id}")
            for index, pair in enumerate(pairs, start=1):
                pair_id = pair.get("pairId")
                if pair_id in seen_pairs or pair.get("denseRank") != index:
                    raise ValueError(f"Duplicate pair or dense rank gap: {question_id}")
                seen_pairs.add(pair_id)
                if pair.get("query") != query or pair.get("querySha256") != query_sha:
                    raise ValueError(f"Pair query identity mismatch: {question_id}")
                if pair.get("sourceSha256") != sha256_text(pair.get("sourceText", "")):
                    raise ValueError(f"Pair source identity mismatch: {question_id}")
                digest_lines.append(":".join([
                    dataset_version,
                    split,
                    question_id,
                    pair_id,
                    str(index),
                    pair["candidateId"],
                    query_sha,
                    pair["sourceSha256"],
                    pair["provenanceSha256"],
                ]))
    if value.get("inputDigest") != sha256_text("\n".join(digest_lines)):
        raise ValueError("Prepared input digest mismatch")


def deterministic_ranking(pairs: list[dict[str, Any]], scores: list[float]) -> list[dict[str, Any]]:
    if len(pairs) != len(scores) or not all(math.isfinite(score) for score in scores):
        raise ValueError("One finite score is required for every pair")
    ranked = [
        {
            "pairId": pair["pairId"],
            "candidateId": pair["candidateId"],
            "denseRank": pair["denseRank"],
            "querySha256": pair["querySha256"],
            "sourceSha256": pair["sourceSha256"],
            "score": float(score),
        }
        for pair, score in zip(pairs, scores, strict=True)
    ]
    ranked.sort(key=lambda item: (-item["score"], item["denseRank"], item["candidateId"]))
    for rank, item in enumerate(ranked, start=1):
        item["rerankerRank"] = rank
    return ranked


def locate_snapshot_file(cache_dir: Path, repository: str, revision: str, filename: str) -> Path:
    repository_dir = "models--" + repository.replace("/", "--")
    path = cache_dir / repository_dir / "snapshots" / revision / filename
    if not path.is_file():
        raise RuntimeError(f"Pinned snapshot file is missing: {path}")
    return path


def locate_remote_code(module_cache: Path, revision: str, filename: str) -> Path:
    matches = [path for path in module_cache.rglob(filename) if revision in path.parts]
    if len(matches) != 1:
        raise RuntimeError(f"Expected one pinned remote-code {filename}, found {len(matches)}")
    return matches[0]


def isolate_evaluation_runtime() -> None:
    """Keep the text-only run from importing an unrelated, incompatible vision wheel."""
    original_find_spec = importlib.util.find_spec

    def text_only_find_spec(name: str, *args: Any, **kwargs: Any):
        if name == "torchvision" or name.startswith("torchvision."):
            return None
        return original_find_spec(name, *args, **kwargs)

    importlib.util.find_spec = text_only_find_spec


def main() -> None:
    args = parse_args()
    prepared = read_json(args.input.resolve())
    validate_input(prepared)
    args.cache_dir.mkdir(parents=True, exist_ok=True)
    args.module_cache.mkdir(parents=True, exist_ok=True)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    os.environ["HF_MODULES_CACHE"] = str(args.module_cache.resolve())

    import torch
    isolate_evaluation_runtime()
    from transformers import AutoModelForSequenceClassification, AutoTokenizer

    if importlib.metadata.version("transformers") != TRANSFORMERS_VERSION:
        raise RuntimeError("Transformers version does not match the frozen contract")
    if (
        platform.python_version() != PYTHON_VERSION
        or torch.__version__ != TORCH_VERSION
        or importlib.metadata.version("psutil") != PSUTIL_VERSION
    ):
        raise RuntimeError("Python or PyTorch version does not match the frozen contract")
    if torch.cuda.is_available():
        raise RuntimeError("PRZ-027 frozen first baseline is CPU-only")
    torch.set_num_threads(CPU_THREADS)
    torch.set_num_interop_threads(1)
    process = psutil.Process()
    rss_before = process.memory_info().rss

    load_started = time.perf_counter_ns()
    tokenizer = AutoTokenizer.from_pretrained(
        MODEL,
        revision=MODEL_REVISION,
        cache_dir=str(args.cache_dir.resolve()),
    )
    model = AutoModelForSequenceClassification.from_pretrained(
        MODEL,
        revision=MODEL_REVISION,
        code_revision=CODE_REVISION,
        trust_remote_code=True,
        torch_dtype=torch.float32,
        cache_dir=str(args.cache_dir.resolve()),
    ).to("cpu")
    model.eval()
    model_load_millis = (time.perf_counter_ns() - load_started) / 1_000_000.0
    rss_after = process.memory_info().rss
    rss_peak = max(rss_before, rss_after)
    parameter_count = sum(parameter.numel() for parameter in model.parameters())
    if parameter_count != MODEL_PARAMETERS:
        raise RuntimeError(f"Model parameter count mismatch: {parameter_count}")

    def score_pairs(pairs: list[list[str]]) -> list[float]:
        nonlocal rss_peak
        scores: list[float] = []
        for start in range(0, len(pairs), BATCH_SIZE):
            batch = pairs[start:start + BATCH_SIZE]
            inputs = tokenizer(
                batch,
                padding=True,
                truncation=True,
                return_tensors="pt",
                max_length=MAX_LENGTH,
            )
            with torch.inference_mode():
                logits = model(**inputs, return_dict=True).logits.view(-1).float()
            scores.extend(float(score) for score in logits.tolist())
            rss_peak = max(rss_peak, process.memory_info().rss)
        return scores

    warmup_started = time.perf_counter_ns()
    score_pairs([["PRIZM reranker warmup", "PRIZM evidence warmup"]])
    warmup_millis = (time.perf_counter_ns() - warmup_started) / 1_000_000.0

    outputs: list[dict[str, Any]] = []
    for dataset in prepared["datasets"]:
        for question in dataset["questions"]:
            pairs = [[pair["query"], pair["sourceText"]] for pair in question["pairs"]]
            started = time.perf_counter_ns()
            scores = score_pairs(pairs)
            ranked = deterministic_ranking(question["pairs"], scores)
            rerank_millis = (time.perf_counter_ns() - started) / 1_000_000.0
            outputs.append({
                "datasetVersion": dataset["datasetVersion"],
                "split": question["split"],
                "questionId": question["questionId"],
                "querySha256": question["querySha256"],
                "pairCount": len(ranked),
                "rerankMillis": rerank_millis,
                "pairs": ranked,
            })

    weight_path = locate_snapshot_file(args.cache_dir, MODEL, MODEL_REVISION, "model.safetensors")
    config_path = locate_snapshot_file(args.cache_dir, MODEL, MODEL_REVISION, "config.json")
    remote_configuration = locate_remote_code(args.module_cache, CODE_REVISION, "configuration.py")
    remote_modeling = locate_remote_code(args.module_cache, CODE_REVISION, "modeling.py")
    if weight_path.stat().st_size != MODEL_WEIGHT_BYTES:
        raise RuntimeError("Downloaded model weight size mismatch")
    downloaded_hashes = {
        "modelWeightSha256": sha256_file(weight_path),
        "configSha256": sha256_file(config_path),
        "remoteConfigurationSha256": sha256_file(remote_configuration),
        "remoteModelingSha256": sha256_file(remote_modeling),
    }
    expected_hashes = {
        "modelWeightSha256": MODEL_WEIGHT_SHA256,
        "configSha256": CONFIG_SHA256,
        "remoteConfigurationSha256": REMOTE_CONFIGURATION_SHA256,
        "remoteModelingSha256": REMOTE_MODELING_SHA256,
    }
    if downloaded_hashes != expected_hashes:
        raise RuntimeError("Downloaded model/config/remote-code SHA-256 changed")
    cache_roots = [
        args.cache_dir / ("models--" + MODEL.replace("/", "--")),
        args.cache_dir / ("models--" + CODE_REPOSITORY.replace("/", "--")),
    ]
    model_cache_bytes = sum(
        path.stat().st_size
        for root in cache_roots
        for path in root.rglob("*")
        if path.is_file()
    )

    result = {
        "schemaVersion": SCHEMA_VERSION,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "inputDigest": prepared["inputDigest"],
        "inputSha256": sha256_file(args.input.resolve()),
        "model": MODEL,
        "modelRevision": MODEL_REVISION,
        "codeRepository": CODE_REPOSITORY,
        "codeRevision": CODE_REVISION,
        "license": LICENSE,
        "transformersVersion": importlib.metadata.version("transformers"),
        "torchVersion": torch.__version__,
        "psutilVersion": importlib.metadata.version("psutil"),
        "pythonVersion": platform.python_version(),
        "device": "cpu",
        "dtype": "float32",
        "topK": TOP_K,
        "maxLength": MAX_LENGTH,
        "batchSize": BATCH_SIZE,
        "cpuThreads": CPU_THREADS,
        "modelParameterCount": parameter_count,
        "modelWeightBytes": weight_path.stat().st_size,
        "modelCacheBytes": model_cache_bytes,
        **downloaded_hashes,
        "modelLoadMillis": model_load_millis,
        "warmupMillis": warmup_millis,
        "processRssBeforeLoadBytes": rss_before,
        "processRssAfterLoadBytes": rss_after,
        "processRssPeakBytes": rss_peak,
        "gpuUsed": False,
        "gpuPeakAllocatedBytes": 0,
        "gpuPeakReservedBytes": 0,
        "questions": outputs,
    }
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
