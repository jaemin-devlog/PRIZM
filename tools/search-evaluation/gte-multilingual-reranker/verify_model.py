#!/usr/bin/env python3
"""Load the exact PRZ-027 model/code revisions and score one non-benchmark smoke pair."""

from __future__ import annotations

import argparse
import importlib.metadata
import json
import os
import time
from pathlib import Path

from generate_scores import (
    CODE_REVISION,
    CPU_THREADS,
    MAX_LENGTH,
    MODEL,
    MODEL_PARAMETERS,
    MODEL_REVISION,
    MODEL_WEIGHT_BYTES,
    MODEL_WEIGHT_SHA256,
    CONFIG_SHA256,
    REMOTE_CONFIGURATION_SHA256,
    REMOTE_MODELING_SHA256,
    PYTHON_VERSION,
    PSUTIL_VERSION,
    TORCH_VERSION,
    TRANSFORMERS_VERSION,
    isolate_evaluation_runtime,
    locate_remote_code,
    locate_snapshot_file,
    sha256_file,
)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--cache-dir", required=True, type=Path)
    parser.add_argument("--module-cache", required=True, type=Path)
    args = parser.parse_args()
    args.cache_dir.mkdir(parents=True, exist_ok=True)
    args.module_cache.mkdir(parents=True, exist_ok=True)
    os.environ["HF_MODULES_CACHE"] = str(args.module_cache.resolve())

    import torch
    isolate_evaluation_runtime()
    from transformers import AutoModelForSequenceClassification, AutoTokenizer

    if importlib.metadata.version("transformers") != TRANSFORMERS_VERSION:
        raise RuntimeError("Transformers version does not match the frozen contract")
    if (
        __import__("platform").python_version() != PYTHON_VERSION
        or torch.__version__ != TORCH_VERSION
        or importlib.metadata.version("psutil") != PSUTIL_VERSION
    ):
        raise RuntimeError("Python or PyTorch version does not match the frozen contract")
    if torch.cuda.is_available():
        raise RuntimeError("PRZ-027 first baseline must remain CPU-only")
    torch.set_num_threads(CPU_THREADS)
    torch.set_num_interop_threads(1)
    started = time.perf_counter_ns()
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
    inputs = tokenizer(
        [["PRIZM smoke query", "PRIZM smoke document"]],
        padding=True,
        truncation=True,
        return_tensors="pt",
        max_length=MAX_LENGTH,
    )
    with torch.inference_mode():
        score = float(model(**inputs, return_dict=True).logits.view(-1).item())
    parameters = sum(parameter.numel() for parameter in model.parameters())
    weight = locate_snapshot_file(args.cache_dir, MODEL, MODEL_REVISION, "model.safetensors")
    config = locate_snapshot_file(args.cache_dir, MODEL, MODEL_REVISION, "config.json")
    remote_configuration = locate_remote_code(args.module_cache, CODE_REVISION, "configuration.py")
    remote_modeling = locate_remote_code(args.module_cache, CODE_REVISION, "modeling.py")
    if parameters != MODEL_PARAMETERS or weight.stat().st_size != MODEL_WEIGHT_BYTES:
        raise RuntimeError("Pinned model parameter count or weight size changed")
    hashes = {
        "modelWeightSha256": sha256_file(weight),
        "configSha256": sha256_file(config),
        "remoteConfigurationSha256": sha256_file(remote_configuration),
        "remoteModelingSha256": sha256_file(remote_modeling),
    }
    if hashes != {
        "modelWeightSha256": MODEL_WEIGHT_SHA256,
        "configSha256": CONFIG_SHA256,
        "remoteConfigurationSha256": REMOTE_CONFIGURATION_SHA256,
        "remoteModelingSha256": REMOTE_MODELING_SHA256,
    }:
        raise RuntimeError("Pinned model/config/remote-code SHA-256 changed")
    print(json.dumps({
        "model": MODEL,
        "modelRevision": MODEL_REVISION,
        "codeRevision": CODE_REVISION,
        "transformersVersion": importlib.metadata.version("transformers"),
        "pythonVersion": __import__("platform").python_version(),
        "torchVersion": torch.__version__,
        "psutilVersion": importlib.metadata.version("psutil"),
        "device": "cpu",
        "dtype": "float32",
        "parameters": parameters,
        "modelWeightBytes": weight.stat().st_size,
        **hashes,
        "smokeScoreFinite": score == score and abs(score) != float("inf"),
        "elapsedMillis": (time.perf_counter_ns() - started) / 1_000_000.0,
    }, indent=2))


if __name__ == "__main__":
    main()
