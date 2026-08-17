from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import unicodedata
from collections import Counter, defaultdict
from difflib import SequenceMatcher
from pathlib import Path

import pdfplumber


ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parents[2]
P0_PATH = REPO / "specs" / "PRZ-016-search-performance-v2" / "p0-benchmark" / "evaluation-dataset.json"
P5_PATH = REPO / "specs" / "PRZ-016-search-performance-v2" / "p5-final-holdout" / "holdout-dataset.json"
QUESTION_PATH = ROOT / "dataset" / "questions.json"
GT_PATH = ROOT / "dataset" / "ground-truth.json"
CORPUS_PATH = ROOT / "dataset" / "corpus-manifest.json"

EXPECTED_CATEGORIES = {
    "DIRECT_EXPERIENCE": 8,
    "NATURAL_VARIATION": 8,
    "INDIRECT_PROBLEM": 8,
    "NUMERIC_IDENTIFIER": 4,
    "COMPLEX_NATURAL_LANGUAGE": 8,
    "NEGATIVE": 12,
}
EXPECTED_PER_USER = {
    "DIRECT_EXPERIENCE": 2,
    "NATURAL_VARIATION": 2,
    "INDIRECT_PROBLEM": 2,
    "NUMERIC_IDENTIFIER": 1,
    "COMPLEX_NATURAL_LANGUAGE": 2,
    "NEGATIVE": 3,
}
LEGACY_FACT_IDENTIFIERS = [
    "AirConnect",
    "MoneyWay",
    "TourAPI",
    "2,329행",
    "19분 22초",
    "1,252건",
    "1,480건",
    "1,654건",
    "11.376초",
]


def load_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def normalized(text: str) -> str:
    text = unicodedata.normalize("NFKC", text).lower()
    return "".join(ch for ch in text if ch.isalnum())


def compact(text: str) -> str:
    return "".join(unicodedata.normalize("NFKC", text).split())


def tokens(text: str) -> set[str]:
    return set(re.findall(r"[0-9a-zA-Z가-힣]+", unicodedata.normalize("NFKC", text).lower()))


def similarity(left: str, right: str) -> tuple[float, float, float]:
    left_norm = normalized(left)
    right_norm = normalized(right)
    sequence = SequenceMatcher(None, left_norm, right_norm).ratio()
    left_bigrams = Counter(left_norm[index : index + 2] for index in range(max(0, len(left_norm) - 1)))
    right_bigrams = Counter(right_norm[index : index + 2] for index in range(max(0, len(right_norm) - 1)))
    overlap = sum((left_bigrams & right_bigrams).values())
    dice = (2 * overlap / (sum(left_bigrams.values()) + sum(right_bigrams.values()))) if left_bigrams or right_bigrams else 0.0
    lt, rt = tokens(left), tokens(right)
    jaccard = len(lt & rt) / len(lt | rt) if lt | rt else 0.0
    return sequence, dice, jaccard


def extract_document(path: Path) -> tuple[str, list[str]]:
    if path.suffix.lower() == ".txt":
        text = path.read_text(encoding="utf-8")
        return text, [text]
    with pdfplumber.open(path) as pdf:
        pages = [(page.extract_text() or "") for page in pdf.pages]
    return "\n".join(pages), pages


def fail(errors: list[str], message: str) -> None:
    errors.append(message)


def validate() -> dict:
    errors: list[str] = []
    questions = load_json(QUESTION_PATH)["questions"]
    ground_truth = load_json(GT_PATH)["entries"]
    corpus = load_json(CORPUS_PATH)

    active_docs = corpus["activeDocuments"]
    inactive_docs = corpus["inactiveVersionFixtures"]
    doc_by_key = {doc["documentKey"]: doc for doc in active_docs}
    version_by_key = {doc["versionKey"]: doc for doc in active_docs + inactive_docs}
    document_text: dict[str, str] = {}
    document_pages: dict[str, list[str]] = {}
    for doc in active_docs:
        path = ROOT / doc["path"]
        if not path.exists():
            fail(errors, f"Missing active document: {path}")
            continue
        text, pages = extract_document(path)
        document_text[doc["documentKey"]] = text
        document_pages[doc["documentKey"]] = pages

    inactive_text: dict[str, str] = {}
    for doc in inactive_docs:
        path = ROOT / doc["path"]
        if not path.exists():
            fail(errors, f"Missing inactive fixture: {path}")
            continue
        inactive_text[doc["versionKey"]] = extract_document(path)[0]

    if len(corpus["users"]) != 4:
        fail(errors, f"Expected 4 users, found {len(corpus['users'])}")
    if len(active_docs) != 8:
        fail(errors, f"Expected 8 active documents, found {len(active_docs)}")
    format_counts = Counter(doc["format"] for doc in active_docs)
    if format_counts != Counter({"TXT": 4, "PDF": 4}):
        fail(errors, f"Expected TXT=4/PDF=4, found {dict(format_counts)}")
    if len(questions) != 48:
        fail(errors, f"Expected 48 questions, found {len(questions)}")
    if len(ground_truth) != 48:
        fail(errors, f"Expected 48 GT entries, found {len(ground_truth)}")

    ids = [item["id"] for item in questions]
    gt_ids = [item["id"] for item in ground_truth]
    if len(set(ids)) != len(ids):
        fail(errors, "Duplicate question IDs")
    if set(ids) != set(gt_ids):
        fail(errors, "Question/GT IDs differ")
    if len({normalized(item["query"]) for item in questions}) != len(questions):
        fail(errors, "Internal normalized duplicate query")

    verbatim_copies = []
    for item in questions:
        owner_text = "\n".join(
            text
            for key, text in document_text.items()
            if doc_by_key[key]["userKey"] == item["userKey"]
        )
        if normalized(item["query"]) in normalized(owner_text):
            verbatim_copies.append(item["id"])
    if verbatim_copies:
        fail(errors, f"Queries copied verbatim from owner documents: {verbatim_copies}")

    polarity_counts = Counter(item["polarity"] for item in questions)
    category_counts = Counter(item["category"] for item in questions)
    if polarity_counts != Counter({"POSITIVE": 36, "NEGATIVE": 12}):
        fail(errors, f"Polarity counts differ: {dict(polarity_counts)}")
    if category_counts != Counter(EXPECTED_CATEGORIES):
        fail(errors, f"Category counts differ: {dict(category_counts)}")
    per_user = defaultdict(Counter)
    for item in questions:
        per_user[item["userKey"]][item["category"]] += 1
    for user in [entry["userKey"] for entry in corpus["users"]]:
        if per_user[user] != Counter(EXPECTED_PER_USER):
            fail(errors, f"Per-user category counts differ for {user}: {dict(per_user[user])}")

    question_by_id = {item["id"]: item for item in questions}
    for entry in ground_truth:
        question = question_by_id.get(entry["id"])
        if question is None:
            continue
        if question["userKey"] != entry["userKey"]:
            fail(errors, f"Owner mismatch: {entry['id']}")
        if entry["expectedLabel"] == "EVIDENCE":
            if question["polarity"] != "POSITIVE":
                fail(errors, f"Positive/GT mismatch: {entry['id']}")
            doc = doc_by_key.get(entry["documentKey"])
            if not doc:
                fail(errors, f"Unknown active document in GT: {entry['id']}")
                continue
            if doc["userKey"] != entry["userKey"] or not doc["active"]:
                fail(errors, f"Positive GT not owner-scoped ACTIVE: {entry['id']}")
            if doc["versionKey"] != entry["versionKey"]:
                fail(errors, f"Positive version mismatch: {entry['id']}")
            source = entry["source"]
            if source["kind"] == "PAGE":
                page_no = source["page"]
                pages = document_pages.get(entry["documentKey"], [])
                if page_no < 1 or page_no > len(pages):
                    fail(errors, f"Invalid page in GT: {entry['id']}")
                    continue
                haystack = pages[page_no - 1]
            else:
                haystack = document_text.get(entry["documentKey"], "")
            for anchor in entry["acceptableAnchors"]:
                if compact(anchor) not in compact(haystack):
                    fail(errors, f"Missing positive anchor {entry['id']}: {anchor}")
        else:
            if question["polarity"] != "NEGATIVE":
                fail(errors, f"Negative/GT mismatch: {entry['id']}")
            owner_active = "\n".join(
                document_text[key]
                for key in entry["reviewedActiveDocuments"]
                if key in document_text
            )
            for key in entry["reviewedActiveDocuments"]:
                doc = doc_by_key.get(key)
                if not doc or doc["userKey"] != entry["userKey"]:
                    fail(errors, f"Negative review scope is not owner-active: {entry['id']} / {key}")
            for forbidden in entry["forbiddenClaimAnchors"]:
                if compact(forbidden) in compact(owner_active):
                    fail(errors, f"Forbidden negative claim exists in ACTIVE owner corpus: {entry['id']} / {forbidden}")
            reject = entry["similarButReject"]
            anchor = reject["anchor"]
            if "inactiveVersionKey" in reject:
                haystack = inactive_text.get(reject["inactiveVersionKey"], "")
            elif "documentKey" in reject:
                haystack = document_text.get(reject["documentKey"], "")
            else:
                haystack = ""
            if compact(anchor) not in compact(haystack):
                fail(errors, f"Missing similar-but-reject anchor: {entry['id']} / {anchor}")

    p0_queries = [item["query"] for item in load_json(P0_PATH)["queries"]]
    p5_queries = [item["query"] for item in load_json(P5_PATH)["queries"]]
    old_queries = [("P0", query) for query in p0_queries] + [("P5", query) for query in p5_queries]
    new_queries = [(item["id"], item["query"]) for item in questions]
    old_raw = {query for _, query in old_queries}
    old_norm = {normalized(query) for _, query in old_queries}
    exact_duplicates = [(qid, query) for qid, query in new_queries if query in old_raw]
    normalized_duplicates = [(qid, query) for qid, query in new_queries if normalized(query) in old_norm]
    if exact_duplicates:
        fail(errors, f"Existing raw duplicates: {exact_duplicates}")
    if normalized_duplicates:
        fail(errors, f"Existing normalized duplicates: {normalized_duplicates}")

    all_pairs = []
    for qid, new_query in new_queries:
        for phase, old_query in old_queries:
            seq, dice, jac = similarity(new_query, old_query)
            all_pairs.append(
                {
                    "newId": qid,
                    "phase": phase,
                    "sequence": round(seq, 4),
                    "bigramDice": round(dice, 4),
                    "tokenJaccard": round(jac, 4),
                    "newQuery": new_query,
                    "oldQuery": old_query,
                }
            )
    all_pairs.sort(
        key=lambda item: (max(item["sequence"], item["bigramDice"]), item["tokenJaccard"]),
        reverse=True,
    )
    candidates = [
        item
        for item in all_pairs
        if item["sequence"] >= 0.62 or item["bigramDice"] >= 0.58 or item["tokenJaccard"] >= 0.46
    ]

    frozen_text = "\n".join(
        [item["query"] for item in questions]
        + list(document_text.values())
        + list(inactive_text.values())
    )
    legacy_hits = [identifier for identifier in LEGACY_FACT_IDENTIFIERS if identifier in frozen_text]
    if legacy_hits:
        fail(errors, f"Legacy project/fact reuse found: {legacy_hits}")

    return {
        "status": "PASS" if not errors else "FAIL",
        "searchExecuted": False,
        "users": len(corpus["users"]),
        "activeDocuments": len(active_docs),
        "formats": dict(sorted(format_counts.items())),
        "questions": len(questions),
        "polarity": dict(sorted(polarity_counts.items())),
        "categories": dict(sorted(category_counts.items())),
        "groundTruthEntries": len(ground_truth),
        "positiveAnchorsVerified": sum(len(item.get("acceptableAnchors", [])) for item in ground_truth),
        "negativeAbsenceEntriesVerified": sum(item["expectedLabel"] == "NONE" for item in ground_truth),
        "p0QueriesCompared": len(p0_queries),
        "p5QueriesCompared": len(p5_queries),
        "exactDuplicates": len(exact_duplicates),
        "normalizedDuplicates": len(normalized_duplicates),
        "verbatimDocumentQueryCopies": verbatim_copies,
        "nearDuplicateReviewCandidates": candidates,
        "highestSimilarityPairsForManualReview": all_pairs[:12],
        "legacyFactIdentifierHits": legacy_hits,
        "errors": errors,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate P7-A frozen inputs without running PRIZM search")
    parser.parse_args()
    result = validate()
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if result["status"] == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
