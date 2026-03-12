package com.jclaw.agent.tui.session;

import java.time.Instant;

public record SessionDescriptor(String id, String workingDirectory, Instant createdAt, Instant lastAccessedAt) {
}
