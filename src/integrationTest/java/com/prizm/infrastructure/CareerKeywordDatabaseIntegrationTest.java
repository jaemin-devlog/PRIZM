package com.prizm.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.careerkeyword.repository.CareerKeywordRepository;
import com.prizm.careerkeyword.repository.KeywordSourceChunk;
import com.prizm.careerkeyword.service.CareerKeywordService;
import com.prizm.document.entity.Document;
import com.prizm.document.entity.DocumentFileType;
import com.prizm.document.entity.DocumentType;
import com.prizm.document.entity.DocumentVersion;
import com.prizm.document.repository.DocumentRepository;
import com.prizm.document.repository.DocumentVersionRepository;
import com.prizm.user.entity.UserAccount;
import com.prizm.user.entity.UserRole;
import com.prizm.user.repository.UserAccountRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@ActiveProfiles("integration-test")
@SpringBootTest
@Testcontainers
class CareerKeywordDatabaseIntegrationTest {

    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName
            .parse("pgvector/pgvector:0.8.2-pg16-bookworm")
            .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(PGVECTOR_IMAGE)
            .withDatabaseName("prizm")
            .withUsername("prizm")
            .withPassword("prizm-test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired CareerKeywordRepository careerKeywordRepository;
    @Autowired CareerKeywordService careerKeywordService;
    @Autowired DocumentRepository documentRepository;
    @Autowired DocumentVersionRepository documentVersionRepository;
    @Autowired UserAccountRepository userAccountRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM document_chunks");
        jdbcTemplate.update("UPDATE documents SET active_version_id = NULL");
        jdbcTemplate.update("DELETE FROM document_versions");
        jdbcTemplate.update("DELETE FROM documents");
        userAccountRepository.deleteAll();
    }

    @Test
    void readsOnlyOwnedActiveResumeAndPortfolioSources() {
        UserAccount owner = createUser("keyword-owner@prizm.local");
        UserAccount otherUser = createUser("keyword-other@prizm.local");

        Document resume = createDocument(owner.getId(), "Backend resume", DocumentType.RESUME);
        DocumentVersion oldResume = createVersion(owner.getId(), resume, 1, "resume-v1.txt", DocumentFileType.TXT, true);
        insertChunk(owner.getId(), oldResume.getId(), 0, "TEXT_CHUNK", 1, "텍스트 구간 1", "Kubernetes");
        DocumentVersion currentResume = createVersion(owner.getId(), resume, 2, "resume-v2.txt", DocumentFileType.TXT, true);
        insertChunk(
                owner.getId(),
                currentResume.getId(),
                0,
                "TEXT_CHUNK",
                1,
                "텍스트 구간 1",
                "Spring Boot와 PostgreSQL Backend Java21");
        activate(resume, currentResume);

        Document portfolio = createDocument(owner.getId(), "Frontend portfolio", DocumentType.PORTFOLIO);
        DocumentVersion activePortfolio = createVersion(owner.getId(), portfolio, 1, "portfolio.pdf", DocumentFileType.PDF, true);
        insertChunk(
                owner.getId(),
                activePortfolio.getId(),
                0,
                "PAGE",
                1,
                "1페이지",
                "React와 TypeScript 백엔드 Java17");
        activate(portfolio, activePortfolio);

        Document otherType = createDocument(owner.getId(), "Other notes", DocumentType.OTHER);
        DocumentVersion activeOtherType = createVersion(owner.getId(), otherType, 1, "notes.txt", DocumentFileType.TXT, true);
        insertChunk(owner.getId(), activeOtherType.getId(), 0, "TEXT_CHUNK", 1, "텍스트 구간 1", "Python");
        activate(otherType, activeOtherType);

        Document failedResume = createDocument(owner.getId(), "Failure guard", DocumentType.RESUME);
        DocumentVersion activeBeforeFailure = createVersion(owner.getId(), failedResume, 1, "guard-v1.txt", DocumentFileType.TXT, true);
        insertChunk(owner.getId(), activeBeforeFailure.getId(), 0, "TEXT_CHUNK", 1, "텍스트 구간 1", "Java");
        DocumentVersion failedVersion = createVersion(owner.getId(), failedResume, 2, "guard-v2.txt", DocumentFileType.TXT, false);
        insertChunk(owner.getId(), failedVersion.getId(), 0, "TEXT_CHUNK", 1, "텍스트 구간 1", "Docker");
        activate(failedResume, activeBeforeFailure);

        Document foreignResume = createDocument(otherUser.getId(), "Foreign resume", DocumentType.RESUME);
        DocumentVersion foreignVersion = createVersion(otherUser.getId(), foreignResume, 1, "foreign.txt", DocumentFileType.TXT, true);
        insertChunk(otherUser.getId(), foreignVersion.getId(), 0, "TEXT_CHUNK", 1, "텍스트 구간 1", "AWS");
        activate(foreignResume, foreignVersion);

        List<KeywordSourceChunk> sources = careerKeywordRepository.findActiveSources(owner.getId());

        assertThat(sources)
                .extracting(KeywordSourceChunk::documentVersionId)
                .containsExactly(currentResume.getId(), activePortfolio.getId(), activeBeforeFailure.getId());
        assertThat(sources)
                .extracting(KeywordSourceChunk::content)
                .containsExactly(
                        "Spring Boot와 PostgreSQL Backend Java21",
                        "React와 TypeScript 백엔드 Java17",
                        "Java")
                .doesNotContain("Kubernetes", "Python", "Docker", "AWS");

        var keywordMap = careerKeywordService.getKeywordMap(owner.getId());
        assertThat(keywordMap.documentCount()).isEqualTo(3);
        assertThat(keywordMap.keywords())
                .extracting(summary -> summary.keyword())
                .contains("Spring Boot", "PostgreSQL", "React", "TypeScript", "Backend", "Java")
                .doesNotContain("Kubernetes", "Python", "Docker", "AWS");
        assertThat(keywordMap.keywords())
                .filteredOn(summary -> summary.keyword().equals("Backend"))
                .singleElement()
                .satisfies(summary -> {
                    assertThat(summary.frequency()).isEqualTo(2);
                    assertThat(summary.documentCount()).isEqualTo(2);
                    assertThat(summary.variants()).containsExactly("Backend", "백엔드");
                });

        var evidence = careerKeywordService.getEvidence(owner.getId(), "Spring Boot");
        assertThat(evidence.totalFrequency()).isEqualTo(1);
        assertThat(evidence.evidence())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.documentId()).isEqualTo(resume.getId());
                    assertThat(item.documentVersionId()).isEqualTo(currentResume.getId());
                    assertThat(item.originalFileName()).isEqualTo("resume-v2.txt");
                });
        assertThat(careerKeywordService.getEvidence(owner.getId(), "AWS").evidence()).isEmpty();
        assertThat(careerKeywordService.getEvidence(otherUser.getId(), "AWS").evidence()).hasSize(1);
        assertThat(careerKeywordService.getEvidence(owner.getId(), "백엔드").evidence()).hasSize(2);
    }

    private UserAccount createUser(String email) {
        return userAccountRepository.saveAndFlush(UserAccount.create(email, "test-password-hash", UserRole.USER));
    }

    private Document createDocument(Long ownerUserId, String title, DocumentType documentType) {
        return documentRepository.saveAndFlush(Document.create(ownerUserId, title, documentType));
    }

    private DocumentVersion createVersion(
            Long ownerUserId,
            Document document,
            int versionNo,
            String fileName,
            DocumentFileType fileType,
            boolean active) {
        DocumentVersion version = DocumentVersion.quarantined(
                ownerUserId,
                document.getId(),
                versionNo,
                fileName,
                fileType,
                Integer.toHexString(versionNo).repeat(64).substring(0, 64));
        version.updateStoredFilePath("documents/%d/%s".formatted(document.getId(), fileName));
        version.startProcessing();
        if (active) {
            version.activate();
        }
        else {
            version.failProcessing();
        }
        return documentVersionRepository.saveAndFlush(version);
    }

    private void activate(Document document, DocumentVersion version) {
        document.activateVersion(version.getId());
        documentRepository.saveAndFlush(document);
    }

    private void insertChunk(
            Long ownerUserId,
            Long versionId,
            int chunkNo,
            String sourceType,
            int sourceIndex,
            String sourceLabel,
            String content) {
        jdbcTemplate.update(
                """
                INSERT INTO document_chunks(
                    owner_user_id, document_version_id, chunk_no,
                    source_type, source_index, source_label, content, embedding
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, array_fill(0.1::real, ARRAY[1024])::vector)
                """,
                ownerUserId,
                versionId,
                chunkNo,
                sourceType,
                sourceIndex,
                sourceLabel,
                content);
    }
}
