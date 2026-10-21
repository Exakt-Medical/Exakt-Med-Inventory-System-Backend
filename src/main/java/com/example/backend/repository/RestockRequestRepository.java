package com.example.backend.repository;

import com.example.backend.entity.RestockRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RestockRequestRepository extends JpaRepository<RestockRequest, Long> {
    List<RestockRequest> findByFacilityIdOrderByCreatedAtDesc(Long facilityId);
    List<RestockRequest> findByFacilityIdAndOrderIsNullOrderByCreatedAtDesc(Long facilityId);
    List<RestockRequest> findAllByOrderByCreatedAtDesc();
    List<RestockRequest> findByOrderIsNullOrderByCreatedAtDesc();
    List<RestockRequest> findBySkuId(Long skuId);
    Optional<RestockRequest> findByOrderId(Long orderId);

    @Query("SELECT r FROM RestockRequest r LEFT JOIN r.order o WHERE " +
           "r.facility.id = :facilityId AND " +
           "(o IS NULL OR o.status NOT IN (com.example.backend.entity.Order.Status.Received, com.example.backend.entity.Order.Status.Denied)) " +
           "ORDER BY r.createdAt DESC")
    List<RestockRequest> findActiveRestockRequestsByFacilityId(@Param("facilityId") Long facilityId);

    @Query("SELECT r FROM RestockRequest r LEFT JOIN r.order o WHERE " +
           "r.facility.id = :facilityId AND r.sku.id = :skuId AND " +
           "(o IS NULL OR o.status NOT IN (com.example.backend.entity.Order.Status.Received, com.example.backend.entity.Order.Status.Denied)) " +
           "ORDER BY r.createdAt DESC")
    List<RestockRequest> findActiveRestockRequestsByFacilityIdAndSkuId(@Param("facilityId") Long facilityId, @Param("skuId") Long skuId);

    @Query("SELECT r FROM RestockRequest r LEFT JOIN r.order o WHERE " +
           "(o IS NULL OR o.status NOT IN (com.example.backend.entity.Order.Status.Received, com.example.backend.entity.Order.Status.Denied)) " +
           "ORDER BY r.createdAt DESC")
    List<RestockRequest> findAllActiveRestockRequests();
}
