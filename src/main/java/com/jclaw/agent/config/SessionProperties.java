package com.jclaw.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("jclaw.session")
public record SessionProperties(
        String projectDirectoryName,
        String sessionDatabaseName,
        String globalDirectoryName,
        String globalConfigDatabaseName) {

    public SessionProperties {
        projectDirectoryName = hasText(projectDirectoryName) ? projectDirectoryName : ".jclaw";
        sessionDatabaseName = hasText(sessionDatabaseName) ? sessionDatabaseName : "sessions";
        globalDirectoryName = hasText(globalDirectoryName) ? globalDirectoryName : ".jclaw";
        globalConfigDatabaseName = hasText(globalConfigDatabaseName) ? globalConfigDatabaseName : "config";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
