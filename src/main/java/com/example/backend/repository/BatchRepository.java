package com.example.backend.repository;

import com.example.backend.entity.Batch;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BatchRepository extends JpaRepository<Batch, Long> {

    List<Batch> findByFacilityIdOrderByReceivedAtDesc(Long facilityId);

    List<Batch> findByFacilityIdAndStatusOrderByReceivedAtDesc(Long facilityId, Batch.Status status);

    Optional<Batch> findByBatchNumAndFacilityId(String batchNum, Long facilityId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Batch b WHERE b.id = :id")
    Optional<Batch> findByIdForUpdate(@Param("id") Long id);

    boolean existsByBatchNumAndFacilityId(String batchNum, Long facilityId);

    List<Batch> findByOrderedItemId(Long orderedItemId);

    boolean existsByOrderedItemId(Long orderedItemId);

    boolean existsByLocationId(Long locationId);

    List<Batch> findByStatusAndExpiryDateLessThanEqual(Batch.Status status, java.time.LocalDate date);

    List<Batch> findByFacilityIdAndStatusAndExpiryDateLessThanEqual(Long facilityId, Batch.Status status, java.time.LocalDate date);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Batch b WHERE " +
           "b.facility.id = :facilityId AND " +
           "b.orderedItem.sku.id = :skuId AND " +
           "(b.status = com.example.backend.entity.Batch.Status.Expired OR (b.status = com.example.backend.entity.Batch.Status.Available AND b.expiryDate <= :date)) AND " +
           "(b.skuDeducted IS NULL OR b.skuDeducted = false) AND " +
           "b.units > 0")
    List<Batch> findExpiredBatches(
            @Param("facilityId") Long facilityId,
            @Param("skuId") Long skuId,
            @Param("date") java.time.LocalDate date);

    @Query("SELECT COALESCE(SUM(b.units), 0) FROM Batch b WHERE " +
           "b.facility.id = :facilityId AND " +
           "b.orderedItem.sku.id = :skuId AND " +
           "(b.status = com.example.backend.entity.Batch.Status.Expired OR (b.status = com.example.backend.entity.Batch.Status.Available AND b.expiryDate <= :today)) AND " +
           "(b.skuDeducted IS NULL OR b.skuDeducted = false) AND " +
           "b.units > 0")
    long sumUndeductedExpiredUnitsForSku(
            @Param("facilityId") Long facilityId,
            @Param("skuId") Long skuId,
            @Param("today") java.time.LocalDate today);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Batch b WHERE b.orderedItem.sku.id = :skuId " +
           "AND b.facility.id = :facilityId " +
           "AND b.status = :status " +
           "AND b.expiryDate > :today " +
           "AND b.units > 0 " +
           "ORDER BY b.expiryDate ASC, b.id ASC")
    List<Batch> findAvailableBatchesForSkuFEFO(
            @Param("skuId") Long skuId,
            @Param("facilityId") Long facilityId,
            @Param("status") Batch.Status status,
            @Param("today") java.time.LocalDate today);

    @Query("SELECT b FROM Batch b WHERE b.facility.id = :facilityId " +
           "AND b.orderedItem.sku.id = :skuId " +
           "ORDER BY b.receivedAt ASC, b.id ASC")
    List<Batch> findBatchesForSkuOrderByReceivedAtAsc(
            @Param("facilityId") Long facilityId,
            @Param("skuId") Long skuId);

    @Query(value = "SELECT b FROM Batch b " +
           "LEFT JOIN b.orderedItem oi " +
           "LEFT JOIN oi.sku s " +
           "WHERE b.facility.id = :facilityId " +
           "AND (:search IS NULL OR :search = '' OR " +
           "     LOWER(b.batchNum) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(s.brandName) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:sku IS NULL OR :sku = '' OR :sku = 'ALL' OR s.name = :sku) " +
           "AND (:status IS NULL OR :status = '' OR :status = 'ALL' OR " +
           "     (:status = 'ACTIVE' AND b.status = com.example.backend.entity.Batch.Status.Available AND b.units > 0) OR " +
           "     (:status = 'QUARANTINED' AND b.status = com.example.backend.entity.Batch.Status.Quarantined) OR " +
           "     (:status = 'EXPIRED' AND b.status = com.example.backend.entity.Batch.Status.Expired) OR " +
           "     (:status = 'DEPLETED' AND b.units = 0)) " +
           "AND (:expiryFilter IS NULL OR :expiryFilter = '' OR :expiryFilter = 'ALL' OR " +
           "     (:expiryFilter = 'EXPIRED' AND b.expiryDate <= :today) OR " +
           "     (:expiryFilter = 'NEAR_EXPIRY' AND b.expiryDate > :today AND b.expiryDate <= :nearExpiryDate) OR " +
           "     (:expiryFilter = 'HEALTHY' AND b.expiryDate > :nearExpiryDate))",
           countQuery = "SELECT COUNT(b) FROM Batch b " +
           "LEFT JOIN b.orderedItem oi " +
           "LEFT JOIN oi.sku s " +
           "WHERE b.facility.id = :facilityId " +
           "AND (:search IS NULL OR :search = '' OR " +
           "     LOWER(b.batchNum) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(s.brandName) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:sku IS NULL OR :sku = '' OR :sku = 'ALL' OR s.name = :sku) " +
           "AND (:status IS NULL OR :status = '' OR :status = 'ALL' OR " +
           "     (:status = 'ACTIVE' AND b.status = com.example.backend.entity.Batch.Status.Available AND b.units > 0) OR " +
           "     (:status = 'QUARANTINED' AND b.status = com.example.backend.entity.Batch.Status.Quarantined) OR " +
           "     (:status = 'EXPIRED' AND b.status = com.example.backend.entity.Batch.Status.Expired) OR " +
           "     (:status = 'DEPLETED' AND b.units = 0)) " +
           "AND (:expiryFilter IS NULL OR :expiryFilter = '' OR :expiryFilter = 'ALL' OR " +
           "     (:expiryFilter = 'EXPIRED' AND b.expiryDate <= :today) OR " +
           "     (:expiryFilter = 'NEAR_EXPIRY' AND b.expiryDate > :today AND b.expiryDate <= :nearExpiryDate) OR " +
           "     (:expiryFilter = 'HEALTHY' AND b.expiryDate > :nearExpiryDate))")
    Page<Batch> findBatchesPaginated(
            @Param("facilityId") Long facilityId,
            @Param("search") String search,
            @Param("status") String status,
            @Param("sku") String sku,
            @Param("expiryFilter") String expiryFilter,
            @Param("today") java.time.LocalDate today,
            @Param("nearExpiryDate") java.time.LocalDate nearExpiryDate,
            Pageable pageable);

    // 2. Aggregate KPI counts
    long countByFacilityId(Long facilityId);

    @Query("SELECT COALESCE(SUM(b.units), 0) FROM Batch b " +
           "WHERE b.facility.id = :facilityId AND b.status = com.example.backend.entity.Batch.Status.Available")
    long sumActiveStockByFacilityId(@Param("facilityId") Long facilityId);

    @Query("SELECT COUNT(b) FROM Batch b " +
           "WHERE b.facility.id = :facilityId AND b.units > 0 AND b.expiryDate <= :nearExpiryDate")
    long countExpiryAlertsByFacilityId(@Param("facilityId") Long facilityId, @Param("nearExpiryDate") java.time.LocalDate nearExpiryDate);

    @Query("SELECT COUNT(b) FROM Batch b " +
           "WHERE b.facility.id = :facilityId AND b.status = com.example.backend.entity.Batch.Status.Quarantined")
    long countQuarantinedByFacilityId(@Param("facilityId") Long facilityId);

    // 3. Distinct SKU names for filter dropdown
    @Query("SELECT DISTINCT s.name FROM Batch b " +
           "JOIN b.orderedItem oi " +
           "JOIN oi.sku s " +
           "WHERE b.facility.id = :facilityId " +
           "ORDER BY s.name ASC")
    List<String> findDistinctSkuNamesByFacilityId(@Param("facilityId") Long facilityId);

    // 4. Aggregate expired batches needing deduction grouped by SKU
    @Query("SELECT b.orderedItem.sku.id, COUNT(b), COALESCE(SUM(b.units), 0) FROM Batch b " +
           "WHERE b.facility.id = :facilityId " +
           "AND (b.status = com.example.backend.entity.Batch.Status.Expired OR (b.status = com.example.backend.entity.Batch.Status.Available AND b.expiryDate <= :today)) " +
           "AND (b.skuDeducted IS NULL OR b.skuDeducted = false) " +
           "AND b.units > 0 " +
           "GROUP BY b.orderedItem.sku.id")
    List<Object[]> findExpiredBatchMetricsByFacilityId(
            @Param("facilityId") Long facilityId,
            @Param("today") java.time.LocalDate today);
}
