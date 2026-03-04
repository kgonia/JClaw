package com.jclaw.agent.chat.tools.claudecode;

import java.util.List;

public record ClaudeStreamEvent(String type, String subtype, String cwd, String sessionId, String result,
                                Message message, String rawJson) {

    public ClaudeStreamEvent {
        if (message != null) {
            message = new Message(message.type(), message.role(), message.content());
        }
    }

    public record Message(String type, String role, List<Content> content) {

        public Message {
            content = content == null ? List.of() : List.copyOf(content);
        }
    }

    public record Content(String type, String thinking, String text) {
    }

    public String summary() {
        StringBuilder summary = new StringBuilder();
        append(summary, "type", type);
        append(summary, "subtype", subtype);
        append(summary, "cwd", cwd);
        append(summary, "session_id", sessionId);
        append(summary, "result", result);
        append(summary, "message", normalizeMessage(messageText()));

        if (summary.isEmpty()) {
            return rawJson == null ? "" : rawJson;
        }
        return summary.toString();
    }

    public String messageText() {
        if (message == null || message.content().isEmpty()) {
            return null;
        }

        StringBuilder out = new StringBuilder();
        for (Content item : message.content()) {
            String itemType = item.type();
            appendLine(out, itemType != null ? itemType : "text", item.text());
            appendLine(out, itemType != null ? itemType + ".thinking" : "thinking", item.thinking());
        }
        return out.isEmpty() ? null : out.toString();
    }

    private static String normalizeMessage(String value) {
        if (value == null) {
            return null;
        }
        return value.replace('\n', ' ').trim();
    }

    private static void appendLine(StringBuilder out, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!out.isEmpty()) {
            out.append('\n');
        }
        if (label != null) {
            out.append(label).append(": ");
        }
        out.append(value);
    }

    private static void append(StringBuilder out, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!out.isEmpty()) {
            out.append(" | ");
        }
        out.append(label).append('=').append(value);
    }
}
