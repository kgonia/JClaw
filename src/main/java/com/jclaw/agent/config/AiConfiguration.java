package com.jclaw.agent.config;

import com.jclaw.agent.tui.ExecutionModeService;
import com.jclaw.agent.tui.JclawAppState;
import com.jclaw.agent.tui.ModeAwareToolCatalog;
import com.jclaw.agent.tui.StreamingChatService;
import com.jclaw.agent.tui.event.AppEventBus;
import com.jclaw.agent.tui.session.SessionRepository;
import com.jclaw.agent.tui.session.SessionService;
import com.jclaw.agent.tui.workflow.WorkflowEventStore;
import com.jclaw.agent.tui.workflow.WorkflowProjectionService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;

@Configuration
public class AiConfiguration {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper(new JsonFactory());
    }

    @Bean
    public JclawPaths jclawPaths(SessionProperties sessionProperties) {
        return JclawPaths.create(sessionProperties);
    }

    @Bean
    @Primary
    public DataSource sessionDataSource(JclawPaths paths) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:file:" + paths.sessionDatabasePath() + ";DB_CLOSE_ON_EXIT=FALSE");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    @Bean
    public JdbcTemplate sessionJdbcTemplate(DataSource sessionDataSource) {
        return new JdbcTemplate(sessionDataSource);
    }

    @Bean
    public DataSourceTransactionManager sessionTransactionManager(DataSource sessionDataSource) {
        return new DataSourceTransactionManager(sessionDataSource);
    }

    @Bean
    public DataSource globalConfigDataSource(JclawPaths paths) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:file:" + paths.globalConfigDatabasePath() + ";DB_CLOSE_ON_EXIT=FALSE");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    @Bean
    public JdbcTemplate globalConfigJdbcTemplate(DataSource globalConfigDataSource) {
        return new JdbcTemplate(globalConfigDataSource);
    }

    @Bean
    public UserConfigStore userConfigStore(JdbcTemplate globalConfigJdbcTemplate) {
        return new UserConfigStore(globalConfigJdbcTemplate);
    }

    @Bean
    public SessionRepository sessionRepository(JdbcTemplate sessionJdbcTemplate) {
        initializeChatMemorySchema(sessionJdbcTemplate);
        return new SessionRepository(sessionJdbcTemplate);
    }

    @Bean
    public WorkflowEventStore workflowEventStore(JdbcTemplate sessionJdbcTemplate, ObjectMapper objectMapper) {
        return new WorkflowEventStore(sessionJdbcTemplate, objectMapper);
    }

    @Bean
    public JdbcChatMemoryRepository jdbcChatMemoryRepository(DataSource sessionDataSource) {
        return JdbcChatMemoryRepository.builder()
                .dataSource(sessionDataSource)
                .build();
    }

    @Bean
    public ChatMemory chatMemory(JdbcChatMemoryRepository jdbcChatMemoryRepository, AiProperties aiProperties) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(jdbcChatMemoryRepository)
                .maxMessages(aiProperties.maxMessages())
                .build();
    }

    @Bean
    public MessageChatMemoryAdvisor messageChatMemoryAdvisor(ChatMemory chatMemory) {
        return MessageChatMemoryAdvisor.builder(chatMemory).build();
    }

    @Bean
    public ChatClient chatClient(OpenAiChatModel chatModel, AiProperties aiProperties,
                                 MessageChatMemoryAdvisor messageChatMemoryAdvisor) {
        return ChatClient.builder(chatModel)
                .defaultSystem(aiProperties.systemPrompt())
                .defaultAdvisors(messageChatMemoryAdvisor)
                .build();
    }

    @Bean
    public JclawAppState appState() {
        return new JclawAppState();
    }

    @Bean
    public WorkflowProjectionService workflowProjectionService(WorkflowEventStore workflowEventStore, JclawAppState appState) {
        return new WorkflowProjectionService(workflowEventStore, appState);
    }

    @Bean
    public SessionService sessionService(SessionRepository sessionRepository, ChatMemory chatMemory,
                                         WorkflowProjectionService workflowProjectionService, AppEventBus appEventBus,
                                         JclawPaths paths) {
        return new SessionService(sessionRepository, chatMemory, workflowProjectionService, appEventBus,
                paths.launchDirectory().toString());
    }

    @Bean
    public ExecutionModeService executionModeService() {
        return new ExecutionModeService();
    }

    @Bean
    public StreamingChatService streamingChatService(ChatClient chatClient, ModeAwareToolCatalog toolCatalog,
                                                     ExecutionModeService executionModeService,
                                                     SessionService sessionService, JclawAppState appState,
                                                     AppEventBus appEventBus) {
        return new StreamingChatService(chatClient, toolCatalog, executionModeService, sessionService, appState, appEventBus);
    }

    private void initializeChatMemorySchema(JdbcTemplate sessionJdbcTemplate) {
        sessionJdbcTemplate.execute("""
                create table if not exists SPRING_AI_CHAT_MEMORY (
                    conversation_id varchar(36) not null,
                    content longvarchar not null,
                    type varchar(10) not null check (type in ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
                    timestamp timestamp default current_timestamp not null
                )
                """);
        sessionJdbcTemplate.execute("""
                create index if not exists SPRING_AI_CHAT_MEMORY_CONVERSATION_ID_TIMESTAMP_IDX
                on SPRING_AI_CHAT_MEMORY (conversation_id, timestamp desc)
                """);
        sessionJdbcTemplate.execute("""
                alter table SPRING_AI_CHAT_MEMORY
                alter column conversation_id varchar(128)
                """);
    }
}
