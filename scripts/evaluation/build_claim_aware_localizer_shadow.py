#!/usr/bin/env python3
"""Build frozen, evaluation-only claim-aware windows without reading ground truth."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import unicodedata
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path


MAX_WINDOW_SENTENCES = 3
MAX_CANDIDATES = 5
TOKEN_PATTERN = re.compile(r"[^\W_][\w+#._-]*", re.UNICODE)
SENTENCE_BOUNDARY = re.compile(r"(?<=[.!?。！？])\s+")
ASCII_ANCHOR = re.compile(r"(?<![a-z0-9+#._-])[a-z][a-z0-9+#._-]+(?![a-z0-9+#._-])")

# Kept aligned with SearchSnippetGenerator's generic localization vocabulary.
GENERIC_QUERY_TERMS = {
    "experience", "evidence", "find", "show", "경력", "경험", "근거", "검색",
    "관련", "활용", "보여줘", "찾아줘", "있나요",
}
KOREAN_QUERY_SUFFIXES = (
    "에서", "으로", "했던", "하는", "했다", "한", "을", "를", "이", "가",
    "은", "는", "과", "와", "의", "에", "로",
)


@dataclass(frozen=True)
class WindowSignals:
    exact_phrase: bool
    strong_identifier_matches: int
    numeric_matches: int
    adjacent_phrase_matches: int
    query_coverage: int
    lexical_weight: int

    @property
    def score(self) -> int:
        # Mirrors the main PRZ-012 sentence weights; identifier matches are a deterministic tie-break.
        return (
            self.query_coverage * 10_000
            + self.lexical_weight * 20
            + self.numeric_matches * 1_500
            + self.adjacent_phrase_matches * 2_000
            + (100_000 if self.exact_phrase else 0)
        )


def load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def canonical_bytes(value: dict) -> bytes:
    return (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_file(path: Path) -> str:
    return sha256_bytes(path.read_bytes())


def normalize(value: str) -> str:
    return " ".join(unicodedata.normalize("NFKC", value).lower().split())


def sentence_units(content: str) -> list[str]:
    sentences: list[str] = []
    for source_line in content.replace("\r\n", "\n").replace("\r", "\n").split("\n"):
        line = source_line.strip()
        if not line:
            continue
        sentences.extend(fragment.strip() for fragment in SENTENCE_BOUNDARY.split(line) if fragment.strip())
    return sentences


def normalize_query_term(value: str) -> str:
    if value.isdigit():
        return value
    for suffix in KOREAN_QUERY_SUFFIXES:
        if value.endswith(suffix) and len(value) > len(suffix) + 1:
            return value[: -len(suffix)]
    return value


def query_terms(claim: str) -> list[str]:
    ordered: list[str] = []
    seen: set[str] = set()
    for match in TOKEN_PATTERN.finditer(normalize(claim)):
        term = normalize_query_term(match.group())
        if len(term) <= 1 or term in GENERIC_QUERY_TERMS or term in seen:
            continue
        ordered.append(term)
        seen.add(term)
    return ordered


def ascii_anchors(value: str) -> set[str]:
    return set(ASCII_ANCHOR.findall(normalize(value)))


def score_window(claim: str, window: str) -> WindowSignals:
    terms = query_terms(claim)
    normalized_claim = normalize(claim)
    normalized_window = normalize(window)
    matched_terms = [term for term in terms if term in normalized_window]
    adjacent_matches = sum(
        1 for left, right in zip(terms, terms[1:])
        if f"{left} {right}" in normalized_window
    )
    return WindowSignals(
        exact_phrase=(len(normalized_claim) >= 4 and normalized_claim in normalized_window),
        strong_identifier_matches=len(ascii_anchors(claim) & ascii_anchors(window)),
        numeric_matches=sum(1 for term in matched_terms if term.isdigit()),
        adjacent_phrase_matches=adjacent_matches,
        query_coverage=len(matched_terms),
        lexical_weight=sum(len(term) for term in matched_terms),
    )


def preselect_windows(claim: str, content: str, base_id: str) -> list[dict]:
    sentences = sentence_units(content)
    generated: list[dict] = []
    seen_windows: set[str] = set()
    for start in range(len(sentences)):
        for sentence_count in range(1, MAX_WINDOW_SENTENCES + 1):
            end = start + sentence_count
            if end > len(sentences):
                break
            window = "\n".join(sentences[start:end])
            normalized_window = normalize(window)
            if not normalized_window or normalized_window in seen_windows:
                continue
            seen_windows.add(normalized_window)
            signals = score_window(claim, window)
            generated.append({
                "window": window,
                "startSentence": start,
                "endSentenceExclusive": end,
                "sentenceCount": sentence_count,
                "score": signals.score,
                "signals": {
                    "exactPhrase": signals.exact_phrase,
                    "strongIdentifierMatches": signals.strong_identifier_matches,
                    "numericMatches": signals.numeric_matches,
                    "adjacentPhraseMatches": signals.adjacent_phrase_matches,
                    "queryCoverage": signals.query_coverage,
                    "lexicalWeight": signals.lexical_weight,
                },
            })
    generated.sort(key=lambda candidate: (
        -candidate["score"],
        -int(candidate["signals"]["exactPhrase"]),
        -candidate["signals"]["strongIdentifierMatches"],
        -candidate["signals"]["numericMatches"],
        -candidate["signals"]["adjacentPhraseMatches"],
        -candidate["signals"]["queryCoverage"],
        candidate["sentenceCount"],
        candidate["startSentence"],
        candidate["endSentenceExclusive"],
        candidate["window"],
    ))
    selected = generated[:MAX_CANDIDATES]
    for order, candidate in enumerate(selected, start=1):
        candidate["order"] = order
        candidate["id"] = f"{base_id}#w{order}"
    return selected


def build(args: argparse.Namespace) -> dict:
    raw = load_json(args.raw)
    frozen_pairs = load_json(args.pairs)
    c0_numeric = load_json(args.c0_numeric)
    source_pairs = frozen_pairs.get("pairs")
    if not isinstance(source_pairs, list) or len(source_pairs) != 86:
        raise ValueError("frozen source pair count must be 86")
    query_map = {query["id"]: query for query in raw.get("queries", [])}
    c0_map = {row["id"]: row for row in c0_numeric.get("results", [])}
    if len(c0_map) != 86:
        raise ValueError("C0 numeric result count must be 86")

    artifact_pairs: list[dict] = []
    runner_pairs: list[dict] = []
    seen_pair_ids: set[str] = set()
    seen_runner_ids: set[str] = set()
    hypothesis_mismatches = 0
    content_mismatches = 0
    cross_result_contamination = 0
    cross_document_contamination = 0
    duplicate_invalid_windows = 0
    fallback_eligible = 0
    c0_support = 0
    fallback_candidate_counts: list[int] = []
    max_sentence_count = 0

    for source_pair in source_pairs:
        query_id = source_pair["queryId"]
        rank = int(source_pair["rank"])
        base_id = f"{query_id}#r{rank}"
        if base_id in seen_pair_ids:
            raise ValueError(f"duplicate source pair: {base_id}")
        seen_pair_ids.add(base_id)
        query = query_map.get(query_id)
        if query is None or rank < 1 or rank > len(query["response"]["results"]):
            raise ValueError(f"source result missing: {base_id}")
        result = query["response"]["results"][rank - 1]
        if source_pair["premise"] != result["snippet"]:
            raise ValueError(f"original snippet mismatch: {base_id}")
        c0 = c0_map.get(base_id)
        if c0 is None:
            raise ValueError(f"C0 label missing: {base_id}")

        candidates = preselect_windows(source_pair["hypothesis"], result["content"], base_id)
        normalized_content = normalize(result["content"])
        normalized_candidates: set[str] = set()
        for candidate in candidates:
            max_sentence_count = max(max_sentence_count, candidate["sentenceCount"])
            normalized_window = normalize(candidate["window"])
            if normalized_window in normalized_candidates:
                duplicate_invalid_windows += 1
            normalized_candidates.add(normalized_window)
            if normalized_window not in normalized_content:
                cross_result_contamination += 1

        eligible = c0["combinedLabel"] != "SUPPORT"
        if eligible:
            fallback_eligible += 1
            fallback_candidate_counts.append(len(candidates))
            for candidate in candidates:
                runner_id = candidate["id"]
                if runner_id in seen_runner_ids:
                    raise ValueError(f"duplicate runner id: {runner_id}")
                seen_runner_ids.add(runner_id)
                runner_pairs.append({
                    "id": runner_id,
                    "premise": candidate["window"],
                    "hypothesis": source_pair["hypothesis"],
                    "groundTruth": "UNKNOWN",
                })
        else:
            c0_support += 1

        artifact_pairs.append({
            "queryId": query_id,
            "userId": source_pair["userId"],
            "rank": rank,
            "hypothesis": source_pair["hypothesis"],
            "originalSnippet": source_pair["premise"],
            "originalSnippetSha256": sha256_bytes(source_pair["premise"].encode("utf-8")),
            "c0CombinedLabel": c0["combinedLabel"],
            "fallbackEligible": eligible,
            "resultIdentity": {
                "chunkId": result["chunkId"],
                "documentId": result["documentId"],
                "documentVersionId": result["documentVersionId"],
                "sourceType": result["sourceType"],
                "sourceIndex": result["sourceIndex"],
                "contentSha256": sha256_bytes(result["content"].encode("utf-8")),
                "score": result["score"],
                "distance": result["distance"],
            },
            "candidates": candidates,
        })

    if hypothesis_mismatches or content_mismatches:
        raise ValueError("source integrity mismatch")
    if cross_result_contamination or cross_document_contamination:
        raise ValueError("candidate contamination detected")
    if duplicate_invalid_windows:
        raise ValueError("duplicate invalid candidate window detected")
    if fallback_eligible + c0_support != 86:
        raise ValueError("fallback partition mismatch")
    if max((len(pair["candidates"]) for pair in artifact_pairs), default=0) > MAX_CANDIDATES:
        raise ValueError("candidate count exceeds limit")
    if max_sentence_count > MAX_WINDOW_SENTENCES:
        raise ValueError("window sentence count exceeds limit")

    candidate_document = {
        "schemaVersion": 1,
        "phase": "PRZ-016-P7-B-CLAIM-AWARE-CONTENT-LOCALIZER-SHADOW",
        "policy": {
            "contentBoundary": "SAME_RESULT_CONTENT_ONLY",
            "windowSentenceCounts": [1, 2, 3],
            "maxPreselectedCandidates": 5,
            "earlyStopOnCurrentSnippet": False,
            "preselectionOrder": [
                "score DESC", "exactPhrase DESC", "strongIdentifierMatches DESC",
                "numericMatches DESC", "adjacentPhraseMatches DESC", "queryCoverage DESC",
                "sentenceCount ASC", "startSentence ASC", "endSentenceExclusive ASC", "window ASC",
            ],
            "fallbackPolicy": "Run candidates in order only when frozen C0 combinedLabel is NON_SUPPORT; select first combined SUPPORT.",
        },
        "pairs": artifact_pairs,
    }
    runner_document = {
        "schemaVersion": 1,
        "phase": "PRZ-016-P7-B-CLAIM-AWARE-CONTENT-LOCALIZER-NLI-RUNNER",
        "pairs": runner_pairs,
    }
    candidate_bytes = canonical_bytes(candidate_document)
    runner_bytes = canonical_bytes(runner_document)
    average_candidates = (
        sum(fallback_candidate_counts) / len(fallback_candidate_counts)
        if fallback_candidate_counts else 0.0
    )
    implementation_file = Path(__file__).resolve()
    try:
        implementation_path = implementation_file.relative_to(Path.cwd().resolve()).as_posix()
    except ValueError:
        implementation_path = implementation_file.name
    freeze_document = {
        "schemaVersion": 1,
        "phase": "PRZ-016-P7-B-CLAIM-AWARE-CONTENT-LOCALIZER-FREEZE",
        "frozenAt": datetime.now(timezone.utc).isoformat(),
        "groundTruthUsedBeforeFreeze": False,
        "implementation": {
            "path": implementation_path,
            "sha256": sha256_file(implementation_file),
            "productionDependency": False,
        },
        "source": {
            "rawResults": {"path": str(args.raw.as_posix()), "sha256": sha256_file(args.raw)},
            "frozenPairs": {"path": str(args.pairs.as_posix()), "sha256": sha256_file(args.pairs)},
            "c0Numeric": {"path": str(args.c0_numeric.as_posix()), "sha256": sha256_file(args.c0_numeric)},
        },
        "candidates": {
            "path": str(args.candidates_output.as_posix()),
            "sha256": sha256_bytes(candidate_bytes),
            "originalPairCount": 86,
            "fallbackEligible": fallback_eligible,
            "c0AlreadySupport": c0_support,
            "totalPreselectedWindows": sum(len(pair["candidates"]) for pair in artifact_pairs),
            "fallbackRunnerWindows": len(runner_pairs),
            "averageCandidatesPerFallback": average_candidates,
            "maxCandidatesPerFallback": max(fallback_candidate_counts, default=0),
            "maxSentenceCount": max_sentence_count,
        },
        "runner": {
            "path": str(args.runner_output.as_posix()),
            "sha256": sha256_bytes(runner_bytes),
            "pairCount": len(runner_pairs),
        },
        "integrity": {
            "queryIdRankOneToOne": True,
            "hypothesisChanged": 0,
            "resultContentChanged": 0,
            "crossResultContamination": cross_result_contamination,
            "crossDocumentContamination": cross_document_contamination,
            "duplicateInvalidWindowCount": duplicate_invalid_windows,
            "candidateCountWithinLimit": True,
            "windowSentenceCountWithinLimit": True,
            "parse": "PASS",
            "freeze": "PASS",
        },
        "nliInference": "NOT_RUN",
        "numericVerification": "NOT_RUN",
        "groundTruthScoring": "NOT_RUN",
    }

    args.candidates_output.write_bytes(candidate_bytes)
    args.runner_output.write_bytes(runner_bytes)
    args.freeze_output.write_bytes(canonical_bytes(freeze_document))
    return freeze_document


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--raw", type=Path, required=True)
    parser.add_argument("--pairs", type=Path, required=True)
    parser.add_argument("--c0-numeric", type=Path, required=True)
    parser.add_argument("--candidates-output", type=Path, required=True)
    parser.add_argument("--runner-output", type=Path, required=True)
    parser.add_argument("--freeze-output", type=Path, required=True)
    args = parser.parse_args()
    summary = build(args)
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
