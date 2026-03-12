package com.jclaw.agent.tui.session;

import com.jclaw.agent.tui.event.AppEventBus;
import com.jclaw.agent.tui.workflow.WorkflowProjectionService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionServiceTest {

    @Test
    void createsUuidSizedSessionIdsForNewSessions() {
        SessionRepository sessionRepository = mock(SessionRepository.class);
        ChatMemory chatMemory = mock(ChatMemory.class);
        WorkflowProjectionService workflowProjectionService = mock(WorkflowProjectionService.class);
        AppEventBus bus = mock(AppEventBus.class);

        when(chatMemory.get(anyString())).thenReturn(List.of());
        when(sessionRepository.find(anyString())).thenAnswer(invocation -> Optional.of(
                new SessionDescriptor(invocation.getArgument(0), "/tmp", java.time.Instant.now(), java.time.Instant.now())
        ));

        SessionService service = new SessionService(
                sessionRepository,
                chatMemory,
                workflowProjectionService,
                bus,
                "/tmp"
        );

        SessionDescriptor descriptor = service.openSession(Optional.empty());

        assertEquals(36, descriptor.id().length());
        assertFalse(descriptor.id().startsWith("session-"));
        verify(sessionRepository).create(descriptor.id(), "/tmp");
    }

    @Test
    void existingExplicitSessionIdIsRespected() {
        SessionRepository sessionRepository = mock(SessionRepository.class);
        ChatMemory chatMemory = mock(ChatMemory.class);
        WorkflowProjectionService workflowProjectionService = mock(WorkflowProjectionService.class);
        AppEventBus bus = mock(AppEventBus.class);

        when(sessionRepository.exists("custom-session")).thenReturn(true);
        when(chatMemory.get("custom-session")).thenReturn(List.of());
        when(sessionRepository.find("custom-session")).thenReturn(Optional.of(
                new SessionDescriptor("custom-session", "/tmp", java.time.Instant.now(), java.time.Instant.now())
        ));

        SessionService service = new SessionService(
                sessionRepository,
                chatMemory,
                workflowProjectionService,
                bus,
                "/tmp"
        );

        SessionDescriptor descriptor = service.openSession(Optional.of("custom-session"));

        assertEquals("custom-session", descriptor.id());
        verify(sessionRepository, never()).create(anyString(), anyString());
    }
}
