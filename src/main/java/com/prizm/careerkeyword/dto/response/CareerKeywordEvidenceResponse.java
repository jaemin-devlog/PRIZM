package com.prizm.careerkeyword.dto.response;

import java.util.List;

public record CareerKeywordEvidenceResponse(
        String keyword,
        int totalFrequency,
        List<CareerKeywordEvidenceItemResponse> evidence) {
}
