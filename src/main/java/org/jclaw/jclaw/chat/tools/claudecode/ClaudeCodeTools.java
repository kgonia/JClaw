package org.jclaw.jclaw.chat.tools.claudecode;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Spring AI tools that expose Claude Code CLI as a subagent.
 *
 * The orchestrating LLM (OpenAI) calls these tools to delegate coding tasks
 * to Claude Code, which runs as a subprocess with full filesystem + shell access.
 *
 * Prepared for:
 * - Telegram bot integration (tools are transport-agnostic)
 * - CLI workflows (CodeMachine-CLI style multi-step orchestration)
 * - Session continuation across turns
 */
@Component
public class ClaudeCodeTools {

    private final ClaudeCodeService service;

    public ClaudeCodeTools(ClaudeCodeService service) {
        this.service = service;
    }

    @Tool(description = """
            Invokes Claude Code as a coding subagent. Claude Code can read/write files, \
            run shell commands, fix bugs, implement features, refactor code, run tests, \
            and perform deep codebase analysis. Use this for any coding task that requires \
            filesystem access or shell execution.
            Returns the result text and a session_id — save the session_id to continue this session.
            """)
    public String runCodingTask(
            @ToolParam(description = "The coding task, question, or instruction for Claude Code") String task,
            @ToolParam(description = "Absolute path to the project root directory") String workingDirectory,
            @ToolParam(description = "Maximum agentic turns Claude Code may take (1–50, default 10)") int maxTurns
    ) {
        int turns = (maxTurns <= 0 || maxTurns > 50) ? 10 : maxTurns;
        ClaudeCodeResponse response = service.execute(task, workingDirectory, null, turns);
        return format(response);
    }

    @Tool(description = """
            Continues a previous Claude Code session identified by session_id. \
            Use this for follow-up questions, additional implementation steps, or \
            multi-stage workflows where context from the previous session is needed.
            """)
    public String continueCodingTask(
            @ToolParam(description = "The follow-up task or question") String task,
            @ToolParam(description = "The session_id returned by a previous runCodingTask call") String sessionId,
            @ToolParam(description = "Absolute path to the project root directory") String workingDirectory
    ) {
        ClaudeCodeResponse response = service.execute(task, workingDirectory, sessionId, 10);
        return format(response);
    }

    private String format(ClaudeCodeResponse r) {
        return """
                %s
                [session_id=%s | turns=%d | cost=$%.4f | %dms]""".formatted(
                r.result(),
                r.sessionId(),
                r.numTurns(),
                r.totalCostUsd(),
                r.durationMs()
        );
    }
}
