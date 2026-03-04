package com.jclaw.agent.chat.tools.claudecode;

import com.jclaw.agent.chat.tools.process.ProcessOutputListener;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertSame;

class ClaudeCodeSubagentToolsListenerTest {

    @Test
    void keepsSingleListenerFromBuilder() throws Exception {
        ProcessOutputListener<ClaudeStreamEvent> listener = new ProcessOutputListener<>() {
        };

        ClaudeCodeSubagentTools tools = ClaudeCodeSubagentTools.builder()
                .listener(listener)
                .build();

        Field field = ClaudeCodeSubagentTools.class.getDeclaredField("listener");
        field.setAccessible(true);
        assertSame(listener, field.get(tools));
    }
}
