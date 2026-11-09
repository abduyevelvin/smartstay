package com.smartstay.notification.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    // Declare the source topics so they exist even if booking-service starts after us.
    // Retry + DLT topics are auto-created by @RetryableTopic on the consumer.

    @Bean
    public NewTopic bookingConfirmedTopic() {
        return TopicBuilder.name("booking.confirmed")
                           .partitions(1)
                           .replicas(1)
                           .build();
    }

    @Bean
    public NewTopic bookingCancelledTopic() {
        return TopicBuilder.name("booking.cancelled")
                           .partitions(1)
                           .replicas(1)
                           .build();
    }
}
