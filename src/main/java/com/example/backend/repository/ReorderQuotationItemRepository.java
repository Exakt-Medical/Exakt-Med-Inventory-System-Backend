package com.example.backend.repository;

import com.example.backend.entity.ReorderQuotationItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReorderQuotationItemRepository extends JpaRepository<ReorderQuotationItem, Long> {

    List<ReorderQuotationItem> findByQuotationId(Long quotationId);

    void deleteByQuotationId(Long quotationId);
}

