from __future__ import annotations

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
PRZ = REPO / "specs" / "PRZ-016-search-performance-v2"
P0_PATH = PRZ / "p0-benchmark" / "evaluation-dataset.json"
P5_PATH = PRZ / "p5-final-holdout" / "holdout-dataset.json"
V1_ROOT = PRZ / "p7-cross-document-generalization"
V1_QUERY_PATH = V1_ROOT / "dataset" / "questions.json"
V1_FREEZE_PATH = V1_ROOT / "freeze-manifest.json"
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
FORBIDDEN_PRIOR_IDENTIFIERS = [
    "AirConnect", "MoneyWay", "TourAPI",
    "FrostLine Dispatch", "GeneTrail QC", "HarborMesh Control", "ArchiveLens",
    "2,329행", "19분 22초", "1,252건", "1,480건", "1,654건", "11.376초",
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
    ln, rn = normalized(left), normalized(right)
    sequence = SequenceMatcher(None, ln, rn).ratio()
    lb = Counter(ln[index:index + 2] for index in range(max(0, len(ln) - 1)))
    rb = Counter(rn[index:index + 2] for index in range(max(0, len(rn) - 1)))
    overlap = sum((lb & rb).values())
    dice = 2 * overlap / (sum(lb.values()) + sum(rb.values())) if lb or rb else 0.0
    lt, rt = tokens(left), tokens(right)
    jaccard = len(lt & rt) / len(lt | rt) if lt | rt else 0.0
    return sequence, dice, jaccard


def extract_document(path: Path) -> tuple[str, list[str]]:
    if path.suffix.lower() == ".txt":
        text = path.read_text(encoding="utf-8")
        return text, [text]
    with pdfplumber.open(path) as pdf:
        pages = [page.extract_text() or "" for page in pdf.pages]
    return "\n".join(pages), pages


def check_v1_freeze(errors: list[str]) -> dict:
    manifest = load_json(V1_FREEZE_PATH)
    listed = manifest["activeDocumentHashes"] + manifest["otherFrozenAssetHashes"]
    mismatches = []
    for asset in listed:
        path = V1_ROOT / asset["path"]
        if not path.exists() or hashlib.sha256(path.read_bytes()).hexdigest() != asset["sha256"]:
            mismatches.append(asset["path"])
    manifest_hash = hashlib.sha256(V1_FREEZE_PATH.read_bytes()).hexdigest()
    expected_manifest_hash = "0b46f12562050c58c6d7ccefe940378a5c42550192d0f35dffc7e2599eae3b79"
    if manifest_hash != expected_manifest_hash or mismatches:
        errors.append(f"P7-A v1 freeze changed: manifest={manifest_hash}, mismatches={mismatches}")
    return {"manifestSha256": manifest_hash, "assetCount": len(listed), "hashMismatches": mismatches}


def validate() -> dict:
    errors: list[str] = []
    v1_freeze = check_v1_freeze(errors)
    corpus = load_json(CORPUS_PATH)
    questions = load_json(QUESTION_PATH)["questions"]
    ground_truth = load_json(GT_PATH)["entries"]
    active_docs = corpus["activeDocuments"]
    inactive_docs = corpus["inactiveVersionFixtures"]
    doc_by_key = {doc["documentKey"]: doc for doc in active_docs}

    document_text: dict[str, str] = {}
    document_pages: dict[str, list[str]] = {}
    page_density: dict[str, list[int]] = {}
    txt_bytes: dict[str, int] = {}
    for doc in active_docs:
        path = ROOT / doc["path"]
        if not path.exists():
            errors.append(f"Missing active document: {path}")
            continue
        text, pages = extract_document(path)
        document_text[doc["documentKey"]] = text
        document_pages[doc["documentKey"]] = pages
        if doc["format"] == "PDF":
            if len(pages) != doc["expectedPages"]:
                errors.append(f"PDF page count mismatch: {doc['documentKey']}={len(pages)}")
            page_density[doc["documentKey"]] = [len(compact(page)) for page in pages]
            if any(count < 700 for count in page_density[doc["documentKey"]]):
                errors.append(f"Sparse PDF page: {doc['documentKey']}={page_density[doc['documentKey']]}")
        else:
            txt_bytes[doc["documentKey"]] = path.stat().st_size
            if path.stat().st_size < 3000:
                errors.append(f"TXT portfolio too short: {doc['documentKey']}={path.stat().st_size}")

    inactive_text = {}
    for doc in inactive_docs:
        path = ROOT / doc["path"]
        if not path.exists():
            errors.append(f"Missing inactive fixture: {path}")
            continue
        inactive_text[doc["versionKey"]] = extract_document(path)[0]

    users = [item["userKey"] for item in corpus["users"]]
    format_counts = Counter(doc["format"] for doc in active_docs)
    if len(users) != 4 or len(active_docs) != 8 or format_counts != Counter({"PDF": 4, "TXT": 4}):
        errors.append(f"Corpus count mismatch: users={len(users)} docs={len(active_docs)} formats={dict(format_counts)}")
    if len(questions) != 48 or len(ground_truth) != 48:
        errors.append(f"Question/GT count mismatch: questions={len(questions)} gt={len(ground_truth)}")

    ids = [item["id"] for item in questions]
    gt_ids = [item["id"] for item in ground_truth]
    if len(set(ids)) != 48 or set(ids) != set(gt_ids):
        errors.append("Question/GT IDs are not one-to-one")
    if len({normalized(item["query"]) for item in questions}) != 48:
        errors.append("Internal normalized duplicate query")

    polarity_counts = Counter(item["polarity"] for item in questions)
    category_counts = Counter(item["category"] for item in questions)
    if polarity_counts != Counter({"POSITIVE": 36, "NEGATIVE": 12}):
        errors.append(f"Polarity mismatch: {dict(polarity_counts)}")
    if category_counts != Counter(EXPECTED_CATEGORIES):
        errors.append(f"Category mismatch: {dict(category_counts)}")
    per_user = defaultdict(Counter)
    for item in questions:
        per_user[item["userKey"]][item["category"]] += 1
    for user in users:
        if per_user[user] != Counter(EXPECTED_PER_USER):
            errors.append(f"Per-user category mismatch: {user}={dict(per_user[user])}")

    verbatim_copies = []
    for item in questions:
        owner_text = "\n".join(text for key, text in document_text.items() if doc_by_key[key]["userKey"] == item["userKey"])
        if normalized(item["query"]) in normalized(owner_text):
            verbatim_copies.append(item["id"])
    if verbatim_copies:
        errors.append(f"Verbatim document query copies: {verbatim_copies}")

    question_by_id = {item["id"]: item for item in questions}
    positive_anchor_count = 0
    negative_absence_count = 0
    for entry in ground_truth:
        question = question_by_id.get(entry["id"])
        if not question or question["userKey"] != entry["userKey"]:
            errors.append(f"Question/GT owner mismatch: {entry['id']}")
            continue
        if entry["expectedLabel"] == "EVIDENCE":
            doc = doc_by_key.get(entry["documentKey"])
            if not doc or not doc["active"] or doc["userKey"] != entry["userKey"] or doc["versionKey"] != entry["versionKey"]:
                errors.append(f"Positive GT is not owner ACTIVE: {entry['id']}")
                continue
            source = entry["source"]
            if source["kind"] == "PAGE":
                pages = document_pages.get(entry["documentKey"], [])
                page = source["page"]
                if page < 1 or page > len(pages):
                    errors.append(f"Invalid positive page: {entry['id']}")
                    continue
                haystack = pages[page - 1]
            else:
                haystack = document_text.get(entry["documentKey"], "")
            for anchor in entry["acceptableAnchors"]:
                positive_anchor_count += 1
                if compact(anchor) not in compact(haystack):
                    errors.append(f"Missing positive anchor {entry['id']}: {anchor}")
        else:
            negative_absence_count += 1
            owner_active = "\n".join(document_text[key] for key in entry["reviewedActiveDocuments"] if key in document_text)
            for key in entry["reviewedActiveDocuments"]:
                doc = doc_by_key.get(key)
                if not doc or doc["userKey"] != entry["userKey"] or not doc["active"]:
                    errors.append(f"Negative review scope is not owner ACTIVE: {entry['id']} / {key}")
            for forbidden in entry["forbiddenClaimAnchors"]:
                if compact(forbidden) in compact(owner_active):
                    errors.append(f"Forbidden claim exists in owner ACTIVE corpus: {entry['id']} / {forbidden}")
            reject = entry["similarButReject"]
            if "inactiveVersionKey" in reject:
                reject_text = inactive_text.get(reject["inactiveVersionKey"], "")
            else:
                reject_text = document_text.get(reject.get("documentKey", ""), "")
            if compact(reject["anchor"]) not in compact(reject_text):
                errors.append(f"Missing similar-but-reject anchor: {entry['id']} / {reject['anchor']}")

    p0_queries = [item["query"] for item in load_json(P0_PATH)["queries"]]
    p5_queries = [item["query"] for item in load_json(P5_PATH)["queries"]]
    v1_queries = [item["query"] for item in load_json(V1_QUERY_PATH)["questions"]]
    sources = {"P0": p0_queries, "P5": p5_queries, "P7_V1": v1_queries}
    duplicate_counts = {}
    normalized_duplicate_counts = {}
    all_pairs = []
    for phase, old_queries in sources.items():
        old_raw = set(old_queries)
        old_norm = {normalized(query) for query in old_queries}
        duplicate_counts[phase] = sum(item["query"] in old_raw for item in questions)
        normalized_duplicate_counts[phase] = sum(normalized(item["query"]) in old_norm for item in questions)
        for item in questions:
            for old_query in old_queries:
                seq, dice, jac = similarity(item["query"], old_query)
                all_pairs.append({
                    "newId": item["id"], "phase": phase,
                    "sequence": round(seq, 4), "bigramDice": round(dice, 4), "tokenJaccard": round(jac, 4),
                    "newQuery": item["query"], "oldQuery": old_query,
                })
    if any(duplicate_counts.values()) or any(normalized_duplicate_counts.values()):
        errors.append(f"Prior duplicates: raw={duplicate_counts} normalized={normalized_duplicate_counts}")
    all_pairs.sort(key=lambda item: (max(item["sequence"], item["bigramDice"]), item["tokenJaccard"]), reverse=True)
    near_candidates = [item for item in all_pairs if item["sequence"] >= 0.62 or item["bigramDice"] >= 0.58 or item["tokenJaccard"] >= 0.46]

    frozen_text = "\n".join([item["query"] for item in questions] + list(document_text.values()) + list(inactive_text.values()))
    prior_identifier_hits = [value for value in FORBIDDEN_PRIOR_IDENTIFIERS if value in frozen_text]
    if prior_identifier_hits:
        errors.append(f"Prior project/fact identifiers reused: {prior_identifier_hits}")

    return {
        "status": "PASS" if not errors else "FAIL",
        "searchExecuted": False,
        "v1Freeze": v1_freeze,
        "users": len(users),
        "activeDocuments": len(active_docs),
        "formats": dict(sorted(format_counts.items())),
        "pdfPageCounts": {key: len(value) for key, value in document_pages.items() if doc_by_key[key]["format"] == "PDF"},
        "pdfPageTextDensity": page_density,
        "txtPortfolioBytes": txt_bytes,
        "questions": len(questions),
        "polarity": dict(sorted(polarity_counts.items())),
        "categories": dict(sorted(category_counts.items())),
        "positiveAnchorsVerified": positive_anchor_count,
        "negativeAbsenceEntriesVerified": negative_absence_count,
        "priorQueriesCompared": {key: len(value) for key, value in sources.items()},
        "exactDuplicates": duplicate_counts,
        "normalizedDuplicates": normalized_duplicate_counts,
        "verbatimDocumentQueryCopies": verbatim_copies,
        "nearDuplicateReviewCandidates": near_candidates,
        "highestSimilarityPairsForManualReview": all_pairs[:16],
        "priorProjectFactIdentifierHits": prior_identifier_hits,
        "errors": errors,
    }


if __name__ == "__main__":
    result = validate()
    print(json.dumps(result, ensure_ascii=False, indent=2))
    sys.exit(0 if result["status"] == "PASS" else 1)
