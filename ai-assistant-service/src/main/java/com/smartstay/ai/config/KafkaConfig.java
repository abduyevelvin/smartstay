package com.smartstay.ai.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    // review.created is published by review-service — no need to declare here
    // We declare review.analyzed because this service publishes it
    @Bean
    public NewTopic reviewAnalyzedTopic() {
        return TopicBuilder.name("review.analyzed")
                           .partitions(1)
                           .replicas(1)
                           .build();
    }
}
