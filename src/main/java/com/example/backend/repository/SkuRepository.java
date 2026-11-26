package com.example.backend.repository;

import com.example.backend.entity.Sku;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SkuRepository extends JpaRepository<Sku, Long> {

    @Query("SELECT s FROM Sku s " +
           "JOIN FETCH s.genericStockSetting g " +
           "JOIN FETCH g.libMedicine m " +
           "LEFT JOIN FETCH g.pharmacy p " +
           "LEFT JOIN FETCH p.facility f " +
           "WHERE s.id = :id")
    Optional<Sku> findByIdWithSetting(@Param("id") Long id);

    @Query("SELECT s FROM Sku s " +
           "JOIN FETCH s.genericStockSetting g " +
           "JOIN FETCH g.libMedicine m " +
           "LEFT JOIN FETCH g.pharmacy p " +
           "WHERE g.pharmacy.id = :pharmacyId " +
           "ORDER BY m.drugDescription ASC, g.packagingUnit ASC, s.brandName ASC")
    List<Sku> findByPharmacyIdWithSettingAndMedicine(@Param("pharmacyId") Long pharmacyId);

    @Query("SELECT s FROM Sku s " +
           "JOIN FETCH s.genericStockSetting g " +
           "JOIN FETCH g.libMedicine m " +
           "WHERE g.id = :settingId " +
           "  AND (s.status IS NULL OR s.status = com.example.backend.entity.Sku.Status.Active) " +
           "ORDER BY s.brandName ASC")
    List<Sku> findActiveByGenericStockSettingId(@Param("settingId") Long settingId);

    @Query("SELECT s FROM Sku s JOIN s.genericStockSetting g WHERE g.pharmacy.id = :pharmacyId")
    List<Sku> findByPharmacyId(@Param("pharmacyId") Long pharmacyId);

    @Query("SELECT s FROM Sku s JOIN s.genericStockSetting g WHERE g.libMedicine.id = :medicineId")
    List<Sku> findByLibMedicineId(@Param("medicineId") Long medicineId);

    @Query("SELECT s FROM Sku s JOIN s.genericStockSetting g WHERE g.pharmacy.id = :pharmacyId AND s.name = :name")
    Optional<Sku> findByPharmacyIdAndName(@Param("pharmacyId") Long pharmacyId, @Param("name") String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Sku s WHERE s.id = :id")
    Optional<Sku> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Sku s JOIN s.genericStockSetting g WHERE g.pharmacy.id = :pharmacyId AND s.name = :name")
    Optional<Sku> findByPharmacyIdAndNameForUpdate(@Param("pharmacyId") Long pharmacyId, @Param("name") String name);

    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END " +
           "FROM Sku s JOIN s.genericStockSetting g " +
           "WHERE g.pharmacy.id = :pharmacyId AND s.name = :name")
    boolean existsByPharmacyIdAndName(@Param("pharmacyId") Long pharmacyId, @Param("name") String name);

    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END " +
           "FROM Sku s JOIN s.genericStockSetting g " +
           "WHERE g.pharmacy.id = :pharmacyId AND s.name = :name AND s.id <> :id")
    boolean existsByPharmacyIdAndNameAndIdNot(
            @Param("pharmacyId") Long pharmacyId,
            @Param("name") String name,
            @Param("id") Long id);

    // 1. Dropdown list for Bin Cards (brand-level SKUs)
    @Query("SELECT s FROM Sku s " +
           "JOIN FETCH s.genericStockSetting g " +
           "LEFT JOIN FETCH g.libMedicine m " +
           "WHERE g.pharmacy.id = :pharmacyId " +
           "  AND (s.status IS NULL OR s.status = com.example.backend.entity.Sku.Status.Active) " +
           "ORDER BY m.drugDescription ASC, s.brandName ASC")
    List<Sku> findDropdownSkusByPharmacyId(@Param("pharmacyId") Long pharmacyId);

    // 2. Query available SKUs by drugCodes and pharmacyId (for OneRx integration)
    @Query("SELECT s FROM Sku s " +
           "JOIN FETCH s.genericStockSetting g " +
           "JOIN FETCH g.libMedicine m " +
           "LEFT JOIN FETCH g.pharmacy p " +
           "LEFT JOIN FETCH p.facility f " +
           "WHERE m.drugCode IN :drugCodes " +
           "  AND p.id = :pharmacyId " +
           "  AND (s.status IS NULL OR s.status = com.example.backend.entity.Sku.Status.Active) " +
           "  AND s.units > 0 " +
           "ORDER BY m.drugCode ASC, s.brandName ASC")
    List<Sku> findAvailableSkusByDrugCodesAndPharmacyId(
            @Param("drugCodes") Collection<String> drugCodes,
            @Param("pharmacyId") Long pharmacyId);

    // 3. Query active SKUs for OneRx RabbitMQ stock snapshot seeding
    @Query("SELECT s FROM Sku s " +
           "JOIN FETCH s.genericStockSetting g " +
           "JOIN FETCH g.libMedicine m " +
           "JOIN FETCH g.pharmacy p " +
           "WHERE (s.status IS NULL OR s.status = com.example.backend.entity.Sku.Status.Active) " +
           "  AND (:pharmacyId IS NULL OR p.id = :pharmacyId) " +
           "ORDER BY p.id ASC, m.drugCode ASC, s.brandName ASC")
    List<Sku> findActiveSkusForSnapshot(@Param("pharmacyId") Long pharmacyId);
}
