package com.prizm.documenttag.controller;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.prizm.auth.security.CurrentUserProvider;
import com.prizm.documenttag.dto.TagResponse;
import com.prizm.documenttag.model.TagSource;
import com.prizm.documenttag.service.DocumentTagService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@ExtendWith(MockitoExtension.class)
class DocumentTagControllerTest {

    @Mock DocumentTagService tagService;
    @Mock CurrentUserProvider currentUserProvider;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        lenient().when(currentUserProvider.userId()).thenReturn(7L);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new DocumentTagController(tagService, currentUserProvider))
                .setControllerAdvice(new DocumentTagExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void returnsOnlyTheCurrentOwnersDocumentTags() throws Exception {
        when(tagService.getDocumentTags(7L, 31L)).thenReturn(List.of(
                new TagResponse(4L, "Redis", TagSource.SYSTEM)));

        mockMvc.perform(get("/api/documents/31/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tagId").value(4))
                .andExpect(jsonPath("$[0].name").value("Redis"));
    }

    @Test
    void replacesMultipleTagsForTheCurrentOwner() throws Exception {
        when(tagService.replaceDocumentTags(7L, 31L, List.of(4L, 8L))).thenReturn(List.of(
                new TagResponse(4L, "Redis", TagSource.SYSTEM),
                new TagResponse(8L, "Tauri", TagSource.USER)));

        mockMvc.perform(put("/api/documents/31/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tagIds\":[4,8]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].name").value("Tauri"));
    }

    @Test
    void removesOneTagWithinTheCurrentOwnerScope() throws Exception {
        mockMvc.perform(delete("/api/documents/31/tags/8"))
                .andExpect(status().isNoContent());

        verify(tagService).removeDocumentTag(7L, 31L, 8L);
    }
}
