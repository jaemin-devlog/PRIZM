#!/usr/bin/env python3
"""Score only the frozen P14 candidate pool with the official BGE reranker."""

from __future__ import annotations

import argparse
import hashlib
import importlib.metadata
import json
import math
import platform
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import psutil
import torch
from transformers import AutoModelForSequenceClassification, AutoTokenizer


SCHEMA_VERSION = 1
MODEL_NAME = "BAAI/bge-reranker-v2-m3"
SOURCE_PROFILE = "dense-bge-m3-sparse-rrf-k60-v1"
SPARSE_MODEL = "BAAI/bge-m3"
MAX_LENGTH = 512
BATCH_SIZE = 32
MINIMUM_DENSE_SCORE = 0.50


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--p14-report", required=True, type=Path)
    parser.add_argument("--sparse-output", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--cache-dir", required=True, type=Path)
    parser.add_argument("--device", default="cuda:0")
    parser.add_argument("--batch-size", type=int, default=BATCH_SIZE)
    parser.add_argument("--revision", default="main")
    parser.add_argument("--question-limit", type=int)
    return parser.parse_args()


def sha256_text(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def read_json(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"Expected a JSON object in {path}.")
    return value


def validate_prepared_input(prepared: dict[str, Any]) -> None:
    if prepared.get("schemaVersion") != SCHEMA_VERSION:
        raise ValueError("Unsupported prepared input schemaVersion.")
    if prepared.get("chunkingProfile") != "production":
        raise ValueError("P15 only permits Production chunking input.")
    chunks = prepared.get("chunks")
    questions = prepared.get("questions")
    if not isinstance(chunks, list) or not chunks:
        raise ValueError("P15 prepared input requires chunks.")
    if not isinstance(questions, list) or not questions:
        raise ValueError("P15 prepared input requires questions.")

    seen_chunks: set[str] = set()
    for chunk in chunks:
        fixture_id = chunk.get("fixtureChunkId")
        content = chunk.get("content")
        if not isinstance(fixture_id, str) or not fixture_id or fixture_id in seen_chunks:
            raise ValueError("Prepared chunk IDs must be unique and non-blank.")
        if not isinstance(content, str) or not content.strip():
            raise ValueError("Prepared chunks require content.")
        if chunk.get("contentSha256") != sha256_text(content):
            raise ValueError(f"Content SHA-256 mismatch for {fixture_id}.")
        seen_chunks.add(fixture_id)

    seen_questions: set[str] = set()
    for question in questions:
        question_id = question.get("questionId")
        query = question.get("query")
        if not isinstance(question_id, str) or not question_id or question_id in seen_questions:
            raise ValueError("Prepared question IDs must be unique and non-blank.")
        if not isinstance(query, str) or not query.strip():
            raise ValueError("Prepared questions require query text.")
        if question.get("querySha256") != sha256_text(query):
            raise ValueError(f"Query SHA-256 mismatch for {question_id}.")
        seen_questions.add(question_id)


def prepare_candidate_pools(
    prepared: dict[str, Any],
    p14_report: dict[str, Any],
    sparse_output: dict[str, Any],
) -> list[dict[str, Any]]:
    profile = p14_report.get("profile")
    if not isinstance(profile, dict) or profile.get("profileId") != SOURCE_PROFILE:
        raise ValueError("P15 requires the frozen P14 Dense+Sparse report.")
    if p14_report.get("datasetId") != prepared.get("datasetId"):
        raise ValueError("P14 report dataset does not match the prepared input.")
    if sparse_output.get("datasetId") != prepared.get("datasetId"):
        raise ValueError("P14 sparse dataset does not match the prepared input.")
    if sparse_output.get("inputDigest") != prepared.get("inputDigest"):
        raise ValueError("P14 sparse input digest does not match the prepared input.")
    if sparse_output.get("model") != SPARSE_MODEL:
        raise ValueError("P15 requires the official BGE-M3 sparse output.")

    chunks_by_id = {chunk["fixtureChunkId"]: chunk for chunk in prepared["chunks"]}
    report_by_id = {question["questionId"]: question for question in p14_report.get("questions", [])}
    sparse_by_id = {question["questionId"]: question for question in sparse_output.get("questions", [])}
    pools: list[dict[str, Any]] = []
    for prepared_question in prepared["questions"]:
        question_id = prepared_question["questionId"]
        report_question = report_by_id.get(question_id)
        sparse_question = sparse_by_id.get(question_id)
        if report_question is None or sparse_question is None:
            raise ValueError(f"P14 artifacts are missing {question_id}.")
        if report_question.get("query") != prepared_question["query"]:
            raise ValueError(f"P14 query text mismatch for {question_id}.")
        if sparse_question.get("querySha256") != prepared_question["querySha256"]:
            raise ValueError(f"P14 sparse query hash mismatch for {question_id}.")

        sparse_ids = {
            candidate["fixtureChunkId"]
            for candidate in sparse_question.get("candidates", [])
        }
        seen_candidates: set[str] = set()
        eligible: list[dict[str, Any]] = []
        for report_candidate in report_question.get("candidates", []):
            fixture_id = report_candidate.get("fixtureChunkId")
            score = report_candidate.get("score")
            if fixture_id not in chunks_by_id or fixture_id in seen_candidates:
                raise ValueError(f"P14 report has an invalid candidate for {question_id}.")
            if not isinstance(score, (int, float)) or not math.isfinite(float(score)):
                raise ValueError(f"P14 dense score is invalid for {question_id}.")
            seen_candidates.add(fixture_id)
            if float(score) >= MINIMUM_DENSE_SCORE or fixture_id in sparse_ids:
                eligible.append(
                    {
                        "p14Rank": len(eligible) + 1,
                        "fixtureChunkId": fixture_id,
                        "content": chunks_by_id[fixture_id]["content"],
                    }
                )
        if not eligible:
            raise ValueError(f"P14 eligible candidate pool is empty for {question_id}.")
        pools.append(
            {
                "questionId": question_id,
                "query": prepared_question["query"],
                "querySha256": prepared_question["querySha256"],
                "candidates": eligible,
            }
        )
    if len(report_by_id) != len(pools) or len(sparse_by_id) != len(pools):
        raise ValueError("P14 artifacts contain unexpected questions.")
    return pools


def synchronize(device: str) -> None:
    if device.startswith("cuda") and torch.cuda.is_available():
        torch.cuda.synchronize()


def model_revision(model: Any, tokenizer: Any) -> str:
    candidates = [
        getattr(model, "config", None),
        tokenizer,
    ]
    for candidate in candidates:
        commit = getattr(candidate, "_commit_hash", None)
        if isinstance(commit, str) and commit:
            return commit
        init_kwargs = getattr(candidate, "init_kwargs", None)
        if isinstance(init_kwargs, dict):
            commit = init_kwargs.get("_commit_hash")
            if isinstance(commit, str) and commit:
                return commit
    return "UNKNOWN"


def score_pairs(
    model: Any,
    tokenizer: Any,
    pairs: list[list[str]],
    batch_size: int,
    device: str,
) -> list[float]:
    values: list[float] = []
    for start in range(0, len(pairs), batch_size):
        batch = pairs[start : start + batch_size]
        inputs = tokenizer(
            batch,
            padding=True,
            truncation=True,
            return_tensors="pt",
            max_length=MAX_LENGTH,
        ).to(device)
        with torch.no_grad():
            logits = model(**inputs, return_dict=True).logits.view(-1).float()
        values.extend(float(score) for score in logits.cpu().tolist())
    if len(values) != len(pairs):
        raise RuntimeError("Transformers did not return one reranker score per pair.")
    if not all(math.isfinite(score) for score in values):
        raise RuntimeError("Reranker scores must be finite.")
    return values


def pair_token_count(tokenizer: Any, query: str, content: str) -> int:
    encoded = tokenizer(
        query,
        content,
        add_special_tokens=True,
        truncation=False,
    )
    return len(encoded["input_ids"])


def main() -> None:
    args = parse_args()
    if args.batch_size != BATCH_SIZE:
        raise ValueError(f"P15 fixes batch-size at {BATCH_SIZE}.")
    if args.question_limit is not None and args.question_limit < 1:
        raise ValueError("question-limit must be positive.")
    if args.device.startswith("cuda") and not torch.cuda.is_available():
        raise RuntimeError("CUDA was requested but is unavailable.")

    input_path = args.input.resolve()
    report_path = args.p14_report.resolve()
    sparse_path = args.sparse_output.resolve()
    prepared = read_json(input_path)
    validate_prepared_input(prepared)
    p14_report = read_json(report_path)
    sparse_output = read_json(sparse_path)
    pools = prepare_candidate_pools(prepared, p14_report, sparse_output)
    if args.question_limit is not None:
        pools = pools[: args.question_limit]

    args.cache_dir.mkdir(parents=True, exist_ok=True)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    process = psutil.Process()
    rss_before_load = process.memory_info().rss

    use_fp16 = args.device.startswith("cuda")
    load_started = time.perf_counter_ns()
    tokenizer = AutoTokenizer.from_pretrained(
        MODEL_NAME,
        cache_dir=str(args.cache_dir.resolve()),
        revision=args.revision,
    )
    model = AutoModelForSequenceClassification.from_pretrained(
        MODEL_NAME,
        cache_dir=str(args.cache_dir.resolve()),
        revision=args.revision,
        dtype=torch.float16 if use_fp16 else torch.float32,
    ).to(args.device)
    model.eval()
    synchronize(args.device)
    model_load_nanos = time.perf_counter_ns() - load_started
    rss_after_load = process.memory_info().rss
    rss_peak = max(rss_before_load, rss_after_load)

    gpu_model_allocated = 0
    gpu_model_reserved = 0
    if args.device.startswith("cuda"):
        gpu_model_allocated = int(torch.cuda.memory_allocated())
        gpu_model_reserved = int(torch.cuda.memory_reserved())

    warmup_started = time.perf_counter_ns()
    score_pairs(
        model,
        tokenizer,
        [["PRIZM reranker warmup", "PRIZM evidence warmup"]],
        1,
        args.device,
    )
    synchronize(args.device)
    warmup_nanos = time.perf_counter_ns() - warmup_started
    if args.device.startswith("cuda"):
        torch.cuda.reset_peak_memory_stats()

    maximum_pair_tokens = 0
    outputs: list[dict[str, Any]] = []
    for pool in pools:
        pairs: list[list[str]] = []
        for candidate in pool["candidates"]:
            token_count = pair_token_count(tokenizer, pool["query"], candidate["content"])
            maximum_pair_tokens = max(maximum_pair_tokens, token_count)
            if token_count > MAX_LENGTH:
                raise RuntimeError(
                    f"P15 pair exceeds max_length for {pool['questionId']}: {token_count}."
                )
            pairs.append([pool["query"], candidate["content"]])

        synchronize(args.device)
        inference_started = time.perf_counter_ns()
        scores = score_pairs(model, tokenizer, pairs, BATCH_SIZE, args.device)
        synchronize(args.device)
        inference_nanos = time.perf_counter_ns() - inference_started
        rss_peak = max(rss_peak, process.memory_info().rss)

        ranked = [
            {
                "p14Rank": candidate["p14Rank"],
                "fixtureChunkId": candidate["fixtureChunkId"],
                "rerankerScore": score,
            }
            for candidate, score in zip(pool["candidates"], scores, strict=True)
        ]
        ranked.sort(
            key=lambda candidate: (
                -candidate["rerankerScore"],
                candidate["p14Rank"],
                candidate["fixtureChunkId"],
            )
        )
        for index, candidate in enumerate(ranked):
            candidate["rerankerRank"] = index + 1
        outputs.append(
            {
                "questionId": pool["questionId"],
                "querySha256": pool["querySha256"],
                "inferenceMillis": inference_nanos / 1_000_000.0,
                "candidates": ranked,
            }
        )

    gpu_peak_allocated = 0
    gpu_peak_reserved = 0
    if args.device.startswith("cuda"):
        gpu_peak_allocated = int(torch.cuda.max_memory_allocated())
        gpu_peak_reserved = int(torch.cuda.max_memory_reserved())

    result = {
        "schemaVersion": SCHEMA_VERSION,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "datasetId": prepared["datasetId"],
        "chunkingProfile": prepared["chunkingProfile"],
        "inputDigest": prepared["inputDigest"],
        "sourceProfile": SOURCE_PROFILE,
        "p14ReportSha256": sha256_file(report_path),
        "sparseOutputSha256": sha256_file(sparse_path),
        "model": MODEL_NAME,
        "modelRevision": model_revision(model, tokenizer),
        "inferenceLibrary": "transformers",
        "inferenceLibraryVersion": importlib.metadata.version("transformers"),
        "pythonVersion": platform.python_version(),
        "torchVersion": torch.__version__,
        "transformersVersion": importlib.metadata.version("transformers"),
        "device": args.device,
        "useFp16": use_fp16,
        "normalized": False,
        "maxLength": MAX_LENGTH,
        "batchSize": BATCH_SIZE,
        "chunkCount": len(prepared["chunks"]),
        "questionCount": len(outputs),
        "maximumPairTokens": maximum_pair_tokens,
        "modelLoadMillis": model_load_nanos / 1_000_000.0,
        "warmupMillis": warmup_nanos / 1_000_000.0,
        "gpuModelAllocatedBytes": gpu_model_allocated,
        "gpuModelReservedBytes": gpu_model_reserved,
        "gpuPeakAllocatedBytes": gpu_peak_allocated,
        "gpuPeakReservedBytes": gpu_peak_reserved,
        "processRssBeforeLoadBytes": rss_before_load,
        "processRssAfterLoadBytes": rss_after_load,
        "processRssPeakBytes": rss_peak,
        "questions": outputs,
    }
    args.output.write_text(
        json.dumps(result, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
