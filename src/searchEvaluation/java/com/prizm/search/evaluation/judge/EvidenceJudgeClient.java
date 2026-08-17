package com.prizm.search.evaluation.judge;

import java.util.List;

public interface EvidenceJudgeClient {

    EvidenceJudgeCall judge(String query, List<EvidenceJudgeCandidate> candidates);
}
