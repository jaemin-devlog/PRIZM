package com.prizm.search.controller;

import com.prizm.auth.security.CurrentUserProvider;
import com.prizm.search.dto.request.SearchRequest;
import com.prizm.search.dto.response.CareerEvidenceSearchResponse;
import com.prizm.search.service.SearchService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/career-evidence")
class CareerEvidenceSearchController {

    private final SearchService searchService;
    private final CurrentUserProvider currentUserProvider;

    CareerEvidenceSearchController(SearchService searchService, CurrentUserProvider currentUserProvider) {
        this.searchService = searchService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/search")
    List<CareerEvidenceSearchResponse> search(@Valid @RequestBody SearchRequest request) {
        return searchService.searchCareerEvidence(currentUserProvider.userId(), request.query());
    }
}
