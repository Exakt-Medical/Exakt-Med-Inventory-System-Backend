package com.example.backend.onerx.messaging;

import com.example.backend.entity.Pharmacy;
import com.example.backend.entity.Sku;
import com.example.backend.onerx.messaging.dto.OneRxStockUpdatePayload;
import com.example.backend.onerx.messaging.event.StockChangedEvent;
import com.example.backend.repository.BatchRepository;
import com.example.backend.repository.SkuRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "inventory.sync.messaging.enabled", havingValue = "true")
public class OneRxStockUpdateListener {

    private final SkuRepository skuRepository;
    private final BatchRepository batchRepository;
    private final OneRxStockPublisher stockPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onStockChanged(StockChangedEvent event) {
        if (event == null || event.skuId() == null) {
            return;
        }

        try {
            Optional<Sku> skuOpt = skuRepository.findById(event.skuId());
            if (skuOpt.isEmpty()) {
                return;
            }

            Sku sku = skuOpt.get();
            Pharmacy pharmacy = sku.getPharmacy();
            if (pharmacy == null || sku.getLibMedicine() == null || sku.getLibMedicine().getDrugCode() == null) {
                return;
            }

            String pharmacyCode = (pharmacy.getPharmacyCode() != null && !pharmacy.getPharmacyCode().isBlank())
                    ? pharmacy.getPharmacyCode()
                    : String.valueOf(pharmacy.getId());

            long effectiveAvailable = computeEffectiveAvailableQuantity(sku, pharmacy.getId());
            OneRxStockUpdatePayload.StockItemDto item = stockPublisher.toStockItemDto(sku, effectiveAvailable);

            stockPublisher.publishStockUpdate(pharmacyCode, List.of(item));
        } catch (Exception ex) {
            log.warn("Failed to handle StockChangedEvent for skuId={}: {}", event.skuId(), ex.getMessage(), ex);
        }
    }

    private long computeEffectiveAvailableQuantity(Sku sku, Long pharmacyId) {
        if (sku.getStatus() == Sku.Status.Inactive) {
            return 0L;
        }
        long rawUnits = sku.getUnits() != null ? sku.getUnits() : 0L;
        long undeductedExpired = batchRepository.sumUndeductedExpiredUnitsForSku(
                pharmacyId,
                sku.getId(),
                LocalDate.now()
        );
        return Math.max(0L, rawUnits - undeductedExpired);
    }
}
