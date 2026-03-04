package com.jclaw.agent;

import com.jclaw.agent.chat.advisor.MyLoggingAdvisor;
import com.jclaw.agent.chat.tools.claudecode.ClaudeCodeSubagentTools;
import com.jclaw.agent.chat.tools.claudecode.ClaudeStreamEvent;
import com.jclaw.agent.chat.tools.process.ProcessOutputListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agent.tools.AskUserQuestionTool;
import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.GrepTool;
import org.springaicommunity.agent.tools.ShellTools;
import org.springaicommunity.agent.utils.CommandLineQuestionHandler;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.Scanner;

@SpringBootApplication
public class JclawApplication {

    private static final Logger logger = LoggerFactory.getLogger(JclawApplication.class);

    static void main(String[] args) {
        SpringApplication.run(JclawApplication.class, args);
    }

    @Bean
    public ProcessOutputListener<ClaudeStreamEvent> claudeCodeSubagentProcessListener() {
        return new ProcessOutputListener<>() {
            @Override
            public void onStarted(String processId, Process process) {
                logger.info("[ClaudeCodeSubagent:{}] started (pid={})", processId, process.pid());
            }

            @Override
            public void onStdoutLine(String line) {
                logger.debug("[ClaudeCodeSubagent][stdout] {}", line);
            }

            @Override
            public void onStderrLine(String line) {
                logger.warn("[ClaudeCodeSubagent][stderr] {}", line);
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
            public void onCompleted(int exitCode) {
                logger.info("[ClaudeCodeSubagent] completed (exitCode={})", exitCode);
            }

            @Override
            public void onReaderError(Exception exception) {
                logger.warn("[ClaudeCodeSubagent][reader-error]", exception);
            }
        };
    }

    @Bean
    public ClaudeCodeSubagentTools claudeCodeSubagentTools(
            ProcessOutputListener<ClaudeStreamEvent> claudeCodeSubagentProcessListener) {
        return ClaudeCodeSubagentTools.builder()
                .listener(claudeCodeSubagentProcessListener)
                .build();
    }

    @Bean
    public ChatClient openAiChatClient(OpenAiChatModel chatModel, ClaudeCodeSubagentTools claudeCodeSubagentTools) {
        return ChatClient
                .builder(chatModel)
                .defaultSystem("You are Jclaw, a helpful and precise planner for software developers." +
                        "You can answer questions and plan but execution should be done by coding subagents.")
                .defaultTools(AskUserQuestionTool.builder()
                        .questionHandler(new CommandLineQuestionHandler())
                        .answersValidation(true)
                        .build()
                )
                .defaultTools(
                        claudeCodeSubagentTools,
                        ShellTools.builder().build(),
                        FileSystemTools.builder().build(),
                        GrepTool.builder().build()
                )
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(MessageWindowChatMemory
                                        .builder()
                                        .maxMessages(100)
                                        .build())
                                .build()
                )
                .defaultAdvisors(
                        MyLoggingAdvisor
                                .builder()
                                .showSystemMessage(true)
                                .showAssistantText(true)
                                .showUserText(true)
                                .showAvailableTools(true)
                                .build()
                )
                .build();
    }

    @Bean
    CommandLineRunner commandLineRunner(ChatClient openAiChatClient) {
        return args -> {
            System.out.println("Hello, Jclaw!");

            try (Scanner scanner = new Scanner(System.in)) {
                while (true) {
                    System.out.print("You: ");
                    if (!scanner.hasNextLine()) {
                        System.out.println("No interactive input detected. Exiting.");
                        break;
                    }
                    String userInput = scanner.nextLine();
                    if ("exit".equalsIgnoreCase(userInput)) {
                        System.out.println("Goodbye!");
                        break;
                    }
                    ChatClient.CallResponseSpec response = openAiChatClient.prompt(userInput).call();
                    System.out.println("AI: " + response.content());
                }
            }
        };
    }

}
