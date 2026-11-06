package com.example.backend.repository;

import com.example.backend.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PatientRepository extends JpaRepository<Patient, Long> {

    List<Patient> findByPharmacyIdOrderByNameAsc(Long pharmacyId);

    List<Patient> findByPharmacyIdAndNameContainingIgnoreCaseOrderByNameAsc(Long pharmacyId, String name);

    boolean existsByPharmacyIdAndNameIgnoreCase(Long pharmacyId, String name);

    boolean existsByPharmacyIdAndNameIgnoreCaseAndIdNot(Long pharmacyId, String name, Long id);
}

