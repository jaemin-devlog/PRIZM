package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prizm.document.entity.DocumentFileType;
import com.prizm.document.entity.DocumentType;
import com.prizm.ingestion.service.PageText;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class Prz044DocumentTypeMappingTest {

    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath().normalize();
    private final Prz044DocumentTypeMapping mapping = new Prz044DocumentTypeMapping();

    @Test
    void verifiesExplicitContractAndMapsEveryReleaseType() {
        var verified = mapping.verifyContract(PROJECT_ROOT);

        assertThat(verified.sha256()).matches("[0-9a-f]{64}");
        assertThat(verified.mappings()).containsExactlyInAnyOrderEntriesOf(java.util.Map.of(
                "CAREER_DESCRIPTION", DocumentType.RESUME,
                "PORTFOLIO", DocumentType.PORTFOLIO,
                "RESUME", DocumentType.RESUME));
        assertThat(mapping.map("CAREER_DESCRIPTION")).isEqualTo(DocumentType.RESUME);
        assertThat(mapping.map("PORTFOLIO")).isEqualTo(DocumentType.PORTFOLIO);
        assertThat(mapping.map("RESUME")).isEqualTo(DocumentType.RESUME);
    }

    @Test
    void rejectsUnknownWithoutFallback() {
        assertThatThrownBy(() -> mapping.map("UNKNOWN"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown PRZ-044 source document type");
    }

    @Test
    void auditsOneUnambiguousMappingPerDocument() {
        var audit = mapping.audit(List.of(
                document("D1", "CAREER_DESCRIPTION"),
                document("D2", "PORTFOLIO"),
                document("D3", "RESUME")));

        assertThat(audit.documentCount()).isEqualTo(3);
        assertThat(audit.mappedCount()).isEqualTo(3);
        assertThat(audit.unmappedCount()).isZero();
        assertThat(audit.ambiguousCount()).isZero();
    }

    @Test
    void verifiesFrozenAttempt3ExecutionContract() {
        var contract = new Prz044PredictionFreeze().verifyContract(PROJECT_ROOT);

        assertThat(contract.attempt()).isEqualTo(3);
        assertThat(contract.attemptIdentity()).isEqualTo(Prz044PredictionFreeze.ATTEMPT_IDENTITY);
        assertThat(contract.mappingContractSha256()).isEqualTo(mapping.verifyContract(PROJECT_ROOT).sha256());
        assertThat(contract.officialRunsAllowed()).isEqualTo(1);
    }

    private static Prz044PredictionDataset.RuntimeDocument document(String id, String sourceType) {
        byte[] bytes = id.getBytes(StandardCharsets.UTF_8);
        return new Prz044PredictionDataset.RuntimeDocument(
                "U1", "P1", "Profession", id, id + "-V1", sourceType,
                DocumentFileType.TXT, id + ".txt", "corpus/" + id + ".txt",
                Prz044PredictionFreeze.sha256(bytes), bytes, List.of(new PageText(1, id)), List.of());
    }
}
