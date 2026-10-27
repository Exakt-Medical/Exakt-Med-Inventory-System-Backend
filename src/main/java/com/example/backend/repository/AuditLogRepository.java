package com.example.backend.repository;

import com.example.backend.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query(value = "SELECT a FROM AuditLog a WHERE a.facility.id = :facilityId " +
           "AND (:module IS NULL OR :module = '' OR :module = 'All Modules' OR LOWER(a.module) = LOWER(:module)) " +
           "AND (:severity IS NULL OR :severity = '' OR :severity = 'All Severities' OR LOWER(a.severity) = LOWER(:severity)) " +
           "AND (:isAdmin = true OR a.visibleRoles IS NULL OR a.visibleRoles = '' OR LOWER(a.visibleRoles) LIKE LOWER(CONCAT('%', :userRole, '%'))) " +
           "AND (:search IS NULL OR :search = '' OR " +
           "     LOWER(a.userName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(a.actionLabel) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(a.action) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(a.target) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(a.description) LIKE LOWER(CONCAT('%', :search, '%')))",
           countQuery = "SELECT COUNT(a) FROM AuditLog a WHERE a.facility.id = :facilityId " +
           "AND (:module IS NULL OR :module = '' OR :module = 'All Modules' OR LOWER(a.module) = LOWER(:module)) " +
           "AND (:severity IS NULL OR :severity = '' OR :severity = 'All Severities' OR LOWER(a.severity) = LOWER(:severity)) " +
           "AND (:isAdmin = true OR a.visibleRoles IS NULL OR a.visibleRoles = '' OR LOWER(a.visibleRoles) LIKE LOWER(CONCAT('%', :userRole, '%'))) " +
           "AND (:search IS NULL OR :search = '' OR " +
           "     LOWER(a.userName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(a.actionLabel) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(a.action) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(a.target) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(a.description) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<AuditLog> filterAuditLogsPaginated(
            @Param("facilityId") Long facilityId,
            @Param("module") String module,
            @Param("severity") String severity,
            @Param("search") String search,
            @Param("userRole") String userRole,
            @Param("isAdmin") boolean isAdmin,
            Pageable pageable);

    @Query("SELECT LOWER(a.severity), COUNT(a) FROM AuditLog a WHERE a.facility.id = :facilityId " +
           "AND (:isAdmin = true OR a.visibleRoles IS NULL OR a.visibleRoles = '' OR LOWER(a.visibleRoles) LIKE LOWER(CONCAT('%', :userRole, '%'))) " +
           "GROUP BY LOWER(a.severity)")
    java.util.List<Object[]> countBySeverityForFacility(
            @Param("facilityId") Long facilityId,
            @Param("userRole") String userRole,
            @Param("isAdmin") boolean isAdmin);
}
