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

    List<InventoryTransaction> findByFacilityIdAndSkuIdOrderByCreatedAtDesc(Long facilityId, Long skuId);

    List<InventoryTransaction> findByFacilityIdAndSkuIdOrderByCreatedAtAsc(Long facilityId, Long skuId);

    List<InventoryTransaction> findByFacilityIdAndSkuIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long facilityId, Long skuId, LocalDateTime start, LocalDateTime end);

    List<InventoryTransaction> findByFacilityIdAndSkuIdAndCreatedAtBetweenOrderByCreatedAtAsc(
            Long facilityId, Long skuId, LocalDateTime start, LocalDateTime end);

    @Query(value = "SELECT tx FROM InventoryTransaction tx " +
           "LEFT JOIN tx.sku s " +
           "WHERE (:facilityId IS NULL OR tx.facility.id = :facilityId) " +
           "AND (:skuId IS NULL OR tx.sku.id = :skuId) " +
           "AND (:types IS NULL OR tx.transactionType IN :types) " +
           "AND (:search IS NULL OR :search = '' OR " +
           "     LOWER(tx.referenceNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(tx.batchNum) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(s.brandName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(tx.reason) LIKE LOWER(CONCAT('%', :search, '%')))",
           countQuery = "SELECT COUNT(tx) FROM InventoryTransaction tx " +
           "LEFT JOIN tx.sku s " +
           "WHERE (:facilityId IS NULL OR tx.facility.id = :facilityId) " +
           "AND (:skuId IS NULL OR tx.sku.id = :skuId) " +
           "AND (:types IS NULL OR tx.transactionType IN :types) " +
           "AND (:search IS NULL OR :search = '' OR " +
           "     LOWER(tx.referenceNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(tx.batchNum) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(s.brandName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(tx.reason) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<InventoryTransaction> findTransactionsPaginated(
            @Param("facilityId") Long facilityId,
            @Param("skuId") Long skuId,
            @Param("types") List<InventoryTransaction.Type> types,
            @Param("search") String search,
            Pageable pageable);

    @Query("SELECT " +
           "COUNT(tx), " +
           "COALESCE(SUM(CASE WHEN tx.quantityIn IS NOT NULL AND tx.quantityIn > 0 THEN tx.quantityIn ELSE 0L END), 0L), " +
           "COALESCE(SUM(CASE WHEN tx.quantityIn IS NOT NULL AND tx.quantityIn > 0 THEN 1L ELSE 0L END), 0L), " +
           "COALESCE(SUM(CASE WHEN tx.quantityOut IS NOT NULL AND tx.quantityOut > 0 THEN tx.quantityOut ELSE 0L END), 0L), " +
           "COALESCE(SUM(CASE WHEN tx.quantityOut IS NOT NULL AND tx.quantityOut > 0 THEN 1L ELSE 0L END), 0L) " +
           "FROM InventoryTransaction tx " +
           "WHERE tx.facility.id = :facilityId " +
           "AND (:types IS NULL OR tx.transactionType IN :types)")
    List<Object[]> getTransactionSummaryByFacility(
            @Param("facilityId") Long facilityId,
            @Param("types") List<InventoryTransaction.Type> types);
}
