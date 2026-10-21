package com.example.backend.repository;

import com.example.backend.entity.LibPackagingUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LibPackagingUnitRepository extends JpaRepository<LibPackagingUnit, Long> {
    Optional<LibPackagingUnit> findByCode(String code);
    Optional<LibPackagingUnit> findByNameIgnoreCase(String name);
    List<LibPackagingUnit> findAllByOrderByNameAsc();
    List<LibPackagingUnit> findAllByOrderByCategoryAscNameAsc();
}
