package com.jclaw.agent.chat.tools.claudecode;

import com.jclaw.agent.chat.tools.process.BackgroundProcess;
import com.jclaw.agent.chat.tools.process.ProcessOutputListener;
import com.jclaw.agent.chat.tools.process.ProcessTrace;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ClaudeCodeSubagentTools {

    private static final int SUMMARY_TEXT_LIMIT = 500;
    private static final int DEBUG_EVENT_LIMIT = 50;
    private static final long DEFAULT_COMPLETED_TRACE_TTL_MS = 10 * 60 * 1000L;

    private final Map<String, BackgroundProcess<ClaudeStreamEvent>> runs = new ConcurrentHashMap<>();

    private final ProcessOutputListener<ClaudeStreamEvent> listener;
    private final String claudeCommand;
    private final String permissionMode;
    private final String defaultWorkingDirectory;
    private final long defaultTimeoutMs;
    private final long maxTimeoutMs;
    private final long completedTraceTtlMs;
    private final int maxOutputCharacters;

    private ClaudeCodeSubagentTools(Builder builder) {
        this.listener = builder.listener;
        this.claudeCommand = builder.claudeCommand;
        this.permissionMode = builder.permissionMode;
        this.defaultWorkingDirectory = builder.defaultWorkingDirectory;
        this.defaultTimeoutMs = builder.defaultTimeoutMs;
        this.maxTimeoutMs = builder.maxTimeoutMs;
        this.completedTraceTtlMs = builder.completedTraceTtlMs;
        this.maxOutputCharacters = builder.maxOutputCharacters;
    }

    // @formatter:off
    @Tool(name = "ClaudeCodeSubagent", description = """
            Runs Claude Code CLI as a coding subagent using strict stream-json output.
            Returns only a concise summary so the main conversation stays compact.
            Detailed events remain available to listeners and ClaudeCodeSubagentDebugOutput.
            If the foreground wait times out, the process keeps running in background and can be polled by subagent_id.
            """)
    public String claudeCodeSubagent(
            @ToolParam(description = "The coding task prompt for Claude Code") String task,
            @ToolParam(description = "Optional working directory path. Defaults to the main app working directory.", required = false) String working_directory,
            @ToolParam(description = "Optional Claude session_id to resume", required = false) String session_id,
            @ToolParam(description = "Maximum turns (1-50, default 10)", required = false) Integer max_turns,
            @ToolParam(description = "Timeout for foreground execution in milliseconds (max 600000, default 120000)", required = false) Long timeout_ms,
            @ToolParam(description = "Set to true to run in background and retrieve summary later with ClaudeCodeSubagentOutput", required = false) Boolean run_in_background) { // @formatter:on

        evictExpiredRuns();

        String subagentId = "claude_" + UUID.randomUUID();
        int turns = sanitizeTurns(max_turns);
        long timeout = sanitizeTimeout(timeout_ms);

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(buildCommand(task, session_id, turns));
            processBuilder.redirectErrorStream(false);
            processBuilder.directory(resolveWorkingDirectory(working_directory));

            Process process = processBuilder.start();
            process.getOutputStream().close();

            BackgroundProcess<ClaudeStreamEvent> backgroundProcess = BackgroundProcess.start(
                    subagentId,
                    process,
                    new ClaudeStreamJsonParser(),
                    listener,
                    true
            );
            runs.put(subagentId, backgroundProcess);

            if (Boolean.TRUE.equals(run_in_background)) {
                return truncate(formatSummaryResponse(subagentId, backgroundProcess.trace(),
                        "Background Claude process started.",
                        "Use ClaudeCodeSubagentOutput with subagent_id='" + subagentId + "' to poll the concise status."));
            }

            boolean completed = backgroundProcess.awaitExit(timeout, TimeUnit.MILLISECONDS);
            ClaudeRunProjection projection = project(backgroundProcess.trace());
            if (!completed && "Running".equals(projection.status())) {
                return truncate(formatSummaryResponse(subagentId, backgroundProcess.trace(),
                        "Foreground timeout after " + timeout + "ms. The process is still running in background.",
                        "Use ClaudeCodeSubagentOutput with subagent_id='" + subagentId + "'. Do not start the same task again unless you intend a second run."));
            }

            return truncate(formatSummaryResponse(subagentId, backgroundProcess.trace(), null, null));
        }
        catch (IllegalArgumentException e) {
            return "Invalid arguments: " + e.getMessage();
        }
        catch (IOException e) {
            return "Failed to start Claude Code process: " + e.getMessage();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            BackgroundProcess<ClaudeStreamEvent> backgroundProcess = runs.get(subagentId);
            if (backgroundProcess == null) {
                return "Interrupted while waiting for Claude Code.";
            }
            return truncate(formatSummaryResponse(subagentId, backgroundProcess.trace(),
                    "Foreground wait was interrupted. The process continues in background.",
                    "Use ClaudeCodeSubagentOutput with subagent_id='" + subagentId + "' to continue polling."));
        }
    }

    // @formatter:off
    @Tool(name = "ClaudeCodeSubagentOutput", description = """
            Retrieves only the concise status and summary for a running or completed Claude Code subagent.
            This tool intentionally does not return the full transcript or raw JSON output.
            Use ClaudeCodeSubagentDebugOutput only when detailed inspection is explicitly needed.
            """)
    public String claudeCodeSubagentOutput(
            @ToolParam(description = "subagent_id returned by ClaudeCodeSubagent") String subagent_id) { // @formatter:on

        evictExpiredRuns();
        BackgroundProcess<ClaudeStreamEvent> backgroundProcess = runs.get(subagent_id);
        if (backgroundProcess == null) {
            return "Error: No Claude run found with subagent_id: " + subagent_id;
        }

        ClaudeRunProjection projection = project(backgroundProcess.trace());
        String nextAction = "Running".equals(projection.status())
                ? "Process still running. Poll ClaudeCodeSubagentOutput again later or use ClaudeCodeSubagentDebugOutput for detailed trace."
                : null;
        return truncate(formatSummaryResponse(subagent_id, backgroundProcess.trace(), null, nextAction));
    }

    // @formatter:off
    @Tool(name = "ClaudeCodeSubagentDebugOutput", description = """
            Returns detailed debugging information for a Claude Code subagent.
            Use this only when you explicitly need the captured stderr, parser errors, reader errors, or parsed event summaries.
            Raw JSON stays attached to each parsed event internally but is not printed by this tool.
            """)
    public String claudeCodeSubagentDebugOutput(
            @ToolParam(description = "subagent_id returned by ClaudeCodeSubagent") String subagent_id,
            @ToolParam(description = "Maximum number of recent events to include (default 50)", required = false) Integer max_events) { // @formatter:on

        evictExpiredRuns();
        BackgroundProcess<ClaudeStreamEvent> backgroundProcess = runs.get(subagent_id);
        if (backgroundProcess == null) {
            return "Error: No Claude run found with subagent_id: " + subagent_id;
        }

        int maxEvents = max_events == null ? DEBUG_EVENT_LIMIT : Math.max(1, max_events);
        return truncate(formatDebugResponse(subagent_id, backgroundProcess.trace(), maxEvents));
    }

    // @formatter:off
    @Tool(name = "KillClaudeCodeSubagent", description = """
            Kills a running Claude Code subagent by its subagent_id.
            Completed traces remain available for polling/debug until the TTL expires.
            """)
    public String killClaudeCodeSubagent(
            @ToolParam(description = "subagent_id returned by ClaudeCodeSubagent") String subagent_id) { // @formatter:on

        evictExpiredRuns();
        BackgroundProcess<ClaudeStreamEvent> backgroundProcess = runs.get(subagent_id);
        if (backgroundProcess == null) {
            return "Error: No Claude run found with subagent_id: " + subagent_id;
        }
        if (!backgroundProcess.isAlive()) {
            return "Claude process " + subagent_id + " already completed.";
        }

        backgroundProcess.markKillRequested();
        backgroundProcess.destroy();
        return "Successfully killed Claude process: " + subagent_id;
    }

    private String formatSummaryResponse(String subagentId, ProcessTrace<ClaudeStreamEvent> trace,
                                         String note, String nextAction) {
        ClaudeRunProjection projection = project(trace);
        StringBuilder result = new StringBuilder();
        result.append("subagent_id: ").append(subagentId).append('\n');
        result.append("status: ").append(projection.status()).append('\n');
        result.append("claude_session_id: ").append(nullSafe(projection.claudeSessionId())).append('\n');
        result.append("exit_code: ").append(nullSafe(projection.exitCode())).append('\n');
        result.append("event_count: ").append(projection.eventCount()).append('\n');
        result.append("summary: ").append(projection.summary()).append('\n');
        if (StringUtils.hasText(note)) {
            result.append("note: ").append(note).append('\n');
        }
        if (StringUtils.hasText(nextAction)) {
            result.append("next_action: ").append(nextAction).append('\n');
        }
        return result.toString();
    }

    private String formatDebugResponse(String subagentId, ProcessTrace<ClaudeStreamEvent> trace, int maxEvents) {
        ClaudeRunProjection projection = project(trace);
        StringBuilder out = new StringBuilder();
        out.append("subagent_id: ").append(subagentId).append('\n');
        out.append("status: ").append(projection.status()).append('\n');
        out.append("claude_session_id: ").append(nullSafe(projection.claudeSessionId())).append('\n');
        out.append("exit_code: ").append(nullSafe(projection.exitCode())).append('\n');
        if (trace.pid() > 0) {
            out.append("pid: ").append(trace.pid()).append('\n');
        }
        out.append("event_count: ").append(projection.eventCount()).append('\n');
        out.append("parser_failed: ").append(trace.parserFailed()).append('\n');
        out.append("kill_requested: ").append(trace.killRequested()).append('\n');
        if (!trace.isRunning()) {
            out.append("duration_ms: ").append(trace.completedAt() - trace.startedAt()).append('\n');
        }
        out.append("summary: ").append(projection.summary()).append('\n');

        appendSection(out, "STDERR", trimTrailingNewline(trace.stderr()));
        appendListSection(out, "PARSER_ERRORS", trace.parserErrors());
        appendListSection(out, "READER_ERRORS", trace.readerErrors());

        List<ClaudeStreamEvent> events = trace.events();
        if (!events.isEmpty()) {
            out.append("\nEVENT_SUMMARIES:\n");
            int fromIndex = Math.max(0, events.size() - maxEvents);
            for (ClaudeStreamEvent event : events.subList(fromIndex, events.size())) {
                out.append("- ").append(event.summary()).append('\n');
            }
        }
        return out.toString();
    }

    private List<String> buildCommand(String task, String sessionId, int maxTurns) {
        List<String> command = new ArrayList<>();
        command.add(claudeCommand);
        command.add("--verbose");
        command.add("--permission-mode");
        command.add(permissionMode);
        command.add("--max-turns");
        command.add(Integer.toString(maxTurns));
        command.add("--output-format");
        command.add("stream-json");
        if (StringUtils.hasText(sessionId)) {
            command.add("--resume");
            command.add(sessionId.trim());
        }
        command.add("--print");
        command.add(task);
        return command;
    }

    private File resolveWorkingDirectory(String workingDirectory) {
        String candidate = StringUtils.hasText(workingDirectory) ? workingDirectory : defaultWorkingDirectory;
        File dir = new File(candidate);
        if (!dir.exists()) {
            throw new IllegalArgumentException("working_directory does not exist: " + candidate);
        }
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException("working_directory is not a directory: " + candidate);
        }
        return dir;
    }

    private int sanitizeTurns(Integer maxTurns) {
        if (maxTurns == null) {
            return 10;
        }
        return Math.max(1, Math.min(maxTurns, 50));
    }

    private long sanitizeTimeout(Long timeoutMs) {
        if (timeoutMs == null) {
            return defaultTimeoutMs;
        }
        return Math.max(1L, Math.min(timeoutMs, maxTimeoutMs));
    }

    private String truncate(String output) {
        if (output.length() <= maxOutputCharacters) {
            return output;
        }
        return output.substring(0, maxOutputCharacters) + "\n... (output truncated)";
    }

    private void evictExpiredRuns() {
        long now = System.currentTimeMillis();
        runs.entrySet().removeIf(entry -> isExpired(entry.getValue().trace(), now));
    }

    private boolean isExpired(ProcessTrace<ClaudeStreamEvent> trace, long now) {
        return !trace.isRunning() && trace.completedAt() > 0 && (now - trace.completedAt()) >= completedTraceTtlMs;
    }

    private ClaudeRunProjection project(ProcessTrace<ClaudeStreamEvent> trace) {
        List<ClaudeStreamEvent> events = trace.events();
        String status = projectStatus(trace);
        String sessionId = extractSessionId(events);
        String summary = projectSummary(trace, status, events);
        return new ClaudeRunProjection(status, sessionId, trace.exitCode(), events.size(), summary);
    }

    private String projectStatus(ProcessTrace<ClaudeStreamEvent> trace) {
        if (trace.killRequested()) {
            return "Killed";
        }
        if (trace.parserFailed()) {
            return "Failed";
        }
        if (trace.isRunning()) {
            return "Running";
        }
        Integer exitCode = trace.exitCode();
        if (exitCode != null && exitCode == 0) {
            return "Completed";
        }
        return "Failed";
    }

    private String projectSummary(ProcessTrace<ClaudeStreamEvent> trace, String status, List<ClaudeStreamEvent> events) {
        String finalResult = extractFinalResult(events);
        if (StringUtils.hasText(finalResult)) {
            return shorten(normalizeInline(finalResult), SUMMARY_TEXT_LIMIT);
        }
        if (trace.killRequested()) {
            return "Claude Code process was killed.";
        }
        if (trace.parserFailed()) {
            return "Claude Code failed due to invalid JSON output.";
        }
        if ("Running".equals(status)) {
            return "Claude Code is still running.";
        }
        if (trace.exitCode() != null && trace.exitCode() == 0) {
            return "Claude Code completed without a final result event.";
        }
        if (trace.exitCode() != null) {
            return "Claude Code failed with exit code " + trace.exitCode() + " without a final result event.";
        }
        return "Claude Code finished without a final result event.";
    }

    private String extractSessionId(List<ClaudeStreamEvent> events) {
        String sessionId = null;
        for (ClaudeStreamEvent event : events) {
            if (StringUtils.hasText(event.sessionId())) {
                sessionId = event.sessionId();
            }
        }
        return sessionId;
    }

    private String extractFinalResult(List<ClaudeStreamEvent> events) {
        String result = null;
        for (ClaudeStreamEvent event : events) {
            if ("result".equals(event.type()) && StringUtils.hasText(event.result())) {
                result = event.result();
            }
        }
        return result;
    }

    private void appendSection(StringBuilder out, String title, String content) {
        if (!StringUtils.hasText(content)) {
            return;
        }
        out.append('\n').append(title).append(":\n").append(content);
        if (!content.endsWith("\n")) {
            out.append('\n');
        }
    }

    private void appendListSection(StringBuilder out, String title, List<String> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        out.append('\n').append(title).append(":\n");
        for (String item : items) {
            out.append("- ").append(item).append('\n');
        }
    }

    private String trimTrailingNewline(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return value.endsWith("\n") ? value.substring(0, value.length() - 1) : value;
    }

    private String shorten(String text, int maxLength) {
        if (!StringUtils.hasText(text) || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private String normalizeInline(String text) {
        if (text == null) {
            return null;
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    private String nullSafe(Object value) {
        return value == null ? "null" : value.toString();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private ProcessOutputListener<ClaudeStreamEvent> listener;
        private String claudeCommand = "claude";
        private String permissionMode = "bypassPermissions";
        private String defaultWorkingDirectory = System.getProperty("user.dir");
        private long defaultTimeoutMs = 120_000;
        private long maxTimeoutMs = 600_000;
        private long completedTraceTtlMs = DEFAULT_COMPLETED_TRACE_TTL_MS;
        private int maxOutputCharacters = 30_000;

        public Builder listener(ProcessOutputListener<ClaudeStreamEvent> listener) {
            Assert.notNull(listener, "listener must not be null");
            this.listener = listener;
            return this;
        }

        public Builder claudeCommand(String claudeCommand) {
            Assert.hasText(claudeCommand, "claudeCommand must not be empty");
            this.claudeCommand = claudeCommand;
            return this;
        }

        public Builder permissionMode(String permissionMode) {
            Assert.hasText(permissionMode, "permissionMode must not be empty");
            this.permissionMode = permissionMode;
            return this;
        }

        public Builder defaultWorkingDirectory(String defaultWorkingDirectory) {
            Assert.hasText(defaultWorkingDirectory, "defaultWorkingDirectory must not be empty");
            this.defaultWorkingDirectory = defaultWorkingDirectory;
            return this;
        }

        public Builder defaultTimeoutMs(long defaultTimeoutMs) {
            Assert.isTrue(defaultTimeoutMs > 0, "defaultTimeoutMs must be greater than 0");
            this.defaultTimeoutMs = defaultTimeoutMs;
            return this;
        }

        public Builder maxTimeoutMs(long maxTimeoutMs) {
            Assert.isTrue(maxTimeoutMs > 0, "maxTimeoutMs must be greater than 0");
            this.maxTimeoutMs = maxTimeoutMs;
            return this;
        }

        public Builder completedTraceTtlMs(long completedTraceTtlMs) {
            Assert.isTrue(completedTraceTtlMs > 0, "completedTraceTtlMs must be greater than 0");
            this.completedTraceTtlMs = completedTraceTtlMs;
            return this;
        }

        public Builder maxOutputCharacters(int maxOutputCharacters) {
            Assert.isTrue(maxOutputCharacters > 0, "maxOutputCharacters must be greater than 0");
            this.maxOutputCharacters = maxOutputCharacters;
            return this;
        }

        public ClaudeCodeSubagentTools build() {
            return new ClaudeCodeSubagentTools(this);
        }
    }

    private record ClaudeRunProjection(String status, String claudeSessionId, Integer exitCode,
                                       int eventCount, String summary) {
    }
}
