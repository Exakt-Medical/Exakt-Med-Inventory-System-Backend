package com.example.backend.repository;

import com.example.backend.entity.InventoryTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    List<InventoryTransaction> findByFacilityIdAndSkuIdOrderByCreatedAtDesc(Long facilityId, Long skuId);

    List<InventoryTransaction> findByFacilityIdAndSkuIdOrderByCreatedAtAsc(Long facilityId, Long skuId);

    List<InventoryTransaction> findByFacilityIdAndSkuIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long facilityId, Long skuId, LocalDateTime start, LocalDateTime end);

    List<InventoryTransaction> findByFacilityIdAndSkuIdAndCreatedAtBetweenOrderByCreatedAtAsc(
            Long facilityId, Long skuId, LocalDateTime start, LocalDateTime end);

    List<InventoryTransaction> findByFacilityIdOrderByCreatedAtDesc(Long facilityId);

    List<InventoryTransaction> findByFacilityIdAndTransactionTypeInOrderByCreatedAtDesc(
            Long facilityId, List<InventoryTransaction.Type> types);

    List<InventoryTransaction> findBySkuIdAndTransactionTypeInOrderByCreatedAtDesc(
            Long skuId, List<InventoryTransaction.Type> types);
}
