package com.jclaw.agent.tui.slash;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class SlashCommandParser {

    public Optional<SlashCommand> parse(String input) {
        if (input == null) {
            return Optional.empty();
        }

        String normalized = input.trim();
        if (!normalized.startsWith("/")) {
            return Optional.empty();
        }

        return switch (normalized) {
            case "/plan" -> Optional.of(SlashCommand.PLAN);
            case "/build" -> Optional.of(SlashCommand.BUILD);
            case "/clear" -> Optional.of(SlashCommand.CLEAR);
            case "/help" -> Optional.of(SlashCommand.HELP);
            default -> Optional.empty();
        };
    }

    public List<String> matchingCommands(String prefix) {
        if (prefix == null || prefix.isBlank()) return List.of();
        String lower = prefix.trim().toLowerCase();
        return Arrays.stream(SlashCommand.values())
                .map(cmd -> "/" + cmd.name().toLowerCase())
                .filter(cmd -> cmd.startsWith(lower))
                .toList();
    }
}
