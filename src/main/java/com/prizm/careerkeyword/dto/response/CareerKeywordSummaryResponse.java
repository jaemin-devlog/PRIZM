package com.prizm.careerkeyword.dto.response;

import com.prizm.careerkeyword.model.CareerKeywordCategory;
import java.util.List;

public record CareerKeywordSummaryResponse(
        String keyword,
        CareerKeywordCategory category,
        int frequency,
        int documentCount,
        List<String> variants) {
}
