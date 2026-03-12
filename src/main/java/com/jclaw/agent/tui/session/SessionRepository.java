package com.jclaw.agent.tui.session;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

public class SessionRepository {

    private final JdbcTemplate jdbcTemplate;

    public SessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        initializeSchema();
    }

    public void create(String sessionId, String workingDirectory) {
        jdbcTemplate.update("""
                insert into sessions (id, working_directory, created_at, last_accessed_at)
                values (?, ?, current_timestamp, current_timestamp)
                """, sessionId, workingDirectory);
    }

    public boolean exists(String sessionId) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from sessions where id = ?", Integer.class, sessionId);
        return count != null && count > 0;
    }

    public Optional<SessionDescriptor> find(String sessionId) {
        return jdbcTemplate.query("""
                        select id, working_directory, created_at, last_accessed_at
                        from sessions
                        where id = ?
                        """,
                rs -> rs.next() ? Optional.of(mapRow(rs)) : Optional.empty(),
                sessionId
        );
    }

    public void touch(String sessionId) {
        jdbcTemplate.update("update sessions set last_accessed_at = current_timestamp where id = ?", sessionId);
    }

    public void delete(String sessionId) {
        jdbcTemplate.update("delete from sessions where id = ?", sessionId);
    }

    private SessionDescriptor mapRow(ResultSet rs) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp lastAccessedAt = rs.getTimestamp("last_accessed_at");
        return new SessionDescriptor(
                rs.getString("id"),
                rs.getString("working_directory"),
                createdAt == null ? Instant.EPOCH : createdAt.toInstant(),
                lastAccessedAt == null ? Instant.EPOCH : lastAccessedAt.toInstant()
        );
    }

    private void initializeSchema() {
        jdbcTemplate.execute("""
                create table if not exists sessions (
                    id varchar(64) primary key,
                    working_directory varchar(1024) not null,
                    created_at timestamp not null,
                    last_accessed_at timestamp not null
                )
                """);
    }
}
