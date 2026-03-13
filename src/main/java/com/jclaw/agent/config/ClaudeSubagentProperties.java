package com.jclaw.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("jclaw.subagent")
public record ClaudeSubagentProperties(
        boolean enabled,
        String claudeCommand,
        String permissionMode,
        long defaultTimeoutMs,
        long maxTimeoutMs,
        long completedTraceTtlMs,
        int maxOutputCharacters) {

    public ClaudeSubagentProperties {
        claudeCommand = hasText(claudeCommand) ? claudeCommand : "claude";
        permissionMode = hasText(permissionMode) ? permissionMode : "bypassPermissions";
        defaultTimeoutMs = defaultTimeoutMs > 0 ? defaultTimeoutMs : 120_000L;
        maxTimeoutMs = maxTimeoutMs > 0 ? maxTimeoutMs : 600_000L;
        completedTraceTtlMs = completedTraceTtlMs > 0 ? completedTraceTtlMs : 600_000L;
        maxOutputCharacters = maxOutputCharacters > 0 ? maxOutputCharacters : 30_000;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
