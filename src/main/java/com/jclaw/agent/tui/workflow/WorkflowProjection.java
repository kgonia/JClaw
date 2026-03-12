package com.jclaw.agent.tui.workflow;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;

public class WorkflowProjection {

    private final List<WorkflowStep> steps = new ArrayList<>();
    private final LinkedHashSet<String> affectedFiles = new LinkedHashSet<>();
    private final List<RiskAnnotation> riskAnnotations = new ArrayList<>();

    private String status = "Idle";
    private String summary = "No active workflow";
    private OptionalDouble estimatedCostUsd = OptionalDouble.empty();

    public synchronized void reset() {
        steps.clear();
        affectedFiles.clear();
        riskAnnotations.clear();
        status = "Idle";
        summary = "No active workflow";
        estimatedCostUsd = OptionalDouble.empty();
    }

    public synchronized void apply(WorkflowProjectionEvent event) {
        if (event == null) {
            return;
        }

        if (event.status() != null && !event.status().isBlank()) {
            status = event.status();
        }
        if (event.title() != null || event.detail() != null) {
            steps.add(new WorkflowStep(
                    normalize(event.title(), event.type().name()),
                    emptyToNull(event.detail()),
                    event.occurredAt()
            ));
        }
        if (event.filePath() != null && !event.filePath().isBlank()) {
            affectedFiles.add(event.filePath());
        }
        if (event.risk() != null) {
            riskAnnotations.add(event.risk());
        }
        if (event.estimatedCostUsd() != null) {
            estimatedCostUsd = OptionalDouble.of(event.estimatedCostUsd());
        }
        if (event.detail() != null && !event.detail().isBlank()) {
            summary = event.detail();
        }
        else if (event.title() != null && !event.title().isBlank()) {
            summary = event.title();
        }
    }

    public synchronized String status() {
        return status;
    }

    public synchronized String summary() {
        return summary;
    }

    public synchronized OptionalDouble estimatedCostUsd() {
        return estimatedCostUsd;
    }

    public synchronized List<WorkflowStep> steps() {
        return List.copyOf(steps);
    }

    public synchronized Set<String> affectedFiles() {
        return Set.copyOf(affectedFiles);
    }

    public synchronized List<RiskAnnotation> riskAnnotations() {
        return List.copyOf(riskAnnotations);
    }

    private String normalize(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : fallback;
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
