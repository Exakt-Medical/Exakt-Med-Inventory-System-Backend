package com.example.backend.repository;

import com.example.backend.entity.GenericStockSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GenericStockSettingRepository extends JpaRepository<GenericStockSetting, Long> {

    @Query("SELECT g FROM GenericStockSetting g " +
           "JOIN FETCH g.libMedicine m " +
           "LEFT JOIN FETCH g.pharmacy p " +
           "WHERE g.id = :id")
    Optional<GenericStockSetting> findByIdWithMedicine(@Param("id") Long id);

    @Query("SELECT g FROM GenericStockSetting g " +
           "JOIN FETCH g.libMedicine m " +
           "LEFT JOIN FETCH g.pharmacy p " +
           "WHERE g.pharmacy.id = :pharmacyId " +
           "ORDER BY m.drugDescription ASC, g.packagingUnit ASC")
    List<GenericStockSetting> findByPharmacyIdWithMedicine(@Param("pharmacyId") Long pharmacyId);

    Optional<GenericStockSetting> findByPharmacyIdAndLibMedicineIdAndPackagingUnit(
            Long pharmacyId, Long medicineId, String packagingUnit);

    Optional<GenericStockSetting> findByPharmacyIdAndLibMedicineIdAndPackagingUnitIgnoreCase(
            Long pharmacyId, Long medicineId, String packagingUnit);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE GenericStockSetting g " +
           "SET g.totalUnits = COALESCE((" +
           "    SELECT SUM(s.units) FROM Sku s " +
           "    WHERE s.genericStockSetting.id = :settingId " +
           "      AND (s.status IS NULL OR s.status = com.example.backend.entity.Sku.Status.Active)" +
           "), 0) " +
           "WHERE g.id = :settingId")
    void recalculateTotalUnits(@Param("settingId") Long settingId);

    @Query("SELECT COUNT(g) FROM GenericStockSetting g " +
           "WHERE g.pharmacy.id = :pharmacyId " +
           "  AND EXISTS (SELECT 1 FROM Sku s WHERE s.genericStockSetting = g AND (s.status IS NULL OR s.status = com.example.backend.entity.Sku.Status.Active))")
    long countActiveGroupsByPharmacyId(@Param("pharmacyId") Long pharmacyId);

    @Query("SELECT COUNT(g) FROM GenericStockSetting g " +
           "WHERE g.pharmacy.id = :pharmacyId " +
           "  AND EXISTS (SELECT 1 FROM Sku s WHERE s.genericStockSetting = g AND (s.status IS NULL OR s.status = com.example.backend.entity.Sku.Status.Active)) " +
           "  AND g.totalUnits > g.reorderLevel")
    long countOptimalGroupsByPharmacyId(@Param("pharmacyId") Long pharmacyId);

    @Query("SELECT COUNT(g) FROM GenericStockSetting g " +
           "WHERE g.pharmacy.id = :pharmacyId " +
           "  AND EXISTS (SELECT 1 FROM Sku s WHERE s.genericStockSetting = g AND (s.status IS NULL OR s.status = com.example.backend.entity.Sku.Status.Active)) " +
           "  AND g.totalUnits <= g.reorderLevel AND g.totalUnits > g.minimumLevel")
    long countReorderGroupsByPharmacyId(@Param("pharmacyId") Long pharmacyId);

    @Query("SELECT COUNT(g) FROM GenericStockSetting g " +
           "WHERE g.pharmacy.id = :pharmacyId " +
           "  AND EXISTS (SELECT 1 FROM Sku s WHERE s.genericStockSetting = g AND (s.status IS NULL OR s.status = com.example.backend.entity.Sku.Status.Active)) " +
           "  AND g.totalUnits <= g.minimumLevel")
    long countCriticalGroupsByPharmacyId(@Param("pharmacyId") Long pharmacyId);

    @Query("SELECT COUNT(g) FROM GenericStockSetting g " +
           "WHERE g.pharmacy.id = :pharmacyId " +
           "  AND EXISTS (SELECT 1 FROM Sku s WHERE s.genericStockSetting = g AND (s.status IS NULL OR s.status = com.example.backend.entity.Sku.Status.Active)) " +
           "  AND g.totalUnits <= g.reorderLevel " +
           "  AND NOT EXISTS (" +
           "      SELECT 1 FROM OrderedItem oi " +
           "      WHERE oi.sku.genericStockSetting = g " +
           "        AND oi.order.pharmacy.id = :pharmacyId " +
           "        AND oi.order.status IN (com.example.backend.entity.Order.Status.Pending, com.example.backend.entity.Order.Status.Approved, com.example.backend.entity.Order.Status.Partially_Received)" +
           "  )")
    long countTotalReorderNeededGroupsByPharmacyId(@Param("pharmacyId") Long pharmacyId);

    @Query("SELECT COUNT(g) FROM GenericStockSetting g " +
           "WHERE g.pharmacy.id = :pharmacyId " +
           "  AND EXISTS (SELECT 1 FROM Sku s WHERE s.genericStockSetting = g AND (s.status IS NULL OR s.status = com.example.backend.entity.Sku.Status.Active)) " +
           "  AND g.totalUnits <= g.minimumLevel " +
           "  AND NOT EXISTS (" +
           "      SELECT 1 FROM OrderedItem oi " +
           "      WHERE oi.sku.genericStockSetting = g " +
           "        AND oi.order.pharmacy.id = :pharmacyId " +
           "        AND oi.order.status IN (com.example.backend.entity.Order.Status.Pending, com.example.backend.entity.Order.Status.Approved, com.example.backend.entity.Order.Status.Partially_Received)" +
           "  )")
    long countCriticalReorderNeededGroupsByPharmacyId(@Param("pharmacyId") Long pharmacyId);

    @Query("SELECT g FROM GenericStockSetting g " +
           "JOIN FETCH g.libMedicine m " +
           "LEFT JOIN FETCH g.pharmacy p " +
           "WHERE g.pharmacy.id = :pharmacyId " +
           "  AND EXISTS (SELECT 1 FROM Sku s WHERE s.genericStockSetting = g AND (s.status IS NULL OR s.status = com.example.backend.entity.Sku.Status.Active)) " +
           "  AND g.totalUnits <= g.reorderLevel " +
           "  AND NOT EXISTS (" +
           "      SELECT 1 FROM OrderedItem oi " +
           "      WHERE oi.sku.genericStockSetting = g " +
           "        AND oi.order.pharmacy.id = :pharmacyId " +
           "        AND oi.order.status IN (com.example.backend.entity.Order.Status.Pending, com.example.backend.entity.Order.Status.Approved, com.example.backend.entity.Order.Status.Partially_Received)" +
           "  ) " +
           "ORDER BY m.drugDescription ASC, g.packagingUnit ASC")
    List<GenericStockSetting> findReorderNeededGroupsByPharmacyId(@Param("pharmacyId") Long pharmacyId);
}
