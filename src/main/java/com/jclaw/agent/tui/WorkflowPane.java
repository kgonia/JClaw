package com.jclaw.agent.tui;

import com.jclaw.agent.tui.workflow.RiskAnnotation;
import com.jclaw.agent.tui.workflow.WorkflowProjection;
import com.jclaw.agent.tui.workflow.WorkflowStep;
import dev.tamboui.style.Color;
import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.common.ScrollBarPolicy;

import java.util.Arrays;
import java.util.Locale;

import static dev.tamboui.toolkit.Toolkit.panel;
import static dev.tamboui.toolkit.Toolkit.richTextArea;

public class WorkflowPane {

    private static final int SCROLL_STEP = 5;
    private static final int MAX_VISIBLE_LINES = 100;

    private int scrollLinesUp = 0;

    public Panel render(WorkflowProjection projection, boolean focused) {
        String content = renderProjection(projection);
        String[] lines = content.split("\n", -1);
        int end = Math.max(0, lines.length - scrollLinesUp);
        int start = Math.max(0, end - MAX_VISIBLE_LINES);
        String windowed = String.join("\n", Arrays.copyOfRange(lines, start, end));

        String title = "Workflow" + (scrollLinesUp > 0 ? " \u2191" : "");
        Panel p = panel(title,
                richTextArea(windowed)
                        .wrapWord()
                        .scrollbar(ScrollBarPolicy.AS_NEEDED)
                        .focusable(false)
                        .fill()
        ).rounded();
        if (focused) {
            p.borderColor(Color.CYAN);
        }
        return p.fill();
    }

    public void scrollBy(int delta) {
        scrollLinesUp = Math.max(0, scrollLinesUp + delta);
    }

    public EventResult handleKeyEvent(KeyEvent event) {
        if (event.isKey(KeyCode.PAGE_UP) || event.isKey(KeyCode.UP)) {
            scrollLinesUp += SCROLL_STEP;
            return EventResult.HANDLED;
        }
        if (event.isKey(KeyCode.PAGE_DOWN) || event.isKey(KeyCode.DOWN)) {
            scrollLinesUp = Math.max(0, scrollLinesUp - SCROLL_STEP);
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }

    private String renderProjection(WorkflowProjection projection) {
        StringBuilder out = new StringBuilder();
        out.append("Status: ").append(projection.status()).append('\n');
        out.append("Summary: ").append(projection.summary()).append('\n');
        out.append("Estimated cost: ")
                .append(projection.estimatedCostUsd().isPresent()
                        ? String.format(Locale.ROOT, "$%.4f", projection.estimatedCostUsd().getAsDouble())
                        : "n/a")
                .append('\n');

        if (!projection.steps().isEmpty()) {
            out.append("\nSteps:\n");
            for (WorkflowStep step : projection.steps()) {
                out.append("- ").append(step.title());
                if (step.detail() != null && !step.detail().isBlank()) {
                    out.append(": ").append(step.detail());
                }
                out.append('\n');
            }
        }

        if (!projection.affectedFiles().isEmpty()) {
            out.append("\nAffected files:\n");
            for (String file : projection.affectedFiles()) {
                out.append("- ").append(file).append('\n');
            }
        }

        if (!projection.riskAnnotations().isEmpty()) {
            out.append("\nRisks:\n");
            for (RiskAnnotation risk : projection.riskAnnotations()) {
                out.append("- ").append(risk.level()).append(": ").append(risk.message()).append('\n');
            }
        }

        return out.toString();
    }
}
