package com.jclaw.agent.chat.tools.claudecode;

import com.jclaw.agent.chat.tools.process.StreamJsonParser;
import com.jclaw.agent.chat.tools.process.StreamParseException;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

public class ClaudeStreamJsonParser implements StreamJsonParser<ClaudeStreamEvent> {

    private static final JsonFactory JSON_FACTORY = new JsonFactory();
    private static final ObjectMapper MAPPER = new ObjectMapper(JSON_FACTORY);

    @Override
    public List<ClaudeStreamEvent> parseValue(String value) throws StreamParseException {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = MAPPER.readTree(value);
            if (root == null) {
                return List.of();
            }

            List<ClaudeStreamEvent> events = new ArrayList<>();
            if (root.isArray()) {
                for (JsonNode node : root) {
                    appendEvent(events, node);
                }
            }
            else if (root.isObject()) {
                appendEvent(events, root);
            }
            return events;
        }
        catch (Exception e) {
            throw new StreamParseException("Malformed Claude stream JSON", e);
        }
    }

    private void appendEvent(List<ClaudeStreamEvent> events, JsonNode node) {
        events.add(new ClaudeStreamEvent(
                text(node, "type"),
                text(node, "subtype"),
                text(node, "cwd"),
                text(node, "session_id"),
                text(node, "result"),
                extractMessage(node.get("message")),
                node.toString()
        ));
    }

    private ClaudeStreamEvent.Message extractMessage(JsonNode messageNode) {
        if (messageNode == null || messageNode.isNull()) {
            return null;
        }
        if (messageNode.isTextual()) {
            return new ClaudeStreamEvent.Message(
                    null,
                    null,
                    List.of(new ClaudeStreamEvent.Content("text", null, messageNode.asText()))
            );
        }

        String type = text(messageNode, "type");
        String role = text(messageNode, "role");
        JsonNode content = messageNode.get("content");
        if (content == null || !content.isArray()) {
            if (type == null && role == null) {
                return null;
            }
            return new ClaudeStreamEvent.Message(type, role, List.of());
        }

        List<ClaudeStreamEvent.Content> items = new ArrayList<>();
        for (JsonNode item : content) {
            items.add(new ClaudeStreamEvent.Content(
                    text(item, "type"),
                    text(item, "thinking"),
                    text(item, "text")
            ));
        }

        if (type == null && role == null && items.isEmpty()) {
            return null;
        }
        return new ClaudeStreamEvent.Message(type, role, items);
    }

    private String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return (value != null && value.isTextual()) ? value.asText() : null;
    }
}
