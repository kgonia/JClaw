package com.jclaw.agent.tui.workflow;

import java.time.Instant;

public record WorkflowStep(String title, String detail, Instant createdAt) {
}
