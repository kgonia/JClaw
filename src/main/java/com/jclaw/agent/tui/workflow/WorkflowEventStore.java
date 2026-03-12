package com.jclaw.agent.tui.workflow;

import tools.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

public class WorkflowEventStore {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public WorkflowEventStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        initializeSchema();
    }

    public void append(String sessionId, WorkflowProjectionEvent event) {
        try {
            jdbcTemplate.update("""
                    insert into workflow_events (session_id, event_json, created_at)
                    values (?, ?, current_timestamp)
                    """, sessionId, objectMapper.writeValueAsString(StoredWorkflowProjectionEvent.from(event)));
        }
        catch (Exception e) {
            throw new IllegalStateException("Failed to persist workflow event", e);
        }
    }

    public List<WorkflowProjectionEvent> load(String sessionId) {
        return jdbcTemplate.query("""
                        select event_json, created_at
                        from workflow_events
                        where session_id = ?
                        order by id asc
                        """,
                (rs, rowNum) -> mapEvent(rs),
                sessionId
        );
    }

    public void delete(String sessionId) {
        jdbcTemplate.update("delete from workflow_events where session_id = ?", sessionId);
    }

    private WorkflowProjectionEvent mapEvent(ResultSet rs) throws SQLException {
        try {
            StoredWorkflowProjectionEvent event = objectMapper.readValue(
                    rs.getString("event_json"),
                    StoredWorkflowProjectionEvent.class
            );
            Timestamp createdAt = rs.getTimestamp("created_at");
            return new WorkflowProjectionEvent(
                    event.type(),
                    event.status(),
                    event.title(),
                    event.detail(),
                    event.filePath(),
                    event.risk(),
                    event.estimatedCostUsd(),
                    createdAt == null ? Instant.now() : createdAt.toInstant()
            );
        }
        catch (Exception e) {
            throw new IllegalStateException("Failed to read workflow event", e);
        }
    }

    private void initializeSchema() {
        jdbcTemplate.execute("""
                create table if not exists workflow_events (
                    id bigint auto_increment primary key,
                    session_id varchar(64) not null,
                    event_json clob not null,
                    created_at timestamp not null
                )
                """);
        jdbcTemplate.execute("""
                create index if not exists workflow_events_session_idx
                on workflow_events (session_id, id)
                """);
    }

    private record StoredWorkflowProjectionEvent(
            WorkflowEventType type,
            String status,
            String title,
            String detail,
            String filePath,
            RiskAnnotation risk,
            Double estimatedCostUsd) {

        private static StoredWorkflowProjectionEvent from(WorkflowProjectionEvent event) {
            return new StoredWorkflowProjectionEvent(
                    event.type(),
                    event.status(),
                    event.title(),
                    event.detail(),
                    event.filePath(),
                    event.risk(),
                    event.estimatedCostUsd()
            );
        }
    }
}
