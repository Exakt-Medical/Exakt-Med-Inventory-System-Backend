package com.example.backend.service;

import com.example.backend.dto.common.PageResponseDto;
import com.example.backend.dto.inventory.InventoryLedgerHeaderDto;
import com.example.backend.dto.inventory.InventoryLedgerResponseDto;
import com.example.backend.dto.inventory.InventoryTransactionResponseDto;
import com.example.backend.dto.inventory.InventoryTransactionSummaryDto;
import com.example.backend.entity.*;
import com.example.backend.repository.InventoryTransactionRepository;
import com.example.backend.repository.PharmacyRepository;
import com.example.backend.repository.SkuRepository;
import com.example.backend.security.FacilityAccessGuard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class InventoryTransactionService {

    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final SkuRepository skuRepository;
    private final PharmacyRepository pharmacyRepository;
    private final FacilityAccessGuard facilityAccessGuard;

    public InventoryTransactionService(InventoryTransactionRepository inventoryTransactionRepository,
                                       SkuRepository skuRepository,
                                       PharmacyRepository pharmacyRepository,
                                       FacilityAccessGuard facilityAccessGuard) {
        this.inventoryTransactionRepository = inventoryTransactionRepository;
        this.skuRepository = skuRepository;
        this.pharmacyRepository = pharmacyRepository;
        this.facilityAccessGuard = facilityAccessGuard;
    }

    /**
     * Pure logging service - records an immutable inventory transaction event.
     * Note: This does NOT modify SKU units, as SkuService / BatchService manages unit calculations.
     */
    @Transactional
    public InventoryTransaction recordTransaction(Sku sku,
                                                  Pharmacy pharmacy,
                                                  Batch batch,
                                                  User user,
                                                  InventoryTransaction.Type type,
                                                  String referenceNumber,
                                                  String batchNum,
                                                  LocalDate expiryDate,
                                                  Long quantityIn,
                                                  Long quantityOut,
                                                  Long previousBalance,
                                                  Long balanceAfter,
                                                  String reason,
                                                  String notes,
                                                  LocalDateTime createdAt) {
        return recordTransaction(sku, pharmacy, batch, user, null, type, referenceNumber, batchNum, expiryDate,
                quantityIn, quantityOut, previousBalance, balanceAfter, reason, notes, createdAt);
    }

    @Transactional
    public InventoryTransaction recordTransaction(Sku sku,
                                                  Pharmacy pharmacy,
                                                  Batch batch,
                                                  User user,
                                                  String authorizedBy,
                                                  InventoryTransaction.Type type,
                                                  String referenceNumber,
                                                  String batchNum,
                                                  LocalDate expiryDate,
                                                  Long quantityIn,
                                                  Long quantityOut,
                                                  Long previousBalance,
                                                  Long balanceAfter,
                                                  String reason,
                                                  String notes,
                                                  LocalDateTime createdAt) {
        if (sku == null) {
            throw new IllegalArgumentException("SKU is required to log an inventory transaction.");
        }
        if (pharmacy == null) {
            pharmacy = sku.getPharmacy() != null ? sku.getPharmacy() : (batch != null ? batch.getPharmacy() : null);
        }
        if (pharmacy == null) {
            throw new IllegalArgumentException("Pharmacy is required to log an inventory transaction.");
        }

        String resolvedAuthorizedBy = authorizedBy != null && !authorizedBy.isBlank() ? authorizedBy.trim() : null;
        if (resolvedAuthorizedBy == null && user != null) {
            resolvedAuthorizedBy = user.getName() != null && !user.getName().isBlank()
                    ? user.getName().trim()
                    : (user.getUsername() != null ? user.getUsername().trim() : null);
        }

        InventoryTransaction tx = InventoryTransaction.builder()
                .pharmacy(pharmacy)
                .sku(sku)
                .batch(batch)
                .user(user)
                .authorizedBy(resolvedAuthorizedBy)
                .transactionType(type)
                .referenceNumber(referenceNumber)
                .batchNum(batchNum != null ? batchNum : (batch != null ? batch.getBatchNum() : null))
                .expiryDate(expiryDate != null ? expiryDate : (batch != null ? batch.getExpiryDate() : null))
                .quantityIn(quantityIn != null && quantityIn > 0 ? quantityIn : null)
                .quantityOut(quantityOut != null && quantityOut > 0 ? quantityOut : null)
                .previousBalance(previousBalance != null ? previousBalance : 0L)
                .balanceAfter(balanceAfter != null ? balanceAfter : 0L)
                .reason(reason != null && !reason.isBlank() ? reason : type.getLabel())
                .notes(notes)
                .createdAt(createdAt != null ? createdAt : LocalDateTime.now())
                .build();

        return inventoryTransactionRepository.save(tx);
    }

    // Legacy overloads accepting Facility parameter for backward compatibility
    @Transactional
    public InventoryTransaction recordTransaction(Sku sku,
                                                  Facility facility,
                                                  Pharmacy pharmacy,
                                                  Batch batch,
                                                  User user,
                                                  InventoryTransaction.Type type,
                                                  String referenceNumber,
                                                  String batchNum,
                                                  LocalDate expiryDate,
                                                  Long quantityIn,
                                                  Long quantityOut,
                                                  Long previousBalance,
                                                  Long balanceAfter,
                                                  String reason,
                                                  String notes,
                                                  LocalDateTime createdAt) {
        return recordTransaction(sku, pharmacy, batch, user, null, type, referenceNumber, batchNum, expiryDate,
                quantityIn, quantityOut, previousBalance, balanceAfter, reason, notes, createdAt);
    }

    @Transactional
    public InventoryTransaction recordTransaction(Sku sku,
                                                  Facility facility,
                                                  Pharmacy pharmacy,
                                                  Batch batch,
                                                  User user,
                                                  String authorizedBy,
                                                  InventoryTransaction.Type type,
                                                  String referenceNumber,
                                                  String batchNum,
                                                  LocalDate expiryDate,
                                                  Long quantityIn,
                                                  Long quantityOut,
                                                  Long previousBalance,
                                                  Long balanceAfter,
                                                  String reason,
                                                  String notes,
                                                  LocalDateTime createdAt) {
        return recordTransaction(sku, pharmacy, batch, user, authorizedBy, type, referenceNumber, batchNum, expiryDate,
                quantityIn, quantityOut, previousBalance, balanceAfter, reason, notes, createdAt);
    }

    /**
     * Retrieves the complete chronological ledger for a SKU (Bin Card equivalent).
     */
    @Transactional(readOnly = true)
    public InventoryLedgerResponseDto getLedgerForSku(Long pharmacyId, Long skuId, LocalDate startDate, LocalDate endDate) {
        if (pharmacyId == null) {
            throw new IllegalArgumentException("Pharmacy ID is required.");
        }
        if (skuId == null) {
            throw new IllegalArgumentException("SKU ID is required.");
        }

        facilityAccessGuard.verifyPharmacyAccess(pharmacyId);
        pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found with id: " + pharmacyId));

        Sku sku = skuRepository.findById(skuId)
                .orElseThrow(() -> new RuntimeException("SKU not found with id: " + skuId));

        if (sku.getPharmacy() != null && !sku.getPharmacy().getId().equals(pharmacyId)) {
            throw new RuntimeException("SKU " + skuId + " does not belong to pharmacy " + pharmacyId);
        }

        List<InventoryTransaction> transactions;
        if (startDate != null && endDate != null) {
            LocalDateTime startDateTime = startDate.atStartOfDay();
            LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);
            transactions = inventoryTransactionRepository
                    .findByPharmacyIdAndSkuIdAndCreatedAtBetweenOrderByCreatedAtDesc(pharmacyId, skuId, startDateTime, endDateTime);
        } else {
            transactions = inventoryTransactionRepository
                    .findByPharmacyIdAndSkuIdOrderByCreatedAtDesc(pharmacyId, skuId);
        }

        long totalReceived = 0;
        long totalDeducted = 0;

        for (InventoryTransaction tx : transactions) {
            if (tx.getQuantityIn() != null && tx.getQuantityIn() > 0) {
                totalReceived += tx.getQuantityIn();
            }
            if (tx.getQuantityOut() != null && tx.getQuantityOut() > 0) {
                totalDeducted += tx.getQuantityOut();
            }
        }

        long currentStock = sku.getUnits() != null ? sku.getUnits() : 0L;
        long min = sku.getMinimumLevel() != null ? sku.getMinimumLevel() : 0L;
        long reorder = sku.getReorderLevel() != null ? sku.getReorderLevel() : 0L;

        String stockStatus = "NORMAL";
        if (currentStock <= 0) {
            stockStatus = "OUT_OF_STOCK";
        } else if (currentStock <= min) {
            stockStatus = "CRITICAL_MINIMUM";
        } else if (currentStock <= reorder) {
            stockStatus = "REORDER_TRIGGERED";
        }

        String brand = sku.getBrandName() != null ? sku.getBrandName() : "";
        String gen = sku.getLibMedicine() != null && sku.getLibMedicine().getDrugDescription() != null
                ? sku.getLibMedicine().getDrugDescription()
                : "";

        InventoryLedgerHeaderDto header = InventoryLedgerHeaderDto.builder()
                .skuId(sku.getId())
                .name(sku.getName())
                .brandName(brand)
                .genericName(gen)
                .dosageForm(sku.getDosageForm())
                .dosage(sku.getDosageStrength())
                .packagingUnit(sku.getPackagingUnit())
                .minimumLevel(sku.getMinimumLevel())
                .reorderLevel(sku.getReorderLevel())
                .maximumLevel(sku.getMaximumLevel())
                .currentUnits(currentStock)
                .stockStatus(stockStatus)
                .totalReceived(totalReceived)
                .totalDeducted(totalDeducted)
                .build();

        List<InventoryTransactionResponseDto> entries = transactions.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());

        return InventoryLedgerResponseDto.builder()
                .header(header)
                .entries(entries)
                .build();
    }

    /**
     * Retrieves transactions with server-side pagination, pharmacy/sku scoping, and type/search filters.
     */
    @Transactional(readOnly = true)
    public PageResponseDto<InventoryTransactionResponseDto> getTransactionsPaginated(
            Long pharmacyId,
            Long skuId,
            List<InventoryTransaction.Type> types,
            String search,
            int page,
            int size,
            String sortBy,
            String sortDir) {

        if (pharmacyId == null) {
            throw new IllegalArgumentException("Pharmacy ID is strictly required to query inventory transactions.");
        }

        facilityAccessGuard.verifyPharmacyAccess(pharmacyId);

        Sort.Direction direction = "ASC".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortProperty = ("referenceNumber".equalsIgnoreCase(sortBy) || "transactionType".equalsIgnoreCase(sortBy))
                ? sortBy : "createdAt";

        int pageIndex = Math.max(0, page);
        int pageSize = size > 0 ? size : 10;
        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by(direction, sortProperty));

        List<InventoryTransaction.Type> filterTypes = (types != null && !types.isEmpty()) ? types : null;
        String cleanSearch = (search != null && !search.trim().isBlank()) ? search.trim() : null;

        Page<InventoryTransaction> txPage = inventoryTransactionRepository.findTransactionsPaginated(
                pharmacyId, skuId, filterTypes, cleanSearch, pageable);

        Page<InventoryTransactionResponseDto> dtoPage = txPage.map(this::mapToDto);
        return PageResponseDto.from(dtoPage);
    }

    /**
     * Retrieves aggregated transaction KPI metrics across the pharmacy (optionally filtered by transaction types).
     */
    @Transactional(readOnly = true)
    public InventoryTransactionSummaryDto getTransactionSummary(Long pharmacyId, List<InventoryTransaction.Type> types) {
        if (pharmacyId == null) {
            throw new IllegalArgumentException("Pharmacy ID is strictly required to query inventory transaction summary.");
        }

        facilityAccessGuard.verifyPharmacyAccess(pharmacyId);

        List<InventoryTransaction.Type> filterTypes = (types != null && !types.isEmpty()) ? types : null;
        List<Object[]> rows = inventoryTransactionRepository.getTransactionSummaryByPharmacy(pharmacyId, filterTypes);

        long totalLogs = 0;
        long additionsUnits = 0;
        long additionsCount = 0;
        long deductionsUnits = 0;
        long deductionsCount = 0;

        if (rows != null && !rows.isEmpty() && rows.get(0) != null) {
            Object[] row = rows.get(0);
            totalLogs = row[0] != null ? ((Number) row[0]).longValue() : 0L;
            additionsUnits = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            additionsCount = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            deductionsUnits = row[3] != null ? ((Number) row[3]).longValue() : 0L;
            deductionsCount = row[4] != null ? ((Number) row[4]).longValue() : 0L;
        }

        long netDelta = additionsUnits - deductionsUnits;

        return InventoryTransactionSummaryDto.builder()
                .totalLogs(totalLogs)
                .netDelta(netDelta)
                .additionsUnits(additionsUnits)
                .additionsCount(additionsCount)
                .deductionsUnits(deductionsUnits)
                .deductionsCount(deductionsCount)
                .build();
    }

    public InventoryTransactionResponseDto mapToDto(InventoryTransaction tx) {
        Sku sku = tx.getSku();
        Pharmacy pharm = tx.getPharmacy() != null ? tx.getPharmacy() : (sku != null ? sku.getPharmacy() : null);
        User u = tx.getUser();

        String userName = "System";
        String userRole = "System";
        if (tx.getAuthorizedBy() != null && !tx.getAuthorizedBy().isBlank()) {
            userName = tx.getAuthorizedBy().trim();
            userRole = u != null && u.getRole() != null ? u.getRole().name() : "Authorized Person";
        } else if (u != null) {
            userName = u.getName() != null && !u.getName().isBlank()
                    ? u.getName()
                    : (u.getUsername() != null ? u.getUsername() : "User #" + u.getId());
            userRole = u.getRole() != null ? u.getRole().name() : "User";
        }

        String gen = sku != null && sku.getLibMedicine() != null && sku.getLibMedicine().getDrugDescription() != null
                ? sku.getLibMedicine().getDrugDescription()
                : null;
        String dosageStr = sku != null ? sku.getDosageStrength() : null;

        return InventoryTransactionResponseDto.builder()
                .id(tx.getId())
                .skuId(sku != null ? sku.getId() : null)
                .skuCode(sku != null ? sku.getName() : "-")
                .brandName(sku != null ? sku.getBrandName() : null)
                .genericName(gen)
                .dosage(dosageStr)
                .packagingUnit(sku != null ? sku.getPackagingUnit() : null)
                .pharmacyId(pharm != null ? pharm.getId() : null)
                .pharmacyName(pharm != null ? pharm.getPharmacyName() : null)
                .userId(u != null ? u.getId() : null)
                .userName(userName.trim())
                .authorizedBy(userName.trim())
                .performedBy(userName.trim())
                .userRole(userRole)
                .transactionType(tx.getTransactionType())
                .typeLabel(tx.getTransactionType() != null ? tx.getTransactionType().getLabel() : "-")
                .referenceNumber(tx.getReferenceNumber())
                .batchNum(tx.getBatchNum())
                .expiryDate(tx.getExpiryDate())
                .quantityIn(tx.getQuantityIn())
                .quantityOut(tx.getQuantityOut())
                .previousBalance(tx.getPreviousBalance())
                .balanceAfter(tx.getBalanceAfter())
                .reason(tx.getReason())
                .notes(tx.getNotes())
                .createdAt(tx.getCreatedAt())
                .build();
    }
}
