package com.example.backend.repository;

import com.example.backend.entity.Pharmacy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PharmacyRepository extends JpaRepository<Pharmacy, Long> {
    List<Pharmacy> findByFacilityId(Long facilityId);
    Optional<Pharmacy> findByPharmacyCode(String pharmacyCode);
    Optional<Pharmacy> findByPharmacyCodeIgnoreCase(String pharmacyCode);
    boolean existsByPharmacyCodeIgnoreCase(String pharmacyCode);
}

