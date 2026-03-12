package com.jclaw.agent.tui.slash;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlashCommandParserTest {

    private final SlashCommandParser parser = new SlashCommandParser();

    @Test
    void parsesKnownCommands() {
        assertEquals(SlashCommand.PLAN, parser.parse("/plan").orElseThrow());
        assertEquals(SlashCommand.BUILD, parser.parse("/build").orElseThrow());
        assertEquals(SlashCommand.CLEAR, parser.parse("/clear").orElseThrow());
        assertEquals(SlashCommand.HELP, parser.parse("/help").orElseThrow());
    }

    @Test
    void ignoresUnknownOrPlainTextInput() {
        assertTrue(parser.parse("hello").isEmpty());
        assertTrue(parser.parse("/unknown").isEmpty());
        assertTrue(parser.parse(null).isEmpty());
    }
}
