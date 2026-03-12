package com.jclaw.agent.config;

import com.jclaw.agent.tui.ChatPane;
import com.jclaw.agent.tui.ExecutionModeService;
import com.jclaw.agent.tui.JclawAppState;
import com.jclaw.agent.tui.JclawTuiApp;
import com.jclaw.agent.tui.StreamingChatService;
import com.jclaw.agent.tui.WorkflowPane;
import com.jclaw.agent.tui.event.AppEvent;
import com.jclaw.agent.tui.event.AppEventBus;
import com.jclaw.agent.tui.event.AppEventLoop;
import com.jclaw.agent.tui.session.SessionService;
import com.jclaw.agent.tui.slash.SlashCommandParser;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

@Configuration
public class TuiConfiguration {

    @Bean
    public SlashCommandParser slashCommandParser() {
        return new SlashCommandParser();
    }

    @Bean
    public ChatPane chatPane(JclawAppState appState, SlashCommandParser slashCommandParser,
                             AppEventBus appEventBus, SessionService sessionService,
                             ExecutionModeService executionModeService) {
        return new ChatPane(appState, slashCommandParser, appEventBus, sessionService, executionModeService);
    }

    @Bean
    public WorkflowPane workflowPane() {
        return new WorkflowPane();
    }

    @Bean
    public AppEventBus appEventBus() {
        return new AppEventBus();
    }

    @Bean
    public AppEventLoop appEventLoop(AppEventBus appEventBus, JclawAppState appState,
                                     StreamingChatService streamingChatService, ChatMemory chatMemory,
                                     SessionService sessionService) {
        return new AppEventLoop(appEventBus, appState, streamingChatService, chatMemory, sessionService);
    }

    @Bean
    public JclawTuiApp jclawTuiApp(JclawAppState appState, ChatPane chatPane, WorkflowPane workflowPane,
                                   AppEventBus appEventBus, TuiProperties tuiProperties) {
        return new JclawTuiApp(appState, chatPane, workflowPane, appEventBus, tuiProperties);
    }

    @Bean
    @ConditionalOnProperty(name = "jclaw.tui.enabled", havingValue = "true", matchIfMissing = true)
    public ApplicationRunner tuiApplicationRunner(JclawTuiApp jclawTuiApp, SessionService sessionService,
                                                  ExecutionModeService executionModeService, AppEventBus appEventBus,
                                                  AppEventLoop appEventLoop) {
        return new ApplicationRunner() {
            @Override
            public void run(ApplicationArguments args) throws Exception {
                Optional<String> requestedSessionId = Optional.ofNullable(args.getOptionValues("session"))
                        .filter(values -> !values.isEmpty())
                        .map(values -> values.getFirst());

                appEventLoop.start();
                sessionService.openSession(requestedSessionId);
                appEventBus.dispatch(new AppEvent.ModeChange(executionModeService.currentMode()));

                try {
                    jclawTuiApp.run();
                }
                catch (Exception e) {
                    System.out.println("Terminal not supported");
                    System.exit(1);
                }
            }
        };
    }
}
