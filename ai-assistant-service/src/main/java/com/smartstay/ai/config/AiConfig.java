package com.smartstay.ai.config;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableFeignClients(basePackages = "com.smartstay.ai.client")
@EnableScheduling
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                         .defaultSystem("""
                                 You are a helpful hotel booking assistant for SmartStay, an online hotel booking platform.
                                 You can chat naturally with guests — answer greetings, general questions, and help with travel advice.
                                 
                                 IMPORTANT RULES — follow these strictly:
                                 1. When a hotel context block is provided, you ONLY recommend hotels that appear in that context.
                                 2. NEVER invent, suggest, or mention hotel names, addresses, or details not present in the provided context.
                                 3. If a guest asks about hotels and no context block was given (or it is empty), tell them politely
                                    that you couldn't retrieve the hotel listings right now and invite them to try refreshing or
                                    searching on the main page.
                                 4. Do NOT use your general world knowledge to fill in hotel names or details.
                                 5. Help guests understand amenities, booking options, and pricing ONLY for hotels shown in context.
                                 6. For greetings, general travel questions, or anything unrelated to a specific hotel lookup,
                                    respond in a warm and helpful way — you don't need hotel data to hold a conversation.
                                 Always be friendly, concise, and accurate.
                                 """)
                         .build();
    }

    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel,
                                   JdbcTemplate jdbcTemplate) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                            .dimensions(1536)           // text-embedding-3-small=1536
                            .distanceType(COSINE_DISTANCE)
                            .initializeSchema(true)     // auto-creates vector_store table
                            .build();
    }

    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                                      .chatMemoryRepository(new InMemoryChatMemoryRepository())
                                      .maxMessages(10)
                                      .build();
    }
}
