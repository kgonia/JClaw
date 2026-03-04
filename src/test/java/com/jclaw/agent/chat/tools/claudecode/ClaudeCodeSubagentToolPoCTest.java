package com.jclaw.agent.chat.tools.claudecode;

import com.jclaw.agent.chat.tools.process.BackgroundProcess;
import com.jclaw.agent.chat.tools.process.ProcessOutputListener;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaudeCodeSubagentToolPoCTest {

    @Test
    void parsesLineDelimitedStreamJsonLikeClaudeCode() throws Exception {
        // Observed with Claude Code 2.1.56:
        // `claude -p --output-format stream-json --verbose ...` emits one compact JSON object per line on stdout.
        Path jsonResponse = Path.of(Objects.requireNonNull(
                getClass().getClassLoader().getResource("claude-response.json")).toURI());

        Process process = new ProcessBuilder(
                "/bin/bash",
                "-c",
                // Stream the fixture line-by-line to match the current Claude Code transport shape.
                "while IFS= read -r line; do printf '%s\\n' \"$line\"; sleep 0.01; done < \"$1\"",
                "bash",
                jsonResponse.toString()
        ).start();
        process.getOutputStream().close();

        List<ClaudeStreamEvent> events = new CopyOnWriteArrayList<>();
        List<String> stdoutLines = new CopyOnWriteArrayList<>();

        ProcessOutputListener<ClaudeStreamEvent> listener = new ProcessOutputListener<>() {
            @Override
            public void onStdoutLine(String line) {
                stdoutLines.add(line);
            }

            @Override
            public void onParsedEvent(ClaudeStreamEvent event) {
                events.add(event);
            }
        };

        BackgroundProcess<ClaudeStreamEvent> bgProcess = BackgroundProcess.start(
                "test",
                process,
                new ClaudeStreamJsonParser(),
                listener
        );

        assertTrue(bgProcess.awaitExit(5, TimeUnit.SECONDS));

        assertTrue(stdoutLines.getFirst().contains("\"type\":\"system\""));
        assertTrue(bgProcess.trace().stderr().isEmpty());
        assertEquals(5, events.size());
        assertEquals(5, stdoutLines.size());
        assertEquals(5, bgProcess.trace().events().size());
        assertEquals("system", events.get(0).type());
        assertEquals("result", events.get(4).type());
        assertEquals("success", events.get(4).subtype());
    }
}
