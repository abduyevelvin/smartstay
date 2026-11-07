package com.smartstay.review.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic reviewCreatedTopic() {
        return TopicBuilder.name("review.created")
                           .partitions(1)
                           .replicas(1)
                           .build();
    }

    @Bean
    public NewTopic reviewAnalyzedTopic() {
        return TopicBuilder.name("review.analyzed")
                           .partitions(1)
                           .replicas(1)
                           .build();
    }
}
