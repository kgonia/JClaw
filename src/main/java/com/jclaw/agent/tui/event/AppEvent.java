package com.jclaw.agent.tui.event;

import com.jclaw.agent.tui.ExecutionMode;
import com.jclaw.agent.tui.UsageSnapshot;
import com.jclaw.agent.tui.workflow.WorkflowProjectionEvent;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

public sealed interface AppEvent permits
        AppEvent.UserInput, AppEvent.AssistantChunk, AppEvent.AssistantComplete,
        AppEvent.AssistantFail, AppEvent.SystemMessage, AppEvent.WorkflowUpdate,
        AppEvent.SessionInit, AppEvent.ClearSession, AppEvent.ModeChange,
        AppEvent.AwaitingConfirmation, AppEvent.UsageUpdate {

    record UserInput(String text) implements AppEvent {}
    record AssistantChunk(String chunk) implements AppEvent {}
    record AssistantComplete(String fallbackText, UsageSnapshot usage) implements AppEvent {}
    record AssistantFail(String error) implements AppEvent {}
    record SystemMessage(String text) implements AppEvent {}
    record WorkflowUpdate(WorkflowProjectionEvent event) implements AppEvent {}
    record SessionInit(String sessionId, String workingDirectory, List<Message> messages) implements AppEvent {}
    record ClearSession() implements AppEvent {}
    record ModeChange(ExecutionMode mode) implements AppEvent {}
    record AwaitingConfirmation(boolean awaiting) implements AppEvent {}
    record UsageUpdate(UsageSnapshot snapshot) implements AppEvent {}
}
