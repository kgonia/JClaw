package com.jclaw.agent.tui;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

public class ModeAwareToolCatalog {

    private final ToolCallback[] planCallbacks;
    private final ToolCallback[] buildCallbacks;

    public ModeAwareToolCatalog(Object[] planToolObjects, Object[] buildToolObjects) {
        this.planCallbacks = ToolCallbacks.from(planToolObjects);
        this.buildCallbacks = ToolCallbacks.from(buildToolObjects);
    }

    public ToolCallback[] callbacksFor(ExecutionMode mode) {
        return mode == ExecutionMode.BUILD ? buildCallbacks.clone() : planCallbacks.clone();
    }
}
