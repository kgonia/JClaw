package com.jclaw.agent.tui.workflow;

import java.time.Instant;

public record WorkflowProjectionEvent(
        WorkflowEventType type,
        String status,
        String title,
        String detail,
        String filePath,
        RiskAnnotation risk,
        Double estimatedCostUsd,
        Instant occurredAt) {

    public WorkflowProjectionEvent {
        type = type == null ? WorkflowEventType.STEP : type;
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }

    public static WorkflowProjectionEvent status(String status, String detail) {
        return new WorkflowProjectionEvent(WorkflowEventType.STATUS, status, null, detail, null, null, null, Instant.now());
    }

    public static WorkflowProjectionEvent step(String title, String detail) {
        return new WorkflowProjectionEvent(WorkflowEventType.STEP, null, title, detail, null, null, null, Instant.now());
    }

    public static WorkflowProjectionEvent result(String detail) {
        return new WorkflowProjectionEvent(WorkflowEventType.RESULT, "Completed", "Result", detail, null, null, null, Instant.now());
    }

    public static WorkflowProjectionEvent risk(RiskAnnotation risk, String detail) {
        return new WorkflowProjectionEvent(WorkflowEventType.RISK, null, "Risk", detail, null, risk, null, Instant.now());
    }

    public static WorkflowProjectionEvent estimatedCost(double usd) {
        return new WorkflowProjectionEvent(WorkflowEventType.COST, null, "Estimated cost", null, null, null, usd, Instant.now());
    }
}
