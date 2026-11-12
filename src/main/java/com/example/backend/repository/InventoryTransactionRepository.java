package com.example.backend.repository;

import com.example.backend.entity.InventoryTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    boolean existsByPharmacyIdAndTransactionTypeAndReferenceNumberIgnoreCase(
            Long pharmacyId,
            InventoryTransaction.Type transactionType,
            String referenceNumber);

    @Query("SELECT tx FROM InventoryTransaction tx WHERE " +
           "tx.pharmacy.id = :pharmacyId AND " +
           "tx.sku.id = :skuId " +
           "ORDER BY tx.createdAt DESC")
    List<InventoryTransaction> findByPharmacyIdAndSkuIdOrderByCreatedAtDesc(
            @Param("pharmacyId") Long pharmacyId,
            @Param("skuId") Long skuId);

    @Query("SELECT tx FROM InventoryTransaction tx WHERE " +
           "tx.pharmacy.id = :pharmacyId AND " +
           "tx.sku.id = :skuId AND " +
           "tx.createdAt BETWEEN :start AND :end " +
           "ORDER BY tx.createdAt DESC")
    List<InventoryTransaction> findByPharmacyIdAndSkuIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            @Param("pharmacyId") Long pharmacyId,
            @Param("skuId") Long skuId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query(value = "SELECT tx FROM InventoryTransaction tx " +
           "LEFT JOIN tx.sku s " +
           "WHERE tx.pharmacy.id = :pharmacyId " +
           "AND (:skuId IS NULL OR tx.sku.id = :skuId) " +
           "AND (:types IS NULL OR tx.transactionType IN :types) " +
           "AND (:reason IS NULL OR :reason = '' OR :reason = 'ALL' OR tx.reason = :reason) " +
           "AND (:search IS NULL OR :search = '' OR " +
           "     LOWER(tx.referenceNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(tx.batchNum) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(s.brandName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(tx.reason) LIKE LOWER(CONCAT('%', :search, '%')))",
           countQuery = "SELECT COUNT(tx) FROM InventoryTransaction tx " +
           "LEFT JOIN tx.sku s " +
           "WHERE tx.pharmacy.id = :pharmacyId " +
           "AND (:skuId IS NULL OR tx.sku.id = :skuId) " +
           "AND (:types IS NULL OR tx.transactionType IN :types) " +
           "AND (:reason IS NULL OR :reason = '' OR :reason = 'ALL' OR tx.reason = :reason) " +
           "AND (:search IS NULL OR :search = '' OR " +
           "     LOWER(tx.referenceNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(tx.batchNum) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(s.brandName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(tx.reason) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<InventoryTransaction> findTransactionsPaginated(
            @Param("pharmacyId") Long pharmacyId,
            @Param("skuId") Long skuId,
            @Param("types") List<InventoryTransaction.Type> types,
            @Param("reason") String reason,
            @Param("search") String search,
            Pageable pageable);

    @Query("SELECT DISTINCT tx.reason FROM InventoryTransaction tx " +
           "WHERE tx.pharmacy.id = :pharmacyId " +
           "AND (:types IS NULL OR tx.transactionType IN :types) " +
           "AND tx.reason IS NOT NULL AND TRIM(tx.reason) <> '' " +
           "ORDER BY tx.reason ASC")
    List<String> findDistinctReasonsByPharmacyId(
            @Param("pharmacyId") Long pharmacyId,
            @Param("types") List<InventoryTransaction.Type> types);

    @Query("SELECT " +
           "COUNT(tx), " +
           "COALESCE(SUM(CASE WHEN tx.quantityIn IS NOT NULL AND tx.quantityIn > 0 THEN tx.quantityIn ELSE 0L END), 0L), " +
           "COALESCE(SUM(CASE WHEN tx.quantityIn IS NOT NULL AND tx.quantityIn > 0 THEN 1L ELSE 0L END), 0L), " +
           "COALESCE(SUM(CASE WHEN tx.quantityOut IS NOT NULL AND tx.quantityOut > 0 THEN tx.quantityOut ELSE 0L END), 0L), " +
           "COALESCE(SUM(CASE WHEN tx.quantityOut IS NOT NULL AND tx.quantityOut > 0 THEN 1L ELSE 0L END), 0L) " +
           "FROM InventoryTransaction tx " +
           "WHERE tx.pharmacy.id = :pharmacyId " +
           "AND (:types IS NULL OR tx.transactionType IN :types)")
    List<Object[]> getTransactionSummaryByPharmacy(
            @Param("pharmacyId") Long pharmacyId,
            @Param("types") List<InventoryTransaction.Type> types);
}
