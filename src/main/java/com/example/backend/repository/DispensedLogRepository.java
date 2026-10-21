package com.example.backend.repository;

import com.example.backend.entity.DispensedLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DispensedLogRepository extends JpaRepository<DispensedLog, Long> {

    List<DispensedLog> findAllByOrderByDispensedAtDesc();

    List<DispensedLog> findBySkuIdOrderByDispensedAtDesc(Long skuId);

    List<DispensedLog> findByStatusOrderByDispensedAtDesc(DispensedLog.Status status);

    List<DispensedLog> findBySkuFacilityIdOrderByDispensedAtDesc(Long facilityId);

    List<DispensedLog> findBySkuFacilityIdAndStatusOrderByDispensedAtDesc(Long facilityId, DispensedLog.Status status);
}
