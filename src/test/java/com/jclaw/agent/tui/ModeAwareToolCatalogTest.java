package com.jclaw.agent.tui;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModeAwareToolCatalogTest {

    @Test
    void exposesReadOnlyToolsInPlanAndAllToolsInBuild() {
        ModeAwareToolCatalog catalog = new ModeAwareToolCatalog(
                new Object[]{new ReadOnlyTools()},
                new Object[]{new ReadOnlyTools(), new BuildTools()}
        );

        assertEquals(Set.of("ReadTool"), names(catalog.callbacksFor(ExecutionMode.PLAN)));
        assertEquals(Set.of("ReadTool", "WriteTool"), names(catalog.callbacksFor(ExecutionMode.BUILD)));
    }

    private Set<String> names(ToolCallback[] callbacks) {
        return Arrays.stream(callbacks)
                .map(callback -> callback.getToolDefinition().name())
                .collect(Collectors.toSet());
    }

    static class ReadOnlyTools {

        @Tool(name = "ReadTool", description = "Reads")
        String read() {
            return "ok";
        }
    }

    static class BuildTools {

        @Tool(name = "WriteTool", description = "Writes")
        String write() {
            return "ok";
        }
    }
}
