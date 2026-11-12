package com.smartstay.booking.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

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

    /**
     * Consumed by booking-service to cancel all active bookings when a hotel is deleted.
     * Produced by hotel-service.
     */
    @Bean
    public NewTopic hotelDeletedTopic() {
        return TopicBuilder.name("hotel.deleted")
                           .partitions(1)
                           .replicas(1)
                           .build();
    }
}
