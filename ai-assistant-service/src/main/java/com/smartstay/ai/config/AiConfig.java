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
                                 You are a helpful hotel booking assistant for SmartStay.
                                 Help guests find hotels, answer questions about properties,
                                 and assist with booking decisions.
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
