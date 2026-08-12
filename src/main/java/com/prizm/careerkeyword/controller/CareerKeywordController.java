package com.prizm.careerkeyword.controller;

import com.prizm.auth.security.CurrentUserProvider;
import com.prizm.careerkeyword.dto.response.CareerKeywordEvidenceResponse;
import com.prizm.careerkeyword.dto.response.CareerKeywordMapResponse;
import com.prizm.careerkeyword.exception.InvalidCareerKeywordException;
import com.prizm.careerkeyword.service.CareerKeywordService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/career-keywords")
public class CareerKeywordController {

    private final CareerKeywordService service;
    private final CurrentUserProvider currentUserProvider;

    public CareerKeywordController(CareerKeywordService service, CurrentUserProvider currentUserProvider) {
        this.service = service;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public CareerKeywordMapResponse getKeywordMap() {
        return service.getKeywordMap(currentUserProvider.userId());
    }

    @GetMapping("/evidence")
    public CareerKeywordEvidenceResponse getEvidence(@RequestParam String keyword) {
        if (keyword.isBlank()) {
            throw new InvalidCareerKeywordException("keyword must not be blank");
        }
        if (keyword.length() > 100) {
            throw new InvalidCareerKeywordException("keyword must be at most 100 characters");
        }
        return service.getEvidence(currentUserProvider.userId(), keyword);
    }
}
