package org.jclaw.jclaw.chat.tools.claudecode;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Maps the JSON output produced by: claude -p "..." --output-format json
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClaudeCodeResponse(
        String result,
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("duration_ms") long durationMs,
        @JsonProperty("total_cost_usd") double totalCostUsd,
        @JsonProperty("is_error") boolean isError,
        @JsonProperty("num_turns") int numTurns
) {}
