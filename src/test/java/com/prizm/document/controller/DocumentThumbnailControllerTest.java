package com.prizm.document.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.prizm.document.dto.response.DocumentOriginalResponse;
import com.prizm.document.dto.response.DocumentThumbnailResponse;
import com.prizm.document.exception.DocumentThumbnailErrorCode;
import com.prizm.document.exception.DocumentThumbnailException;
import com.prizm.document.service.DocumentThumbnailService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class DocumentThumbnailControllerTest {

    private static final String ENDPOINT = "/api/documents/11/versions/22/thumbnail";
    private static final String ORIGINAL_ENDPOINT = "/api/documents/11/versions/22/original";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    DocumentThumbnailService documentThumbnailService;

    @Test
    void returnsPrivateCacheablePngToAnAuthenticatedOwner() throws Exception {
        byte[] pngBytes = {(byte) 0x89, 'P', 'N', 'G'};
        String contentHash = "a".repeat(64);
        when(documentThumbnailService.get(7L, 11L, 22L))
                .thenReturn(new DocumentThumbnailResponse(pngBytes, contentHash));

        mockMvc.perform(get(ENDPOINT).with(userJwt(7L)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(pngBytes))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "private, max-age=3600, must-revalidate, no-transform"))
                .andExpect(header().string(
                        HttpHeaders.ETAG,
                        "W/\"thumbnail-v1-480x640-" + contentHash + "\""));

        verify(documentThumbnailService).get(7L, 11L, 22L);
    }

    @Test
    void returnsUtf8NamedPdfInlineWithPrivateNoStoreSecurityHeaders() throws Exception {
        byte[] pdfBytes = "%PDF-1.7".getBytes();
        when(documentThumbnailService.getOriginal(7L, 11L, 22L))
                .thenReturn(new DocumentOriginalResponse(pdfBytes, "경력 증명서.pdf"));

        mockMvc.perform(get(ORIGINAL_ENDPOINT).with(userJwt(7L)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(pdfBytes))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "private, no-store, no-cache, must-revalidate"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, Matchers.startsWith("inline;")))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        Matchers.containsString("filename*=UTF-8''")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "sandbox"));

        verify(documentThumbnailService).getOriginal(7L, 11L, 22L);
    }

    @Test
    void sanitizesControlCharactersInTheOriginalResponseFileName() throws Exception {
        when(documentThumbnailService.getOriginal(7L, 11L, 22L))
                .thenReturn(new DocumentOriginalResponse("%PDF".getBytes(), "report\r\nInjected.pdf"));

        mockMvc.perform(get(ORIGINAL_ENDPOINT).with(userJwt(7L)))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        Matchers.not(Matchers.containsString("\r"))))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        Matchers.not(Matchers.containsString("\n"))));
    }

    @Test
    void rejectsUnauthenticatedThumbnailRequests() throws Exception {
        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(documentThumbnailService);
    }

    @Test
    void rejectsUnauthenticatedOriginalRequests() throws Exception {
        mockMvc.perform(get(ORIGINAL_ENDPOINT))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(documentThumbnailService);
    }

    @Test
    void rejectsAuthenticatedRequestsWithoutTheUserRole() throws Exception {
        mockMvc.perform(get(ENDPOINT).with(jwt().jwt(token -> token.subject("7"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(documentThumbnailService);
    }

    @Test
    void returnsUnsupportedMediaTypeForTxtAndUnreadablePdfVersions() throws Exception {
        when(documentThumbnailService.get(7L, 11L, 22L)).thenThrow(new DocumentThumbnailException(
                DocumentThumbnailErrorCode.UNSUPPORTED_FILE_TYPE,
                "Thumbnail previews are only available for PDF documents."));

        mockMvc.perform(get(ENDPOINT).with(userJwt(7L)))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
    }

    @Test
    void returnsNotFoundWhenTheOriginalFileIsUnavailable() throws Exception {
        when(documentThumbnailService.get(7L, 11L, 22L)).thenThrow(new DocumentThumbnailException(
                DocumentThumbnailErrorCode.ORIGINAL_FILE_NOT_FOUND,
                "The original file is not available."));

        mockMvc.perform(get(ENDPOINT).with(userJwt(7L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORIGINAL_FILE_NOT_FOUND"));
    }

    @Test
    void returnsSafeOriginalErrorsForTxtMissingAndTransientStorageFailures() throws Exception {
        when(documentThumbnailService.getOriginal(7L, 11L, 22L))
                .thenThrow(new DocumentThumbnailException(
                        DocumentThumbnailErrorCode.UNSUPPORTED_FILE_TYPE,
                        "Original viewing is only available for PDF documents."))
                .thenThrow(new DocumentThumbnailException(
                        DocumentThumbnailErrorCode.ORIGINAL_FILE_NOT_FOUND,
                        "The original file is not available."))
                .thenThrow(new DocumentThumbnailException(
                        DocumentThumbnailErrorCode.ORIGINAL_FILE_READ_FAILED,
                        "The original file is temporarily unavailable."));

        mockMvc.perform(get(ORIGINAL_ENDPOINT).with(userJwt(7L)))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
        mockMvc.perform(get(ORIGINAL_ENDPOINT).with(userJwt(7L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORIGINAL_FILE_NOT_FOUND"));
        mockMvc.perform(get(ORIGINAL_ENDPOINT).with(userJwt(7L)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ORIGINAL_FILE_READ_FAILED"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor userJwt(long ownerUserId) {
        return jwt()
                .jwt(token -> token.subject(Long.toString(ownerUserId)))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }
}
