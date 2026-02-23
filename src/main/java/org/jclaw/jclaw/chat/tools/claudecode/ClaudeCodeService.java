package org.jclaw.jclaw.chat.tools.claudecode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Executes the Claude Code CLI as a subprocess using --output-format stream-json,
 * printing live progress as each event arrives.
 *
 * Claude Code must be installed: npm install -g @anthropic-ai/claude-code
 * ANTHROPIC_API_KEY must be set in the environment.
 */
@Service
public class ClaudeCodeService {

    static void main() {
        new ClaudeCodeService().execute(
                "Create a file named hello.txt with the content 'Hello, World!'",
                null,
                null,
                10
        );
    }

    private static final Logger log = LoggerFactory.getLogger(ClaudeCodeService.class);
    private static final int TIMEOUT_SECONDS = 300;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * @param prompt    task/instruction for Claude Code
     * @param cwd       working directory (absolute path); null = inherit
     * @param sessionId resume a previous session; null = start fresh
     * @param maxTurns  max agentic turns (1-50)
     */
    public ClaudeCodeResponse execute(String prompt, String cwd, String sessionId, int maxTurns) {
        List<String> command = buildCommand(prompt, sessionId, maxTurns);
        log.debug("Executing: {}", command);

        ProcessBuilder pb = new ProcessBuilder(command);
        if (cwd != null && !cwd.isBlank()) {
            pb.directory(new File(cwd));
        }

        try {
            Process process = pb.start();

            // Drain stderr in background to avoid blocking
            CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    return "";
                }
            });

            // Stream stdout line-by-line, printing progress and capturing the result
            ClaudeCodeResponse result = null;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    ClaudeCodeResponse r = processLine(line);
                    if (r != null) result = r;
                }
            }

            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new RuntimeException("Claude Code timed out after " + TIMEOUT_SECONDS + "s");
            }

            String stderr = stderrFuture.get(5, TimeUnit.SECONDS);
            int exitCode = process.exitValue();

            if (!stderr.isBlank()) log.debug("stderr: {}", stderr);

            if (exitCode != 0) {
                String err = stderr.substring(0, Math.min(stderr.length(), 500));
                throw new RuntimeException("Claude Code failed (exit " + exitCode + "): " + err);
            }
            if (result == null) {
                throw new RuntimeException("Claude Code produced no result. Stderr: " + stderr);
            }
            return result;

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute Claude Code: " + e.getMessage(), e);
        }
    }

    /**
     * Parses one stream-json line, prints human-readable progress, and returns
     * a ClaudeCodeResponse only for the final "result" event (null for all others).
     */
    private ClaudeCodeResponse processLine(String line) {
        if (line.isBlank()) return null;
        try {
            JsonNode node = MAPPER.readTree(line);
            return switch (node.path("type").asText()) {
                case "system" -> {
                    System.out.println("[claude] starting (model: " + node.path("model").asText() + ")");
                    yield null;
                }
                case "assistant" -> {
                    for (JsonNode block : node.path("message").path("content")) {
                        switch (block.path("type").asText()) {
                            case "text" -> {
                                String text = block.path("text").asText().strip();
                                if (!text.isEmpty()) System.out.println("[claude] " + text);
                            }
                            case "tool_use" -> System.out.println(
                                    "[claude] → " + block.path("name").asText()
                                    + " " + block.path("input"));
                        }
                    }
                    yield null;
                }
                case "result" -> new ClaudeCodeResponse(
                        node.path("result").asText(),
                        node.path("session_id").asText(),
                        node.path("duration_ms").asLong(),
                        node.path("total_cost_usd").asDouble(),
                        "error".equals(node.path("subtype").asText()),
                        node.path("num_turns").asInt()
                );
                default -> null;
            };
        } catch (Exception e) {
            log.debug("Unrecognised stream line: {}", line);
            return null;
        }
    }

    private List<String> buildCommand(String prompt, String sessionId, int maxTurns) {
        List<String> cmd = new ArrayList<>(List.of(
                "claude",
                "--print", prompt,
                "--output-format", "stream-json",
                "--permission-mode", "bypassPermissions",
                "--max-turns", String.valueOf(maxTurns)
        ));
        if (sessionId != null && !sessionId.isBlank()) {
            cmd.addAll(List.of("--resume", sessionId));
        }
        return cmd;
    }
}
