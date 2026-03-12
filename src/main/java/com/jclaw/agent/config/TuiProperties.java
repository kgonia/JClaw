package com.jclaw.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("jclaw.tui")
public record TuiProperties(
        String appName,
        long tickRateMs,
        long resizeGracePeriodMs) {

    public TuiProperties {
        appName = hasText(appName) ? appName : "Jclaw";
        tickRateMs = tickRateMs > 0 ? tickRateMs : 75L;
        resizeGracePeriodMs = resizeGracePeriodMs > 0 ? resizeGracePeriodMs : 50L;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
