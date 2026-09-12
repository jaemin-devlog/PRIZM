package com.prizm.search.evaluation.searchv3.structural;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Evaluation-only canonical freeze for source-grounded B3 candidate rankings.
 *
 * <p>The records intentionally cannot carry answerability, query categories, support relations,
 * Gold IDs, or runtime database IDs. A benchmark adapter must finish and verify this freeze before
 * opening evaluation Gold through {@link PhaseGuard}.
 */
final class SearchV3CandidateFreeze {

    static final int SCHEMA_VERSION = 1;
    static final int MAX_CANDIDATES_PER_QUERY = 20;
    private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");

    private SearchV3CandidateFreeze() {
    }

    static FrozenCandidates freeze(FreezeInput input) {
        FreezeInput normalized = validateAndNormalize(input);
        byte[] canonical = canonicalBytes(normalized);
        return new FrozenCandidates(normalized, sha256(canonical), canonical.length);
    }

    static VerifiedCandidates verify(FrozenCandidates frozen) {
        Objects.requireNonNull(frozen, "frozen");
        FreezeInput normalized = validateAndNormalize(frozen.input());
        byte[] canonical = canonicalBytes(normalized);
        String actual = sha256(canonical);
        if (!actual.equals(frozen.canonicalSha256()) || canonical.length != frozen.canonicalByteLength()) {
            throw new IllegalStateException("candidate freeze hash or canonical length mismatch");
        }
        return new VerifiedCandidates(new FrozenCandidates(normalized, actual, canonical.length));
    }

    private static FreezeInput validateAndNormalize(FreezeInput input) {
        Objects.requireNonNull(input, "input");
        requireNonBlank(input.suite(), "suite");
        requireNonBlank(input.datasetVersion(), "datasetVersion");
        requireSha(input.sourceArtifactSha256(), "sourceArtifactSha256");
        Objects.requireNonNull(input.track(), "track");
        if (input.schemaVersion() != SCHEMA_VERSION || input.queries().isEmpty()) {
            throw new IllegalArgumentException("candidate freeze schema/query inventory is invalid");
        }

        List<QueryProjection> queries = new ArrayList<>(input.queries());
        for (QueryProjection query : queries) {
            Objects.requireNonNull(query, "query");
            requireNonBlank(query.split(), "split");
            requireNonBlank(query.queryId(), "queryId");
        }
        queries.sort(Comparator.comparingInt((QueryProjection value) -> splitOrder(value.split()))
                .thenComparing(QueryProjection::split)
                .thenComparing(QueryProjection::queryId));
        Set<String> queryIds = new HashSet<>();
        Map<String, CandidateSourceIdentity> candidateIdentity = new HashMap<>();
        Map<String, DocumentVersionIdentity> versionIdentity = new HashMap<>();
        Map<String, EvidenceChildIdentity> childIdentity = new HashMap<>();
        for (QueryProjection query : queries) {
            validateQuery(query, input.track());
            if (!queryIds.add(query.queryId())) {
                throw new IllegalArgumentException("duplicate queryId: " + query.queryId());
            }
            for (CandidateProjection candidate : query.rankedCandidates()) {
                CandidateSourceIdentity sourceIdentity = CandidateSourceIdentity.from(candidate);
                CandidateSourceIdentity previous = candidateIdentity.putIfAbsent(
                        candidate.candidateId(), sourceIdentity);
                if (previous != null && !previous.equals(sourceIdentity)) {
                    throw new IllegalArgumentException(
                            "candidate source identity changed across queries: " + candidate.candidateId());
                }
                DocumentVersionIdentity documentVersion = new DocumentVersionIdentity(
                        candidate.userBundleId(), candidate.documentId());
                DocumentVersionIdentity previousVersion = versionIdentity.putIfAbsent(
                        candidate.versionId(), documentVersion);
                if (previousVersion != null && !previousVersion.equals(documentVersion)) {
                    throw new IllegalArgumentException(
                            "document version crosses owner/document identity: " + candidate.versionId());
                }
                for (EvidenceChildProjection child : candidate.evidenceChildren()) {
                    EvidenceChildIdentity identity = EvidenceChildIdentity.from(candidate.candidateId(), child);
                    EvidenceChildIdentity previousChild = childIdentity.putIfAbsent(
                            child.evidenceChildId(), identity);
                    if (previousChild != null && !previousChild.equals(identity)) {
                        throw new IllegalArgumentException(
                                "EvidenceChild source identity changed across queries: "
                                        + child.evidenceChildId());
                    }
                }
            }
        }
        return new FreezeInput(
                input.schemaVersion(),
                input.suite(),
                input.datasetVersion(),
                input.sourceArtifactSha256(),
                input.track(),
                List.copyOf(queries));
    }

    private static void validateQuery(QueryProjection query, EvaluationTrack expectedTrack) {
        Objects.requireNonNull(query, "query");
        requireNonBlank(query.queryId(), "queryId");
        requireNonBlank(query.userBundleId(), "query userBundleId");
        requireNonBlank(query.split(), "split");
        if (query.track() != expectedTrack || query.rankedCandidates().isEmpty()
                || query.rankedCandidates().size() > MAX_CANDIDATES_PER_QUERY) {
            throw new IllegalArgumentException("query track/candidate inventory is invalid: " + query.queryId());
        }
        Set<String> candidateIds = new LinkedHashSet<>();
        double previousCosine = Double.POSITIVE_INFINITY;
        String previousCandidateId = null;
        for (int index = 0; index < query.rankedCandidates().size(); index++) {
            CandidateProjection candidate = query.rankedCandidates().get(index);
            validateCandidate(candidate);
            if (candidate.rank() != index + 1) {
                throw new IllegalArgumentException("candidate rank/order mismatch: " + query.queryId());
            }
            if (!candidate.userBundleId().equals(query.userBundleId())) {
                throw new IllegalArgumentException("candidate crosses query owner: " + candidate.candidateId());
            }
            if (!candidateIds.add(candidate.candidateId())) {
                throw new IllegalArgumentException("duplicate candidate in query: " + candidate.candidateId());
            }
            if (Double.compare(candidate.cosineScore(), previousCosine) > 0
                    || (Double.compare(candidate.cosineScore(), previousCosine) == 0
                            && previousCandidateId != null
                            && candidate.candidateId().compareTo(previousCandidateId) < 0)) {
                throw new IllegalArgumentException(
                        "candidate ranking must be cosine-descending with candidate-ID tie-break: "
                                + query.queryId());
            }
            previousCosine = candidate.cosineScore();
            previousCandidateId = candidate.candidateId();
        }
    }

    private static void validateCandidate(CandidateProjection candidate) {
        Objects.requireNonNull(candidate, "candidate");
        requireNonBlank(candidate.candidateId(), "candidateId");
        requireNonBlank(candidate.userBundleId(), "candidate userBundleId");
        requireNonBlank(candidate.documentId(), "documentId");
        requireNonBlank(candidate.versionId(), "versionId");
        requireNonBlank(candidate.parentAnnotationCandidateId(), "parentAnnotationCandidateId");
        requireNonBlank(candidate.sourceText(), "candidate sourceText");
        requireNonBlank(candidate.retrievalText(), "candidate retrievalText");
        requireSha(candidate.sourceTextSha256(), "candidate sourceTextSha256");
        requireSha(candidate.retrievalTextSha256(), "candidate retrievalTextSha256");
        if (candidate.rank() < 1 || !Double.isFinite(candidate.cosineScore())
                || candidate.cosineScore() < -1.0d || candidate.cosineScore() > 1.0d) {
            throw new IllegalArgumentException("candidate rank/cosine is invalid: " + candidate.candidateId());
        }
        if (!candidate.sourceTextSha256().equals(sha256(candidate.sourceText()))
                || !candidate.retrievalTextSha256().equals(sha256(candidate.retrievalText()))) {
            throw new IllegalArgumentException("candidate source/retrieval hash mismatch: "
                    + candidate.candidateId());
        }
        if (candidate.evidenceChildren().isEmpty()) {
            throw new IllegalArgumentException("candidate requires ordered EvidenceChildren");
        }
        Set<String> childIds = new LinkedHashSet<>();
        Integer candidatePage = null;
        int previousEnd = -1;
        boolean firstChild = true;
        for (EvidenceChildProjection child : candidate.evidenceChildren()) {
            validateChild(candidate, child);
            if (!childIds.add(child.evidenceChildId())) {
                throw new IllegalArgumentException("duplicate EvidenceChild: " + child.evidenceChildId());
            }
            if (!firstChild
                    && (!Objects.equals(candidatePage, child.page())
                            || previousEnd > child.codePointStart())) {
                throw new IllegalArgumentException(
                        "EvidenceChildren must preserve one-page source order: " + candidate.candidateId());
            }
            candidatePage = child.page();
            previousEnd = child.codePointEnd();
            firstChild = false;
        }
    }

    private static void validateChild(CandidateProjection candidate, EvidenceChildProjection child) {
        Objects.requireNonNull(child, "evidenceChild");
        requireNonBlank(child.evidenceChildId(), "evidenceChildId");
        requireNonBlank(child.documentId(), "EvidenceChild documentId");
        requireNonBlank(child.versionId(), "EvidenceChild versionId");
        requireNonBlank(child.sourceText(), "EvidenceChild sourceText");
        requireSha(child.sourceTextSha256(), "EvidenceChild sourceTextSha256");
        int length = child.sourceText().codePointCount(0, child.sourceText().length());
        if (!candidate.documentId().equals(child.documentId())
                || !candidate.versionId().equals(child.versionId())
                || (child.page() != null && child.page() < 1)
                || child.codePointStart() < 0 || child.codePointEnd() <= child.codePointStart()
                || child.codePointEnd() - child.codePointStart() != length
                || !child.sourceTextSha256().equals(sha256(child.sourceText()))) {
            throw new IllegalArgumentException(
                    "EvidenceChild provenance/hash mismatch: " + child.evidenceChildId());
        }
    }

    private static byte[] canonicalBytes(FreezeInput input) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                writeString(output, "PRIZM_SEARCH_V3_CANDIDATE_FREEZE");
                output.writeInt(input.schemaVersion());
                writeString(output, input.suite());
                writeString(output, input.datasetVersion());
                writeString(output, input.sourceArtifactSha256());
                writeString(output, input.track().name());
                output.writeInt(input.queries().size());
                for (QueryProjection query : input.queries()) {
                    writeString(output, query.queryId());
                    writeString(output, query.userBundleId());
                    writeString(output, query.split());
                    writeString(output, query.track().name());
                    output.writeInt(query.rankedCandidates().size());
                    for (CandidateProjection candidate : query.rankedCandidates()) {
                        output.writeInt(candidate.rank());
                        writeString(output, candidate.candidateId());
                        output.writeLong(Double.doubleToLongBits(candidate.cosineScore()));
                        writeString(output, candidate.userBundleId());
                        writeString(output, candidate.documentId());
                        writeString(output, candidate.versionId());
                        writeString(output, candidate.parentAnnotationCandidateId());
                        writeString(output, candidate.sourceText());
                        writeString(output, candidate.retrievalText());
                        writeString(output, candidate.sourceTextSha256());
                        writeString(output, candidate.retrievalTextSha256());
                        output.writeInt(candidate.evidenceChildren().size());
                        for (EvidenceChildProjection child : candidate.evidenceChildren()) {
                            writeString(output, child.evidenceChildId());
                            writeString(output, child.documentId());
                            writeString(output, child.versionId());
                            output.writeBoolean(child.page() != null);
                            if (child.page() != null) {
                                output.writeInt(child.page());
                            }
                            output.writeInt(child.codePointStart());
                            output.writeInt(child.codePointEnd());
                            writeString(output, child.sourceText());
                            writeString(output, child.sourceTextSha256());
                        }
                    }
                }
            }
            return bytes.toByteArray();
        }
        catch (IOException exception) {
            throw new IllegalStateException("cannot canonicalize in-memory candidate freeze", exception);
        }
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must be non-blank");
        }
    }

    private static void requireSha(String value, String label) {
        if (value == null || !SHA256.matcher(value).matches()) {
            throw new IllegalArgumentException(label + " must be a lowercase SHA-256");
        }
    }

    private static int splitOrder(String split) {
        return switch (split) {
            case "DEV" -> 0;
            case "CALIBRATION" -> 1;
            default -> 2;
        };
    }

    enum EvaluationTrack {
        SEMANTIC,
        TYPED
    }

    enum Phase {
        SOURCE_ONLY,
        FROZEN,
        VERIFIED,
        GOLD_JOINED
    }

    record EvidenceChildProjection(
            String evidenceChildId,
            String documentId,
            String versionId,
            Integer page,
            int codePointStart,
            int codePointEnd,
            String sourceText,
            String sourceTextSha256) {
    }

    record CandidateProjection(
            int rank,
            String candidateId,
            double cosineScore,
            String userBundleId,
            String documentId,
            String versionId,
            String parentAnnotationCandidateId,
            String sourceText,
            String retrievalText,
            String sourceTextSha256,
            String retrievalTextSha256,
            List<EvidenceChildProjection> evidenceChildren) {

        CandidateProjection {
            evidenceChildren = evidenceChildren == null ? List.of() : List.copyOf(evidenceChildren);
        }
    }

    record QueryProjection(
            String queryId,
            String userBundleId,
            String split,
            EvaluationTrack track,
            List<CandidateProjection> rankedCandidates) {

        QueryProjection {
            rankedCandidates = rankedCandidates == null ? List.of() : List.copyOf(rankedCandidates);
        }
    }

    record FreezeInput(
            int schemaVersion,
            String suite,
            String datasetVersion,
            String sourceArtifactSha256,
            EvaluationTrack track,
            List<QueryProjection> queries) {

        FreezeInput {
            queries = queries == null ? List.of() : List.copyOf(queries);
        }
    }

    record FrozenCandidates(FreezeInput input, String canonicalSha256, int canonicalByteLength) {

        FrozenCandidates {
            Objects.requireNonNull(input, "input");
            requireSha(canonicalSha256, "canonicalSha256");
            if (canonicalByteLength <= 0) {
                throw new IllegalArgumentException("canonicalByteLength must be positive");
            }
        }
    }

    static final class VerifiedCandidates {

        private final FrozenCandidates frozen;

        private VerifiedCandidates(FrozenCandidates frozen) {
            this.frozen = Objects.requireNonNull(frozen, "frozen");
        }

        FrozenCandidates frozen() {
            return frozen;
        }
    }

    static final class GoldJoined<T> {

        private final VerifiedCandidates verified;
        private final T gold;

        private GoldJoined(VerifiedCandidates verified, T gold) {
            this.verified = Objects.requireNonNull(verified, "verified");
            this.gold = Objects.requireNonNull(gold, "gold");
        }

        VerifiedCandidates verified() {
            return verified;
        }

        T gold() {
            return gold;
        }
    }

    /** Stateful fail-closed transition guard for benchmark runners. */
    static final class PhaseGuard {

        private Phase phase = Phase.SOURCE_ONLY;
        private FrozenCandidates frozen;
        private VerifiedCandidates verified;

        Phase phase() {
            return phase;
        }

        FrozenCandidates freezeCandidates(FreezeInput input) {
            requirePhase(Phase.SOURCE_ONLY, "freeze");
            frozen = SearchV3CandidateFreeze.freeze(input);
            phase = Phase.FROZEN;
            return frozen;
        }

        VerifiedCandidates verifyFreeze() {
            requirePhase(Phase.FROZEN, "verify");
            verified = SearchV3CandidateFreeze.verify(frozen);
            phase = Phase.VERIFIED;
            return verified;
        }

        <T> GoldJoined<T> joinGold(Supplier<T> supplier) {
            Objects.requireNonNull(supplier, "supplier");
            requirePhase(Phase.VERIFIED, "Gold join");
            T gold = Objects.requireNonNull(supplier.get(), "Gold supplier returned null");
            GoldJoined<T> joined = new GoldJoined<>(verified, gold);
            phase = Phase.GOLD_JOINED;
            return joined;
        }

        private void requirePhase(Phase expected, String operation) {
            if (phase != expected) {
                throw new IllegalStateException(operation + " requires phase " + expected + ", actual " + phase);
            }
        }
    }

    private record CandidateSourceIdentity(
            String userBundleId,
            String documentId,
            String versionId,
            String parentAnnotationCandidateId,
            String sourceTextSha256,
            String retrievalTextSha256,
            List<String> evidenceChildIds) {

        static CandidateSourceIdentity from(CandidateProjection candidate) {
            return new CandidateSourceIdentity(
                    candidate.userBundleId(),
                    candidate.documentId(),
                    candidate.versionId(),
                    candidate.parentAnnotationCandidateId(),
                    candidate.sourceTextSha256(),
                    candidate.retrievalTextSha256(),
                    candidate.evidenceChildren().stream()
                            .map(EvidenceChildProjection::evidenceChildId)
                            .toList());
        }
    }

    private record DocumentVersionIdentity(String userBundleId, String documentId) {
    }

    private record EvidenceChildIdentity(
            String candidateId,
            String documentId,
            String versionId,
            Integer page,
            int start,
            int end,
            String sourceTextSha256) {

        static EvidenceChildIdentity from(String candidateId, EvidenceChildProjection child) {
            return new EvidenceChildIdentity(
                    candidateId,
                    child.documentId(),
                    child.versionId(),
                    child.page(),
                    child.codePointStart(),
                    child.codePointEnd(),
                    child.sourceTextSha256());
        }
    }
}
