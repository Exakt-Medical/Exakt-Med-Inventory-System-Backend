package com.example.backend.repository;

import com.example.backend.entity.Facility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FacilityRepository extends JpaRepository<Facility, Long> {
    List<Facility> findByProjectId(Long projectId);
    Optional<Facility> findByFacilityCode(String facilityCode);
    Optional<Facility> findByFacilityCodeIgnoreCase(String facilityCode);
    Optional<Facility> findByIpkrasFacilityId(Long ipkrasFacilityId);
    boolean existsByFacilityCodeIgnoreCase(String facilityCode);
}
