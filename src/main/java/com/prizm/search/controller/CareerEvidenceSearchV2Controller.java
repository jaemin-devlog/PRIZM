package com.prizm.search.controller;

import com.prizm.auth.security.CurrentUserProvider;
import com.prizm.search.dto.request.SearchRequest;
import com.prizm.search.dto.response.CareerEvidenceSearchV2Response;
import com.prizm.search.service.SearchService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/career-evidence")
class CareerEvidenceSearchV2Controller {

    private final SearchService searchService;
    private final CurrentUserProvider currentUserProvider;

    CareerEvidenceSearchV2Controller(SearchService searchService, CurrentUserProvider currentUserProvider) {
        this.searchService = searchService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/search")
    CareerEvidenceSearchV2Response search(@Valid @RequestBody SearchRequest request) {
        return searchService.searchCareerEvidenceV2(currentUserProvider.userId(), request.query());
    }
}
