package com.prizm.careerkeyword.dto.response;

import java.util.List;

public record CareerKeywordMapResponse(int documentCount, List<CareerKeywordSummaryResponse> keywords) {
}
