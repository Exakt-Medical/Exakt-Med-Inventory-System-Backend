package com.example.backend.onerx.service;

import com.example.backend.entity.AuditLog;
import com.example.backend.entity.Facility;
import com.example.backend.entity.InventoryTransaction;
import com.example.backend.entity.Sku;
import com.example.backend.entity.User;
import com.example.backend.onerx.dto.OneRxDispenseRequestDto;
import com.example.backend.onerx.dto.OneRxDispenseResponseDto;
import com.example.backend.repository.BatchRepository;
import com.example.backend.repository.FacilityRepository;
import com.example.backend.repository.InventoryTransactionRepository;
import com.example.backend.repository.SkuRepository;
import com.example.backend.repository.UserRepository;
import com.example.backend.security.FacilityAccessGuard;
import com.example.backend.service.AuditLogService;
import com.example.backend.service.BatchService;
import com.example.backend.service.InventoryTransactionService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

@Service
public class OneRxDispenseService {

    private final SkuRepository skuRepository;
    private final BatchRepository batchRepository;
    private final FacilityRepository facilityRepository;
    private final BatchService batchService;
    private final InventoryTransactionService inventoryTransactionService;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final AuditLogService auditLogService;
    private final UserRepository userRepository;
    private final FacilityAccessGuard facilityAccessGuard;

    public OneRxDispenseService(SkuRepository skuRepository,
                                BatchRepository batchRepository,
                                FacilityRepository facilityRepository,
                                BatchService batchService,
                                InventoryTransactionService inventoryTransactionService,
                                InventoryTransactionRepository inventoryTransactionRepository,
                                AuditLogService auditLogService,
                                UserRepository userRepository,
                                FacilityAccessGuard facilityAccessGuard) {
        this.skuRepository = skuRepository;
        this.batchRepository = batchRepository;
        this.facilityRepository = facilityRepository;
        this.batchService = batchService;
        this.inventoryTransactionService = inventoryTransactionService;
        this.inventoryTransactionRepository = inventoryTransactionRepository;
        this.auditLogService = auditLogService;
        this.userRepository = userRepository;
        this.facilityAccessGuard = facilityAccessGuard;
    }

    /**
     * Executes atomic dispensing of medications requested by OneRx.
     *
     * Validates all requested SKUs and available stock upfront with pessimistic row locks
     * and idempotency verification on rx_number.
     * If all are valid, performs FEFO batch deductions and logs individual per-batch
     * ledger entries in inventory_transactions with progressive running balances.
     */
    @Transactional
    public OneRxDispenseResponseDto dispenseMedications(OneRxDispenseRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body cannot be null.");
        }

        String rxNumber = request.getRxNumber();
        if (rxNumber == null || rxNumber.trim().isBlank()) {
            throw new IllegalArgumentException("Field 'rx_number' is required in the request body.");
        }
        String refNumber = rxNumber.trim();

        String facilityCode = request.getFacilityCode();
        if (facilityCode == null || facilityCode.trim().isBlank()) {
            throw new IllegalArgumentException("Field 'facility_code' is required in the request body.");
        }

        Facility facility = resolveFacility(facilityCode.trim())
                .orElseThrow(() -> new IllegalArgumentException("Facility not found with code: " + facilityCode));

        // Enforce facility access if called by a regular user token
        facilityAccessGuard.verifyFacilityAccess(facility.getId());

        // Idempotency guard: Prevent duplicate dispense for the same rx_number in this facility
        if (inventoryTransactionRepository.existsByFacilityIdAndTransactionTypeAndReferenceNumberIgnoreCase(
                facility.getId(), InventoryTransaction.Type.DISPENSE, refNumber)) {
            throw new IllegalArgumentException(
                    "Duplicate rx_number: Prescription '" + refNumber + "' has already been dispensed in facility '" + facility.getName() + "'.");
        }

        if (request.getMedicines() == null || request.getMedicines().isEmpty()) {
            throw new IllegalArgumentException("Medicines list cannot be empty.");
        }

        // ==========================================
        // PHASE 1: ATOMIC VALIDATION & PESSIMISTIC LOCKING
        // ==========================================
        class ValidatedItem {
            final Sku sku;
            final long unitsToDispense;

            ValidatedItem(Sku sku, long unitsToDispense) {
                this.sku = sku;
                this.unitsToDispense = unitsToDispense;
            }
        }

        // First collect and sort distinct SKU names to acquire pessimistic locks in deterministic order (prevents deadlocks)
        Set<String> sortedSkuNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (OneRxDispenseRequestDto.MedicineDispenseDto med : request.getMedicines()) {
            if (med.getBrands() == null || med.getBrands().isEmpty()) {
                throw new IllegalArgumentException("Each medicine must have at least one brand SKU specified.");
            }
            for (OneRxDispenseRequestDto.BrandDispenseDto brand : med.getBrands()) {
                String skuName = brand.getSkuName();
                if (skuName == null || skuName.trim().isBlank()) {
                    throw new IllegalArgumentException("SKU name is required for brand dispense item.");
                }
                Long requestedUnits = brand.getDispensedUnits();
                if (requestedUnits == null || requestedUnits <= 0) {
                    throw new IllegalArgumentException("Dispensed units must be greater than zero for SKU: " + skuName);
                }
                sortedSkuNames.add(skuName.trim());
            }
        }

        // Acquire PESSIMISTIC_WRITE locks on all target SKUs in alphabetical order
        // and compute effective unexpired available units (excluding un-deducted expired batches)
        LocalDate today = LocalDate.now();
        Map<String, Sku> lockedSkusByName = new HashMap<>();
        Map<Long, Long> effectiveAvailableBySkuId = new HashMap<>();
        for (String name : sortedSkuNames) {
            Sku lockedSku = skuRepository.findByFacilityIdAndNameForUpdate(facility.getId(), name)
                    .orElseThrow(() -> new IllegalArgumentException(
                            String.format("SKU '%s' not found for facility '%s'.", name, facility.getName())));
            lockedSkusByName.put(name.toUpperCase(), lockedSku);

            long rawUnits = lockedSku.getUnits() != null ? lockedSku.getUnits() : 0L;
            long undeductedExpiredUnits = batchRepository.sumUndeductedExpiredUnitsForSku(
                    facility.getId(),
                    lockedSku.getId(),
                    today
            );
            long effectiveAvailable = Math.max(0L, rawUnits - undeductedExpiredUnits);
            effectiveAvailableBySkuId.put(lockedSku.getId(), effectiveAvailable);
        }

        List<ValidatedItem> itemsToDispense = new ArrayList<>();
        Set<Long> processedSkuIds = new HashSet<>();
        Map<Long, Long> cumulativeRequestedBySkuId = new HashMap<>();
        long totalUnitsToDeduct = 0L;
        int totalBrandItems = 0;

        for (OneRxDispenseRequestDto.MedicineDispenseDto med : request.getMedicines()) {
            for (OneRxDispenseRequestDto.BrandDispenseDto brand : med.getBrands()) {
                String skuName = brand.getSkuName().trim();
                Long requestedUnits = brand.getDispensedUnits();

                Sku sku = lockedSkusByName.get(skuName.toUpperCase());
                long availableUnits = effectiveAvailableBySkuId.getOrDefault(sku.getId(), 0L);
                long cumulativeRequested = cumulativeRequestedBySkuId.getOrDefault(sku.getId(), 0L) + requestedUnits;

                if (availableUnits < cumulativeRequested) {
                    throw new IllegalArgumentException(String.format(
                            "Insufficient stock for SKU '%s' (%s). Requested: %d, Available (unexpired): %d",
                            sku.getName(),
                            sku.getBrandName() != null ? sku.getBrandName() : "N/A",
                            cumulativeRequested,
                            availableUnits
                    ));
                }

                cumulativeRequestedBySkuId.put(sku.getId(), cumulativeRequested);
                itemsToDispense.add(new ValidatedItem(sku, requestedUnits));
                processedSkuIds.add(sku.getId());
                totalUnitsToDeduct += requestedUnits;
                totalBrandItems++;
            }
        }

        // ==========================================
        // PHASE 2: EXECUTION & FEFO PER-BATCH DEDUCTION
        // ==========================================
        User currentUser = getCurrentUser();
        LocalDateTime eventTime = parseDispensedAt(request.getDispensedAt());

        String patientLabel = (request.getPatientName() != null && !request.getPatientName().isBlank())
                ? request.getPatientName().trim()
                : "N/A";
        String dispensedByLabel = (request.getDispensedBy() != null && !request.getDispensedBy().isBlank())
                ? request.getDispensedBy().trim()
                : (currentUser != null ? currentUser.getUsername() : "OneRx Integration");

        StringBuilder baseNotesBuilder = new StringBuilder();
        baseNotesBuilder.append("Dispensed by: ").append(dispensedByLabel);
        if (request.getNotes() != null && !request.getNotes().isBlank()) {
            baseNotesBuilder.append("; ").append(request.getNotes().trim());
        }
        String baseNotes = baseNotesBuilder.toString();

        int secondOffset = 0;

        for (ValidatedItem item : itemsToDispense) {
            Sku sku = item.sku;
            long unitsToDispense = item.unitsToDispense;

            // 1. FEFO Batch Deduction (locks candidate batches with PESSIMISTIC_WRITE)
            List<BatchService.BatchDeductionDetail> deductions = batchService.deductBatchesFEFO(
                    sku.getId(),
                    facility.getId(),
                    unitsToDispense
            );

            long currentRunningBalance = sku.getUnits() != null ? sku.getUnits() : 0L;

            if (!deductions.isEmpty()) {
                long totalDeductedFromBatches = 0L;
                // Option A: Log individual ledger rows per batch with progressive running balance
                for (BatchService.BatchDeductionDetail detail : deductions) {
                    long deductedFromBatch = detail.getDeductedUnits();
                    if (deductedFromBatch <= 0) {
                        continue;
                    }

                    long balanceAfterThisBatch = currentRunningBalance - deductedFromBatch;
                    LocalDateTime txTime = eventTime.plusSeconds(secondOffset++);

                    inventoryTransactionService.recordTransaction(
                            sku,
                            facility,
                            detail.getBatch(),
                            currentUser,
                            dispensedByLabel,
                            InventoryTransaction.Type.DISPENSE,
                            refNumber,
                            detail.getBatch().getBatchNum(),
                            detail.getBatch().getExpiryDate(),
                            null,
                            deductedFromBatch,
                            currentRunningBalance,
                            balanceAfterThisBatch,
                            "OneRx Dispense - Patient: " + patientLabel,
                            baseNotes,
                            txTime
                    );

                    currentRunningBalance = balanceAfterThisBatch;
                    totalDeductedFromBatches += deductedFromBatch;
                }

                // If legacy unbatched stock covered the remainder
                long unbatchedRemainder = unitsToDispense - totalDeductedFromBatches;
                if (unbatchedRemainder > 0) {
                    long balanceAfter = currentRunningBalance - unbatchedRemainder;
                    LocalDateTime txTime = eventTime.plusSeconds(secondOffset++);
                    inventoryTransactionService.recordTransaction(
                            sku,
                            facility,
                            null,
                            currentUser,
                            dispensedByLabel,
                            InventoryTransaction.Type.DISPENSE,
                            refNumber,
                            null,
                            null,
                            null,
                            unbatchedRemainder,
                            currentRunningBalance,
                            balanceAfter,
                            "OneRx Dispense - Patient: " + patientLabel,
                            baseNotes,
                            txTime
                    );
                    currentRunningBalance = balanceAfter;
                }
            } else {
                // Fallback for unbatched legacy SKU units
                long balanceAfter = currentRunningBalance - unitsToDispense;
                LocalDateTime txTime = eventTime.plusSeconds(secondOffset++);

                inventoryTransactionService.recordTransaction(
                        sku,
                        facility,
                        null,
                        currentUser,
                        dispensedByLabel,
                        InventoryTransaction.Type.DISPENSE,
                        refNumber,
                        null,
                        null,
                        null,
                        unitsToDispense,
                        currentRunningBalance,
                        balanceAfter,
                        "OneRx Dispense - Patient: " + patientLabel,
                        baseNotes,
                        txTime
                );

                currentRunningBalance = balanceAfter;
            }

            // 2. Decrement SKU on-hand inventory
            long finalSkuUnits = Math.max(0L, (sku.getUnits() != null ? sku.getUnits() : 0L) - unitsToDispense);
            sku.setUnits(finalSkuUnits);
            skuRepository.save(sku);
        }

        // ==========================================
        // PHASE 3: AUDIT LOGGING
        // ==========================================
        String auditDesc = String.format(
                "OneRx Dispensed %d total units across %d SKU brand(s) for patient '%s'. Dispensed by: %s.",
                totalUnitsToDeduct,
                totalBrandItems,
                patientLabel,
                dispensedByLabel
        );

        auditLogService.logAction(
                facility,
                currentUser,
                dispensedByLabel,
                "Dispensing",
                "ONERX_DISPENSED",
                "Medication Dispensed (" + totalUnitsToDeduct + " units)",
                AuditLog.Severity.INFO,
                refNumber,
                null,
                auditDesc,
                "Admin,Pharmacist"
        );

        return OneRxDispenseResponseDto.builder()
                .status("success")
                .referenceNumber(refNumber)
                .processedSkus(processedSkuIds.size())
                .processedSkuBrands(totalBrandItems)
                .totalUnitsDeducted(totalUnitsToDeduct)
                .processedAt(LocalDateTime.now())
                .build();
    }

    private Optional<Facility> resolveFacility(String facilityCode) {
        return facilityRepository.findByFacilityCode(facilityCode);
    }

    private User getCurrentUser() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                String username = authentication.getName();
                return userRepository.findByUsername(username).orElse(null);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private LocalDateTime parseDispensedAt(String dispensedAt) {
        if (dispensedAt == null || dispensedAt.trim().isBlank()) {
            return LocalDateTime.now();
        }
        String trimmed = dispensedAt.trim();
        try {
            return LocalDateTime.parse(trimmed);
        } catch (DateTimeParseException ignored) {
        }

        try {
            return OffsetDateTime.parse(trimmed).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
        }

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            return LocalDateTime.parse(trimmed, formatter);
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalDate.parse(trimmed).atStartOfDay();
        } catch (DateTimeParseException ignored) {
        }

        return LocalDateTime.now();
    }
}
