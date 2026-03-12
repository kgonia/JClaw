package com.jclaw.agent.tui.workflow;

import com.jclaw.agent.tui.JclawAppState;

import java.util.List;

public class WorkflowProjectionService {

    private final WorkflowEventStore workflowEventStore;
    private final JclawAppState appState;

    public WorkflowProjectionService(WorkflowEventStore workflowEventStore, JclawAppState appState) {
        this.workflowEventStore = workflowEventStore;
        this.appState = appState;
    }

    public void loadSession(String sessionId) {
        WorkflowProjection projection = appState.workflowProjection();
        projection.reset();
        List<WorkflowProjectionEvent> events = workflowEventStore.load(sessionId);
        for (WorkflowProjectionEvent event : events) {
            projection.apply(event);
        }
    }

    public void append(String sessionId, WorkflowProjectionEvent event) {
        workflowEventStore.append(sessionId, event);
        appState.workflowProjection().apply(event);
    }

    public void clear(String sessionId) {
        workflowEventStore.delete(sessionId);
        appState.workflowProjection().reset();
    }
}
