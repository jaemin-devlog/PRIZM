package com.prizm.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.prizm.auth.bootstrap.BcryptPasswordPolicy;
import com.prizm.auth.bootstrap.BootstrapDemoUserProperties;
import com.prizm.auth.bootstrap.BootstrapSystemAdminProperties;
import com.prizm.auth.bootstrap.DemoUserBootstrapRunner;
import com.prizm.auth.bootstrap.SystemAdminBootstrapRunner;
import com.prizm.auth.dto.request.LoginRequest;
import com.prizm.auth.service.AuthService;
import com.prizm.changelog.service.ChangeLogDispatchTransaction;
import com.prizm.cleanup.service.FileCleanupCoordinator;
import com.prizm.document.dto.response.DocumentUploadResponse;
import com.prizm.document.entity.DocumentType;
import com.prizm.document.repository.DocumentVersionRepository;
import com.prizm.document.service.DocumentUploadService;
import com.prizm.embedding.service.EmbeddingService;
import com.prizm.infrastructure.storage.FileStorage;
import com.prizm.ingestion.entity.ProcessingJobStatus;
import com.prizm.ingestion.repository.ProcessingJobRepository;
import com.prizm.user.entity.UserAccount;
import com.prizm.user.entity.UserRole;
import com.prizm.user.repository.UserAccountRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@ActiveProfiles("integration-test")
@SpringBootTest
@Testcontainers
class AuthenticationIntegrationTest {

    private static final Path STORAGE_ROOT = createStorageRoot();
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
        registry.add("prizm.storage.root", STORAGE_ROOT::toString);
    }

    @Autowired
    WebApplicationContext applicationContext;

    @Autowired
    UserAccountRepository userAccountRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    BcryptPasswordPolicy bcryptPasswordPolicy;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    AuthService authService;

    @Autowired
    DocumentUploadService documentUploadService;

    @Autowired
    DocumentVersionRepository documentVersionRepository;

    @Autowired
    ProcessingJobRepository processingJobRepository;

    @Autowired
    ChangeLogDispatchTransaction changeLogDispatchTransaction;

    @Autowired
    EmbeddingService embeddingService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    FileStorage fileStorage;

    @Autowired
    FileCleanupCoordinator fileCleanupCoordinator;

    @Autowired
    Validator validator;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM document_change_logs");
        jdbcTemplate.update("DELETE FROM file_cleanup_jobs");
        jdbcTemplate.update("DELETE FROM processing_jobs");
        jdbcTemplate.update("DELETE FROM document_chunks");
        jdbcTemplate.update("UPDATE documents SET active_version_id = NULL");
        jdbcTemplate.update("DELETE FROM document_versions");
        jdbcTemplate.update("DELETE FROM documents");
        userAccountRepository.deleteAll();
    }

    @Test
    void loginEndpointReturnsAccessTokenAndPublicUserInformation() throws Exception {
        createUser(UserRole.SYSTEM_ADMIN, true);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"system-admin@prizm.local","password":"test-password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.user.email").value("system-admin@prizm.local"))
                .andExpect(jsonPath("$.user.role").value("SYSTEM_ADMIN"));
    }

    @Test
    void createsInitialSystemAdminInCleanDatabaseOnlyWhenExplicitlyInvoked() throws Exception {
        SystemAdminBootstrapRunner runner = new SystemAdminBootstrapRunner(
                new BootstrapSystemAdminProperties(
                        true, "SYSTEM-ADMIN@Prizm.Local", "integration-password"),
                userAccountRepository,
                bcryptPasswordPolicy,
                validator);

        runner.run(new DefaultApplicationArguments(new String[0]));

        UserAccount systemAdmin = userAccountRepository.findByEmail("system-admin@prizm.local").orElseThrow();
        assertThat(systemAdmin.getRole()).isEqualTo(UserRole.SYSTEM_ADMIN);
        assertThat(systemAdmin.isEnabled()).isTrue();
        assertThat(systemAdmin.getPasswordHash()).isNotEqualTo("integration-password");
        assertThat(passwordEncoder.matches("integration-password", systemAdmin.getPasswordHash())).isTrue();
    }

    @Test
    void bootstrappedDemoUserLogsInThroughHttpAndUsesJwtProtectedRoute() throws Exception {
        DemoUserBootstrapRunner runner = new DemoUserBootstrapRunner(
                new BootstrapDemoUserProperties(
                        true, "DEMO-USER@Prizm.Local", "integration-password"),
                userAccountRepository,
                bcryptPasswordPolicy,
                validator);
        runner.run(new DefaultApplicationArguments(new String[0]));

        String responseBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"demo-user@prizm.local","password":"integration-password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("demo-user@prizm.local"))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String accessToken = JsonPath.read(responseBody, "$.accessToken");

        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("demo-user@prizm.local"))
                .andExpect(jsonPath("$.role").value("USER"));
        assertThat(userAccountRepository.existsByRole(UserRole.SYSTEM_ADMIN)).isFalse();
    }

    @Test
    void signedUpUserLogsInAndJwtRevalidationIsolatesDocumentsByOwner() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"new-user@prizm.local","password":"signup-password","role":"SYSTEM_ADMIN"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().string(""));

        UserAccount signedUpUser = userAccountRepository.findByEmail("new-user@prizm.local").orElseThrow();
        assertThat(signedUpUser.getRole()).isEqualTo(UserRole.USER);
        assertThat(signedUpUser.isEnabled()).isTrue();
        assertThat(signedUpUser.getPasswordHash()).isNotEqualTo("signup-password");
        assertThat(passwordEncoder.matches("signup-password", signedUpUser.getPasswordHash())).isTrue();

        String responseBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"new-user@prizm.local","password":"signup-password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("new-user@prizm.local"))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String accessToken = JsonPath.read(responseBody, "$.accessToken");
        UserAccount otherUser = createUser("other-user@prizm.local", UserRole.USER, true);
        ActiveDocument signedUpUserDocument = createActiveDocument(
                signedUpUser.getId(), "신규 사용자 문서", "신규 사용자에게만 보이는 기록");
        ActiveDocument otherDocument = createActiveDocument(
                otherUser.getId(), "다른 사용자 문서", "다른 사용자에게만 보이는 기록");

        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new-user@prizm.local"))
                .andExpect(jsonPath("$.role").value("USER"));
        mockMvc.perform(get("/api/documents")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentId").value(signedUpUserDocument.documentId()))
                .andExpect(jsonPath("$[1]").doesNotExist());
        mockMvc.perform(get("/api/documents/{documentId}", otherDocument.documentId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isNotFound());

        signedUpUser.disable();
        userAccountRepository.saveAndFlush(signedUpUser);

        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        mockMvc.perform(post("/api/auth/local-session"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void rejectsUnauthenticatedDocumentListWith401() throws Exception {
        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void rejectsSystemAdminDocumentUploadListDetailThumbnailAndSearchAccessWith403() throws Exception {
        String token = tokenFor(UserRole.SYSTEM_ADMIN);
        MockMultipartFile file = new MockMultipartFile(
                "file", "system-admin-upload.txt", "text/plain", "document content".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/documents")
                        .file(file)
                        .param("title", "System admin document")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(get("/api/documents")
                        .param("documentType", "PORTFOLIO")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(get("/api/documents/1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(get("/api/documents/1/versions/1/thumbnail")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(post("/api/search")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"career evidence"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(post("/api/career-evidence/search")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"career evidence"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void allowsAuthenticatedUserToUploadTxtDocument() throws Exception {
        UserAccount owner = createUser(UserRole.USER, true);
        String token = login(owner.getEmail());
        MockMultipartFile file = new MockMultipartFile(
                "file", "user-upload.txt", "text/plain", "사용자 업로드 문서".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/documents")
                        .file(file)
                        .param("title", "사용자 업로드")
                        .param("documentType", "PORTFOLIO")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("사용자 업로드"))
                .andExpect(jsonPath("$.documentType").value("PORTFOLIO"))
                .andExpect(jsonPath("$.status").value("QUARANTINED"));

        assertThat(processingJobRepository.count()).isZero();
        assertThat(changeLogDispatchTransaction.dispatchNext()).isTrue();
        assertThat(processingJobRepository.count()).isEqualTo(1L);
        var job = processingJobRepository.findAll().get(0);
        assertThat(job.getStatus()).isEqualTo(ProcessingJobStatus.PENDING);
        var version = documentVersionRepository.findById(job.getDocumentVersionId()).orElseThrow();
        assertThat(job.getOwnerUserId()).isEqualTo(owner.getId());
        assertThat(version.getOwnerUserId()).isEqualTo(owner.getId());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT owner_user_id FROM documents WHERE id = ?",
                Long.class,
                version.getDocumentId())).isEqualTo(owner.getId());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT document_type FROM documents WHERE id = ?",
                String.class,
                version.getDocumentId())).isEqualTo("PORTFOLIO");
    }

    @Test
    void rejectsUnknownDocumentTypeWith400() throws Exception {
        UserAccount owner = createUser(UserRole.USER, true);
        String token = login(owner.getEmail());
        MockMultipartFile file = new MockMultipartFile(
                "file", "invalid-type.txt", "text/plain", "문서 유형 검증".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/documents")
                        .file(file)
                        .param("title", "유형 검증")
                        .param("documentType", "UNKNOWN")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void allowsAuthenticatedUserToSearchActiveDocument() throws Exception {
        UserAccount owner = createUser(UserRole.USER, true);
        String token = login(owner.getEmail());
        String content = "연차 신청은 인사 시스템에서 진행합니다.";
        ActiveDocument activeDocument = createActiveDocument(owner.getId(), "인사 안내", content);

        mockMvc.perform(post("/api/search")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"휴가는 어디에서 신청하나요?"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentVersionId").value(activeDocument.versionId()))
                .andExpect(jsonPath("$.sourceType").value("TEXT_CHUNK"))
                .andExpect(jsonPath("$.sourceIndex").value(1))
                .andExpect(jsonPath("$.sourceLabel").value("텍스트 구간 1"))
                .andExpect(jsonPath("$.content").value(content));

        mockMvc.perform(post("/api/career-evidence/search")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"휴가 안내에서 요청하는 내용"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].chunkId").isNumber())
                .andExpect(jsonPath("$[0].documentId").value(activeDocument.documentId()))
                .andExpect(jsonPath("$[0].documentVersionId").value(activeDocument.versionId()))
                .andExpect(jsonPath("$[0].sourceType").value("TEXT_CHUNK"))
                .andExpect(jsonPath("$[0].sourceLabel").value("텍스트 구간 1"))
                .andExpect(jsonPath("$[0].content").value(content));
    }

    @Test
    void returnsAnEmptyCareerEvidenceArrayAndValidatesTheQuery() throws Exception {
        UserAccount owner = createUser(UserRole.USER, true);
        String token = login(owner.getEmail());

        mockMvc.perform(post("/api/career-evidence/search")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"no registered evidence\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(post("/api/career-evidence/search")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SEARCH_QUERY"));

        String overlongQuery = "x".repeat(501);
        mockMvc.perform(post("/api/career-evidence/search")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"%s\"}".formatted(overlongQuery)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SEARCH_QUERY"));
    }

    @Test
    void isolatesDocumentListAndDetailByAuthenticatedUser() throws Exception {
        UserAccount userA = createUser("user-a@prizm.local", UserRole.USER, true);
        UserAccount userB = createUser("user-b@prizm.local", UserRole.USER, true);
        String tokenA = login(userA.getEmail());
        String tokenB = login(userB.getEmail());
        ActiveDocument documentA = createActiveDocument(userA.getId(), "A 프로젝트", "A 사용자의 프로젝트 기록");
        ActiveDocument documentB = createActiveDocument(userB.getId(), "B 프로젝트", "B 사용자의 프로젝트 기록");

        mockMvc.perform(get("/api/documents").header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentId").value(documentA.documentId()))
                .andExpect(jsonPath("$[1]").doesNotExist());
        mockMvc.perform(get("/api/documents").header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentId").value(documentB.documentId()))
                .andExpect(jsonPath("$[1]").doesNotExist());

        mockMvc.perform(get("/api/documents/{documentId}", documentA.documentId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("A 프로젝트"));
        mockMvc.perform(get("/api/documents/{documentId}", documentA.documentId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/documents/{documentId}", documentB.documentId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void protectsPdfThumbnailByAuthenticationAndDocumentOwnership() throws Exception {
        UserAccount owner = createUser("thumbnail-owner@prizm.local", UserRole.USER, true);
        UserAccount otherUser = createUser("thumbnail-other@prizm.local", UserRole.USER, true);
        String ownerToken = login(owner.getEmail());
        String otherToken = login(otherUser.getEmail());
        DocumentUploadResponse upload = documentUploadService.upload(
                owner.getId(),
                "Thumbnail source",
                DocumentType.PORTFOLIO,
                new MockMultipartFile(
                        "file",
                        "thumbnail-source.pdf",
                        MediaType.APPLICATION_PDF_VALUE,
                        textPdf("PRIZM thumbnail integration test")));
        String thumbnailPath = "/api/documents/%d/versions/%d/thumbnail"
                .formatted(upload.documentId(), upload.versionId());

        mockMvc.perform(get(thumbnailPath))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        byte[] response = mockMvc.perform(get(thumbnailPath)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                        "private, max-age=3600, must-revalidate, no-transform"))
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        assertThat(response).startsWith((byte) 0x89, (byte) 0x50, (byte) 0x4e, (byte) 0x47);

        mockMvc.perform(get(thumbnailPath)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
    }

    @Test
    void rejectsTxtThumbnailWith415() throws Exception {
        UserAccount owner = createUser("thumbnail-txt@prizm.local", UserRole.USER, true);
        String token = login(owner.getEmail());
        DocumentUploadResponse upload = documentUploadService.upload(
                owner.getId(),
                "Text thumbnail source",
                DocumentType.OTHER,
                new MockMultipartFile(
                        "file",
                        "thumbnail-source.txt",
                        MediaType.TEXT_PLAIN_VALUE,
                        "Text documents do not have PDF thumbnails.".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/api/documents/{documentId}/versions/{versionId}/thumbnail",
                        upload.documentId(), upload.versionId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
    }

    @Test
    void filtersDocumentListByTypeWithinAuthenticatedUserBoundary() throws Exception {
        UserAccount userA = createUser("filter-a@prizm.local", UserRole.USER, true);
        UserAccount userB = createUser("filter-b@prizm.local", UserRole.USER, true);
        String tokenA = login(userA.getEmail());
        String tokenB = login(userB.getEmail());
        ActiveDocument portfolioA = createActiveDocument(
                userA.getId(), "A 포트폴리오", "A 포트폴리오 내용", DocumentType.PORTFOLIO);
        ActiveDocument resumeA = createActiveDocument(
                userA.getId(), "A 이력서", "A 이력서 내용", DocumentType.RESUME);
        ActiveDocument portfolioB = createActiveDocument(
                userB.getId(), "B 포트폴리오", "B 포트폴리오 내용", DocumentType.PORTFOLIO);

        mockMvc.perform(get("/api/documents").header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/documents")
                        .param("documentType", "PORTFOLIO")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentId").value(portfolioA.documentId()))
                .andExpect(jsonPath("$[0].documentType").value("PORTFOLIO"))
                .andExpect(jsonPath("$[1]").doesNotExist());
        mockMvc.perform(get("/api/documents")
                        .param("documentType", "RESUME")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentId").value(resumeA.documentId()))
                .andExpect(jsonPath("$[1]").doesNotExist());
        mockMvc.perform(get("/api/documents")
                        .param("documentType", "CERTIFICATE")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/api/documents")
                        .param("documentType", "PORTFOLIO")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentId").value(portfolioB.documentId()))
                .andExpect(jsonPath("$[1]").doesNotExist());
    }

    @Test
    void managesOnlyOwnersDocumentsAndQueuesCleanupAfterTerminalDeletion() throws Exception {
        UserAccount owner = createUser("document-manager-owner@prizm.local", UserRole.USER, true);
        UserAccount otherUser = createUser("document-manager-other@prizm.local", UserRole.USER, true);
        String ownerToken = login(owner.getEmail());
        String otherUserToken = login(otherUser.getEmail());
        DocumentUploadResponse ownerUpload = documentUploadService.upload(
                owner.getId(),
                "Owner document",
                DocumentType.OTHER,
                new MockMultipartFile(
                        "file", "owner-document.txt", "text/plain", "owner document body".getBytes(StandardCharsets.UTF_8)));
        DocumentUploadResponse otherUpload = documentUploadService.upload(
                otherUser.getId(),
                "Other document",
                DocumentType.OTHER,
                new MockMultipartFile(
                        "file", "other-document.txt", "text/plain", "other document body".getBytes(StandardCharsets.UTF_8)));
        assertThat(changeLogDispatchTransaction.dispatchNext()).isTrue();
        assertThat(changeLogDispatchTransaction.dispatchNext()).isTrue();
        String storageKey = jdbcTemplate.queryForObject(
                "SELECT stored_file_path FROM document_versions WHERE id = ?", String.class, ownerUpload.versionId());

        mockMvc.perform(get("/api/documents")
                        .param("title", "Owner")
                        .param("processingStatus", "PENDING")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentId").value(ownerUpload.documentId()))
                .andExpect(jsonPath("$[1]").doesNotExist());

        mockMvc.perform(patch("/api/documents/{documentId}", ownerUpload.documentId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated owner document\",\"documentType\":\"RESUME\"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated owner document"))
                .andExpect(jsonPath("$.documentType").value("RESUME"));
        mockMvc.perform(patch("/api/documents/{documentId}", ownerUpload.documentId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Not allowed\",\"documentType\":\"RESUME\"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherUserToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/documents/{documentId}", ownerUpload.documentId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\" \",\"documentType\":\"RESUME\"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/documents/{documentId}", ownerUpload.documentId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_PROCESSING"));
        mockMvc.perform(delete("/api/documents/{documentId}", ownerUpload.documentId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherUserToken)))
                .andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM documents WHERE id = ?", Long.class, ownerUpload.documentId())).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM documents WHERE id = ?", Long.class, otherUpload.documentId())).isEqualTo(1L);

        jdbcTemplate.update(
                "UPDATE processing_jobs SET status = 'FAILED', completed_at = now(), lease_expires_at = NULL WHERE document_version_id = ?",
                ownerUpload.versionId());
        jdbcTemplate.update("UPDATE document_versions SET status = 'ACTIVE' WHERE id = ?", ownerUpload.versionId());
        jdbcTemplate.update(
                "UPDATE documents SET active_version_id = ? WHERE id = ?", ownerUpload.versionId(), ownerUpload.documentId());

        mockMvc.perform(delete("/api/documents/{documentId}", ownerUpload.documentId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM documents WHERE id = ?", Long.class, ownerUpload.documentId())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_versions WHERE id = ?", Long.class, ownerUpload.versionId())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM file_cleanup_jobs WHERE storage_key = ?", String.class, storageKey))
                .isEqualTo("PENDING");
        if (System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("linux")) {
            assertThat(fileCleanupCoordinator.processNext()).isTrue();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM file_cleanup_jobs WHERE storage_key = ?", String.class, storageKey))
                    .isEqualTo("COMPLETED");
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> fileStorage.read(storageKey))
                    .isInstanceOf(RuntimeException.class);
        }

        mockMvc.perform(delete("/api/documents/{documentId}", ownerUpload.documentId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isNoContent());
    }

    @Test
    void isolatesVectorSearchCandidatesByAuthenticatedUser() throws Exception {
        UserAccount userA = createUser("search-a@prizm.local", UserRole.USER, true);
        UserAccount userB = createUser("search-b@prizm.local", UserRole.USER, true);
        String tokenA = login(userA.getEmail());
        String tokenB = login(userB.getEmail());
        String content = "Spring Boot와 Redis를 이용해 동시 요청 처리를 구현했습니다.";
        ActiveDocument documentA = createActiveDocument(userA.getId(), "A 동시성 프로젝트", content);
        ActiveDocument documentB = createActiveDocument(userB.getId(), "B 동시성 프로젝트", content);

        mockMvc.perform(post("/api/search")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"Spring Boot 동시 요청 처리 경험\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(documentA.documentId()))
                .andExpect(jsonPath("$.documentVersionId").value(documentA.versionId()));
        mockMvc.perform(post("/api/search")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"Spring Boot 동시 요청 처리 경험\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(documentB.documentId()))
                .andExpect(jsonPath("$.documentVersionId").value(documentB.versionId()));
    }

    @Test
    void returnsCurrentAuthenticatedUser() throws Exception {
        String token = tokenFor(UserRole.USER);

        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@prizm.local"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void rejectsPreviouslyIssuedTokenAfterUserIsDisabled() throws Exception {
        UserAccount user = createUser(UserRole.USER, true);
        String token = login(user.getEmail());
        user.disable();
        userAccountRepository.saveAndFlush(user);

        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void rejectsDisabledAndExpiredTokensBeforeDocumentManagementRoutes() throws Exception {
        UserAccount disabledUser = createUser("disabled-document-manager@prizm.local", UserRole.USER, true);
        String disabledToken = login(disabledUser.getEmail());
        disabledUser.disable();
        userAccountRepository.saveAndFlush(disabledUser);

        mockMvc.perform(patch("/api/documents/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Blocked\",\"documentType\":\"RESUME\"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer(disabledToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        UserAccount expiredUser = createUser("expired-document-manager@prizm.local", UserRole.USER, true);
        Instant now = Instant.now();
        String expiredToken = signedToken(
                expiredUser,
                expiredUser.getEmail(),
                "USER",
                "prizm",
                now.minusSeconds(300),
                now.minusSeconds(120));

        mockMvc.perform(delete("/api/documents/1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(expiredToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void rejectsExpiredJwtThroughSecurityFilterChain() throws Exception {
        UserAccount user = createUser(UserRole.USER, true);
        Instant now = Instant.now();
        String token = signedToken(user, user.getEmail(), "USER", "prizm", now.minusSeconds(300), now.minusSeconds(120));

        expectUnauthorized("Bearer " + token);
    }

    @Test
    void rejectsJwtWithoutExpirationThroughSecurityFilterChain() throws Exception {
        UserAccount user = createUser(UserRole.USER, true);
        String token = signedTokenWithoutExpiration(user);

        String responseBody = expectUnauthorized("Bearer " + token);

        assertThat(responseBody)
                .doesNotContain(token)
                .doesNotContain("exp is required")
                .doesNotContain("JwtValidationException");
    }

    @Test
    void rejectsDeterministicallyTamperedJwtThroughSecurityFilterChain() throws Exception {
        String token = tokenFor(UserRole.USER);
        String[] parts = token.split("\\.");
        char replacement = parts[1].charAt(0) == 'a' ? 'b' : 'a';
        parts[1] = replacement + parts[1].substring(1);

        expectUnauthorized("Bearer " + String.join(".", parts));
    }

    @Test
    void rejectsMalformedAndEmptyBearerHeaders() throws Exception {
        expectUnauthorized("Bearer not-a-jwt");
        expectUnauthorized("Bearer");
        expectUnauthorized("Bearer ");
        expectUnauthorized("Basic dXNlcjpwYXNzd29yZA==");
    }

    @Test
    void rejectsDuplicateAndCommaJoinedAuthorizationValues() throws Exception {
        String token = tokenFor(UserRole.USER);

        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        expectUnauthorized("Bearer " + token + ", Bearer " + token);
    }

    @Test
    void rejectsJwtFromAnotherIssuer() throws Exception {
        UserAccount user = createUser(UserRole.USER, true);
        Instant now = Instant.now();
        String token = signedToken(user, user.getEmail(), "USER", "another-service", now, now.plusSeconds(3600));

        expectUnauthorized("Bearer " + token);
    }

    @Test
    void rejectsJwtForDeletedUser() throws Exception {
        UserAccount user = createUser(UserRole.USER, true);
        String token = login(user.getEmail());
        userAccountRepository.delete(user);
        userAccountRepository.flush();

        expectUnauthorized("Bearer " + token);
    }

    @Test
    void rejectsJwtWhoseEmailDiffersFromDatabase() throws Exception {
        UserAccount user = createUser(UserRole.USER, true);
        Instant now = Instant.now();
        String token = signedToken(user, "other@prizm.local", "USER", "prizm", now, now.plusSeconds(3600));

        expectUnauthorized("Bearer " + token);
    }

    @Test
    void rejectsJwtWhoseRoleDiffersFromDatabase() throws Exception {
        UserAccount user = createUser(UserRole.USER, true);
        Instant now = Instant.now();
        String token = signedToken(user, user.getEmail(), "SYSTEM_ADMIN", "prizm", now, now.plusSeconds(3600));

        expectUnauthorized("Bearer " + token);
    }

    @Test
    void deniesUnregisteredPathEvenForAuthenticatedUser() throws Exception {
        String token = tokenFor(UserRole.SYSTEM_ADMIN);

        mockMvc.perform(get("/unregistered-path")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void exposesOnlyExactHealthEndpointWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void allowsConfiguredCorsPreflightAndRejectsUnknownOrigin() throws Exception {
        mockMvc.perform(options("/api/documents")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));

        mockMvc.perform(options("/api/documents")
                        .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    private String tokenFor(UserRole role) {
        UserAccount user = createUser(role, true);
        return login(user.getEmail());
    }

    private String login(String email) {
        return authService.login(new LoginRequest(email, "test-password")).accessToken();
    }

    private String expectUnauthorized(String authorization) throws Exception {
        return mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String signedToken(
            UserAccount user,
            String email,
            String role,
            String issuer,
            Instant issuedAt,
            Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("email", email)
                .claim("role", role)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(),
                        claims))
                .getTokenValue();
    }

    private String signedTokenWithoutExpiration(UserAccount user) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("prizm")
                .subject(user.getId().toString())
                .issuedAt(Instant.now())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(),
                        claims))
                .getTokenValue();
    }

    private UserAccount createUser(UserRole role, boolean enabled) {
        String email = role == UserRole.SYSTEM_ADMIN ? "system-admin@prizm.local" : "user@prizm.local";
        return createUser(email, role, enabled);
    }

    private UserAccount createUser(String email, UserRole role, boolean enabled) {
        UserAccount user = enabled
                ? UserAccount.create(email, passwordEncoder.encode("test-password"), role)
                : UserAccount.createDisabled(email, passwordEncoder.encode("test-password"), role);
        return userAccountRepository.saveAndFlush(user);
    }

    private ActiveDocument createActiveDocument(Long ownerUserId, String title, String content) {
        return createActiveDocument(ownerUserId, title, content, DocumentType.OTHER);
    }

    private ActiveDocument createActiveDocument(
            Long ownerUserId, String title, String content, DocumentType documentType) {
        Long documentId = jdbcTemplate.queryForObject(
                "INSERT INTO documents(owner_user_id, title, document_type) VALUES (?, ?, ?) RETURNING id",
                Long.class,
                ownerUserId,
                title,
                documentType.name());
        Long versionId = jdbcTemplate.queryForObject(
                """
                INSERT INTO document_versions(
                    owner_user_id, document_id, version_no, original_file_name, stored_file_path, file_type,
                    content_hash, status
                )
                VALUES (?, ?, 1, 'search.txt', 'test/search.txt', 'TXT', repeat('a', 64), 'ACTIVE')
                RETURNING id
                """,
                Long.class,
                ownerUserId,
                documentId);
        jdbcTemplate.update("UPDATE documents SET active_version_id = ? WHERE id = ?", versionId, documentId);
        jdbcTemplate.update(
                """
                INSERT INTO document_chunks(
                    owner_user_id, content, embedding, document_version_id, chunk_no, page_no,
                    source_type, source_index, source_label
                )
                VALUES (?, ?, CAST(? AS vector), ?, 1, NULL, 'TEXT_CHUNK', 1, '텍스트 구간 1')
                """,
                ownerUserId,
                content,
                toVectorLiteral(embeddingService.embed(content)),
                versionId);
        return new ActiveDocument(documentId, versionId);
    }

    private String toVectorLiteral(float[] embedding) {
        StringBuilder literal = new StringBuilder("[");
        for (int index = 0; index < embedding.length; index++) {
            if (index > 0) {
                literal.append(',');
            }
            literal.append(embedding[index]);
        }
        return literal.append(']').toString();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private byte[] textPdf(String text) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(72, 720);
                stream.showText(text);
                stream.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
        catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private record ActiveDocument(Long documentId, Long versionId) {
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("prizm-auth-integration-storage-");
        }
        catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
