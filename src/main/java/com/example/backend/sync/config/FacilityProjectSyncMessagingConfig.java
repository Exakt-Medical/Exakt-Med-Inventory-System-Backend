package com.example.backend.sync.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "facility-project.sync.messaging.enabled", havingValue = "true")
public class FacilityProjectSyncMessagingConfig {

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    public DirectExchange facilityProjectSyncExchange(
            @Value("${facility-project.sync.messaging.exchange:facility-project.sync.exchange}") String name) {
        return new DirectExchange(name, true, false);
    }

    @Bean
    public Queue facilitySyncQueue(
            @Value("${facility-project.sync.messaging.facility-queue:ipkras.facility.sync.queue}") String name) {
        return new Queue(name, true);
    }

    @Bean
    public Queue projectSyncQueue(
            @Value("${facility-project.sync.messaging.project-queue:ipkras.project.sync.queue}") String name) {
        return new Queue(name, true);
    }

    @Bean
    public Binding facilitySyncBinding(
            @Qualifier("facilitySyncQueue") Queue facilitySyncQueue,
            @Qualifier("facilityProjectSyncExchange") DirectExchange facilityProjectSyncExchange,
            @Value("${facility-project.sync.messaging.facility-routing-key:facility.sync}") String routingKey) {
        return BindingBuilder.bind(facilitySyncQueue).to(facilityProjectSyncExchange).with(routingKey);
    }

    @Bean
    public Binding projectSyncBinding(
            @Qualifier("projectSyncQueue") Queue projectSyncQueue,
            @Qualifier("facilityProjectSyncExchange") DirectExchange facilityProjectSyncExchange,
            @Value("${facility-project.sync.messaging.project-routing-key:project.sync}") String routingKey) {
        return BindingBuilder.bind(projectSyncQueue).to(facilityProjectSyncExchange).with(routingKey);
    }
}

