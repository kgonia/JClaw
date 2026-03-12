package com.jclaw.agent.config;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

public class UserConfigStore {

    private final JdbcTemplate jdbcTemplate;

    public UserConfigStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        initializeSchema();
    }

    public Optional<String> get(String key) {
        return jdbcTemplate.query("""
                        select config_value
                        from user_config
                        where config_key = ?
                        """,
                rs -> rs.next() ? Optional.ofNullable(rs.getString(1)) : Optional.empty(),
                key
        );
    }

    public void put(String key, String value) {
        jdbcTemplate.update("""
                merge into user_config key(config_key)
                values (?, ?)
                """, key, value);
    }

    private void initializeSchema() {
        jdbcTemplate.execute("""
                create table if not exists user_config (
                    config_key varchar(128) primary key,
                    config_value clob
                )
                """);
    }
}
