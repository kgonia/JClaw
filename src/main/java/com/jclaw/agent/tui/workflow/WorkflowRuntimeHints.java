package com.jclaw.agent.tui.workflow;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

public final class WorkflowRuntimeHints implements RuntimeHintsRegistrar {

    private static final MemberCategory[] JACKSON_RECORD_CATEGORIES = new MemberCategory[] {
            MemberCategory.INTROSPECT_DECLARED_CONSTRUCTORS,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.INTROSPECT_DECLARED_METHODS,
            MemberCategory.INVOKE_DECLARED_METHODS,
            MemberCategory.DECLARED_FIELDS
    };

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.reflection().registerType(WorkflowProjectionEvent.class, JACKSON_RECORD_CATEGORIES);
        hints.reflection().registerType(RiskAnnotation.class, JACKSON_RECORD_CATEGORIES);
        hints.reflection().registerType(
                TypeReference.of("com.jclaw.agent.tui.workflow.WorkflowEventStore$StoredWorkflowProjectionEvent"),
                JACKSON_RECORD_CATEGORIES
        );
    }
}
