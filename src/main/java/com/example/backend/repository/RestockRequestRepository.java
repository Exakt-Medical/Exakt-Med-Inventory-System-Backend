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
    List<RestockRequest> findByPharmacyIdOrderByCreatedAtDesc(Long pharmacyId);
    List<RestockRequest> findByPharmacyIdAndOrderIsNullOrderByCreatedAtDesc(Long pharmacyId);
    List<RestockRequest> findAllByOrderByCreatedAtDesc();
    List<RestockRequest> findByOrderIsNullOrderByCreatedAtDesc();
    List<RestockRequest> findBySkuId(Long skuId);
    Optional<RestockRequest> findByOrderId(Long orderId);

    @Query("SELECT r FROM RestockRequest r LEFT JOIN r.order o WHERE " +
           "r.pharmacy.id = :pharmacyId AND " +
           "(o IS NULL OR o.status NOT IN (com.example.backend.entity.Order.Status.Received, com.example.backend.entity.Order.Status.Denied)) " +
           "ORDER BY r.createdAt DESC")
    List<RestockRequest> findActiveRestockRequestsByPharmacyId(@Param("pharmacyId") Long pharmacyId);

    @Query("SELECT r FROM RestockRequest r LEFT JOIN r.order o WHERE " +
           "r.pharmacy.id = :pharmacyId AND " +
           "r.sku.id = :skuId AND " +
           "(o IS NULL OR o.status NOT IN (com.example.backend.entity.Order.Status.Received, com.example.backend.entity.Order.Status.Denied)) " +
           "ORDER BY r.createdAt DESC")
    List<RestockRequest> findActiveRestockRequestsByPharmacyIdAndSkuId(
            @Param("pharmacyId") Long pharmacyId,
            @Param("skuId") Long skuId);

    @Query("SELECT r FROM RestockRequest r LEFT JOIN r.order o WHERE " +
           "(o IS NULL OR o.status NOT IN (com.example.backend.entity.Order.Status.Received, com.example.backend.entity.Order.Status.Denied)) " +
           "ORDER BY r.createdAt DESC")
    List<RestockRequest> findAllActiveRestockRequests();
}
