package com.example.backend.repository;

import com.example.backend.entity.DispensedLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DispensedLogRepository extends JpaRepository<DispensedLog, Long> {

    List<DispensedLog> findAllByOrderByDispensedAtDesc();

    List<DispensedLog> findBySkuIdOrderByDispensedAtDesc(Long skuId);

    List<DispensedLog> findByStatusOrderByDispensedAtDesc(DispensedLog.Status status);

    @Query("SELECT d FROM DispensedLog d " +
           "JOIN FETCH d.sku s " +
           "JOIN FETCH s.genericStockSetting g " +
           "LEFT JOIN FETCH g.libMedicine " +
           "WHERE g.pharmacy.id = :pharmacyId " +
           "ORDER BY d.dispensedAt DESC")
    List<DispensedLog> findBySkuPharmacyIdOrderByDispensedAtDesc(@Param("pharmacyId") Long pharmacyId);

    @Query("SELECT d FROM DispensedLog d " +
           "JOIN FETCH d.sku s " +
           "JOIN FETCH s.genericStockSetting g " +
           "LEFT JOIN FETCH g.libMedicine " +
           "WHERE g.pharmacy.id = :pharmacyId " +
           "  AND d.status = :status " +
           "ORDER BY d.dispensedAt DESC")
    List<DispensedLog> findBySkuPharmacyIdAndStatusOrderByDispensedAtDesc(
            @Param("pharmacyId") Long pharmacyId,
            @Param("status") DispensedLog.Status status);
}
