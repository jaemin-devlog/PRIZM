package com.prizm.documenttag.controller;

import com.prizm.auth.security.CurrentUserProvider;
import com.prizm.documenttag.dto.CreateTagRequest;
import com.prizm.documenttag.dto.TagResponse;
import com.prizm.documenttag.dto.TagUsageResponse;
import com.prizm.documenttag.dto.TaggedDocumentsResponse;
import com.prizm.documenttag.service.DocumentTagService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/tags")
public class TagController {

    private final DocumentTagService tagService;
    private final CurrentUserProvider currentUserProvider;

    public TagController(DocumentTagService tagService, CurrentUserProvider currentUserProvider) {
        this.tagService = tagService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public List<TagResponse> search(
            @RequestParam(required = false) @Size(max = 100) String query) {
        return tagService.search(currentUserProvider.userId(), query);
    }

    @PostMapping
    public ResponseEntity<TagResponse> create(@Valid @RequestBody CreateTagRequest request) {
        var result = tagService.createUserTag(currentUserProvider.userId(), request.name());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.tag());
    }

    @GetMapping("/usage")
    public List<TagUsageResponse> usage() {
        return tagService.getUsage(currentUserProvider.userId());
    }

    @GetMapping("/{tagId}/documents")
    public TaggedDocumentsResponse taggedDocuments(@PathVariable Long tagId) {
        return tagService.getTaggedDocuments(currentUserProvider.userId(), tagId);
    }
}
