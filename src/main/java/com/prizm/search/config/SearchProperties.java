package com.prizm.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 기본 검색 정책을 선택하며, 설정값으로 legacy dense 정책을 명시할 수 있다. */
@ConfigurationProperties("prizm.search")
public record SearchProperties(String profile) {

    public static final String DEFAULT_PROFILE = "source-dedup-evidence-signals-v1";

    public SearchProperties {
        profile = profile == null ? DEFAULT_PROFILE : profile;
        SearchProfile.fromProperty(profile);
    }

    public SearchProfile selectedProfile() {
        return SearchProfile.fromProperty(profile);
    }
}
