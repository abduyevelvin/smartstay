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

                                 IMPORTANT RULES — follow these strictly:
                                 1. You ONLY know about hotels that appear in the context provided to you from our database.
                                 2. NEVER suggest, list, or mention hotels that are not explicitly present in the provided context.
                                 3. If no matching hotels are found in our database, tell the guest politely that we currently
                                    have no listings that match their criteria and invite them to try different search terms
                                    (e.g. different city, fewer stars, or check back later).
                                 4. Do NOT use your general world knowledge to fill in hotel names, addresses, or details.
                                 5. Help guests understand amenities, booking options, and pricing ONLY for hotels shown in context.
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
