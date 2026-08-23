package com.prizm.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prizm.document.entity.Document;
import com.prizm.document.entity.DocumentType;
import com.prizm.document.repository.DocumentRepository;
import com.prizm.document.service.DocumentUploadService;
import com.prizm.documenttag.model.TagSource;
import com.prizm.documenttag.service.DocumentTagErrorCode;
import com.prizm.documenttag.service.DocumentTagService;
import com.prizm.documenttag.service.InvalidDocumentTagException;
import com.prizm.user.entity.UserAccount;
import com.prizm.user.entity.UserRole;
import com.prizm.user.repository.UserAccountRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@ActiveProfiles("integration-test")
@SpringBootTest
@Testcontainers
@Transactional
class DocumentTagDatabaseIntegrationTest {

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

    @Autowired DocumentTagService tagService;
    @Autowired DocumentUploadService documentUploadService;
    @Autowired DocumentRepository documentRepository;
    @Autowired UserAccountRepository userAccountRepository;

    @Test
    void isolatesUserTagsAndAggregatesOnlyActualDocumentLinks() {
        UserAccount owner = createUser("tag-owner@prizm.local");
        UserAccount other = createUser("tag-other@prizm.local");
        var redis = tagService.search(owner.getId(), "Redis").stream()
                .filter(tag -> tag.name().equals("Redis"))
                .findFirst()
                .orElseThrow();
        var ownerTauri = tagService.createUserTag(owner.getId(), "  Tauri ").tag();
        var ownerTauriDuplicate = tagService.createUserTag(owner.getId(), "tauri").tag();
        var ownerUnicodeSpacing = tagService.createUserTag(owner.getId(), "Nebula\u2028Harbor").tag();
        var ownerUnicodeSpacingDuplicate = tagService.createUserTag(owner.getId(), "nebula harbor").tag();
        var otherTauri = tagService.createUserTag(other.getId(), "TAURI").tag();

        assertThat(redis.source()).isEqualTo(TagSource.SYSTEM);
        assertThat(ownerTauri.tagId()).isEqualTo(ownerTauriDuplicate.tagId());
        assertThat(ownerUnicodeSpacing.tagId()).isEqualTo(ownerUnicodeSpacingDuplicate.tagId());
        assertThat(otherTauri.tagId()).isNotEqualTo(ownerTauri.tagId());
        assertThat(tagService.search(owner.getId(), "Tauri"))
                .extracting(tag -> tag.tagId())
                .containsExactly(ownerTauri.tagId());

        Document first = createDocument(owner.getId(), "Backend guide", DocumentType.PROJECT_REPORT);
        Document second = createDocument(owner.getId(), "Desktop portfolio", DocumentType.PORTFOLIO);
        Document foreign = createDocument(other.getId(), "Foreign portfolio", DocumentType.PORTFOLIO);
        tagService.replaceDocumentTags(owner.getId(), first.getId(), List.of(redis.tagId(), ownerTauri.tagId()));
        tagService.replaceDocumentTags(owner.getId(), second.getId(), List.of(ownerTauri.tagId()));
        tagService.replaceDocumentTags(other.getId(), foreign.getId(), List.of(otherTauri.tagId()));

        assertThat(tagService.getUsage(owner.getId()))
                .extracting(usage -> usage.name() + ":" + usage.documentCount())
                .containsExactly("Tauri:2", "Redis:1");
        assertThat(tagService.getTaggedDocuments(owner.getId(), ownerTauri.tagId()).documents())
                .extracting(document -> document.title())
                .containsExactlyInAnyOrder("Backend guide", "Desktop portfolio");
        assertThatThrownBy(() -> tagService.replaceDocumentTags(
                owner.getId(), first.getId(), List.of(otherTauri.tagId())))
                .isInstanceOf(InvalidDocumentTagException.class)
                .extracting(exception -> ((InvalidDocumentTagException) exception).code())
                .isEqualTo(DocumentTagErrorCode.TAG_NOT_FOUND);

        tagService.removeDocumentTag(owner.getId(), first.getId(), redis.tagId());
        assertThat(tagService.getDocumentTags(owner.getId(), first.getId()))
                .extracting(tag -> tag.name())
                .containsExactly("Tauri");
    }

    @Test
    void uploadPersistsSelectedTagsInTheDocumentTransaction() {
        UserAccount owner = createUser("tag-upload@prizm.local");
        var redis = tagService.search(owner.getId(), "Redis").stream()
                .filter(tag -> tag.name().equals("Redis"))
                .findFirst()
                .orElseThrow();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "career.txt",
                "text/plain",
                "synthetic career evidence".getBytes(StandardCharsets.UTF_8));

        var uploaded = documentUploadService.upload(
                owner.getId(),
                "Career evidence",
                DocumentType.RESUME,
                List.of(redis.tagId()),
                file);

        assertThat(tagService.getDocumentTags(owner.getId(), uploaded.documentId()))
                .extracting(tag -> tag.name())
                .containsExactly("Redis");
    }

    private UserAccount createUser(String email) {
        return userAccountRepository.saveAndFlush(
                UserAccount.create(email, "test-password-hash", UserRole.USER));
    }

    private Document createDocument(Long ownerUserId, String title, DocumentType documentType) {
        return documentRepository.saveAndFlush(Document.create(ownerUserId, title, documentType));
    }
}
