package com.orbita.payments.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Pre-creates Kafka topics on startup.
 * (Kafka also auto-creates them, but explicit creation ensures correct partition count.)
 */
@Configuration
public class KafkaTopicConfig {

    @Value("${orbita.kafka.topics.payment-requested}")
    private String paymentRequestedTopic;

    @Value("${orbita.kafka.topics.payment-completed}")
    private String paymentCompletedTopic;

    @Value("${orbita.kafka.topics.payment-failed}")
    private String paymentFailedTopic;

    @Bean
    public NewTopic paymentRequestedTopic() {
        return TopicBuilder.name(paymentRequestedTopic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentCompletedTopic() {
        return TopicBuilder.name(paymentCompletedTopic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentFailedTopic() {
        return TopicBuilder.name(paymentFailedTopic).partitions(3).replicas(1).build();
    }
}
