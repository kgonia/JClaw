package com.jclaw.agent.chat.tools.claudecode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaudeCodeSubagentToolsSummaryTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsCompactSummaryInsteadOfTranscript() throws Exception {
        Path claudeScript = createClaudeStub("""
                printf '%s\\n' \
                  '{"type":"system","subtype":"init","session_id":"sess-1"}' \
                  '{"type":"assistant","message":{"content":[{"type":"text","text":"Updated main.py to print Hello World"}]},"session_id":"sess-1"}' \
                  '{"type":"result","subtype":"success","result":"Updated main.py and verified Hello World output.","session_id":"sess-1"}'
                """);

        ClaudeCodeSubagentTools tools = ClaudeCodeSubagentTools.builder()
                .claudeCommand(claudeScript.toString())
                .build();

        String response = tools.claudeCodeSubagent("ignored", tempDir.toString(), null, 1, 5_000L, false);

        assertTrue(response.contains("status: Completed"));
        assertTrue(response.contains("claude_session_id: sess-1"));
        assertTrue(response.contains("summary: Updated main.py and verified Hello World output."));
        assertFalse(response.contains("STDOUT:"));
        assertFalse(response.contains("STDERR:"));
        assertFalse(response.contains("EVENT_SUMMARIES:"));
        assertFalse(response.contains("RAW_JSON_EVENTS:"));
    }

    @Test
    void debugOutputReturnsDetailedTraceWithoutRawJsonDump() throws Exception {
        Path claudeScript = createClaudeStub("""
                printf '%s\\n' \
                  '{"type":"system","subtype":"init","session_id":"sess-2"}' \
                  '{"type":"assistant","message":{"content":[{"type":"text","text":"Inspecting files"}]},"session_id":"sess-2"}' \
                  '{"type":"result","subtype":"success","result":"Task finished successfully.","session_id":"sess-2"}'
                printf 'minor warning\\n' >&2
                """);

        ClaudeCodeSubagentTools tools = ClaudeCodeSubagentTools.builder()
                .claudeCommand(claudeScript.toString())
                .build();

        String response = tools.claudeCodeSubagent("ignored", tempDir.toString(), null, 1, 5_000L, false);
        String subagentId = extractValue(response, "subagent_id:");

        String debugOutput = tools.claudeCodeSubagentDebugOutput(subagentId, 10);

        assertTrue(debugOutput.contains("EVENT_SUMMARIES:"));
        assertTrue(debugOutput.contains("Inspecting files"));
        assertTrue(debugOutput.contains("minor warning"));
        assertFalse(debugOutput.contains("RAW_JSON_EVENTS:"));
        assertFalse(debugOutput.contains("{\"type\":\"system\""));
    }

    @Test
    void timeoutResponseAndPollingStayCompact() throws Exception {
        Path claudeScript = createClaudeStub("""
                printf '%s\\n' '{"type":"system","subtype":"init","session_id":"sess-3"}'
                sleep 1
                printf '%s\\n' '{"type":"result","subtype":"success","result":"Background task finished.","session_id":"sess-3"}'
                """);

        ClaudeCodeSubagentTools tools = ClaudeCodeSubagentTools.builder()
                .claudeCommand(claudeScript.toString())
                .build();

        String timeoutResponse = tools.claudeCodeSubagent("ignored", tempDir.toString(), null, 1, 100L, false);
        String subagentId = extractValue(timeoutResponse, "subagent_id:");

        assertTrue(timeoutResponse.contains("status: Running"));
        assertTrue(timeoutResponse.contains("claude_session_id: sess-3"));
        assertTrue(timeoutResponse.contains("next_action: Use ClaudeCodeSubagentOutput"));
        assertFalse(timeoutResponse.contains("EVENT_SUMMARIES:"));

        Thread.sleep(1_200L);

        String polledResponse = tools.claudeCodeSubagentOutput(subagentId);
        assertTrue(polledResponse.contains("status: Completed"));
        assertTrue(polledResponse.contains("summary: Background task finished."));
        assertFalse(polledResponse.contains("STDOUT:"));
        assertFalse(polledResponse.contains("RAW_JSON_EVENTS:"));
    }

    @Test
    void parseFailureFailsTheRunAndTerminatesTheProcess() throws Exception {
        Path claudeScript = createClaudeStub("""
                printf 'not-json\\n'
                sleep 5
                """);

        ClaudeCodeSubagentTools tools = ClaudeCodeSubagentTools.builder()
                .claudeCommand(claudeScript.toString())
                .build();

        String response = tools.claudeCodeSubagent("ignored", tempDir.toString(), null, 1, 2_000L, false);
        String subagentId = extractValue(response, "subagent_id:");

        assertTrue(response.contains("status: Failed"));
        assertTrue(response.contains("claude_session_id: null"));
        assertTrue(response.contains("summary: Claude Code failed due to invalid JSON output."));

        String debugOutput = tools.claudeCodeSubagentDebugOutput(subagentId, 10);
        assertTrue(debugOutput.contains("parser_failed: true"));
        assertTrue(debugOutput.contains("PARSER_ERRORS:"));
        assertFalse(debugOutput.contains("RAW_JSON_EVENTS:"));
    }

    @Test
    void completedWithoutResultReturnsMinimalStatusSummary() throws Exception {
        Path claudeScript = createClaudeStub("""
                printf '%s\\n' '{"type":"system","subtype":"init","session_id":"sess-4"}'
                """);

        ClaudeCodeSubagentTools tools = ClaudeCodeSubagentTools.builder()
                .claudeCommand(claudeScript.toString())
                .build();

        String response = tools.claudeCodeSubagent("ignored", tempDir.toString(), null, 1, 5_000L, false);

        assertTrue(response.contains("status: Completed"));
        assertTrue(response.contains("claude_session_id: sess-4"));
        assertTrue(response.contains("summary: Claude Code completed without a final result event."));
        assertFalse(response.contains("Inspecting files"));
    }

    @Test
    void completedTraceExpiresAfterTtl() throws Exception {
        Path claudeScript = createClaudeStub("""
                printf '%s\\n' \
                  '{"type":"system","subtype":"init","session_id":"sess-5"}' \
                  '{"type":"result","subtype":"success","result":"done","session_id":"sess-5"}'
                """);

        ClaudeCodeSubagentTools tools = ClaudeCodeSubagentTools.builder()
                .claudeCommand(claudeScript.toString())
                .completedTraceTtlMs(100L)
                .build();

        String response = tools.claudeCodeSubagent("ignored", tempDir.toString(), null, 1, 5_000L, false);
        String subagentId = extractValue(response, "subagent_id:");

        Thread.sleep(150L);

        String expiredResponse = tools.claudeCodeSubagentOutput(subagentId);
        assertTrue(expiredResponse.contains("Error: No Claude run found with subagent_id:"));
    }

    private Path createClaudeStub(String body) throws IOException {
        Path script = tempDir.resolve("claude-stub.sh");
        Files.writeString(script, "#!/usr/bin/env bash\nset -euo pipefail\n" + body + "\n");
        script.toFile().setExecutable(true);
        return script;
    }

    private String extractValue(String response, String key) {
        for (String line : response.split("\\n")) {
            if (line.startsWith(key)) {
                return line.substring(key.length()).trim();
            }
        }
        throw new IllegalStateException("Missing key in response: " + key + "\n" + response);
    }
}
