package com.example.backend.repository;

import com.example.backend.entity.UserPharmacyLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserPharmacyLinkRepository extends JpaRepository<UserPharmacyLink, Long> {

    Optional<UserPharmacyLink> findByUserIdAndPharmacyId(Long userId, Long pharmacyId);

    boolean existsByUserIdAndPharmacyId(Long userId, Long pharmacyId);

    void deleteByUserIdAndPharmacyId(Long userId, Long pharmacyId);

    List<UserPharmacyLink> findByUserId(Long userId);

    List<UserPharmacyLink> findByPharmacyId(Long pharmacyId);

    void deleteByUserId(Long userId);
}

