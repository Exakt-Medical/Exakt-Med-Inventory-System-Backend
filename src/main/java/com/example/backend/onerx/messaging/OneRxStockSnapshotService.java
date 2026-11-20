package com.example.backend.onerx.messaging;

import com.example.backend.entity.Pharmacy;
import com.example.backend.entity.Sku;
import com.example.backend.onerx.messaging.dto.OneRxStockUpdatePayload;
import com.example.backend.repository.BatchRepository;
import com.example.backend.repository.PharmacyRepository;
import com.example.backend.repository.SkuRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "inventory.sync.messaging.enabled", havingValue = "true")
public class OneRxStockSnapshotService {

    private final SkuRepository skuRepository;
    private final BatchRepository batchRepository;
    private final PharmacyRepository pharmacyRepository;
    private final OneRxStockPublisher stockPublisher;

    @Value("${inventory.sync.messaging.snapshot-batch-size:200}")
    private int snapshotBatchSize;

    /**
     * Publishes a full (or single-pharmacy) unexpired stock snapshot to OneRx
     * via inventory.stock.snapshot in chunked batches (default 200 items/message).
     */
    @Transactional(readOnly = true)
    public SnapshotResult pushStockSnapshot(String optionalPharmacyCode) {
        Long targetPharmacyId = null;
        if (optionalPharmacyCode != null && !optionalPharmacyCode.isBlank()) {
            Pharmacy pharmacy = resolvePharmacy(optionalPharmacyCode.trim())
                    .orElseThrow(() -> new IllegalArgumentException("Pharmacy not found with code: " + optionalPharmacyCode));
            targetPharmacyId = pharmacy.getId();
        }

        int batchSize = snapshotBatchSize > 0 ? snapshotBatchSize : 200;
        LocalDate today = LocalDate.now();

        log.info("Starting OneRx stock snapshot push (pharmacyFilter={}, batchSize={})...",
                optionalPharmacyCode != null ? optionalPharmacyCode : "ALL", batchSize);

        List<Sku> activeSkus = skuRepository.findActiveSkusForSnapshot(targetPharmacyId);
        if (activeSkus.isEmpty()) {
            log.info("No active SKUs found for OneRx stock snapshot.");
            return SnapshotResult.builder()
                    .status("success")
                    .pharmaciesSynced(0)
                    .totalSkusPublished(0)
                    .totalMessagesPublished(0)
                    .pharmacyCodes(List.of())
                    .build();
        }

        // Group SKUs by Pharmacy preserving query order
        Map<Long, List<Sku>> skusByPharmacyId = new LinkedHashMap<>();
        Map<Long, Pharmacy> pharmacyById = new HashMap<>();
        for (Sku sku : activeSkus) {
            if (sku.getPharmacy() == null || sku.getLibMedicine() == null || sku.getLibMedicine().getDrugCode() == null) {
                continue;
            }
            Long pharmacyId = sku.getPharmacy().getId();
            pharmacyById.putIfAbsent(pharmacyId, sku.getPharmacy());
            skusByPharmacyId.computeIfAbsent(pharmacyId, k -> new ArrayList<>()).add(sku);
        }

        int totalSkusPublished = 0;
        int totalMessagesPublished = 0;
        List<String> syncedPharmacyCodes = new ArrayList<>();

        for (Map.Entry<Long, List<Sku>> entry : skusByPharmacyId.entrySet()) {
            Long pharmacyId = entry.getKey();
            Pharmacy pharmacy = pharmacyById.get(pharmacyId);
            List<Sku> pharmacySkus = entry.getValue();

            String pharmacyCode = (pharmacy.getPharmacyCode() != null && !pharmacy.getPharmacyCode().isBlank())
                    ? pharmacy.getPharmacyCode()
                    : String.valueOf(pharmacy.getId());

            // Fetch un-deducted expired batch units for this pharmacy in one query
            Map<Long, Long> expiredUnitsBySkuId = new HashMap<>();
            List<Object[]> expiredMetrics = batchRepository.findExpiredBatchMetricsByPharmacyId(pharmacyId, today);
            for (Object[] row : expiredMetrics) {
                if (row != null && row.length >= 3 && row[0] != null && row[2] != null) {
                    expiredUnitsBySkuId.put(((Number) row[0]).longValue(), ((Number) row[2]).longValue());
                }
            }

            List<OneRxStockUpdatePayload.StockItemDto> items = new ArrayList<>(pharmacySkus.size());
            for (Sku sku : pharmacySkus) {
                long rawUnits = sku.getUnits() != null ? sku.getUnits() : 0L;
                long expiredUnits = expiredUnitsBySkuId.getOrDefault(sku.getId(), 0L);
                long effectiveAvailable = Math.max(0L, rawUnits - expiredUnits);
                items.add(stockPublisher.toStockItemDto(sku, effectiveAvailable));
            }

            // Publish in chunks of batchSize (default 200)
            for (int start = 0; start < items.size(); start += batchSize) {
                int end = Math.min(start + batchSize, items.size());
                List<OneRxStockUpdatePayload.StockItemDto> chunk = items.subList(start, end);
                stockPublisher.publishStockSnapshot(pharmacyCode, chunk);
                totalMessagesPublished++;
                totalSkusPublished += chunk.size();
            }

            syncedPharmacyCodes.add(pharmacyCode);
        }

        log.info("Completed OneRx stock snapshot push: pharmacies={}, skus={}, messages={}",
                syncedPharmacyCodes.size(), totalSkusPublished, totalMessagesPublished);

        return SnapshotResult.builder()
                .status("success")
                .pharmaciesSynced(syncedPharmacyCodes.size())
                .totalSkusPublished(totalSkusPublished)
                .totalMessagesPublished(totalMessagesPublished)
                .pharmacyCodes(syncedPharmacyCodes)
                .build();
    }

    private Optional<Pharmacy> resolvePharmacy(String pharmacyCode) {
        Optional<Pharmacy> opt = pharmacyRepository.findByPharmacyCodeIgnoreCase(pharmacyCode);
        if (opt.isPresent()) {
            return opt;
        }
        try {
            return pharmacyRepository.findById(Long.parseLong(pharmacyCode));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SnapshotResult {
        private String status;
        private int pharmaciesSynced;
        private int totalSkusPublished;
        private int totalMessagesPublished;
        private List<String> pharmacyCodes;
    }
}
