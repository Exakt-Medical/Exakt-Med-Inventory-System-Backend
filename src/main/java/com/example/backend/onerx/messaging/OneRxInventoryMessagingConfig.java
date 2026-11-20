package com.example.backend.onerx.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "inventory.sync.messaging.enabled", havingValue = "true")
public class OneRxInventoryMessagingConfig {

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    public TopicExchange inventorySyncExchange(
            @Value("${inventory.sync.messaging.exchange:inventory.sync.exchange}") String exchangeName) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public DirectExchange inventorySyncDlx(
            @Value("${inventory.sync.messaging.dlx:inventory.sync.dlx}") String dlxName) {
        return new DirectExchange(dlxName, true, false);
    }

    @Bean
    public Queue inventoryDispenseDlq(
            @Value("${inventory.sync.messaging.dispense-dlq:inventory.dispense.dlq}") String dlqName) {
        return QueueBuilder.durable(dlqName).build();
    }

    @Bean
    public Binding inventoryDispenseDlqBinding(
            @Qualifier("inventoryDispenseDlq") Queue inventoryDispenseDlq,
            @Qualifier("inventorySyncDlx") DirectExchange inventorySyncDlx,
            @Value("${inventory.sync.messaging.dispense-dlq-routing-key:inventory.dispense.dlq}") String dlqRoutingKey) {
        return BindingBuilder.bind(inventoryDispenseDlq).to(inventorySyncDlx).with(dlqRoutingKey);
    }

    @Bean
    public Queue inventoryDispenseQueue(
            @Value("${inventory.sync.messaging.dispense-queue:inventory.dispense.sync.queue}") String queueName,
            @Value("${inventory.sync.messaging.dlx:inventory.sync.dlx}") String dlxName,
            @Value("${inventory.sync.messaging.dispense-dlq-routing-key:inventory.dispense.dlq}") String dlqRoutingKey) {
        return QueueBuilder.durable(queueName)
                .deadLetterExchange(dlxName)
                .deadLetterRoutingKey(dlqRoutingKey)
                .build();
    }

    @Bean
    public Binding inventoryDispenseBinding(
            @Qualifier("inventoryDispenseQueue") Queue inventoryDispenseQueue,
            @Qualifier("inventorySyncExchange") TopicExchange inventorySyncExchange,
            @Value("${inventory.sync.messaging.dispense-routing-key:inventory.dispense.onerx}") String routingKey) {
        return BindingBuilder.bind(inventoryDispenseQueue).to(inventorySyncExchange).with(routingKey);
    }
}
