package com.jclaw.agent.tui;

import java.util.Locale;

public final class UsageTextFormatter {

    private UsageTextFormatter() {
    }

    public static String formatTokens(UsageSnapshot usageSnapshot) {
        if (usageSnapshot == null || usageSnapshot.totalTokens().isEmpty()) {
            return "n/a";
        }
        return Long.toString(usageSnapshot.totalTokens().getAsLong());
    }

    public static String formatCost(UsageSnapshot usageSnapshot) {
        if (usageSnapshot == null || usageSnapshot.totalCostUsd().isEmpty()) {
            return "n/a";
        }
        return String.format(Locale.ROOT, "$%.4f", usageSnapshot.totalCostUsd().getAsDouble());
    }
}
