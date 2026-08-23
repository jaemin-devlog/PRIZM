package com.prizm.documenttag.controller;

import com.prizm.auth.security.CurrentUserProvider;
import com.prizm.documenttag.dto.TagResponse;
import com.prizm.documenttag.dto.TagSelectionRequest;
import com.prizm.documenttag.service.DocumentTagService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents/{documentId}/tags")
public class DocumentTagController {

    private final DocumentTagService tagService;
    private final CurrentUserProvider currentUserProvider;

    public DocumentTagController(DocumentTagService tagService, CurrentUserProvider currentUserProvider) {
        this.tagService = tagService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public List<TagResponse> getTags(@PathVariable Long documentId) {
        return tagService.getDocumentTags(currentUserProvider.userId(), documentId);
    }

    @PutMapping
    public List<TagResponse> replaceTags(
            @PathVariable Long documentId,
            @Valid @RequestBody TagSelectionRequest request) {
        return tagService.replaceDocumentTags(
                currentUserProvider.userId(),
                documentId,
                request.tagIds());
    }

    @DeleteMapping("/{tagId}")
    public ResponseEntity<Void> removeTag(
            @PathVariable Long documentId,
            @PathVariable Long tagId) {
        tagService.removeDocumentTag(currentUserProvider.userId(), documentId, tagId);
        return ResponseEntity.noContent().build();
    }
}
