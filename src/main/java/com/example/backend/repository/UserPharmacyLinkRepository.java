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

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT u.email FROM UserPharmacyLink link " +
           "JOIN link.user u " +
           "WHERE link.pharmacy.id = :pharmacyId " +
           "AND u.role = com.example.backend.entity.User.Role.Admin " +
           "AND u.email IS NOT NULL AND TRIM(u.email) <> ''")
    List<String> findAdminEmailsByPharmacyId(@org.springframework.data.repository.query.Param("pharmacyId") Long pharmacyId);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT u.email FROM UserPharmacyLink link " +
           "JOIN link.user u " +
           "WHERE link.pharmacy.id = :pharmacyId " +
           "AND u.role = com.example.backend.entity.User.Role.Procurement " +
           "AND u.email IS NOT NULL AND TRIM(u.email) <> ''")
    List<String> findProcurementEmailsByPharmacyId(@org.springframework.data.repository.query.Param("pharmacyId") Long pharmacyId);

    default List<String> findProcurementByPharmacyId(Long pharmacyId) {
        return findProcurementEmailsByPharmacyId(pharmacyId);
    }

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT u.email FROM UserPharmacyLink link " +
           "JOIN link.user u " +
           "WHERE link.pharmacy.id = :pharmacyId " +
           "AND u.role = com.example.backend.entity.User.Role.Pharmacist " +
           "AND u.email IS NOT NULL AND TRIM(u.email) <> ''")
    List<String> findPharmacistEmailsByPharmacyId(@org.springframework.data.repository.query.Param("pharmacyId") Long pharmacyId);

    void deleteByUserId(Long userId);
}

