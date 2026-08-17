package com.prizm.search.evaluation.judge;

import java.util.Optional;

public interface EvidenceJudgeVerificationRepository {

    Optional<StoredChunk> findActiveOwnedChunk(long ownerUserId, long chunkId);

    record StoredChunk(long chunkId, String content) {
    }
}
