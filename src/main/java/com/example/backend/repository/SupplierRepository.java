package com.example.backend.repository;

import com.example.backend.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    List<Supplier> findByFacilityId(Long facilityId);

    List<Supplier> findByPharmacyId(Long pharmacyId);

    boolean existsByFacilityIdAndEmail(Long facilityId, String email);

    boolean existsByFacilityIdAndEmailAndIdNot(Long facilityId, String email, Long id);

    boolean existsByPharmacyIdAndEmail(Long pharmacyId, String email);

    boolean existsByPharmacyIdAndEmailAndIdNot(Long pharmacyId, String email, Long id);

    Optional<Supplier> findByFacilityIdAndEmail(Long facilityId, String email);

    Optional<Supplier> findByPharmacyIdAndEmail(Long pharmacyId, String email);

}
