package com.example.backend.onerx.messaging;

import com.example.backend.entity.Sku;
import com.example.backend.onerx.messaging.dto.OneRxStockUpdatePayload;
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
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "inventory.sync.messaging.enabled", havingValue = "true")
public class OneRxStockPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${inventory.sync.messaging.exchange:inventory.sync.exchange}")
    private String exchange;

    @Value("${inventory.sync.messaging.stock-routing-key:inventory.stock.update}")
    private String stockRoutingKey;

    @Value("${inventory.sync.messaging.snapshot-routing-key:inventory.stock.snapshot}")
    private String snapshotRoutingKey;

    public void publishStockUpdate(String pharmacyCode, List<OneRxStockUpdatePayload.StockItemDto> items) {
        publishPayload(stockRoutingKey, "STOCK_UPDATED", pharmacyCode, items);
    }

    public void publishStockSnapshot(String pharmacyCode, List<OneRxStockUpdatePayload.StockItemDto> items) {
        publishPayload(snapshotRoutingKey, "STOCK_SNAPSHOT", pharmacyCode, items);
    }

    public void publishPayload(String routingKey,
                               String eventType,
                               String pharmacyCode,
                               List<OneRxStockUpdatePayload.StockItemDto> items) {
        if (pharmacyCode == null || pharmacyCode.isBlank() || items == null || items.isEmpty()) {
            return;
        }

        try {
            OneRxStockUpdatePayload payload = OneRxStockUpdatePayload.builder()
                    .eventId(UUID.randomUUID().toString())
                    .sourceSystem("INVENTORY")
                    .eventType(eventType)
                    .pharmacyCode(pharmacyCode)
                    .timestamp(Instant.now().toString())
                    .items(items)
                    .build();

            byte[] jsonBytes = objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);
            Message amqpMessage = MessageBuilder.withBody(jsonBytes)
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setContentEncoding(StandardCharsets.UTF_8.name())
                    .build();

            rabbitTemplate.send(exchange, routingKey, amqpMessage);
            log.info("Published OneRx {} event: routingKey={}, pharmacyCode={}, itemsCount={}",
                    eventType, routingKey, pharmacyCode, items.size());
        } catch (Exception ex) {
            log.warn("Failed to publish OneRx {} event for pharmacyCode={}: {}",
                    eventType, pharmacyCode, ex.getMessage(), ex);
        }
    }

    public OneRxStockUpdatePayload.StockItemDto toStockItemDto(Sku sku, long effectiveAvailableQuantity) {
        int safeQty = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, effectiveAvailableQuantity));
        String drugCode = sku.getLibMedicine() != null ? sku.getLibMedicine().getDrugCode() : null;
        String drugName = sku.getLibMedicine() != null ? sku.getLibMedicine().getDrugDescription() : null;

        return OneRxStockUpdatePayload.StockItemDto.builder()
                .drugCode(drugCode)
                .drugName(drugName)
                .brandName(sku.getBrandName())
                .skuName(sku.getName())
                .availableQuantity(safeQty)
                .packagingUnit(sku.getPackagingUnit())
                .dosageForm(sku.getDosageForm())
                .dosageStrength(sku.getDosageStrength())
                .build();
    }
}
