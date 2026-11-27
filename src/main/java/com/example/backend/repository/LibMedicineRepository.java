package com.example.backend.repository;

import com.example.backend.entity.LibMedicine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LibMedicineRepository extends JpaRepository<LibMedicine, Long> {

    @Query("SELECT m FROM LibMedicine m WHERE " +
           "(:search IS NULL OR :search = '' OR " +
           "LOWER(m.drugDescription) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(m.drugCode) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(m.genCode) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:classification IS NULL OR :classification = '' OR " +
           "LOWER(m.classification) LIKE LOWER(CONCAT('%', :classification, '%')))")
    Page<LibMedicine> searchMedicines(
            @Param("search") String search,
            @Param("classification") String classification,
            Pageable pageable);

    Optional<LibMedicine> findByDrugCode(String drugCode);

    List<LibMedicine> findByDrugCodeIn(java.util.Collection<String> drugCodes);
}
