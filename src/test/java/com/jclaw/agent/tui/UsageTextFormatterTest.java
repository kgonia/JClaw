package com.jclaw.agent.tui;

import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UsageTextFormatterTest {

    @Test
    void formatsUsageOrFallsBackToNa() {
        assertEquals("n/a", UsageTextFormatter.formatTokens(UsageSnapshot.empty()));
        assertEquals("n/a", UsageTextFormatter.formatCost(UsageSnapshot.empty()));

        UsageSnapshot usage = new UsageSnapshot(
                OptionalLong.of(12),
                OptionalLong.of(34),
                OptionalLong.of(46),
                OptionalDouble.of(0.0123)
        );

        assertEquals("46", UsageTextFormatter.formatTokens(usage));
        assertEquals("$0.0123", UsageTextFormatter.formatCost(usage));
    }
}
