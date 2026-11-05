package com.example.backend.repository;

import com.example.backend.entity.LibLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LibLocationRepository extends JpaRepository<LibLocation, Long> {

    Optional<LibLocation> findByCodeIgnoreCase(String code);

    Optional<LibLocation> findByCodeIgnoreCaseAndPharmacyId(String code, Long pharmacyId);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndPharmacyId(String code, Long pharmacyId);

    List<LibLocation> findByPharmacyId(Long pharmacyId);

    List<LibLocation> findByPharmacyIdAndIsActiveTrue(Long pharmacyId);

    List<LibLocation> findByIsActiveTrue();

    @Query("SELECT l.code FROM LibLocation l WHERE l.code LIKE CONCAT(:prefix, '-%')")
    List<String> findCodesByPrefix(@Param("prefix") String prefix);

    @Query("SELECT l.code FROM LibLocation l WHERE " +
           "l.pharmacy.id = :pharmacyId AND " +
           "l.code LIKE CONCAT(:prefix, '-%')")
    List<String> findCodesByPrefixAndPharmacyId(
            @Param("prefix") String prefix,
            @Param("pharmacyId") Long pharmacyId);
}
