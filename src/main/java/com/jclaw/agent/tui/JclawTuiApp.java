package com.jclaw.agent.tui;

import com.jclaw.agent.config.TuiProperties;
import com.jclaw.agent.tui.event.AppEventBus;
import dev.tamboui.style.Color;
import dev.tamboui.toolkit.app.ToolkitApp;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.TuiConfig;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEventKind;

import java.time.Duration;

import static dev.tamboui.toolkit.Toolkit.column;
import static dev.tamboui.toolkit.Toolkit.panel;
import static dev.tamboui.toolkit.Toolkit.row;
import static dev.tamboui.toolkit.Toolkit.text;

public class JclawTuiApp extends ToolkitApp {

    private final JclawAppState appState;
    private final ChatPane chatPane;
    private final WorkflowPane workflowPane;
    private final AppEventBus bus;
    private final TuiProperties tuiProperties;

    public JclawTuiApp(JclawAppState appState, ChatPane chatPane, WorkflowPane workflowPane,
                       AppEventBus bus, TuiProperties tuiProperties) {
        this.appState = appState;
        this.chatPane = chatPane;
        this.workflowPane = workflowPane;
        this.bus = bus;
        this.tuiProperties = tuiProperties;
    }

    @Override
    protected TuiConfig configure() {
        return TuiConfig.builder()
                .tickRate(Duration.ofMillis(tuiProperties.tickRateMs()))
                .resizeGracePeriod(Duration.ofMillis(tuiProperties.resizeGracePeriodMs()))
                .mouseCapture(true)
                .build();
    }

    @Override
    protected Element render() {
        return switch (appState.screen()) {
            case HELP -> renderHelpScreen();
            case CHAT -> renderChatScreen();
        };
    }

    private Element renderChatScreen() {
        boolean chatFocused = appState.focusedPane() == FocusedPane.CHAT;
        chatPane.setFocused(chatFocused);
        Panel workflowPanel = workflowPane.render(appState.workflowProjection(), !chatFocused);
        workflowPanel.onMouseEvent(e -> {
            if (e.isPress()) {
                appState.focusedPane(FocusedPane.WORKFLOW);
                return EventResult.HANDLED;
            }
            if (e.isScroll()) {
                workflowPane.scrollBy(e.kind() == MouseEventKind.SCROLL_UP ? 3 : -3);
                return EventResult.HANDLED;
            }
            return EventResult.UNHANDLED;
        });
        return column(
                renderHeader(),
                row(
                        chatPane,
                        workflowPanel.fill(2)
                ).spacing(1).fill()
        ).fill().id("root").focusable().onKeyEvent(this::handleRootEvent);
    }

    private Element renderHelpScreen() {
        String helpText = """
                /plan  Switch to read-only planning mode
                /build Request write-capable build mode
                /clear Clear the current session transcript and workflow
                /help  Show this help screen

                Esc, q, or Enter returns to chat.
                Ctrl+C quits the application.
                """;
        return panel("Help",
                text(helpText)
        ).rounded().fill().id("root").focusable().onKeyEvent(this::handleRootEvent);
    }

    private Element renderHeader() {
        String focusHint = appState.focusedPane() == FocusedPane.CHAT ? "Chat" : "Workflow";
        String mode = appState.executionMode().name();
        Color modeColor = appState.executionMode() == ExecutionMode.BUILD ? Color.RED : Color.GREEN;
        return column(
                row(
                        text(tuiProperties.appName()).bold().fg(Color.CYAN),
                        text("  "),
                        text(appState.workingDirectory() != null ? appState.workingDirectory() : "").fg(Color.WHITE),
                        text("  "),
                        text(mode).fg(modeColor),
                        text("  "),
                        text(UsageTextFormatter.formatTokens(appState.usageSnapshot())).fg(Color.YELLOW)
                ).length(1),
                row(
                        text("[F2] Focus: " + focusHint).fg(Color.CYAN),
                        text("  [F1] Help  [PgUp/Dn] Scroll  [Ctrl+C] Quit").fg(Color.WHITE)
                ).length(1)
        );
    }

    private EventResult handleRootEvent(KeyEvent event) {
        if (event.isCtrlC() || event.isQuit()) {
            quit();
            return EventResult.HANDLED;
        }

        if (appState.screen() == Screen.HELP) {
            if (event.isCancel() || event.isConfirm() || event.isCharIgnoreCase('q')) {
                appState.screen(Screen.CHAT);
                return EventResult.HANDLED;
            }
            return EventResult.UNHANDLED;
        }

        return handleChatScreenEvent(event);
    }

    private EventResult handleChatScreenEvent(KeyEvent event) {
        if (event.isKey(KeyCode.F1)) {
            appState.screen(Screen.HELP);
            return EventResult.HANDLED;
        }

        boolean chatActive = appState.focusedPane() == FocusedPane.CHAT;

        // F2 toggles focus between Chat and Workflow
        if (event.isKey(KeyCode.F2)) {
            appState.focusedPane(chatActive ? FocusedPane.WORKFLOW : FocusedPane.CHAT);
            return EventResult.HANDLED;
        }

        // When workflow is focused, route scroll keys there first
        if (!chatActive) {
            EventResult r = workflowPane.handleKeyEvent(event);
            if (r == EventResult.HANDLED) return r;
        }

        return chatPane.handleKeyEvent(event, chatActive);
    }
}
