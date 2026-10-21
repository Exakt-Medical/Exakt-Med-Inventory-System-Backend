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

    Optional<LibLocation> findByCodeIgnoreCaseAndFacilityId(String code, Long facilityId);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndFacilityId(String code, Long facilityId);

    List<LibLocation> findByFacilityId(Long facilityId);

    List<LibLocation> findByFacilityIdAndIsActiveTrue(Long facilityId);

    List<LibLocation> findByIsActiveTrue();

    @Query("SELECT l.code FROM LibLocation l WHERE l.code LIKE CONCAT(:prefix, '-%')")
    List<String> findCodesByPrefix(@Param("prefix") String prefix);

    @Query("SELECT l.code FROM LibLocation l WHERE (:facilityId IS NULL OR l.facility.id = :facilityId) AND l.code LIKE CONCAT(:prefix, '-%')")
    List<String> findCodesByPrefixAndFacilityId(@Param("prefix") String prefix, @Param("facilityId") Long facilityId);
}
