package com.example.backend.repository;

import com.example.backend.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findAllByOrderByCreatedAtDesc();
    List<Order> findByFacilityIdOrderByCreatedAtDesc(Long facilityId);
    List<Order> findByFacilityIdAndStatusOrderByCreatedAtDesc(Long facilityId, Order.Status status);
    List<Order> findByFacilityIdAndStatusInOrderByCreatedAtDesc(Long facilityId, List<Order.Status> statuses);
    List<Order> findByFacilityId(Long facilityId);
    Optional<Order> findByPurchaseOrderNum(String purchaseOrderNum);
    List<Order> findBySupplierId(Long supplierId);
}
