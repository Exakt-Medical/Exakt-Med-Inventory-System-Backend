package com.example.backend.service;

import com.example.backend.dto.batch.BatchReleaseRequestDto;
import com.example.backend.dto.batch.BatchRequestDto;
import com.example.backend.dto.batch.BatchResponseDto;
import com.example.backend.entity.Batch;
import com.example.backend.entity.Facility;
import com.example.backend.entity.InventoryTransaction;
import com.example.backend.entity.Order;
import com.example.backend.entity.OrderedItem;
import com.example.backend.entity.Sku;
import com.example.backend.entity.AuditLog;
import com.example.backend.entity.User;
import com.example.backend.repository.BatchRepository;
import com.example.backend.repository.FacilityRepository;
import com.example.backend.repository.OrderRepository;
import com.example.backend.repository.OrderedItemRepository;
import com.example.backend.repository.SkuRepository;
import com.example.backend.repository.UserRepository;
import com.example.backend.repository.LibLocationRepository;
import com.example.backend.entity.LibLocation;
import com.example.backend.dto.common.PageResponseDto;
import com.example.backend.dto.batch.BatchSummaryDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class BatchService {

    private final BatchRepository batchRepository;
    private final OrderedItemRepository orderedItemRepository;
    private final SkuRepository skuRepository;
    private final FacilityRepository facilityRepository;
    private final OrderRepository orderRepository;
    private final AuditLogService auditLogService;
    private final InventoryTransactionService inventoryTransactionService;
    private final UserRepository userRepository;
    private final LibLocationRepository libLocationRepository;

    public BatchService(BatchRepository batchRepository,
                        OrderedItemRepository orderedItemRepository,
                        SkuRepository skuRepository,
                        FacilityRepository facilityRepository,
                        OrderRepository orderRepository,
                        AuditLogService auditLogService,
                        InventoryTransactionService inventoryTransactionService,
                        UserRepository userRepository,
                        LibLocationRepository libLocationRepository) {
        this.batchRepository = batchRepository;
        this.orderedItemRepository = orderedItemRepository;
        this.skuRepository = skuRepository;
        this.facilityRepository = facilityRepository;
        this.orderRepository = orderRepository;
        this.auditLogService = auditLogService;
        this.inventoryTransactionService = inventoryTransactionService;
        this.userRepository = userRepository;
        this.libLocationRepository = libLocationRepository;
    }

    // 1. RECEIVE SINGLE BATCH (Pharmacist / Admin / SuperAdmin)
    @Transactional
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Pharmacist')")
    public BatchResponseDto receiveBatch(BatchRequestDto request) {
        if (request == null) {
            throw new RuntimeException("Request body cannot be null.");
        }

        if (request.getOrderedItemId() == null) {
            throw new RuntimeException("Ordered item ID is required.");
        }

        OrderedItem orderedItem = orderedItemRepository.findById(request.getOrderedItemId())
                .orElseThrow(() -> new RuntimeException("Ordered item not found with id: " + request.getOrderedItemId()));

        Facility facility = null;
        if (request.getFacilityId() != null) {
            facility = facilityRepository.findById(request.getFacilityId())
                    .orElseThrow(() -> new RuntimeException("Facility not found with id: " + request.getFacilityId()));
        } else if (orderedItem.getOrder() != null && orderedItem.getOrder().getFacility() != null) {
            facility = orderedItem.getOrder().getFacility();
        } else if (orderedItem.getSku() != null && orderedItem.getSku().getFacility() != null) {
            facility = orderedItem.getSku().getFacility();
        }

        if (facility == null) {
            throw new RuntimeException("Facility is required for receiving batch.");
        }

        String trimmedBatchNum = request.getBatchNum() != null ? request.getBatchNum().trim() : "";
        if (trimmedBatchNum.isEmpty()) {
            throw new RuntimeException("Batch number is required.");
        }

        if (batchRepository.existsByBatchNumAndFacilityId(trimmedBatchNum, facility.getId())) {
            throw new RuntimeException("Batch number '" + trimmedBatchNum + "' already exists in this facility.");
        }

        if (request.getManufactureDate() == null) {
            throw new RuntimeException("Manufacture date is required.");
        }

        if (request.getExpiryDate() == null) {
            throw new RuntimeException("Expiry date is required.");
        }

        if (!request.getExpiryDate().isAfter(request.getManufactureDate())) {
            throw new RuntimeException("Expiry date must be after manufacture date.");
        }

        if (request.getLocationId() == null) {
            throw new RuntimeException("Storage location is required for receiving batch.");
        }

        LibLocation location = libLocationRepository.findById(request.getLocationId())
                .orElseThrow(() -> new RuntimeException("Storage location not found with ID: " + request.getLocationId()));

        if (location.getIsActive() != null && !location.getIsActive()) {
            throw new RuntimeException("Storage location '" + location.getCode() + "' is inactive and cannot accept new batches.");
        }

        if (location.getFacility() != null && !location.getFacility().getId().equals(facility.getId())) {
            throw new RuntimeException("Storage location '" + location.getCode() + "' belongs to another facility.");
        }

        Batch batch = new Batch();
        batch.setFacility(facility);
        batch.setOrderedItem(orderedItem);
        batch.setLocation(location);
        batch.setBatchNum(trimmedBatchNum);
        long units = (request.getUnits() != null && request.getUnits() > 0)
                ? request.getUnits()
                : (orderedItem.getOrderedUnits() != null ? orderedItem.getOrderedUnits() : 0L);
        batch.setUnits(units);
        batch.setManufactureDate(request.getManufactureDate());
        batch.setExpiryDate(request.getExpiryDate());
        batch.setStatus(request.getStatus() != null ? request.getStatus() : Batch.Status.Available);
        batch.setNotes(request.getNotes());

        Batch savedBatch = batchRepository.save(batch);

        // INCREMENT SKU UNITS only if status is Available (quarantined batches do not increment active SKU units)
        if (savedBatch.getStatus() == Batch.Status.Available) {
            Sku sku = orderedItem.getSku();
            if (sku != null) {
                long currentUnits = sku.getUnits() != null ? sku.getUnits() : 0L;
                long unitsToAdd = savedBatch.getUnits() != null ? savedBatch.getUnits() : 0L;
                sku.setUnits(currentUnits + unitsToAdd);
                skuRepository.save(sku);

                String ref = "BATCH-" + savedBatch.getBatchNum();
                String poSupplier = "";
                if (orderedItem.getOrder() != null) {
                    if (orderedItem.getOrder().getPurchaseOrderNum() != null) {
                        ref = orderedItem.getOrder().getPurchaseOrderNum();
                    }
                    if (orderedItem.getOrder().getSupplier() != null) {
                        poSupplier = " from " + orderedItem.getOrder().getSupplier().getName();
                    }
                }

                inventoryTransactionService.recordTransaction(
                        sku,
                        savedBatch.getFacility(),
                        savedBatch,
                        getCurrentUser(),
                        InventoryTransaction.Type.RECEIPT,
                        ref,
                        savedBatch.getBatchNum(),
                        savedBatch.getExpiryDate(),
                        unitsToAdd,
                        null,
                        currentUnits,
                        currentUnits + unitsToAdd,
                        "PO Stock Received" + poSupplier,
                        savedBatch.getNotes(),
                        savedBatch.getReceivedAt() != null ? savedBatch.getReceivedAt() : java.time.LocalDateTime.now()
                );
            }
        }

        // UPDATE ORDERED ITEM RECEIVED UNITS & UPDATE PARENT ORDER STATUS
        long itemReceived = (orderedItem.getReceivedUnits() != null ? orderedItem.getReceivedUnits() : 0L) + units;
        orderedItem.setReceivedUnits(itemReceived);
        orderedItemRepository.save(orderedItem);

        Order order = orderedItem.getOrder();
        if (order != null) {
            updateOrderStatusOnReceipt(order);
        }

        auditLogService.logAction(
                savedBatch.getFacility(),
                "Inventory",
                "BATCH_RECEIVED",
                "Batch Stock Received (" + savedBatch.getUnits() + " units)",
                AuditLog.Severity.SUCCESS,
                savedBatch.getBatchNum(),
                savedBatch.getId(),
                "Received batch '" + savedBatch.getBatchNum() + "' (" + savedBatch.getUnits() + " units) for SKU '" + (orderedItem.getSku() != null ? orderedItem.getSku().getName() : "") + "' at location [" + location.getCode() + "]. Expiry: " + savedBatch.getExpiryDate() + ".",
                "Admin,Pharmacist"
        );

        return mapToResponseDto(savedBatch);
    }

    // 2. RECEIVE MULTIPLE BATCHES BULK (e.g. from multi-item PO)
    @Transactional
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Pharmacist')")
    public List<BatchResponseDto> receiveBatchesBulk(List<BatchRequestDto> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new RuntimeException("Batch requests list cannot be empty.");
        }

        List<BatchResponseDto> responses = new ArrayList<>();
        for (BatchRequestDto req : requests) {
            responses.add(receiveBatch(req));
        }
        return responses;
    }

    // 3. GET BATCHES PAGINATED (Search strictly by batchNum as requested)
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Pharmacist', 'Procurement')")
    public PageResponseDto<BatchResponseDto> getBatchesPaginated(
            Long facilityId,
            String search,
            String status,
            String sku,
            String expiryFilter,
            int page,
            int size,
            String sortBy,
            String sortDir) {

        if (facilityId == null) {
            throw new IllegalArgumentException("Facility ID is required.");
        }

        int pageIndex = Math.max(0, page);
        int pageSize = size > 0 ? size : 10;

        Sort.Direction direction = "ASC".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortProperty = (sortBy != null && !sortBy.isBlank()) ? sortBy.trim() : "receivedAt";

        if ("batchNumber".equalsIgnoreCase(sortProperty)) sortProperty = "batchNum";
        else if ("quantity".equalsIgnoreCase(sortProperty)) sortProperty = "units";
        else if ("expiryDate".equalsIgnoreCase(sortProperty)) sortProperty = "expiryDate";
        else if ("manufacturingDate".equalsIgnoreCase(sortProperty)) sortProperty = "manufactureDate";
        else if ("status".equalsIgnoreCase(sortProperty)) sortProperty = "status";
        else sortProperty = "receivedAt";

        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by(direction, sortProperty));

        LocalDate today = LocalDate.now();
        LocalDate nearExpiryDate = today.plusDays(90);

        String trimmedSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        String trimmedStatus = (status != null && !status.trim().isEmpty()) ? status.trim() : null;
        String trimmedSku = (sku != null && !sku.trim().isEmpty()) ? sku.trim() : null;
        String trimmedExpiry = (expiryFilter != null && !expiryFilter.trim().isEmpty()) ? expiryFilter.trim() : null;

        Page<Batch> batchPage = batchRepository.findBatchesPaginated(
                facilityId,
                trimmedSearch,
                trimmedStatus,
                trimmedSku,
                trimmedExpiry,
                today,
                nearExpiryDate,
                pageable);

        Page<BatchResponseDto> dtoPage = batchPage.map(this::mapToResponseDto);
        return PageResponseDto.from(dtoPage);
    }

    // 3c. GET BATCH SUMMARY KPIS
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Pharmacist', 'Procurement')")
    public BatchSummaryDto getBatchSummary(Long facilityId) {
        if (facilityId == null) {
            throw new IllegalArgumentException("Facility ID is required.");
        }
        LocalDate nearExpiryDate = LocalDate.now().plusDays(90);

        long totalBatches = batchRepository.countByFacilityId(facilityId);
        long totalActiveStock = batchRepository.sumActiveStockByFacilityId(facilityId);
        long expiryAlertCount = batchRepository.countExpiryAlertsByFacilityId(facilityId, nearExpiryDate);
        long quarantinedCount = batchRepository.countQuarantinedByFacilityId(facilityId);

        return BatchSummaryDto.builder()
                .totalBatches(totalBatches)
                .totalActiveStock(totalActiveStock)
                .expiryAlertCount(expiryAlertCount)
                .quarantinedCount(quarantinedCount)
                .build();
    }

    // 3d. GET DISTINCT SKUS IN BATCHES
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Pharmacist', 'Procurement')")
    public List<String> getDistinctBatchSkus(Long facilityId) {
        if (facilityId == null) {
            return Collections.emptyList();
        }
        return batchRepository.findDistinctSkuNamesByFacilityId(facilityId);
    }

    // 3e. GET BATCHES BY SKU (Targeted retrieval for modal adjustments & expired deductions)
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Pharmacist', 'Procurement')")
    public List<BatchResponseDto> getBatchesBySku(Long facilityId, Long skuId) {
        if (facilityId == null || skuId == null) {
            return Collections.emptyList();
        }
        return batchRepository.findBatchesForSkuOrderByReceivedAtAsc(facilityId, skuId)
                .stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
    }

    // 3f. GET AGGREGATED EXPIRED BATCH METRICS PER SKU FOR A FACILITY
    @Transactional(readOnly = true)
    public Map<Long, long[]> getExpiredBatchMetrics(Long facilityId) {
        if (facilityId == null) {
            return Collections.emptyMap();
        }
        List<Object[]> rows = batchRepository.findExpiredBatchMetricsByFacilityId(facilityId, LocalDate.now());
        Map<Long, long[]> result = new HashMap<>();
        for (Object[] row : rows) {
            Long skuId = (Long) row[0];
            if (skuId != null) {
                long count = ((Number) row[1]).longValue();
                long units = ((Number) row[2]).longValue();
                result.put(skuId, new long[]{count, units});
            }
        }
        return result;
    }


    // 4. GET BATCH BY ID
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Pharmacist', 'Procurement')")
    public BatchResponseDto getBatchById(Long id) {
        Batch batch = batchRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Batch not found with id: " + id));
        return mapToResponseDto(batch);
    }

    // 5. UPDATE BATCH STATUS & NOTES (Quarantine / Release / Expiry)
    @Transactional
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Pharmacist')")
    public BatchResponseDto updateBatchStatus(Long id, Batch.Status status, String notes) {
        Batch batch = batchRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Batch not found with id: " + id));

        Batch.Status oldStatus = batch.getStatus();
        if (status != null && status != oldStatus) {
            // 1. Available -> Quarantined: deduct units from active SKU inventory
            if (oldStatus == Batch.Status.Available && status == Batch.Status.Quarantined) {
                adjustSkuUnits(batch, -(batch.getUnits() != null ? batch.getUnits() : 0L));
            }
            // 2. Available -> Expired: deduct units from active SKU inventory
            else if (oldStatus == Batch.Status.Available && status == Batch.Status.Expired) {
                adjustSkuUnits(batch, -(batch.getUnits() != null ? batch.getUnits() : 0L));
            }
            // 3. Expired -> Available: add units back if mistakenly marked expired
            else if (oldStatus == Batch.Status.Expired && status == Batch.Status.Available) {
                adjustSkuUnits(batch, batch.getUnits() != null ? batch.getUnits() : 0L);
            }

            batch.setStatus(status);
        }

        if (notes != null) {
            batch.setNotes(notes.trim());
        }

        Batch updated = batchRepository.save(batch);

        auditLogService.logAction(
                updated.getFacility(),
                "Inventory",
                "BATCH_STATUS_UPDATED",
                "Batch Status Changed (" + oldStatus + " -> " + status + ")",
                status == Batch.Status.Quarantined ? AuditLog.Severity.WARNING : AuditLog.Severity.INFO,
                updated.getBatchNum(),
                updated.getId(),
                "Updated batch status for '" + updated.getBatchNum() + "' from " + oldStatus + " to " + status + "." + (notes != null && !notes.isBlank() ? " Notes: " + notes : ""),
                "Admin,Pharmacist"
        );

        return mapToResponseDto(updated);
    }

    // 5b. RELEASE QUARANTINED BATCH WITH PARTIAL/FULL ACCEPTANCE & REJECTION WRITE-OFF
    @Transactional
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Pharmacist')")
    public BatchResponseDto releaseBatchFromQuarantine(Long id, BatchReleaseRequestDto request) {
        if (id == null) {
            throw new IllegalArgumentException("Batch ID is required.");
        }
        if (request == null || request.getAcceptedUnits() == null) {
            throw new IllegalArgumentException("Accepted units is required.");
        }

        Batch batch = batchRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Batch not found with id: " + id));

        if (batch.getStatus() != Batch.Status.Quarantined) {
            throw new IllegalStateException("Only Quarantined batches can be released. Current status is " + batch.getStatus());
        }

        long currentBatchUnits = batch.getUnits() != null ? batch.getUnits() : 0L;
        long acceptedUnits = request.getAcceptedUnits();

        if (acceptedUnits <= 0) {
            throw new IllegalArgumentException("Accepted units must be greater than zero.");
        }
        if (acceptedUnits > currentBatchUnits) {
            throw new IllegalArgumentException("Accepted units (" + acceptedUnits + ") cannot exceed current quarantined units (" + currentBatchUnits + ").");
        }

        long rejectedUnits = currentBatchUnits - acceptedUnits;
        if (rejectedUnits > 0 && (request.getRejectionReason() == null || request.getRejectionReason().isBlank())) {
            throw new IllegalArgumentException("Rejection reason is required when accepting fewer units than quarantined.");
        }

        // 1. Update Batch: set status to Available, update units to acceptedUnits
        batch.setStatus(Batch.Status.Available);
        batch.setUnits(acceptedUnits);

        StringBuilder notesBuilder = new StringBuilder();
        if (batch.getNotes() != null && !batch.getNotes().isBlank()) {
            notesBuilder.append(batch.getNotes().trim()).append("; ");
        }
        notesBuilder.append("Released from Quarantine (Accepted: ").append(acceptedUnits);
        if (rejectedUnits > 0) {
            notesBuilder.append(", Rejected: ").append(rejectedUnits).append(" [Reason: ").append(request.getRejectionReason().trim()).append("]");
        }
        notesBuilder.append(")");
        if (request.getNotes() != null && !request.getNotes().isBlank()) {
            notesBuilder.append(" - Notes: ").append(request.getNotes().trim());
        }
        batch.setNotes(notesBuilder.toString());

        Batch savedBatch = batchRepository.save(batch);

        // 2. Increment active SKU inventory by accepted units only
        Sku sku = savedBatch.getOrderedItem() != null ? savedBatch.getOrderedItem().getSku() : null;
        long currentSkuUnits = sku != null && sku.getUnits() != null ? sku.getUnits() : 0L;
        adjustSkuUnits(savedBatch, acceptedUnits);
        long finalSkuUnits = sku != null && sku.getUnits() != null ? sku.getUnits() : currentSkuUnits + acceptedUnits;

        User currentUser = getCurrentUser();
        LocalDateTime now = LocalDateTime.now();

        // 3. Log InventoryTransaction(s) with accurate running balances
        if (sku != null) {
            if (rejectedUnits > 0) {
                // Step A: Log the release of the entire quarantined batch quantity into stock
                long fullQuarantineUnits = currentBatchUnits;
                long intermediateBalance = currentSkuUnits + fullQuarantineUnits;

                inventoryTransactionService.recordTransaction(
                        sku,
                        savedBatch.getFacility(),
                        savedBatch,
                        currentUser,
                        InventoryTransaction.Type.QUARANTINE_RELEASE,
                        "BATCH-" + savedBatch.getBatchNum(),
                        savedBatch.getBatchNum(),
                        savedBatch.getExpiryDate(),
                        fullQuarantineUnits,
                        null,
                        currentSkuUnits,
                        intermediateBalance,
                        "Batch Released from Quarantine",
                        savedBatch.getNotes(),
                        now
                );

                // Step B: Log the deduction for rejected write-off units
                String adjNotes = String.format("Quarantine QC write-off (Batch: %s, Expiry: %s)%s",
                        savedBatch.getBatchNum(),
                        savedBatch.getExpiryDate() != null ? savedBatch.getExpiryDate().toString() : "N/A",
                        (request.getNotes() != null && !request.getNotes().isBlank()) ? " - " + request.getNotes().trim() : "");

                inventoryTransactionService.recordTransaction(
                        sku,
                        savedBatch.getFacility(),
                        savedBatch,
                        currentUser,
                        InventoryTransaction.Type.ADJUSTMENT_SUB,
                        "QC-REJECT-" + savedBatch.getBatchNum(),
                        savedBatch.getBatchNum(),
                        savedBatch.getExpiryDate(),
                        null,
                        rejectedUnits,
                        intermediateBalance,
                        finalSkuUnits,
                        request.getRejectionReason().trim(),
                        adjNotes,
                        now.plusSeconds(1)
                );
            } else if (acceptedUnits > 0) {
                // Fully accepted batch
                inventoryTransactionService.recordTransaction(
                        sku,
                        savedBatch.getFacility(),
                        savedBatch,
                        currentUser,
                        InventoryTransaction.Type.QUARANTINE_RELEASE,
                        "BATCH-" + savedBatch.getBatchNum(),
                        savedBatch.getBatchNum(),
                        savedBatch.getExpiryDate(),
                        acceptedUnits,
                        null,
                        currentSkuUnits,
                        finalSkuUnits,
                        "Batch Released from Quarantine",
                        savedBatch.getNotes(),
                        now
                );
            }
        }

        // 4. Adjust PO OrderedItem received units if units were rejected
        if (rejectedUnits > 0 && savedBatch.getOrderedItem() != null) {
            OrderedItem oi = savedBatch.getOrderedItem();
            long currentReceived = oi.getReceivedUnits() != null ? oi.getReceivedUnits() : 0L;
            oi.setReceivedUnits(Math.max(0L, currentReceived - rejectedUnits));
            orderedItemRepository.save(oi);
            if (oi.getOrder() != null) {
                updateOrderStatusOnReceipt(oi.getOrder());
            }
        }

        // 5. Central Audit Log
        String auditDesc = String.format("Batch '%s' released from Quarantine. %d units accepted into active inventory.%s%s",
                savedBatch.getBatchNum(),
                acceptedUnits,
                rejectedUnits > 0 ? " " + rejectedUnits + " units rejected (" + request.getRejectionReason().trim() + ")." : "",
                (request.getNotes() != null && !request.getNotes().isBlank()) ? " Comments: " + request.getNotes().trim() : "");

        auditLogService.logAction(
                savedBatch.getFacility(),
                currentUser,
                "Inventory",
                "BATCH_RELEASED",
                "Batch Released (" + acceptedUnits + " units accepted" + (rejectedUnits > 0 ? ", " + rejectedUnits + " rejected" : "") + ")",
                rejectedUnits > 0 ? AuditLog.Severity.WARNING : AuditLog.Severity.SUCCESS,
                savedBatch.getBatchNum(),
                savedBatch.getId(),
                auditDesc,
                "Admin,Pharmacist"
        );

        return mapToResponseDto(savedBatch);
    }

    // 5c. MARK PAST-DUE BATCHES AS EXPIRED (Status change only, no SKU deduction)
    @Transactional
    public void markPastDueBatchesAsExpired(Long facilityId) {
        LocalDate today = LocalDate.now();
        List<Batch> pastDue;
        if (facilityId != null) {
            pastDue = batchRepository.findByFacilityIdAndStatusAndExpiryDateLessThanEqual(
                    facilityId, Batch.Status.Available, today);
        } else {
            pastDue = batchRepository.findByStatusAndExpiryDateLessThanEqual(
                    Batch.Status.Available, today);
        }
        for (Batch batch : pastDue) {
            batch.setStatus(Batch.Status.Expired);
            String autoNote = "Expired on " + today;
            if (batch.getNotes() == null || batch.getNotes().isBlank()) {
                batch.setNotes(autoNote);
            } else if (!batch.getNotes().contains(autoNote)) {
                batch.setNotes(batch.getNotes() + " | " + autoNote);
            }
            batchRepository.save(batch);
        }
    }

    // 5d. SCHEDULED DAILY MIDNIGHT EXPIRATION STATUS UPDATE
    @Scheduled(cron = "0 0 0 * * ?")
    public void autoMarkExpiredBatchesScheduled() {
        markPastDueBatchesAsExpired(null);
    }

    // 6. PROCESS EXPIRED BATCHES FOR A SKU (Deducts active SKU units & keeps batch units intact)
    @Transactional
    public int processExpiredBatches(Long facilityId, Long skuId) {
        if (facilityId == null) {
            throw new IllegalArgumentException("Facility ID is required to process expired batches.");
        }
        if (skuId == null) {
            throw new IllegalArgumentException("SKU ID is required to process expired batches.");
        }
        LocalDate today = LocalDate.now();
        List<Batch> expiredBatches = batchRepository.findExpiredBatches(
                facilityId, skuId, today);

        for (Batch batch : expiredBatches) {
            long batchUnits = batch.getUnits() != null ? batch.getUnits() : 0L;
            batch.setStatus(Batch.Status.Expired);
            if (batchUnits > 0 && !Boolean.TRUE.equals(batch.getSkuDeducted())) {
                Sku s = batch.getOrderedItem() != null ? batch.getOrderedItem().getSku() : null;
                long curUnits = s != null && s.getUnits() != null ? s.getUnits() : 0L;
                adjustSkuUnits(batch, -batchUnits);
                long newUnits = Math.max(0L, curUnits - batchUnits);
                batch.setSkuDeducted(true);

                if (s != null) {
                    inventoryTransactionService.recordTransaction(
                            s,
                            batch.getFacility(),
                            batch,
                            null,
                            InventoryTransaction.Type.EXPIRY_DEDUCTION,
                            "EXP-" + batch.getBatchNum(),
                            batch.getBatchNum(),
                            batch.getExpiryDate(),
                            null,
                            batchUnits,
                            curUnits,
                            newUnits,
                            "Expired stock write-off",
                            batch.getNotes(),
                            java.time.LocalDateTime.now()
                    );
                }
            }
            // Do not make batch units 0; deduction is only in SKU units
            String autoNote = "Expired & SKU units deducted on " + today;
            if (batch.getNotes() == null || batch.getNotes().isBlank()) {
                batch.setNotes(autoNote);
            } else if (!batch.getNotes().contains(autoNote)) {
                batch.setNotes(batch.getNotes() + " | " + autoNote);
            }
            batchRepository.save(batch);

            auditLogService.logAction(
                    batch.getFacility(),
                    null,
                    "Inventory",
                    "BATCH_EXPIRED",
                    "Batch Expired (" + batch.getBatchNum() + ")",
                    AuditLog.Severity.CRITICAL,
                    batch.getBatchNum(),
                    batch.getId(),
                    "Batch '" + batch.getBatchNum() + "' passed expiration date (" + batch.getExpiryDate() + "). Deducted " + batchUnits + " units from active SKU inventory.",
                    "Admin,Pharmacist"
            );
        }
        return expiredBatches.size();
    }

    // 7. CHECK IF SKU HAS UNPROCESSED EXPIRED BATCHES
    @Transactional(readOnly = true)
    public boolean hasUnprocessedExpiredBatches(Long facilityId, Long skuId) {
        if (facilityId == null || skuId == null) {
            return false;
        }
        return !batchRepository.findExpiredBatches(facilityId, skuId, LocalDate.now()).isEmpty();
    }

    // 8. DEDUCT BATCHES ACCORDING TO FEFO (FIRST EXPIRE FIRST OUT - AVAILABLE & UNEXPIRED ONLY)
    public static class BatchDeductionDetail {
        private final Batch batch;
        private final long deductedUnits;

        public BatchDeductionDetail(Batch batch, long deductedUnits) {
            this.batch = batch;
            this.deductedUnits = deductedUnits;
        }

        public Batch getBatch() {
            return batch;
        }

        public long getDeductedUnits() {
            return deductedUnits;
        }
    }

    @Transactional
    public List<BatchDeductionDetail> deductBatchesFEFO(Long skuId, Long facilityId, long unitsToDeduct) {
        List<BatchDeductionDetail> details = new ArrayList<>();
        if (unitsToDeduct <= 0 || skuId == null || facilityId == null) {
            return details;
        }

        LocalDate today = LocalDate.now();
        List<Batch> availableBatches = batchRepository.findAvailableBatchesForSkuFEFO(
                skuId, facilityId, Batch.Status.Available, today);

        long remaining = unitsToDeduct;
        for (Batch batch : availableBatches) {
            if (remaining <= 0) {
                break;
            }
            long currentBatchUnits = batch.getUnits() != null ? batch.getUnits() : 0L;
            if (currentBatchUnits <= 0) {
                continue;
            }

            long deductFromThisBatch;
            if (currentBatchUnits <= remaining) {
                deductFromThisBatch = currentBatchUnits;
                remaining -= currentBatchUnits;
                batch.setUnits(0L);
            } else {
                deductFromThisBatch = remaining;
                batch.setUnits(currentBatchUnits - remaining);
                remaining = 0L;
            }
            Batch saved = batchRepository.save(batch);
            details.add(new BatchDeductionDetail(saved, deductFromThisBatch));
        }
        return details;
    }


    // 9. ADD UNITS TO SPECIFIC BATCH (Disallows expired batches)
    @Transactional
    public Batch addUnitsToBatch(Long batchId, Long skuId, long unitsToAdd) {
        if (batchId == null || unitsToAdd <= 0) {
            return null;
        }
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Target batch not found with id: " + batchId));

        if (skuId != null && batch.getOrderedItem() != null && batch.getOrderedItem().getSku() != null) {
            if (!batch.getOrderedItem().getSku().getId().equals(skuId)) {
                throw new RuntimeException("Selected batch does not belong to this SKU.");
            }
        }

        if (batch.getStatus() == Batch.Status.Expired ||
                (batch.getExpiryDate() != null && !batch.getExpiryDate().isAfter(LocalDate.now()))) {
            throw new RuntimeException("Cannot add stock to an expired batch.");
        }

        long currentBatchUnits = batch.getUnits() != null ? batch.getUnits() : 0L;
        batch.setUnits(currentBatchUnits + unitsToAdd);

        return batchRepository.save(batch);
    }

    // 10. DEDUCT UNITS FROM SPECIFIC BATCH (When adjustment reason is not Dispensed)
    @Transactional
    public Batch deductUnitsFromBatch(Long batchId, Long skuId, long unitsToDeduct) {
        if (batchId == null || unitsToDeduct <= 0) {
            return null;
        }
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Target batch not found with id: " + batchId));

        if (skuId != null && batch.getOrderedItem() != null && batch.getOrderedItem().getSku() != null) {
            if (!batch.getOrderedItem().getSku().getId().equals(skuId)) {
                throw new RuntimeException("Selected batch does not belong to this SKU.");
            }
        }

        long currentBatchUnits = batch.getUnits() != null ? batch.getUnits() : 0L;
        if (unitsToDeduct > currentBatchUnits) {
            throw new RuntimeException("Cannot deduct " + unitsToDeduct + " units from Batch #" + batch.getBatchNum() +
                    " (only " + currentBatchUnits + " units available in this batch).");
        }

        batch.setUnits(currentBatchUnits - unitsToDeduct);
        return batchRepository.save(batch);
    }

    // 11. CHECK IF FACILITY HAS BATCHES FOR SKU
    @Transactional(readOnly = true)
    public boolean hasBatchesForSku(Long facilityId, Long skuId) {
        if (facilityId == null || skuId == null) {
            return false;
        }
        return !batchRepository.findBatchesForSkuOrderByReceivedAtAsc(facilityId, skuId).isEmpty();
    }

    // HELPER: ADJUST ACTIVE SKU UNITS
    private void adjustSkuUnits(Batch batch, long delta) {
        OrderedItem item = batch.getOrderedItem();
        if (item != null && item.getSku() != null) {
            Sku sku = item.getSku();
            long currentUnits = sku.getUnits() != null ? sku.getUnits() : 0L;
            long newUnits = Math.max(0L, currentUnits + delta);
            sku.setUnits(newUnits);
            skuRepository.save(sku);
        }
    }

    // MAPPER HELPER
    private BatchResponseDto mapToResponseDto(Batch batch) {
        BatchResponseDto dto = new BatchResponseDto();
        dto.setId(batch.getId());
        dto.setBatchNum(batch.getBatchNum());
        dto.setManufactureDate(batch.getManufactureDate());
        dto.setUnits(batch.getUnits());
        dto.setExpiryDate(batch.getExpiryDate());
        dto.setStatus(batch.getStatus());
        dto.setNotes(batch.getNotes());
        dto.setReceivedAt(batch.getReceivedAt());

        if (batch.getFacility() != null) {
            dto.setFacilityId(batch.getFacility().getId());
            dto.setFacilityName(batch.getFacility().getName());
        }

        OrderedItem item = batch.getOrderedItem();
        if (item != null) {
            dto.setOrderedItemId(item.getId());
            dto.setQuantity(item.getOrderedUnits());

            if (item.getOrder() != null) {
                dto.setOrderId(item.getOrder().getId());
                dto.setPoNumber(item.getOrder().getPurchaseOrderNum());
                if (item.getOrder().getSupplier() != null) {
                    dto.setSupplierName(item.getOrder().getSupplier().getName());
                }
            }

            Sku sku = item.getSku();
            if (sku != null) {
                dto.setSkuId(sku.getId());
                dto.setSkuName(sku.getName());
                dto.setBrandName(sku.getBrandName());
                dto.setDosageForm(sku.getDosageForm());
                dto.setPackagingUnit(sku.getPackagingUnit());
                if (sku.getLibMedicine() != null) {
                    dto.setGenericName(sku.getLibMedicine().getDrugDescription());
                }
            }
        }
        dto.setSkuDeducted(batch.getSkuDeducted() != null ? batch.getSkuDeducted() : false);

        if (batch.getLocation() != null) {
            dto.setLocationId(batch.getLocation().getId());
            dto.setLocationCode(batch.getLocation().getCode());
            dto.setLocationStorageType(batch.getLocation().getStorageType());
        }

        return dto;
    }

    // UPDATE BATCH LOCATION (Relocate)
    @Transactional
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Pharmacist')")
    public BatchResponseDto updateBatchLocation(Long batchId, Long locationId) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Batch not found with ID: " + batchId));

        LibLocation location = null;
        if (locationId != null) {
            location = libLocationRepository.findById(locationId)
                    .orElseThrow(() -> new RuntimeException("Storage location not found with ID: " + locationId));
        }

        batch.setLocation(location);
        Batch updated = batchRepository.save(batch);

        auditLogService.logAction(
                updated.getFacility(),
                "Inventory",
                "BATCH_RELOCATED",
                "Batch Storage Location Updated",
                AuditLog.Severity.INFO,
                updated.getBatchNum(),
                updated.getId(),
                "Batch '" + updated.getBatchNum() + "' assigned to storage location " + (location != null ? location.getCode() : "Unassigned") + ".",
                "Admin,Pharmacist"
        );

        return mapToResponseDto(updated);
    }

    private void updateOrderStatusOnReceipt(Order order) {
        if (order == null || order.getId() == null) return;
        List<OrderedItem> items = orderedItemRepository.findByOrderId(order.getId());
        long totalOrdered = order.getTotalOrderedUnits() != null ? order.getTotalOrderedUnits() : 0L;
        long totalReceived = items.stream()
                .mapToLong(i -> i.getReceivedUnits() != null ? i.getReceivedUnits() : 0L)
                .sum();

        Order.Status oldStatus = order.getStatus();
        Order.Status newStatus;
        if (totalReceived >= totalOrdered && totalOrdered > 0) {
            newStatus = Order.Status.Received;
        } else if (totalReceived > 0) {
            newStatus = Order.Status.Partially_Received;
        } else {
            newStatus = oldStatus != null ? oldStatus : Order.Status.Approved;
        }

        if (newStatus != oldStatus) {
            order.setStatus(newStatus);
            Order savedOrder = orderRepository.save(order);

            auditLogService.logAction(
                    savedOrder.getFacility(),
                    "Purchasing",
                    "ORDER_STATUS_UPDATED",
                    "Order Status Updated (" + oldStatus + " -> " + newStatus + ")",
                    AuditLog.Severity.INFO,
                    savedOrder.getPurchaseOrderNum(),
                    savedOrder.getId(),
                    "Order " + savedOrder.getPurchaseOrderNum() + " updated to " + newStatus + ". Received " + totalReceived + " of " + totalOrdered + " units.",
                    "Admin,Pharmacist"
            );
        }
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
}
