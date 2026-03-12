package com.jclaw.agent.tui.event;

import com.jclaw.agent.tui.JclawAppState;
import com.jclaw.agent.tui.StreamingChatService;
import com.jclaw.agent.tui.session.SessionService;
import org.springframework.ai.chat.memory.ChatMemory;

public class AppEventLoop {

    private final AppEventBus bus;
    private final JclawAppState appState;
    private final StreamingChatService streamingChatService;
    private final ChatMemory chatMemory;
    private final SessionService sessionService;

    public AppEventLoop(AppEventBus bus, JclawAppState appState, StreamingChatService streamingChatService,
                        ChatMemory chatMemory, SessionService sessionService) {
        this.bus = bus;
        this.appState = appState;
        this.streamingChatService = streamingChatService;
        this.chatMemory = chatMemory;
        this.sessionService = sessionService;
    }

    public void start() {
        Thread.ofVirtual().name("app-event-loop").start(this::run);
    }

    private void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                process(bus.take());
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void process(AppEvent event) {
        switch (event) {
            case AppEvent.UserInput e -> {
                if (appState.chatInFlight()) {
                    appState.addSystemMessage("A response is already streaming.");
                    return;
                }
                appState.addUserMessage(e.text());
                appState.beginAssistantMessage();
                streamingChatService.startStream(e.text());
            }
            case AppEvent.AssistantChunk e    -> appState.appendAssistantChunk(e.chunk());
            case AppEvent.AssistantComplete e -> {
                appState.finishAssistantMessage(e.fallbackText());
                appState.usageSnapshot(e.usage());
            }
            case AppEvent.AssistantFail e     -> appState.failAssistantMessage(e.error());
            case AppEvent.SystemMessage e     -> appState.addSystemMessage(e.text());
            case AppEvent.WorkflowUpdate e    -> appState.workflowProjection().apply(e.event());
            case AppEvent.SessionInit e       -> appState.initializeSession(e.sessionId(), e.workingDirectory(), e.messages());
            case AppEvent.ClearSession e -> {
                chatMemory.clear(sessionService.currentSessionId());
                appState.clearConversation();
            }
            case AppEvent.ModeChange e           -> appState.executionMode(e.mode());
            case AppEvent.AwaitingConfirmation e -> appState.awaitingBuildConfirmation(e.awaiting());
            case AppEvent.UsageUpdate e          -> appState.usageSnapshot(e.snapshot());
        }
    }
}
