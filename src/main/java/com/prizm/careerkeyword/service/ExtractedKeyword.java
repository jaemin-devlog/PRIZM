package com.prizm.careerkeyword.service;

import com.prizm.careerkeyword.model.CareerKeywordCategory;
import java.util.List;

record ExtractedKeyword(
        String normalized,
        String keyword,
        CareerKeywordCategory category,
        int frequency,
        int firstIndex,
        int firstMatchLength,
        List<String> matchedTerms) {
}
