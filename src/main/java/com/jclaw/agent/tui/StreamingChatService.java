package com.jclaw.agent.tui;

import com.jclaw.agent.tui.event.AppEvent;
import com.jclaw.agent.tui.event.AppEventBus;
import com.jclaw.agent.tui.session.SessionService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicReference;

public class StreamingChatService {

    private final ChatClient chatClient;
    private final ModeAwareToolCatalog toolCatalog;
    private final ExecutionModeService executionModeService;
    private final SessionService sessionService;
    private final JclawAppState appState;
    private final AppEventBus bus;

    public StreamingChatService(ChatClient chatClient, ModeAwareToolCatalog toolCatalog,
                                ExecutionModeService executionModeService, SessionService sessionService,
                                JclawAppState appState, AppEventBus bus) {
        this.chatClient = chatClient;
        this.toolCatalog = toolCatalog;
        this.executionModeService = executionModeService;
        this.sessionService = sessionService;
        this.appState = appState;
        this.bus = bus;
    }

    public void startStream(String text) {
        Thread.ofVirtual().name("jclaw-chat-stream").start(() -> streamConversation(text));
    }

    private void streamConversation(String text) {
        String sessionId = sessionService.currentSessionId();
        AtomicReference<ChatResponse> aggregatedResponse = new AtomicReference<>();
        AtomicReference<ChatResponse> lastWithUsage = new AtomicReference<>();
        MessageAggregator aggregator = new MessageAggregator();

        try {
            Flux<ChatResponse> responseFlux = chatClient.prompt()
                    .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, sessionId))
                    .user(text)
                    .toolCallbacks(toolCatalog.callbacksFor(executionModeService.currentMode()))
                    .toolContext(Map.of(
                            "workingDirectory", appState.workingDirectory(),
                            "executionMode", executionModeService.currentMode().name()
                    ))
                    .stream()
                    .chatResponse();

            aggregator.aggregate(responseFlux, aggregatedResponse::set)
                    .doOnNext(r -> {
                        dispatchChunk(r);
                        if (hasRealUsage(r)) lastWithUsage.set(r);
                    })
                    .blockLast();

            ChatResponse forUsage = lastWithUsage.get() != null ? lastWithUsage.get() : aggregatedResponse.get();
            bus.dispatch(new AppEvent.AssistantComplete(extractText(aggregatedResponse.get()), toUsageSnapshot(forUsage)));
        }
        catch (Exception e) {
            bus.dispatch(new AppEvent.AssistantFail("Chat failed: " + rootCauseMessage(e)));
        }
    }

    private void dispatchChunk(ChatResponse chatResponse) {
        String text = extractText(chatResponse);
        if (text != null && !text.isBlank()) {
            bus.dispatch(new AppEvent.AssistantChunk(text));
        }
    }

    private String extractText(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getResult() == null || chatResponse.getResult().getOutput() == null) {
            return null;
        }
        return chatResponse.getResult().getOutput().getText();
    }

    private UsageSnapshot toUsageSnapshot(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return UsageSnapshot.empty();
        }
        Usage usage = response.getMetadata().getUsage();
        if (usage == null) {
            return UsageSnapshot.empty();
        }
        return new UsageSnapshot(
                optionalLong(usage.getPromptTokens()),
                optionalLong(usage.getCompletionTokens()),
                optionalLong(usage.getTotalTokens()),
                OptionalDouble.empty()
        );
    }

    private boolean hasRealUsage(ChatResponse r) {
        if (r == null || r.getMetadata() == null || r.getMetadata().getUsage() == null) return false;
        Integer total = r.getMetadata().getUsage().getTotalTokens();
        return total != null && total > 0;
    }

    private OptionalLong optionalLong(Integer value) {
        return value == null || value <= 0 ? OptionalLong.empty() : OptionalLong.of(value.longValue());
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
