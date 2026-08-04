package com.prizm.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("prizm.local-demo")
public record LocalDemoProperties(boolean enabled) {}
