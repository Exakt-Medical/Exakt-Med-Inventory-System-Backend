package com.example.backend.repository;

import com.example.backend.entity.Sku;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SkuRepository extends JpaRepository<Sku, Long> {

    List<Sku> findByFacilityId(Long facilityId);

    List<Sku> findByLibMedicineId(Long medicineId);

    Optional<Sku> findByFacilityIdAndName(Long facilityId, String name);

    boolean existsByFacilityIdAndName(Long facilityId, String name);

    boolean existsByFacilityIdAndNameAndIdNot(Long facilityId, String name, Long id);

    // 1. Paginated search and stock-level filter
    @Query(value = "SELECT s FROM Sku s LEFT JOIN s.libMedicine m WHERE " +
           "s.facility.id = :facilityId AND " +
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
           "s.facility.id = :facilityId AND " +
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
            @Param("facilityId") Long facilityId,
            @Param("search") String search,
            @Param("status") String status,
            Pageable pageable);

    // 2. Summary KPI counts
    long countByFacilityId(Long facilityId);

    @Query("SELECT COUNT(s) FROM Sku s WHERE s.facility.id = :facilityId AND s.units > s.reorderLevel")
    long countOptimalByFacilityId(@Param("facilityId") Long facilityId);

    @Query("SELECT COUNT(s) FROM Sku s WHERE s.facility.id = :facilityId AND s.units <= s.reorderLevel AND s.units > s.minimumLevel")
    long countReorderNeededByFacilityId(@Param("facilityId") Long facilityId);

    @Query("SELECT COUNT(s) FROM Sku s WHERE s.facility.id = :facilityId AND s.units <= s.minimumLevel")
    long countCriticalByFacilityId(@Param("facilityId") Long facilityId);

    // 3. Dropdown list for modals
    @Query("SELECT s FROM Sku s LEFT JOIN FETCH s.libMedicine m WHERE s.facility.id = :facilityId ORDER BY s.brandName ASC")
    List<Sku> findDropdownSkusByFacilityId(@Param("facilityId") Long facilityId);

    // 4. Procurement reorder-needed query
    @Query("SELECT s FROM Sku s LEFT JOIN s.libMedicine m WHERE " +
           "s.facility.id = :facilityId AND " +
           "(s.units <= s.reorderLevel) AND " +
           "NOT EXISTS (" +
           "    SELECT 1 FROM OrderedItem oi " +
           "    WHERE oi.sku = s " +
           "      AND oi.order.facility = s.facility" +
           "      AND oi.order.status IN (com.example.backend.entity.Order.Status.Pending, com.example.backend.entity.Order.Status.Approved, com.example.backend.entity.Order.Status.Partially_Received)" +
           ") AND " +
           "(:search IS NULL OR :search = '' OR " +
           "LOWER(s.brandName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(m.drugDescription) LIKE LOWER(CONCAT('%', :search, '%')))")
    List<Sku> findReorderNeededSkus(@Param("facilityId") Long facilityId, @Param("search") String search);

    // 5. Query available SKUs by drugCode and facilityId (for 3rd-party webhook lookup)
    @Query("SELECT s FROM Sku s " +
           "JOIN FETCH s.libMedicine m " +
           "JOIN FETCH s.facility f " +
           "WHERE m.drugCode = :drugCode " +
           "  AND f.id = :facilityId " +
           "  AND s.units > 0 " +
           "ORDER BY s.brandName ASC")
    List<Sku> findAvailableSkusByDrugCodeAndFacilityId(
            @Param("drugCode") String drugCode,
            @Param("facilityId") Long facilityId);
}
