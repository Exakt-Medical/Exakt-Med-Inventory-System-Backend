package com.example.backend.onerx.messaging;

import com.example.backend.entity.InventoryDispenseInbox;
import com.example.backend.onerx.dto.OneRxDispenseResponseDto;
import com.example.backend.onerx.messaging.dto.OneRxDispenseMessageDto;
import com.example.backend.onerx.service.OneRxDispenseService;
import com.example.backend.repository.InventoryDispenseInboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "inventory.sync.messaging.enabled", havingValue = "true")
public class OneRxDispenseConsumer {

    private final ObjectMapper objectMapper;
    private final InventoryDispenseInboxRepository inboxRepository;
    private final OneRxDispenseService oneRxDispenseService;
    private final TransactionTemplate transactionTemplate;
    private final OneRxAckPublisher ackPublisher;

    @RabbitListener(queues = "${inventory.sync.messaging.dispense-queue:inventory.dispense.sync.queue}")
    public void handleDispenseEvent(String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) {
            log.warn("Received empty dispense payload from OneRx; rejecting to DLQ.");
            throw new AmqpRejectAndDontRequeueException("Empty dispense payload");
        }

        OneRxDispenseMessageDto message;
        try {
            message = objectMapper.readValue(rawPayload, OneRxDispenseMessageDto.class);
        } catch (Exception ex) {
            log.error("Failed to parse OneRx dispense JSON payload; rejecting to DLQ. payload={}", rawPayload, ex);
            throw new AmqpRejectAndDontRequeueException("Malformed OneRx dispense JSON payload", ex);
        }

        if (message == null
                || message.getRxNumber() == null || message.getRxNumber().isBlank()
                || message.getPharmacyCode() == null || message.getPharmacyCode().isBlank()) {
            log.warn("Received OneRx dispense event missing rx_number or pharmacy_code; rejecting to DLQ. payload={}", rawPayload);
            throw new AmqpRejectAndDontRequeueException("Missing required rx_number or pharmacy_code");
        }

        String rxNumber = message.getRxNumber().trim();
        String pharmacyCode = message.getPharmacyCode().trim();
        String eventId = resolveEventId(message.getEventId(), pharmacyCode, rxNumber);

        try {
            OneRxDispenseResponseDto response = transactionTemplate.execute(status -> {
                // 1. Idempotency check on event_id
                if (inboxRepository.existsByEventId(eventId)) {
                    log.warn("Dispense eventId={} for RX={} already exists in inbox. Skipping duplicate.", eventId, rxNumber);
                    return null;
                }

                log.info("Processing OneRx RabbitMQ dispense event: eventId={}, RX={}, pharmacyCode={}",
                        eventId, rxNumber, pharmacyCode);

                // 2. Reuse existing atomic FEFO deduction + ledger + dispensed_logs + audit_logs
                OneRxDispenseResponseDto dispenseResponse =
                        oneRxDispenseService.dispenseMedicationsFromMessaging(message.toDispenseRequestDto());

                // 3. Record PROCESSED in inventory_dispense_inbox inside the same transaction
                int totalUnits = dispenseResponse.getTotalUnitsDeducted() != null
                        ? Math.toIntExact(dispenseResponse.getTotalUnitsDeducted())
                        : 0;
                saveInboxEntry(eventId, rxNumber, pharmacyCode, InventoryDispenseInbox.Status.PROCESSED,
                        totalUnits, rawPayload, null);

                return dispenseResponse;
            });

            if (response != null) {
                int totalUnits = response.getTotalUnitsDeducted() != null
                        ? Math.toIntExact(response.getTotalUnitsDeducted())
                        : 0;
                log.info("Successfully processed OneRx RabbitMQ dispense: eventId={}, RX={}, totalUnitsDeducted={}",
                        eventId, rxNumber, totalUnits);
                ackPublisher.sendAck(eventId, rxNumber, pharmacyCode, totalUnits, "SUCCESS", null);
            } else {
                ackPublisher.sendAck(eventId, rxNumber, pharmacyCode, 0, "SKIPPED_DUPLICATE", null);
            }
        } catch (DataIntegrityViolationException ex) {
            // Concurrent delivery of the same eventId hit the UNIQUE constraint
            log.warn("Duplicate dispense eventId={} detected via unique constraint for RX={}. Skipping.", eventId, rxNumber);
            ackPublisher.sendAck(eventId, rxNumber, pharmacyCode, 0, "SKIPPED_DUPLICATE", null);
        } catch (IllegalArgumentException ex) {
            String errMsg = ex.getMessage() != null ? ex.getMessage() : "Invalid dispense request";

            // If HTTP fallback (POST /api/dispense) already processed this rx_number, mark SKIPPED_DUPLICATE and ACK
            if (errMsg.startsWith("Duplicate rx_number:")) {
                log.warn("RX={} already dispensed (likely via HTTP fallback). Recording SKIPPED_DUPLICATE for eventId={}.",
                        rxNumber, eventId);
                recordNonProcessedInboxSafely(eventId, rxNumber, pharmacyCode,
                        InventoryDispenseInbox.Status.SKIPPED_DUPLICATE, rawPayload, errMsg);
                ackPublisher.sendAck(eventId, rxNumber, pharmacyCode, 0, "SKIPPED_DUPLICATE", errMsg);
                return;
            }

            log.error("OneRx dispense validation/stock error for eventId={}, RX={}: {}. Routing to DLQ.",
                    eventId, rxNumber, errMsg);
            recordNonProcessedInboxSafely(eventId, rxNumber, pharmacyCode,
                    InventoryDispenseInbox.Status.FAILED, rawPayload, errMsg);
            ackPublisher.sendAck(eventId, rxNumber, pharmacyCode, 0, "FAILED", errMsg);
            throw new AmqpRejectAndDontRequeueException(errMsg, ex);
        } catch (Exception ex) {
            String errMsg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            log.error("Unexpected error processing OneRx dispense eventId={}, RX={}. Routing to DLQ.",
                    eventId, rxNumber, ex);
            recordNonProcessedInboxSafely(eventId, rxNumber, pharmacyCode,
                    InventoryDispenseInbox.Status.FAILED, rawPayload, errMsg);
            ackPublisher.sendAck(eventId, rxNumber, pharmacyCode, 0, "FAILED", errMsg);
            throw new AmqpRejectAndDontRequeueException("Failed to process OneRx dispense event", ex);
        }
    }

    private void recordNonProcessedInboxSafely(String eventId,
                                               String rxNumber,
                                               String pharmacyCode,
                                               InventoryDispenseInbox.Status status,
                                               String rawPayload,
                                               String errorMessage) {
        try {
            transactionTemplate.executeWithoutResult(tx -> {
                if (!inboxRepository.existsByEventId(eventId)) {
                    saveInboxEntry(eventId, rxNumber, pharmacyCode, status, 0, rawPayload, errorMessage);
                }
            });
        } catch (Exception inboxEx) {
            log.error("Failed to record {} inbox entry for eventId={}, RX={}", status, eventId, rxNumber, inboxEx);
        }
    }

    private void saveInboxEntry(String eventId,
                                String rxNumber,
                                String pharmacyCode,
                                InventoryDispenseInbox.Status status,
                                int totalUnitsDeducted,
                                String rawPayload,
                                String errorMessage) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Manila"));
        InventoryDispenseInbox entry = new InventoryDispenseInbox();
        entry.setEventId(truncate(eventId, 100));
        entry.setRxNumber(truncate(rxNumber, 100));
        entry.setPharmacyCode(truncate(pharmacyCode, 100));
        entry.setStatus(status);
        entry.setTotalUnitsDeducted(totalUnitsDeducted);
        entry.setRawPayload(rawPayload);
        entry.setErrorMessage(errorMessage);
        entry.setProcessedAt(now);
        entry.setCreatedAt(now);
        inboxRepository.save(entry);
    }

    private String resolveEventId(String rawEventId, String pharmacyCode, String rxNumber) {
        if (rawEventId != null && !rawEventId.isBlank()) {
            return truncate(rawEventId.trim(), 100);
        }
        return truncate("RX:" + pharmacyCode + ":" + rxNumber, 100);
    }

    private String truncate(String value, int maxLen) {
        if (value == null || value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen);
    }
}
