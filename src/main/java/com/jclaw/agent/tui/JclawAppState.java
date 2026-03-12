package com.jclaw.agent.tui;

import com.jclaw.agent.tui.workflow.WorkflowProjection;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

public class JclawAppState {

    private final List<ChatTranscriptEntry> transcript = new ArrayList<>();
    private final WorkflowProjection workflowProjection = new WorkflowProjection();

    private Screen screen = Screen.CHAT;
    private ExecutionMode executionMode = ExecutionMode.BUILD;
    private FocusedPane focusedPane = FocusedPane.CHAT;
    private String sessionId;
    private String workingDirectory;
    private boolean chatInFlight;
    private boolean awaitingBuildConfirmation;
    private int activeAssistantEntryIndex = -1;
    private UsageSnapshot usageSnapshot = UsageSnapshot.empty();

    public synchronized void initializeSession(String sessionId, String workingDirectory, List<Message> persistedMessages) {
        this.sessionId = sessionId;
        this.workingDirectory = workingDirectory;
        this.transcript.clear();
        this.workflowProjection.reset();
        this.chatInFlight = false;
        this.awaitingBuildConfirmation = false;
        this.activeAssistantEntryIndex = -1;
        this.usageSnapshot = UsageSnapshot.empty();
        if (persistedMessages != null) {
            for (Message message : persistedMessages) {
                appendPersistedMessage(message);
            }
        }
    }

    public synchronized Screen screen() {
        return screen;
    }

    public synchronized void screen(Screen screen) {
        this.screen = screen == null ? Screen.CHAT : screen;
    }

    public synchronized FocusedPane focusedPane() {
        return focusedPane;
    }

    public synchronized void focusedPane(FocusedPane fp) {
        this.focusedPane = fp == null ? FocusedPane.CHAT : fp;
    }

    public synchronized ExecutionMode executionMode() {
        return executionMode;
    }

    public synchronized void executionMode(ExecutionMode executionMode) {
        this.executionMode = executionMode == null ? ExecutionMode.PLAN : executionMode;
    }

    public synchronized String sessionId() {
        return sessionId;
    }

    public synchronized String workingDirectory() {
        return workingDirectory;
    }

    public synchronized void addUserMessage(String text) {
        transcript.add(new ChatTranscriptEntry(ChatRole.USER, text));
    }

    public synchronized void addSystemMessage(String text) {
        transcript.add(new ChatTranscriptEntry(ChatRole.SYSTEM, text));
    }

    public synchronized void addToolMessage(String text) {
        transcript.add(new ChatTranscriptEntry(ChatRole.TOOL, text));
    }

    public synchronized void beginAssistantMessage() {
        activeAssistantEntryIndex = transcript.size();
        transcript.add(new ChatTranscriptEntry(ChatRole.ASSISTANT, ""));
        chatInFlight = true;
    }

    public synchronized void appendAssistantChunk(String chunk) {
        if (activeAssistantEntryIndex < 0) {
            beginAssistantMessage();
        }
        ChatTranscriptEntry previous = transcript.get(activeAssistantEntryIndex);
        transcript.set(activeAssistantEntryIndex, new ChatTranscriptEntry(
                ChatRole.ASSISTANT,
                previous.text() + chunk
        ));
    }

    public synchronized void finishAssistantMessage(String fallbackIfEmpty) {
        if (activeAssistantEntryIndex >= 0) {
            ChatTranscriptEntry entry = transcript.get(activeAssistantEntryIndex);
            if ((entry.text() == null || entry.text().isBlank()) && fallbackIfEmpty != null && !fallbackIfEmpty.isBlank()) {
                transcript.set(activeAssistantEntryIndex, new ChatTranscriptEntry(ChatRole.ASSISTANT, fallbackIfEmpty));
            }
        }
        activeAssistantEntryIndex = -1;
        chatInFlight = false;
    }

    public synchronized void failAssistantMessage(String message) {
        if (activeAssistantEntryIndex >= 0) {
            transcript.remove(activeAssistantEntryIndex);
            activeAssistantEntryIndex = -1;
        }
        chatInFlight = false;
        addSystemMessage(message);
    }

    public synchronized boolean chatInFlight() {
        return chatInFlight;
    }

    public synchronized void awaitingBuildConfirmation(boolean awaitingBuildConfirmation) {
        this.awaitingBuildConfirmation = awaitingBuildConfirmation;
    }

    public synchronized boolean awaitingBuildConfirmation() {
        return awaitingBuildConfirmation;
    }

    public synchronized void usageSnapshot(UsageSnapshot usageSnapshot) {
        this.usageSnapshot = usageSnapshot == null ? UsageSnapshot.empty() : usageSnapshot;
    }

    public synchronized UsageSnapshot usageSnapshot() {
        return usageSnapshot;
    }

    public WorkflowProjection workflowProjection() {
        return workflowProjection;
    }

    public synchronized void clearConversation() {
        transcript.clear();
        workflowProjection.reset();
        usageSnapshot = UsageSnapshot.empty();
        chatInFlight = false;
        activeAssistantEntryIndex = -1;
    }

    public synchronized List<ChatTranscriptEntry> transcript() {
        return List.copyOf(transcript);
    }

    private void appendPersistedMessage(Message message) {
        if (message == null) {
            return;
        }

        if (message instanceof UserMessage userMessage) {
            transcript.add(new ChatTranscriptEntry(ChatRole.USER, normalize(userMessage.getText())));
            return;
        }
        if (message instanceof AssistantMessage assistantMessage) {
            transcript.add(new ChatTranscriptEntry(ChatRole.ASSISTANT, normalize(assistantMessage.getText())));
            return;
        }
        if (message instanceof ToolResponseMessage toolResponseMessage) {
            StringBuilder out = new StringBuilder();
            for (ToolResponseMessage.ToolResponse response : toolResponseMessage.getResponses()) {
                if (!out.isEmpty()) {
                    out.append('\n');
                }
                out.append(response.name()).append(": ").append(response.responseData());
            }
            transcript.add(new ChatTranscriptEntry(ChatRole.TOOL, out.toString()));
            return;
        }
        if (message.getMessageType() == MessageType.SYSTEM) {
            transcript.add(new ChatTranscriptEntry(ChatRole.SYSTEM, normalize(message.getText())));
        }
    }

    private String normalize(String text) {
        return text == null ? "" : text;
    }
}
