package com.jclaw.agent.tui.workflow;

import com.jclaw.agent.chat.tools.claudecode.ClaudeStreamEvent;
import com.jclaw.agent.chat.tools.process.ProcessOutputListener;
import com.jclaw.agent.tui.event.AppEvent;
import com.jclaw.agent.tui.event.AppEventBus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class ClaudeWorkflowAdapter implements ProcessOutputListener<ClaudeStreamEvent> {

    private final AppEventBus bus;
    private final ObjectMapper objectMapper;

    public ClaudeWorkflowAdapter(AppEventBus bus, ObjectMapper objectMapper) {
        this.bus = bus;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onStarted(String processId, Process process) {
        append(WorkflowProjectionEvent.status("Running", "Claude Code started"));
    }

    @Override
    public void onStderrLine(String line) {
        if (line != null && !line.isBlank()) {
            append(WorkflowProjectionEvent.risk(new RiskAnnotation(RiskLevel.MEDIUM, line), "Claude stderr"));
        }
    }

    @Override
    public void onParsedEvent(ClaudeStreamEvent event) {
        if (event == null) {
            return;
        }

        if ("system".equals(event.type()) && "init".equals(event.subtype())) {
            append(WorkflowProjectionEvent.step("Claude session initialized", event.sessionId()));
            return;
        }
        if ("assistant".equals(event.type()) && event.message() != null) {
            String message = event.messageText();
            if (message != null && !message.isBlank()) {
                append(WorkflowProjectionEvent.step("Claude output", message));
            }
            return;
        }
        if ("result".equals(event.type())) {
            append(WorkflowProjectionEvent.result(event.result()));
            extractTotalCost(event).ifPresent(cost -> append(WorkflowProjectionEvent.estimatedCost(cost)));
        }
    }

    @Override
    public void onParserError(String line, Exception exception) {
        String detail = exception == null ? "Parser error" : exception.getMessage();
        append(WorkflowProjectionEvent.risk(new RiskAnnotation(RiskLevel.HIGH, detail), detail));
    }

    @Override
    public void onCompleted(int exitCode) {
        append(WorkflowProjectionEvent.status(exitCode == 0 ? "Completed" : "Failed",
                exitCode == 0 ? "Claude Code completed" : "Claude Code exited with " + exitCode));
    }

    private java.util.Optional<Double> extractTotalCost(ClaudeStreamEvent event) {
        if (event.rawJson() == null || event.rawJson().isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(event.rawJson());
            JsonNode totalCost = root.get("total_cost_usd");
            return totalCost != null && totalCost.isNumber() ? java.util.Optional.of(totalCost.asDouble()) : java.util.Optional.empty();
        }
        catch (Exception e) {
            return java.util.Optional.empty();
        }
    }

    private void append(WorkflowProjectionEvent event) {
        bus.dispatch(new AppEvent.WorkflowUpdate(event));
    }
}
