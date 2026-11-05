package com.example.backend.repository;

import com.example.backend.entity.Sku;
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
public interface SkuRepository extends JpaRepository<Sku, Long> {

    List<Sku> findByPharmacyId(Long pharmacyId);

    List<Sku> findByLibMedicineId(Long medicineId);

    Optional<Sku> findByPharmacyIdAndName(Long pharmacyId, String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Sku s WHERE s.id = :id")
    Optional<Sku> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Sku s WHERE s.pharmacy.id = :pharmacyId AND s.name = :name")
    Optional<Sku> findByPharmacyIdAndNameForUpdate(@Param("pharmacyId") Long pharmacyId, @Param("name") String name);

    boolean existsByPharmacyIdAndName(Long pharmacyId, String name);

    boolean existsByPharmacyIdAndNameAndIdNot(Long pharmacyId, String name, Long id);

    // 1. Paginated search and stock-level filter (scoped to pharmacyId)
    @Query(value = "SELECT s FROM Sku s LEFT JOIN s.libMedicine m WHERE " +
           "s.pharmacy.id = :pharmacyId AND " +
           "(:search IS NULL OR :search = '' OR " +
           " LOWER(s.brandName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(m.drugDescription) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
           "(:status IS NULL OR :status = '' OR :status = 'ALL' OR " +
           " (:status = 'OPTIMAL' AND s.units > s.reorderLevel) OR " +
           " (:status = 'REORDER' AND s.units <= s.reorderLevel AND s.units > s.minimumLevel) OR " +
           " (:status = 'CRITICAL' AND s.units <= s.minimumLevel) OR " +
           " (:status = 'OUT_OF_STOCK' AND s.units = 0))",
           countQuery = "SELECT COUNT(s) FROM Sku s LEFT JOIN s.libMedicine m WHERE " +
           "s.pharmacy.id = :pharmacyId AND " +
           "(:search IS NULL OR :search = '' OR " +
           " LOWER(s.brandName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(m.drugDescription) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
           "(:status IS NULL OR :status = '' OR :status = 'ALL' OR " +
           " (:status = 'OPTIMAL' AND s.units > s.reorderLevel) OR " +
           " (:status = 'REORDER' AND s.units <= s.reorderLevel AND s.units > s.minimumLevel) OR " +
           " (:status = 'CRITICAL' AND s.units <= s.minimumLevel) OR " +
           " (:status = 'OUT_OF_STOCK' AND s.units = 0))")
    Page<Sku> findSkusPaginated(
            @Param("pharmacyId") Long pharmacyId,
            @Param("search") String search,
            @Param("status") String status,
            Pageable pageable);

    // 2. Summary KPI counts
    @Query("SELECT COUNT(s) FROM Sku s WHERE s.pharmacy.id = :pharmacyId")
    long countByPharmacyId(@Param("pharmacyId") Long pharmacyId);

    @Query("SELECT COUNT(s) FROM Sku s WHERE s.pharmacy.id = :pharmacyId AND s.units > s.reorderLevel")
    long countOptimalByPharmacyId(@Param("pharmacyId") Long pharmacyId);

    @Query("SELECT COUNT(s) FROM Sku s WHERE s.pharmacy.id = :pharmacyId AND s.units <= s.reorderLevel AND s.units > s.minimumLevel")
    long countReorderNeededByPharmacyId(@Param("pharmacyId") Long pharmacyId);

    @Query("SELECT COUNT(s) FROM Sku s WHERE s.pharmacy.id = :pharmacyId AND s.units <= s.minimumLevel")
    long countCriticalByPharmacyId(@Param("pharmacyId") Long pharmacyId);

    // 3. Dropdown list for modals
    @Query("SELECT s FROM Sku s LEFT JOIN FETCH s.libMedicine m WHERE s.pharmacy.id = :pharmacyId ORDER BY s.brandName ASC")
    List<Sku> findDropdownSkusByPharmacyId(@Param("pharmacyId") Long pharmacyId);

    // 4. Procurement reorder-needed query
    @Query("SELECT s FROM Sku s LEFT JOIN s.libMedicine m WHERE " +
           "s.pharmacy.id = :pharmacyId AND " +
           "(s.units <= s.reorderLevel) AND " +
           "NOT EXISTS (" +
           "    SELECT 1 FROM OrderedItem oi " +
           "    WHERE oi.sku = s " +
           "      AND oi.order.pharmacy.id = :pharmacyId " +
           "      AND oi.order.status IN (com.example.backend.entity.Order.Status.Pending, com.example.backend.entity.Order.Status.Approved, com.example.backend.entity.Order.Status.Partially_Received)" +
           ") AND " +
           "(:search IS NULL OR :search = '' OR " +
           "LOWER(s.brandName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(m.drugDescription) LIKE LOWER(CONCAT('%', :search, '%')))")
    List<Sku> findReorderNeededSkus(
            @Param("pharmacyId") Long pharmacyId,
            @Param("search") String search);

    // 5. Query available SKUs by drugCodes and pharmacyId (for OneRx integration)
    @Query("SELECT s FROM Sku s " +
           "JOIN FETCH s.libMedicine m " +
           "LEFT JOIN FETCH s.pharmacy p " +
           "LEFT JOIN FETCH p.facility f " +
           "WHERE m.drugCode IN :drugCodes " +
           "  AND p.id = :pharmacyId " +
           "  AND s.units > 0 " +
           "ORDER BY m.drugCode ASC, s.brandName ASC")
    List<Sku> findAvailableSkusByDrugCodesAndPharmacyId(
            @Param("drugCodes") java.util.Collection<String> drugCodes,
            @Param("pharmacyId") Long pharmacyId);
}
