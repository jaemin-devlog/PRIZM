package com.prizm.documenttag.controller;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class TagControllerTest {

    @Mock DocumentTagService tagService;
    @Mock CurrentUserProvider currentUserProvider;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        lenient().when(currentUserProvider.userId()).thenReturn(7L);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new TagController(tagService, currentUserProvider))
                .setControllerAdvice(new DocumentTagExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void searchesAccessibleTags() throws Exception {
        when(tagService.search(7L, "Spring")).thenReturn(List.of(
                new TagResponse(1L, "Spring Boot", TagSource.SYSTEM)));

        mockMvc.perform(get("/api/tags").param("query", "Spring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Spring Boot"))
                .andExpect(jsonPath("$[0].source").value("SYSTEM"));
    }

    @Test
    void createsAnUnseenUserTag() throws Exception {
        when(tagService.createUserTag(7L, "Tauri")).thenReturn(new DocumentTagService.CreateResult(
                new TagResponse(9L, "Tauri", TagSource.USER),
                true));

        mockMvc.perform(post("/api/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tauri\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tagId").value(9))
                .andExpect(jsonPath("$.source").value("USER"));
    }
}
