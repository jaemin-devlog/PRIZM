package com.prizm.search.controller;

import com.prizm.auth.security.CurrentUserProvider;
import com.prizm.search.dto.request.SearchRequest;
import com.prizm.search.dto.response.SearchResponse;
import com.prizm.search.service.SearchService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;
    private final CurrentUserProvider currentUserProvider;

    public SearchController(SearchService searchService, CurrentUserProvider currentUserProvider) {
        this.searchService = searchService;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * 질문을 받아 임베딩 기반 검색 결과를 반환한다.
     */
    @PostMapping
    public SearchResponse search(@Valid @RequestBody SearchRequest request) {
        return searchService.search(currentUserProvider.userId(), request.query());
    }
}
