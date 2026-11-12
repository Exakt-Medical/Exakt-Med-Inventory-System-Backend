package com.example.backend.repository;

import com.example.backend.entity.ReorderQuotation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReorderQuotationRepository extends JpaRepository<ReorderQuotation, Long> {

    Optional<ReorderQuotation> findByQuotationNumber(String quotationNumber);

    List<ReorderQuotation> findByPharmacyIdOrderByCreatedAtDesc(Long pharmacyId);

    List<ReorderQuotation> findByPharmacyIdAndStatusOrderByCreatedAtDesc(Long pharmacyId, ReorderQuotation.Status status);

    long countByPharmacyIdAndStatus(Long pharmacyId, ReorderQuotation.Status status);

    @Query("SELECT q FROM ReorderQuotation q " +
           "LEFT JOIN q.supplier s " +
           "LEFT JOIN q.user u " +
           "WHERE q.pharmacy.id = :pharmacyId " +
           "AND (:supplierId IS NULL OR q.supplier.id = :supplierId) " +
           "AND (:status IS NULL OR q.status = :status) " +
           "AND (:priority IS NULL OR :priority = '' OR :priority = 'ALL' OR LOWER(q.priority) = LOWER(:priority)) " +
           "AND (:search IS NULL OR :search = '' OR " +
           "     LOWER(q.quotationNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(q.notes) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     EXISTS ( " +
           "         SELECT 1 FROM ReorderQuotationItem qi " +
           "         LEFT JOIN qi.sku sku " +
           "         LEFT JOIN sku.libMedicine med " +
           "         WHERE qi.quotation = q AND ( " +
           "             LOWER(sku.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "             LOWER(sku.brandName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "             LOWER(med.drugDescription) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "         ) " +
           "     ) " +
           ")")
    Page<ReorderQuotation> findQuotations(
            @Param("pharmacyId") Long pharmacyId,
            @Param("supplierId") Long supplierId,
            @Param("status") ReorderQuotation.Status status,
            @Param("priority") String priority,
            @Param("search") String search,
            Pageable pageable);
}

