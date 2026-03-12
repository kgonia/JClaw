package com.jclaw.agent.tui;

import java.util.OptionalDouble;
import java.util.OptionalLong;

public record UsageSnapshot(
        OptionalLong promptTokens,
        OptionalLong completionTokens,
        OptionalLong totalTokens,
        OptionalDouble totalCostUsd) {

    public UsageSnapshot {
        promptTokens = promptTokens == null ? OptionalLong.empty() : promptTokens;
        completionTokens = completionTokens == null ? OptionalLong.empty() : completionTokens;
        totalTokens = totalTokens == null ? OptionalLong.empty() : totalTokens;
        totalCostUsd = totalCostUsd == null ? OptionalDouble.empty() : totalCostUsd;
    }

    public static UsageSnapshot empty() {
        return new UsageSnapshot(OptionalLong.empty(), OptionalLong.empty(), OptionalLong.empty(), OptionalDouble.empty());
    }
}
