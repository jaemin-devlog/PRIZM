#!/usr/bin/env python3
"""Evaluation-only combination of frozen NLI output with exact numeric consistency checks."""
import argparse
import json
import re
from pathlib import Path

NUMBER_WITH_UNIT = re.compile(r"(?<![0-9])([0-9]+(?:[.,][0-9]+)?)(%|밀리초|ms|초|분|시간|개|건|명|회)", re.I)
UPPERCASE_METRIC = re.compile(r"[A-Z][A-Z0-9]{1,}")
SENTENCE = re.compile(r"(?<=[.!?])\s+|\n+")
NUMERIC_DENIAL = re.compile(r"(?:근거|기록|수치|보고서|내역).{0,16}(?:없|않)")


def normalize_number(value: str) -> str:
    return value.replace(",", ".")


def numeric_values(text: str):
    return {(normalize_number(value), unit.lower()) for value, unit in NUMBER_WITH_UNIT.findall(text)}


def metric_tokens(text: str):
    return set(UPPERCASE_METRIC.findall(text))


def numeric_veto(premise: str, hypothesis: str):
    expected = numeric_values(hypothesis)
    metrics = metric_tokens(hypothesis)
    if not expected or not metrics:
        return None
    sentences = [item.strip() for item in SENTENCE.split(premise) if item.strip()]
    relevant = []
    for index, sentence in enumerate(sentences):
        if metrics & metric_tokens(sentence):
            relevant.append(sentence)
            if index + 1 < len(sentences):
                relevant.append(sentence + " " + sentences[index + 1])
    for claim in relevant:
        values = numeric_values(claim)
        if expected & values and NUMERIC_DENIAL.search(claim):
            return "EXACT_VALUE_EXPLICITLY_DENIED"
        if values and not (expected & values) and all(unit in {unit for _, unit in values} for _, unit in expected):
            return "SAME_METRIC_DIFFERENT_VALUE"
    return None


def distribution(rows):
    labels = ("SUPPORT", "CONTRADICT", "UNKNOWN")
    result = {truth: {label: 0 for label in labels} for truth in labels}
    for row in rows:
        result[row["groundTruth"]][row["combinedLabel"]] += 1
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--pairs", type=Path, required=True)
    parser.add_argument("--nli-results", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    pairs = {pair["id"]: pair for pair in json.loads(args.pairs.read_text(encoding="utf-8"))["pairs"]}
    nli = json.loads(args.nli_results.read_text(encoding="utf-8"))
    rows = []
    for result in nli["results"]:
        pair = pairs.get(result["id"])
        if pair is None:
            raise ValueError(f"NLI result has no frozen pair: {result['id']}")
        veto = numeric_veto(pair["premise"], pair["hypothesis"])
        combined = "CONTRADICT" if result["predictedLabel"] == "SUPPORT" and veto else result["predictedLabel"]
        rows.append({"id": result["id"], "groundTruth": result["groundTruth"],
                     "nliLabel": result["predictedLabel"], "combinedLabel": combined,
                     "numericVeto": veto, "correct": combined == result["groundTruth"]})
    args.output.write_text(json.dumps({"schemaVersion": 1, "mode": "NLI_PLUS_NUMERIC_SHADOW",
        "results": rows, "summary": {"nliOnly": nli["summary"]["byGroundTruth"],
        "combined": distribution(rows)}}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
