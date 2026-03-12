package com.jclaw.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("jclaw.ai")
public record AiProperties(String systemPrompt, int maxMessages) {

    public AiProperties {
        systemPrompt = systemPrompt == null ? "" : systemPrompt;
        maxMessages = maxMessages <= 0 ? 100 : maxMessages;
    }
}
