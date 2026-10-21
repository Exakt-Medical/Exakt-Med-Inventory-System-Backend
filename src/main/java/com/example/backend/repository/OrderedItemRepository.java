package com.example.backend.repository;

import com.example.backend.entity.OrderedItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderedItemRepository extends JpaRepository<OrderedItem, Long> {
    List<OrderedItem> findByOrderId(Long orderId);
    void deleteByOrderId(Long orderId);

    @Query("SELECT oi FROM OrderedItem oi JOIN oi.order o WHERE " +
           "o.facility.id = :facilityId AND " +
           "o.status IN (com.example.backend.entity.Order.Status.Pending, com.example.backend.entity.Order.Status.Approved, com.example.backend.entity.Order.Status.Partially_Received)")
    List<OrderedItem> findActiveOrderedItemsByFacilityId(@Param("facilityId") Long facilityId);

    @Query("SELECT oi FROM OrderedItem oi JOIN oi.order o WHERE " +
           "o.facility.id = :facilityId AND oi.sku.id = :skuId AND " +
           "o.status IN (com.example.backend.entity.Order.Status.Pending, com.example.backend.entity.Order.Status.Approved, com.example.backend.entity.Order.Status.Partially_Received)")
    List<OrderedItem> findActiveOrderedItemsByFacilityIdAndSkuId(@Param("facilityId") Long facilityId, @Param("skuId") Long skuId);

    @Query("SELECT oi FROM OrderedItem oi JOIN oi.order o WHERE " +
           "o.status IN (com.example.backend.entity.Order.Status.Pending, com.example.backend.entity.Order.Status.Approved, com.example.backend.entity.Order.Status.Partially_Received)")
    List<OrderedItem> findAllActiveOrderedItems();
}
