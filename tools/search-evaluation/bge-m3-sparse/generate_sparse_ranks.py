#!/usr/bin/env python3
"""Generate evaluation-only BGE-M3 sparse rankings for a prepared PRIZM corpus."""

from __future__ import annotations

import argparse
import hashlib
import importlib.metadata
import json
import math
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import torch
from FlagEmbedding import BGEM3FlagModel


SCHEMA_VERSION = 1
MODEL_NAME = "BAAI/bge-m3"
BRANCH_LIMIT = 20
MAX_LENGTH = 8192


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--cache-dir", required=True, type=Path)
    parser.add_argument("--device", default="cuda:0")
    parser.add_argument("--batch-size", type=int, default=8)
    parser.add_argument("--revision", default="main")
    return parser.parse_args()


def sha256_text(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def read_input(path: Path) -> dict[str, Any]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if data.get("schemaVersion") != SCHEMA_VERSION:
        raise ValueError("Unsupported sparse input schemaVersion.")
    if data.get("chunkingProfile") != "production":
        raise ValueError("P14 only permits Production chunking input.")
    chunks = data.get("chunks")
    questions = data.get("questions")
    if not isinstance(chunks, list) or not chunks:
        raise ValueError("Sparse input requires chunks.")
    if not isinstance(questions, list) or not questions:
        raise ValueError("Sparse input requires questions.")

    chunk_ids: set[str] = set()
    for chunk in chunks:
        fixture_id = chunk.get("fixtureChunkId")
        content = chunk.get("content")
        if not isinstance(fixture_id, str) or not fixture_id or fixture_id in chunk_ids:
            raise ValueError("Sparse input chunk IDs must be unique and non-blank.")
        if not isinstance(content, str) or not content.strip():
            raise ValueError("Sparse input chunks require content.")
        if chunk.get("contentSha256") != sha256_text(content):
            raise ValueError(f"Content SHA-256 mismatch for {fixture_id}.")
        chunk_ids.add(fixture_id)

    question_ids: set[str] = set()
    for question in questions:
        question_id = question.get("questionId")
        query = question.get("query")
        if not isinstance(question_id, str) or not question_id or question_id in question_ids:
            raise ValueError("Sparse input question IDs must be unique and non-blank.")
        if not isinstance(query, str) or not query.strip():
            raise ValueError("Sparse input questions require query text.")
        if question.get("querySha256") != sha256_text(query):
            raise ValueError(f"Query SHA-256 mismatch for {question_id}.")
        question_ids.add(question_id)
    return data


def synchronize(device: str) -> None:
    if device.startswith("cuda") and torch.cuda.is_available():
        torch.cuda.synchronize()


def model_revision(model: BGEM3FlagModel) -> str:
    candidates = [
        getattr(getattr(model, "model", None), "config", None),
        getattr(model, "tokenizer", None),
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


def lexical_score(query_weights: dict[str, float], passage_weights: dict[str, float]) -> float:
    if len(query_weights) > len(passage_weights):
        query_weights, passage_weights = passage_weights, query_weights
    return float(sum(weight * passage_weights.get(token_id, 0.0) for token_id, weight in query_weights.items()))


def encode_sparse(
    model: BGEM3FlagModel,
    texts: list[str],
    batch_size: int,
) -> list[dict[str, float]]:
    output = model.encode(
        texts,
        batch_size=batch_size,
        max_length=MAX_LENGTH,
        return_dense=False,
        return_sparse=True,
        return_colbert_vecs=False,
    )
    weights = output.get("lexical_weights")
    if not isinstance(weights, list) or len(weights) != len(texts):
        raise RuntimeError("FlagEmbedding did not return one sparse vector per text.")
    return weights


def main() -> None:
    args = parse_args()
    if args.batch_size < 1:
        raise ValueError("batch-size must be positive.")
    if args.device.startswith("cuda") and not torch.cuda.is_available():
        raise RuntimeError("CUDA was requested but is unavailable.")

    prepared = read_input(args.input.resolve())
    args.cache_dir.mkdir(parents=True, exist_ok=True)
    args.output.parent.mkdir(parents=True, exist_ok=True)

    if args.device.startswith("cuda"):
        torch.cuda.reset_peak_memory_stats()

    load_started = time.perf_counter_ns()
    model = BGEM3FlagModel(
        MODEL_NAME,
        use_fp16=args.device.startswith("cuda"),
        devices=args.device,
        cache_dir=str(args.cache_dir.resolve()),
        batch_size=args.batch_size,
        query_max_length=MAX_LENGTH,
        passage_max_length=MAX_LENGTH,
        revision=args.revision,
    )
    synchronize(args.device)
    model_load_nanos = time.perf_counter_ns() - load_started

    chunk_texts = [chunk["content"] for chunk in prepared["chunks"]]
    corpus_started = time.perf_counter_ns()
    passage_weights = encode_sparse(model, chunk_texts, args.batch_size)
    synchronize(args.device)
    corpus_nanos = time.perf_counter_ns() - corpus_started

    # Warm the persistent query path without recording it as a search sample.
    warmup_started = time.perf_counter_ns()
    encode_sparse(model, ["PRIZM sparse evaluation warmup"], 1)
    synchronize(args.device)
    warmup_nanos = time.perf_counter_ns() - warmup_started

    question_outputs: list[dict[str, Any]] = []
    for question in prepared["questions"]:
        synchronize(args.device)
        encode_started = time.perf_counter_ns()
        query_weights = encode_sparse(model, [question["query"]], 1)[0]
        synchronize(args.device)
        encode_nanos = time.perf_counter_ns() - encode_started

        score_started = time.perf_counter_ns()
        ranked: list[tuple[str, float]] = []
        for chunk, weights in zip(prepared["chunks"], passage_weights, strict=True):
            score = lexical_score(query_weights, weights)
            if not math.isfinite(score):
                raise RuntimeError("Sparse score must be finite.")
            if score > 0.0:
                ranked.append((chunk["fixtureChunkId"], score))
        ranked.sort(key=lambda item: (-item[1], item[0]))
        score_nanos = time.perf_counter_ns() - score_started

        converted = model.convert_id_to_token(query_weights)
        question_outputs.append(
            {
                "questionId": question["questionId"],
                "querySha256": question["querySha256"],
                "queryEncodingMillis": encode_nanos / 1_000_000.0,
                "scoringMillis": score_nanos / 1_000_000.0,
                "queryLexicalWeights": {key: float(value) for key, value in converted.items()},
                "candidates": [
                    {
                        "rank": index + 1,
                        "fixtureChunkId": fixture_id,
                        "sparseScore": score,
                    }
                    for index, (fixture_id, score) in enumerate(ranked[:BRANCH_LIMIT])
                ],
            }
        )

    peak_memory = 0
    if args.device.startswith("cuda"):
        peak_memory = int(torch.cuda.max_memory_allocated())

    result = {
        "schemaVersion": SCHEMA_VERSION,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "datasetId": prepared["datasetId"],
        "chunkingProfile": prepared["chunkingProfile"],
        "inputDigest": prepared["inputDigest"],
        "model": MODEL_NAME,
        "modelRevision": model_revision(model),
        "flagEmbeddingVersion": importlib.metadata.version("FlagEmbedding"),
        "device": args.device,
        "useFp16": args.device.startswith("cuda"),
        "maxLength": MAX_LENGTH,
        "branchLimit": BRANCH_LIMIT,
        "chunkCount": len(prepared["chunks"]),
        "questionCount": len(prepared["questions"]),
        "modelLoadMillis": model_load_nanos / 1_000_000.0,
        "corpusEncodingMillis": corpus_nanos / 1_000_000.0,
        "warmupMillis": warmup_nanos / 1_000_000.0,
        "gpuPeakMemoryBytes": peak_memory,
        "questions": question_outputs,
    }
    args.output.write_text(
        json.dumps(result, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
