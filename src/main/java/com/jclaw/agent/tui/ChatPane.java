package com.jclaw.agent.tui;

import com.jclaw.agent.tui.event.AppEvent;
import com.jclaw.agent.tui.event.AppEventBus;
import com.jclaw.agent.tui.session.SessionService;
import com.jclaw.agent.tui.slash.SlashCommand;
import com.jclaw.agent.tui.slash.SlashCommandParser;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.toolkit.element.Size;
import dev.tamboui.style.Color;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.Toolkit;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import dev.tamboui.widgets.common.ScrollBarPolicy;
import dev.tamboui.widgets.input.TextInputState;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static dev.tamboui.toolkit.Toolkit.column;
import static dev.tamboui.toolkit.Toolkit.panel;
import static dev.tamboui.toolkit.Toolkit.richTextArea;
import static dev.tamboui.toolkit.Toolkit.text;
import static dev.tamboui.toolkit.Toolkit.textInput;

public class ChatPane implements Element {

    private static final int OVERHEAD_ROWS = 8;
    private static final int SCROLL_STEP = 5;

    private final JclawAppState appState;
    private final SlashCommandParser slashCommandParser;
    private final AppEventBus bus;
    private final SessionService sessionService;
    private final ExecutionModeService executionModeService;
    private final TextInputState inputState = new TextInputState();
    private int scrollLinesUp = 0;
    private int tabCycleIndex = -1;
    private boolean focused = true;

    public ChatPane(JclawAppState appState, SlashCommandParser slashCommandParser, AppEventBus bus,
                    SessionService sessionService, ExecutionModeService executionModeService) {
        this.appState = appState;
        this.slashCommandParser = slashCommandParser;
        this.bus = bus;
        this.sessionService = sessionService;
        this.executionModeService = executionModeService;
    }

    @Override
    public void render(Frame frame, Rect area, RenderContext context) {
        int transcriptLines = Math.max(1, area.height() - OVERHEAD_ROWS);
        String transcript = windowedTranscript(appState.transcript(), transcriptLines);
        String input = inputState.text() == null ? "" : inputState.text();
        String suggestions = input.startsWith("/")
                ? String.join("  ", slashCommandParser.matchingCommands(input))
                : "";
        String placeholder = appState.awaitingBuildConfirmation()
                ? "Enter /build again to confirm write access" : "Type a prompt or /help";

        var chatPanel = panel("Chat",
                column(
                        richTextArea(transcript)
                                .wrapWord()
                                .scrollbar(ScrollBarPolicy.AS_NEEDED)
                                .focusable(false)
                                .fill(),
                        text(scrollLinesUp > 0 ? "\u2191 scrolled — PgDn to return" : "")
                                .fg(Color.YELLOW),
                        text(suggestions.isBlank() ? "" : suggestions)
                                .fg(Color.CYAN),
                        text(appState.chatInFlight() ? "Streaming response..." : "Enter to submit")
                                .fg(appState.chatInFlight() ? Color.YELLOW : Color.GREEN),
                        textInput(inputState)
                                .placeholder(placeholder)
                                .cursorRequiresFocus(false)
                                .focusable(false)
                                .showCursor(true)
                                .rounded()
                ).fill()
        ).rounded();
        if (focused) {
            chatPanel.borderColor(Color.CYAN);
        }
        column(chatPanel.fill()).fill().render(frame, area, context);
    }

    @Override
    public EventResult handleKeyEvent(KeyEvent event, boolean focused) {
        if (event.isKey(KeyCode.PAGE_UP)) {
            scrollLinesUp += SCROLL_STEP;
            return EventResult.HANDLED;
        }
        if (event.isKey(KeyCode.PAGE_DOWN)) {
            scrollLinesUp = Math.max(0, scrollLinesUp - SCROLL_STEP);
            return EventResult.HANDLED;
        }
        if (event.isKey(KeyCode.TAB)) {
            String current = inputState.text() == null ? "" : inputState.text();
            if (current.startsWith("/")) {
                List<String> matches = slashCommandParser.matchingCommands(current);
                if (!matches.isEmpty()) {
                    tabCycleIndex = (tabCycleIndex + 1) % matches.size();
                    inputState.setText(matches.get(tabCycleIndex));
                }
            }
            return EventResult.HANDLED;
        }
        tabCycleIndex = -1;
        if (event.isConfirm()) {
            scrollLinesUp = 0;
            submitInput();
            return EventResult.HANDLED;
        }
        return Toolkit.handleTextInputKey(inputState, event) ? EventResult.HANDLED : EventResult.UNHANDLED;
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    @Override
    public EventResult handleMouseEvent(MouseEvent event) {
        if (event.isPress()) {
            appState.focusedPane(FocusedPane.CHAT);
            return EventResult.HANDLED;
        }
        if (event.isScroll()) {
            if (event.kind() == MouseEventKind.SCROLL_UP) {
                scrollLinesUp += SCROLL_STEP;
            } else if (event.kind() == MouseEventKind.SCROLL_DOWN) {
                scrollLinesUp = Math.max(0, scrollLinesUp - SCROLL_STEP);
            }
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }

    @Override
    public Constraint constraint() {
        return Constraint.fill(3);
    }

    @Override
    public Size preferredSize(int w, int h, RenderContext ctx) {
        return Size.UNKNOWN;
    }

    private void submitInput() {
        String input = inputState.text() == null ? "" : inputState.text().trim();
        inputState.clear();
        if (input.isBlank()) return;
        if (appState.awaitingBuildConfirmation() && !"/build".equals(input) && !"/plan".equals(input)) {
            bus.dispatch(new AppEvent.SystemMessage("Build confirmation is pending. Enter /build again to confirm or /plan to cancel."));
            return;
        }
        Optional<SlashCommand> cmd = slashCommandParser.parse(input);
        if (cmd.isPresent()) {
            handleSlashCommand(cmd.get());
            return;
        }
        bus.dispatch(new AppEvent.UserInput(input));
    }

    private void handleSlashCommand(SlashCommand slashCommand) {
        switch (slashCommand) {
            case PLAN -> {
                executionModeService.switchToPlanMode();
                bus.dispatch(new AppEvent.ModeChange(ExecutionMode.PLAN));
                bus.dispatch(new AppEvent.AwaitingConfirmation(false));
                bus.dispatch(new AppEvent.SystemMessage("Switched to PLAN mode."));
            }
            case BUILD -> {
                if (executionModeService.isAwaitingBuildConfirmation()) {
                    executionModeService.confirmBuildMode();
                    bus.dispatch(new AppEvent.ModeChange(ExecutionMode.BUILD));
                    bus.dispatch(new AppEvent.AwaitingConfirmation(false));
                    bus.dispatch(new AppEvent.SystemMessage("BUILD mode enabled for this session."));
                    return;
                }
                var result = executionModeService.requestBuildMode();
                if (result.confirmationRequired()) {
                    bus.dispatch(new AppEvent.AwaitingConfirmation(true));
                    bus.dispatch(new AppEvent.SystemMessage("BUILD mode requires confirmation. Enter /build again to confirm."));
                } else if (result.alreadyActive()) {
                    bus.dispatch(new AppEvent.SystemMessage("BUILD mode is already active."));
                } else {
                    bus.dispatch(new AppEvent.ModeChange(ExecutionMode.BUILD));
                    bus.dispatch(new AppEvent.AwaitingConfirmation(false));
                    bus.dispatch(new AppEvent.SystemMessage("Switched to BUILD mode."));
                }
            }
            case CLEAR -> {
                sessionService.clearCurrentSession();
                bus.dispatch(new AppEvent.SystemMessage("Cleared the current session history."));
            }
            case HELP -> appState.screen(Screen.HELP);
        }
    }

    private String windowedTranscript(List<ChatTranscriptEntry> entries, int visibleLines) {
        if (entries.isEmpty()) return "No conversation yet.";
        StringBuilder out = new StringBuilder();
        for (ChatTranscriptEntry entry : entries) {
            if (!out.isEmpty()) out.append("\n\n");
            out.append(entry.role().name()).append(":\n").append(entry.text());
        }
        String[] lines = out.toString().split("\n", -1);
        int end = Math.max(0, lines.length - scrollLinesUp);
        int start = Math.max(0, end - visibleLines);
        return String.join("\n", Arrays.copyOfRange(lines, start, end));
    }
}
