package com.jclaw.agent.config;

import com.jclaw.agent.chat.tools.claudecode.ClaudeCodeSubagentTools;
import com.jclaw.agent.chat.tools.claudecode.ClaudeStreamEvent;
import com.jclaw.agent.chat.tools.process.CompositeProcessOutputListener;
import com.jclaw.agent.chat.tools.process.ProcessOutputListener;
import com.jclaw.agent.tui.event.AppEventBus;
import com.jclaw.agent.tui.workflow.ClaudeWorkflowAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Configuration
public class SubagentConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(SubagentConfiguration.class);

    @Bean
    public ClaudeWorkflowAdapter claudeWorkflowAdapter(AppEventBus appEventBus, ObjectMapper objectMapper) {
        return new ClaudeWorkflowAdapter(appEventBus, objectMapper);
    }

    @Bean
    public ProcessOutputListener<ClaudeStreamEvent> claudeProcessLoggingListener() {
        return new ProcessOutputListener<>() {
            @Override
            public void onStarted(String processId, Process process) {
                logger.info("[ClaudeCodeSubagent:{}] started (pid={})", processId, process.pid());
            }

            @Override
            public void onParsedEvent(ClaudeStreamEvent event) {
                logger.info("[ClaudeCodeSubagent][event] {}", event.summary());
            }

            @Override
            public void onParserError(String line, Exception exception) {
                logger.warn("[ClaudeCodeSubagent][parser-error] line={}", line, exception);
            }

            @Override
            public void onReaderError(Exception exception) {
                logger.warn("[ClaudeCodeSubagent][reader-error]", exception);
            }

            @Override
            public void onCompleted(int exitCode) {
                logger.info("[ClaudeCodeSubagent] completed (exitCode={})", exitCode);
            }
        };
    }

    @Bean
    public ProcessOutputListener<ClaudeStreamEvent> claudeCodeSubagentProcessListener(
            ProcessOutputListener<ClaudeStreamEvent> claudeProcessLoggingListener,
            ClaudeWorkflowAdapter claudeWorkflowAdapter) {
        return new CompositeProcessOutputListener<>(List.of(claudeProcessLoggingListener, claudeWorkflowAdapter));
    }

    @Bean
    public ClaudeCodeSubagentTools claudeCodeSubagentTools(
            ProcessOutputListener<ClaudeStreamEvent> claudeCodeSubagentProcessListener,
            ClaudeSubagentProperties properties,
            JclawPaths paths) {
        return ClaudeCodeSubagentTools.builder()
                .listener(claudeCodeSubagentProcessListener)
                .claudeCommand(properties.claudeCommand())
                .permissionMode(properties.permissionMode())
                .defaultWorkingDirectory(paths.launchDirectory().toString())
                .defaultTimeoutMs(properties.defaultTimeoutMs())
                .maxTimeoutMs(properties.maxTimeoutMs())
                .completedTraceTtlMs(properties.completedTraceTtlMs())
                .maxOutputCharacters(properties.maxOutputCharacters())
                .build();
    }
}
