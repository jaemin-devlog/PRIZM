package com.prizm.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Search profile selection with an explicit legacy rollback override. */
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
