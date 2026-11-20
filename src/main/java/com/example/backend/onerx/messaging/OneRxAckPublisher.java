package com.example.backend.onerx.messaging;

import com.example.backend.onerx.messaging.dto.OneRxDispenseAckDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "inventory.sync.messaging.enabled", havingValue = "true")
public class OneRxAckPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${inventory.sync.messaging.exchange:inventory.sync.exchange}")
    private String exchange;

    @Value("${inventory.sync.messaging.ack-routing-key:inventory.dispense.ack}")
    private String ackRoutingKey;

    public void sendAck(String originalEventId,
                        String rxNumber,
                        String pharmacyCode,
                        int totalUnitsDeducted,
                        String status,
                        String errorMessage) {
        try {
            OneRxDispenseAckDto ack = OneRxDispenseAckDto.builder()
                    .eventId(UUID.randomUUID().toString())
                    .originalEventId(originalEventId)
                    .sourceSystem("INVENTORY")
                    .eventType("DISPENSE_ACK")
                    .rxNumber(rxNumber)
                    .pharmacyCode(pharmacyCode)
                    .totalUnitsDeducted(totalUnitsDeducted)
                    .status(status)
                    .errorMessage(errorMessage)
                    .timestamp(Instant.now().toString())
                    .build();

            byte[] jsonBytes = objectMapper.writeValueAsString(ack).getBytes(StandardCharsets.UTF_8);
            Message amqpMessage = MessageBuilder.withBody(jsonBytes)
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setContentEncoding(StandardCharsets.UTF_8.name())
                    .build();

            rabbitTemplate.send(exchange, ackRoutingKey, amqpMessage);
            log.info("Published OneRx dispense ACK: routingKey={}, RX={}, status={}, totalUnitsDeducted={}",
                    ackRoutingKey, rxNumber, status, totalUnitsDeducted);
        } catch (Exception ex) {
            // Never fail or roll back stock deduction if ACK publishing fails
            log.warn("Failed to publish OneRx dispense ACK for RX={}, status={}: {}",
                    rxNumber, status, ex.getMessage(), ex);
        }
    }
}
