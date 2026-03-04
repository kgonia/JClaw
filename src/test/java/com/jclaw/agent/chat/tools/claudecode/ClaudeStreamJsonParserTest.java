package com.jclaw.agent.chat.tools.claudecode;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaudeStreamJsonParserTest {

    private final ClaudeStreamJsonParser parser = new ClaudeStreamJsonParser();

    @Test
    void returnsNoEventsForBlankInput() throws Exception {
        assertTrue(parser.parseLine(null).isEmpty());
        assertTrue(parser.parseLine("").isEmpty());
        assertTrue(parser.parseLine("   ").isEmpty());
    }

    @Test
    void parsesSingleStreamJsonObject() throws Exception {
        List<ClaudeStreamEvent> events = parser.parseLine("""
                {"type":"assistant","session_id":"sess-1","message":{"type":"message","role":"assistant","content":[
                {"type":"thinking","thinking":"plan"},
                {"type":"text","text":"hello"}
                ]}}
                """);

        assertEquals(1, events.size());

        ClaudeStreamEvent event = events.getFirst();
        assertEquals("assistant", event.type());
        assertEquals("sess-1", event.sessionId());
        assertNotNull(event.message());
        assertEquals("message", event.message().type());
        assertEquals("assistant", event.message().role());
        assertEquals(2, event.message().content().size());
        assertEquals("thinking", event.message().content().get(0).type());
        assertEquals("plan", event.message().content().get(0).thinking());
        assertEquals("text", event.message().content().get(1).type());
        assertEquals("hello", event.message().content().get(1).text());
    }

    @Test
    void parsesFixtureLinesAndExtractsExpectedFields() throws Exception {
        // This fixture is raw line-delimited `stream-json` output, not `jq -s` output.
        // Each line is a complete Claude event, matching the observed CLI behavior on February 28, 2026.
        Path fixture = Path.of(Objects.requireNonNull(
                getClass().getClassLoader().getResource("claude-response.json")).toURI());

        List<ClaudeStreamEvent> events = new ArrayList<>();
        for (String line : Files.readAllLines(fixture)) {
            events.addAll(parser.parseLine(line));
        }

        assertEquals(5, events.size());

        ClaudeStreamEvent init = events.get(0);
        assertEquals("system", init.type());
        assertEquals("init", init.subtype());
        assertEquals("/home/krz", init.cwd());
        assertEquals("6ad9d154-7257-4afd-887f-5150ba7f08dc", init.sessionId());

        ClaudeStreamEvent thinking = events.get(1);
        assertEquals("assistant", thinking.type());
        assertNotNull(thinking.message());
        assertEquals("message", thinking.message().type());
        assertEquals("assistant", thinking.message().role());
        assertEquals(1, thinking.message().content().size());
        assertEquals("thinking", thinking.message().content().getFirst().type());
        assertEquals("The user wants a very short story about a dog.",
                thinking.message().content().getFirst().thinking());

        ClaudeStreamEvent text = events.get(2);
        assertEquals("assistant", text.type());
        assertNotNull(text.message());
        assertEquals("message", text.message().type());
        assertEquals("assistant", text.message().role());
        assertEquals(1, text.message().content().size());
        assertEquals("text", text.message().content().getFirst().type());
        assertTrue(text.message().content().getFirst().text().startsWith("Max found a stick at the edge of the yard"));
        assertTrue(text.message().content().getFirst().text().contains("His owner looked up from the phone"));

        ClaudeStreamEvent result = events.get(4);
        assertEquals("result", result.type());
        assertEquals("success", result.subtype());
        assertTrue(result.result().startsWith("Max found a stick at the edge of the yard"));
    }
}
