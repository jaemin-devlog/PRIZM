package com.prizm.search.v3.indexing.model;

/** 현재 claim이 읽거나 동결할 Search V3 generation build 계약이다. */
public record SearchV3GenerationBuildContract(
        long generationId,
        String status,
        String structurePolicyVersion,
        String passagePolicyVersion,
        String childPolicyVersion,
        String embeddingModelId,
        String resolvedModelDigest,
        int embeddingDimension,
        String passageInputPolicyVersion,
        String childInputPolicyVersion,
        Integer expectedPassageCount,
        Integer expectedChildCount,
        String expectedManifestSha256) {

    public boolean manifestFrozen() {
        return expectedPassageCount != null
                && expectedChildCount != null
                && expectedManifestSha256 != null;
    }

    public SearchV3EmbeddingModelContract embeddingContract() {
        return new SearchV3EmbeddingModelContract(
                embeddingModelId,
                resolvedModelDigest,
                embeddingDimension);
    }
}
