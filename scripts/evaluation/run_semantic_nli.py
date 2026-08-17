#!/usr/bin/env python3
"""Evaluation-only semantic NLI screen; --check never loads a model or runs inference."""
import argparse
import hashlib
import json
import math
import statistics
import time
from pathlib import Path

LABEL_MAP = {"entailment": "SUPPORT", "contradiction": "CONTRADICT", "neutral": "UNKNOWN"}


def load_pairs(path: Path):
    data = json.loads(path.read_text(encoding="utf-8"))
    pairs = data.get("pairs")
    if not isinstance(pairs, list):
        raise ValueError("pair JSON must contain a pairs array")
    required = {"id", "premise", "hypothesis", "groundTruth"}
    ids = set()
    counts = {label: 0 for label in ("SUPPORT", "CONTRADICT", "UNKNOWN")}
    for pair in pairs:
        if set(pair) != required or not all(isinstance(pair[key], str) and pair[key] for key in required):
            raise ValueError(f"invalid pair: {pair!r}")
        if pair["id"] in ids or pair["groundTruth"] not in counts:
            raise ValueError(f"invalid id or ground truth: {pair['id']}")
        ids.add(pair["id"])
        counts[pair["groundTruth"]] += 1
    return pairs, counts


def percentile_95(values):
    if not values:
        return 0.0
    ordered = sorted(values)
    return ordered[math.ceil(len(ordered) * 0.95) - 1]


def check_only(pair_path: Path, expected_sha: str | None):
    pairs, counts = load_pairs(pair_path)
    digest = hashlib.sha256(pair_path.read_bytes()).hexdigest()
    if expected_sha and digest != expected_sha:
        raise ValueError(f"pair SHA-256 mismatch: expected {expected_sha}, got {digest}")
    print(json.dumps({"status": "CHECK_PASS", "pairSha256": digest, "count": len(pairs), "counts": counts}, ensure_ascii=False))


def run(pair_path: Path, model_ref: str, output_path: Path):
    pairs, _ = load_pairs(pair_path)
    from transformers import AutoModelForSequenceClassification, AutoTokenizer
    import torch

    tokenizer = AutoTokenizer.from_pretrained(model_ref)
    model = AutoModelForSequenceClassification.from_pretrained(model_ref)
    model.eval()
    id2label = {int(key): str(value).lower() for key, value in model.config.id2label.items()}
    if set(id2label.values()) != set(LABEL_MAP):
        raise RuntimeError(f"unsupported NLI labels: {id2label}")
    device = "cuda" if torch.cuda.is_available() else "cpu"
    model.to(device)
    results = []
    for pair in pairs:
        started = time.perf_counter()
        inputs = tokenizer(pair["premise"], pair["hypothesis"], return_tensors="pt", truncation=True).to(device)
        if getattr(model.config, "type_vocab_size", 0) == 1:
            inputs.pop("token_type_ids", None)
        with torch.no_grad():
            probabilities = torch.softmax(model(**inputs).logits[0], dim=-1).cpu().tolist()
        raw = {id2label[index]: probabilities[index] for index in range(len(probabilities))}
        predicted = LABEL_MAP[max(raw, key=raw.get)]
        results.append({"id": pair["id"], "groundTruth": pair["groundTruth"], "predictedLabel": predicted,
                        "supportProbability": raw["entailment"], "contradictionProbability": raw["contradiction"],
                        "unknownProbability": raw["neutral"], "correct": predicted == pair["groundTruth"],
                        "latencyMs": (time.perf_counter() - started) * 1000})
    groups = {label: {prediction: 0 for prediction in LABEL_MAP.values()} for label in LABEL_MAP.values()}
    for result in results:
        groups[result["groundTruth"]][result["predictedLabel"]] += 1
    latencies = [result["latencyMs"] for result in results]
    output_path.write_text(json.dumps({"model": model_ref, "device": device, "results": results,
        "summary": {"byGroundTruth": groups, "averageLatencyMs": statistics.fmean(latencies), "p95LatencyMs": percentile_95(latencies)}}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--pairs", type=Path, required=True)
    parser.add_argument("--model", default="MoritzLaurer/mDeBERTa-v3-base-mnli-xnli")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--expected-sha256")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    if args.check:
        check_only(args.pairs, args.expected_sha256)
        return
    if args.output is None:
        raise ValueError("--output is required unless --check is used")
    run(args.pairs, args.model, args.output)


if __name__ == "__main__":
    main()
