package com.jclaw.agent.tui.workflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkflowEventStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void persistsAndReloadsWorkflowEvents() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:file:" + tempDir.resolve("workflow-events") + ";DB_CLOSE_ON_EXIT=FALSE");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        WorkflowEventStore store = new WorkflowEventStore(new JdbcTemplate(dataSource), new ObjectMapper());
        store.append("session-1", WorkflowProjectionEvent.status("Running", "Started"));
        store.append("session-1", WorkflowProjectionEvent.estimatedCost(0.75));

        List<WorkflowProjectionEvent> events = store.load("session-1");

        assertEquals(2, events.size());
        assertEquals(WorkflowEventType.STATUS, events.get(0).type());
        assertEquals("Started", events.get(0).detail());
        assertEquals(WorkflowEventType.COST, events.get(1).type());
        assertEquals(0.75, events.get(1).estimatedCostUsd());
    }
}
