package com.jclaw.agent.tui.workflow;

import com.jclaw.agent.chat.tools.claudecode.ClaudeStreamEvent;
import com.jclaw.agent.tui.event.AppEvent;
import com.jclaw.agent.tui.event.AppEventBus;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ClaudeWorkflowAdapterTest {

    @Test
    void mapsClaudeEventsToGenericWorkflowProjectionEvents() {
        AppEventBus bus = mock(AppEventBus.class);

        ClaudeWorkflowAdapter adapter = new ClaudeWorkflowAdapter(bus, new ObjectMapper());

        adapter.onParsedEvent(new ClaudeStreamEvent(
                "result",
                "success",
                null,
                "claude-session",
                "Build finished",
                null,
                "{\"type\":\"result\",\"total_cost_usd\":0.42}"
        ));

        verify(bus).dispatch(argThat(event ->
                event instanceof AppEvent.WorkflowUpdate wu
                        && wu.event().type() == WorkflowEventType.RESULT
                        && "Build finished".equals(wu.event().detail())
        ));
        verify(bus).dispatch(argThat(event ->
                event instanceof AppEvent.WorkflowUpdate wu
                        && wu.event().type() == WorkflowEventType.COST
                        && wu.event().estimatedCostUsd() != null
                        && wu.event().estimatedCostUsd() == 0.42
        ));
    }
}
