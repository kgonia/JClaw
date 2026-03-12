package com.jclaw.agent.tui.session;

import com.jclaw.agent.tui.event.AppEvent;
import com.jclaw.agent.tui.event.AppEventBus;
import com.jclaw.agent.tui.workflow.WorkflowProjectionService;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SessionService {

    private final SessionRepository sessionRepository;
    private final ChatMemory chatMemory;
    private final WorkflowProjectionService workflowProjectionService;
    private final AppEventBus bus;
    private final String workingDirectory;

    private volatile String currentSessionId;

    public SessionService(SessionRepository sessionRepository, ChatMemory chatMemory,
                          WorkflowProjectionService workflowProjectionService, AppEventBus bus,
                          String workingDirectory) {
        this.sessionRepository = sessionRepository;
        this.chatMemory = chatMemory;
        this.workflowProjectionService = workflowProjectionService;
        this.bus = bus;
        this.workingDirectory = workingDirectory;
    }

    public SessionDescriptor openSession(Optional<String> requestedSessionId) {
        String sessionId = requestedSessionId.filter(value -> !value.isBlank()).orElseGet(() -> UUID.randomUUID().toString());
        if (!requestedSessionId.isPresent()) {
            sessionRepository.create(sessionId, workingDirectory);
        }
        else if (!sessionRepository.exists(sessionId)) {
            throw new IllegalArgumentException("Unknown session id: " + sessionId);
        }

        sessionRepository.touch(sessionId);
        List<Message> persistedMessages = chatMemory.get(sessionId);
        workflowProjectionService.loadSession(sessionId);
        this.currentSessionId = sessionId;
        bus.dispatch(new AppEvent.SessionInit(sessionId, workingDirectory, persistedMessages));

        return sessionRepository.find(sessionId)
                .orElseGet(() -> new SessionDescriptor(sessionId, workingDirectory, java.time.Instant.now(), java.time.Instant.now()));
    }

    public void clearCurrentSession() {
        String sessionId = currentSessionId();
        workflowProjectionService.clear(sessionId);
        sessionRepository.touch(sessionId);
        bus.dispatch(new AppEvent.ClearSession());
    }

    public String currentSessionId() {
        String sessionId = this.currentSessionId;
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalStateException("No active session");
        }
        return sessionId;
    }
}
