import importlib.util
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("generate_scores.py")
SPEC = importlib.util.spec_from_file_location("prz027_scores", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class GenerateScoresTest(unittest.TestCase):
    def test_runtime_isolation_blocks_only_torchvision_discovery(self):
        import importlib.util

        original = importlib.util.find_spec
        try:
            MODULE.isolate_evaluation_runtime()
            self.assertIsNone(importlib.util.find_spec("torchvision"))
            self.assertIsNone(importlib.util.find_spec("torchvision.transforms"))
            self.assertIsNotNone(importlib.util.find_spec("json"))
        finally:
            importlib.util.find_spec = original

    def prepared(self):
        query = "직접 근거"
        source = "완료한 직접 행동"
        pair = {
            "pairId": "PAIR",
            "denseRank": 1,
            "candidateId": "C1",
            "querySha256": MODULE.sha256_text(query),
            "sourceSha256": MODULE.sha256_text(source),
            "provenanceSha256": "P",
            "documentId": "D",
            "versionId": "V",
            "query": query,
            "sourceText": source,
        }
        digest = MODULE.sha256_text(":".join([
            "DATA", "DEV", "Q1", "PAIR", "1", "C1",
            pair["querySha256"], pair["sourceSha256"], "P",
        ]))
        return {
            "schemaVersion": 1,
            "profile": MODULE.PROFILE,
            "topK": 20,
            "maxLength": 512,
            "batchSize": 8,
            "cpuThreads": 8,
            "model": MODULE.MODEL,
            "modelRevision": MODULE.MODEL_REVISION,
            "codeRepository": MODULE.CODE_REPOSITORY,
            "codeRevision": MODULE.CODE_REVISION,
            "license": MODULE.LICENSE,
            "transformersVersion": MODULE.TRANSFORMERS_VERSION,
            "pairPolicy": "ORIGINAL_QUERY_AND_B3_SOURCE_TEXT_NO_INSTRUCTION",
            "goldPolicy": "GOLD_NOT_PRESENT",
            "inputDigest": digest,
            "datasets": [{
                "label": "ORIGINAL",
                "datasetVersion": "DATA",
                "splitManifestHashes": {"DEV": "H"},
                "questions": [{
                    "questionId": "Q1",
                    "split": "DEV",
                    "querySha256": pair["querySha256"],
                    "query": query,
                    "fullCandidateCount": 1,
                    "pairCount": 1,
                    "pairs": [pair],
                }],
            }],
        }

    def test_accepts_gold_free_top20_input(self):
        MODULE.validate_input(self.prepared())

    def test_rejects_gold_or_evaluation_fields(self):
        prepared = self.prepared()
        prepared["datasets"][0]["questions"][0]["expectedEvidence"] = []
        with self.assertRaisesRegex(ValueError, "forbidden"):
            MODULE.validate_input(prepared)

    def test_rejects_duplicate_pair(self):
        prepared = self.prepared()
        question = prepared["datasets"][0]["questions"][0]
        question["fullCandidateCount"] = 2
        question["pairCount"] = 2
        question["pairs"] = [question["pairs"][0], question["pairs"][0]]
        with self.assertRaisesRegex(ValueError, "Duplicate pair"):
            MODULE.validate_input(prepared)

    def test_ties_use_dense_rank_then_candidate_id(self):
        pairs = [
            {"pairId": "B", "candidateId": "C2", "denseRank": 2,
             "querySha256": "Q", "sourceSha256": "S"},
            {"pairId": "A", "candidateId": "C1", "denseRank": 1,
             "querySha256": "Q", "sourceSha256": "S"},
        ]
        ranked = MODULE.deterministic_ranking(pairs, [0.5, 0.5])
        self.assertEqual([item["candidateId"] for item in ranked], ["C1", "C2"])
        self.assertEqual([item["rerankerRank"] for item in ranked], [1, 2])


if __name__ == "__main__":
    unittest.main()
