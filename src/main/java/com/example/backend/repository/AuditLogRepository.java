package com.example.backend.repository;

import com.example.backend.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByFacilityIdOrderByCreatedAtDesc(Long facilityId);

    @Query("SELECT a FROM AuditLog a WHERE a.facility.id = :facilityId " +
           "AND (:module IS NULL OR :module = '' OR :module = 'All Modules' OR LOWER(a.module) = LOWER(:module)) " +
           "AND (:severity IS NULL OR :severity = '' OR :severity = 'All Severities' OR LOWER(a.severity) = LOWER(:severity)) " +
           "AND (:search IS NULL OR :search = '' OR " +
           "     LOWER(a.userName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(a.actionLabel) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(a.action) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(a.target) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(a.description) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY a.createdAt DESC")
    List<AuditLog> filterAuditLogs(
            @Param("facilityId") Long facilityId,
            @Param("module") String module,
            @Param("severity") String severity,
            @Param("search") String search);
}
