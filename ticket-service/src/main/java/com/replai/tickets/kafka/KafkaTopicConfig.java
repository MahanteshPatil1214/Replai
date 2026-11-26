package com.replai.tickets.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

/**
 * Creates the two topics at startup so the first send never blocks on auto-creation.
 */
@Configuration
public class KafkaTopicConfig {

    public static final int PARTITIONS = 1;
    public static final int REPLICATION = 1;

    @Bean
    public NewTopic ticketCreatedTopic() {
        return TopicBuilder.name(KafkaTopics.TICKET_CREATED)
                .partitions(PARTITIONS)
                .replicas(REPLICATION)
                .build();
    }

    @Bean
    public NewTopic ticketEnrichedTopic() {
        return TopicBuilder.name(KafkaTopics.TICKET_ENRICHED)
                .partitions(PARTITIONS)
                .replicas(REPLICATION)
                .build();
    }

    @Bean
    public KafkaAdmin.NewTopics topicsToCreate() {
        return new KafkaAdmin.NewTopics(ticketCreatedTopic(), ticketEnrichedTopic());
    }
}
