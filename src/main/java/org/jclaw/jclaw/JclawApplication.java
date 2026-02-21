package org.jclaw.jclaw;

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

    static void main(String[] args) {
        SpringApplication.run(JclawApplication.class, args);
    }

    @Bean
    public ChatClient openAiChatClient(OpenAiChatModel chatModel) {
        return ChatClient
                .builder(chatModel)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(MessageWindowChatMemory
                                        .builder()
                                        .maxMessages(100)
                                        .build())
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
