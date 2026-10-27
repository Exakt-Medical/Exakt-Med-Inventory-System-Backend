package com.example.backend.repository;

import com.example.backend.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query(value = "SELECT o FROM Order o " +
           "LEFT JOIN o.supplier s " +
           "WHERE o.facility.id = :facilityId " +
           "AND (:statuses IS NULL OR o.status IN :statuses) " +
           "AND (:priority IS NULL OR :priority = '' OR :priority = 'ALL' OR LOWER(o.priority) = LOWER(:priority)) " +
           "AND (:search IS NULL OR :search = '' OR " +
           "     LOWER(o.purchaseOrderNum) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(o.notes) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     EXISTS ( " +
           "         SELECT 1 FROM OrderedItem oi " +
           "         LEFT JOIN oi.sku sku " +
           "         LEFT JOIN sku.libMedicine med " +
           "         WHERE oi.order = o AND ( " +
           "             LOWER(sku.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "             LOWER(sku.brandName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "             LOWER(med.drugDescription) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "         ) " +
           "     ) " +
           ")",
           countQuery = "SELECT COUNT(o) FROM Order o " +
           "LEFT JOIN o.supplier s " +
           "WHERE o.facility.id = :facilityId " +
           "AND (:statuses IS NULL OR o.status IN :statuses) " +
           "AND (:priority IS NULL OR :priority = '' OR :priority = 'ALL' OR LOWER(o.priority) = LOWER(:priority)) " +
           "AND (:search IS NULL OR :search = '' OR " +
           "     LOWER(o.purchaseOrderNum) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(o.notes) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     EXISTS ( " +
           "         SELECT 1 FROM OrderedItem oi " +
           "         LEFT JOIN oi.sku sku " +
           "         LEFT JOIN sku.libMedicine med " +
           "         WHERE oi.order = o AND ( " +
           "             LOWER(sku.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "             LOWER(sku.brandName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "             LOWER(med.drugDescription) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "         ) " +
           "     ) " +
           ")")
    Page<Order> findOrdersPaginated(
            @Param("facilityId") Long facilityId,
            @Param("statuses") List<Order.Status> statuses,
            @Param("priority") String priority,
            @Param("search") String search,
            Pageable pageable);

    @Query("SELECT LOWER(o.status), COUNT(o) FROM Order o WHERE o.facility.id = :facilityId GROUP BY LOWER(o.status)")
    List<Object[]> countOrdersByStatus(@Param("facilityId") Long facilityId);
}
