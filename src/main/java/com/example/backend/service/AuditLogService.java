package com.example.backend.service;

import com.example.backend.dto.audit.AuditLogResponseDto;
import com.example.backend.dto.audit.AuditLogSummaryDto;
import com.example.backend.dto.common.PageResponseDto;
import com.example.backend.entity.AuditLog;
import com.example.backend.entity.Facility;
import com.example.backend.entity.User;
import com.example.backend.repository.AuditLogRepository;
import com.example.backend.repository.FacilityRepository;
import com.example.backend.repository.UserRepository;
import com.example.backend.security.FacilityAccessGuard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AuditLogService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AuditLogRepository auditLogRepository;
    private final FacilityRepository facilityRepository;
    private final UserRepository userRepository;
    private final FacilityAccessGuard facilityAccessGuard;

    public AuditLogService(AuditLogRepository auditLogRepository,
                           FacilityRepository facilityRepository,
                           UserRepository userRepository,
                           FacilityAccessGuard facilityAccessGuard) {
        this.auditLogRepository = auditLogRepository;
        this.facilityRepository = facilityRepository;
        this.userRepository = userRepository;
        this.facilityAccessGuard = facilityAccessGuard;
    }

    // 1. REUSABLE LOG ACTION ENGINE
    @Transactional
    public AuditLog logAction(Facility facility,
                              User user,
                              String module,
                              String action,
                              String actionLabel,
                              AuditLog.Severity severity,
                              String target,
                              Long targetId,
                              String description,
                              String visibleRoles) {
        return logAction(facility, user, null, module, action, actionLabel, severity, target, targetId, description, visibleRoles);
    }

    @Transactional
    public AuditLog logAction(Facility facility,
                              User user,
                              String customUserName,
                              String module,
                              String action,
                              String actionLabel,
                              AuditLog.Severity severity,
                              String target,
                              Long targetId,
                              String description,
                              String visibleRoles) {
        if (facility == null) {
            return null; // Audit log requires an operating facility
        }

        AuditLog log = new AuditLog();
        log.setFacility(facility);

        User actingUser = user != null ? user : getCurrentUser();
        log.setUser(actingUser);

        if (customUserName != null && !customUserName.isBlank()) {
            log.setUserName(customUserName.trim());
            log.setUserRole(actingUser != null && actingUser.getRole() != null ? actingUser.getRole().name() : "Authorized Person");
        } else if (actingUser != null) {
            log.setUserName(actingUser.getName() != null && !actingUser.getName().isBlank() ? actingUser.getName() : actingUser.getUsername());
            if (actingUser.getRole() != null) {
                log.setUserRole(actingUser.getRole().name());
            }
        } else {
            log.setUserName("System");
            log.setUserRole("System");
        }

        log.setModule(module != null ? module : "System");
        log.setAction(action != null ? action : "ACTION");
        log.setActionLabel(actionLabel != null ? actionLabel : action);
        log.setSeverity(severity != null ? severity : AuditLog.Severity.INFO);
        log.setTarget(target);
        log.setTargetId(targetId);
        log.setDescription(description != null ? description : "");
        log.setVisibleRoles(visibleRoles != null ? visibleRoles : "Admin,Pharmacist,Procurement");
        log.setCreatedAt(LocalDateTime.now());

        return auditLogRepository.save(log);
    }

    // Overloaded helper that automatically detects current authenticated user
    @Transactional
    public AuditLog logAction(Facility facility,
                              String module,
                              String action,
                              String actionLabel,
                              AuditLog.Severity severity,
                              String target,
                              Long targetId,
                              String description,
                              String visibleRoles) {
        return logAction(facility, getCurrentUser(), module, action, actionLabel, severity, target, targetId, description, visibleRoles);
    }

    // 2. QUERY AUDIT LOGS BY FACILITY (Server-side paginated with role-based visibility)
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public PageResponseDto<AuditLogResponseDto> getAuditLogs(Long facilityId,
                                                             String module,
                                                             String severity,
                                                             String search,
                                                             int page,
                                                             int size,
                                                             String sortBy,
                                                             String sortDir) {
        if (facilityId == null) {
            throw new RuntimeException("Facility ID is strictly required to query audit logs.");
        }
        facilityAccessGuard.verifyFacilityAccess(facilityId);
        if (!facilityRepository.existsById(facilityId)) {
            throw new RuntimeException("Facility not found with id: " + facilityId);
        }

        Sort.Direction direction = "ASC".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortProperty = ("userName".equalsIgnoreCase(sortBy) || "action".equalsIgnoreCase(sortBy) || "module".equalsIgnoreCase(sortBy))
                ? sortBy : "createdAt";

        int pageIndex = Math.max(0, page);
        int pageSize = size > 0 ? size : 10;
        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by(direction, sortProperty));

        User currentUser = getCurrentUser();
        String userRole = (currentUser != null && currentUser.getRole() != null)
                ? currentUser.getRole().name()
                : "";

        boolean isAdmin = userRole.equalsIgnoreCase("Admin") ||
                          userRole.equalsIgnoreCase("SuperAdmin") ||
                          userRole.toLowerCase().contains("admin");

        String cleanSearch = (search != null && !search.trim().isBlank()) ? search.trim() : null;
        String cleanModule = (module != null && !module.trim().isBlank() && !"All Modules".equalsIgnoreCase(module.trim())) ? module.trim() : null;
        String cleanSeverity = (severity != null && !severity.trim().isBlank() && !"All Severities".equalsIgnoreCase(severity.trim())) ? severity.trim() : null;

        Page<AuditLog> logPage = auditLogRepository.filterAuditLogsPaginated(
                facilityId, cleanModule, cleanSeverity, cleanSearch, userRole, isAdmin, pageable);

        Page<AuditLogResponseDto> dtoPage = logPage.map(this::mapToResponseDto);
        return PageResponseDto.from(dtoPage);
    }

    // 2b. GET AUDIT LOG SUMMARY KPIS
    @Transactional(readOnly = true)
    public AuditLogSummaryDto getAuditLogSummary(Long facilityId) {
        if (facilityId == null) {
            throw new RuntimeException("Facility ID is strictly required to query audit log summary.");
        }
        facilityAccessGuard.verifyFacilityAccess(facilityId);
        if (!facilityRepository.existsById(facilityId)) {
            throw new RuntimeException("Facility not found with id: " + facilityId);
        }

        User currentUser = getCurrentUser();
        String userRole = (currentUser != null && currentUser.getRole() != null)
                ? currentUser.getRole().name()
                : "";

        boolean isAdmin = userRole.equalsIgnoreCase("Admin") ||
                          userRole.equalsIgnoreCase("SuperAdmin") ||
                          userRole.toLowerCase().contains("admin");

        List<Object[]> rows = auditLogRepository.countBySeverityForFacility(facilityId, userRole, isAdmin);
        long criticalCount = 0;
        long warningCount = 0;
        long infoCount = 0;
        long successCount = 0;
        long totalLogs = 0;

        for (Object[] row : rows) {
            String severity = row[0] != null ? row[0].toString().toLowerCase() : "";
            long count = row[1] != null ? ((Number) row[1]).longValue() : 0;
            totalLogs += count;

            if ("critical".equals(severity)) {
                criticalCount += count;
            } else if ("warning".equals(severity)) {
                warningCount += count;
            } else if ("info".equals(severity)) {
                infoCount += count;
            } else if ("success".equals(severity)) {
                successCount += count;
            }
        }

        return AuditLogSummaryDto.builder()
                .totalLogs(totalLogs)
                .criticalCount(criticalCount)
                .warningCount(warningCount)
                .infoCount(infoCount)
                .successCount(successCount)
                .build();
    }

    // MAPPER HELPER
    private AuditLogResponseDto mapToResponseDto(AuditLog log) {
        AuditLogResponseDto dto = new AuditLogResponseDto();
        dto.setId(log.getId());
        dto.setLogCode(String.format("LOG-%d-%04d", log.getCreatedAt().getYear(), log.getId()));

        if (log.getFacility() != null) {
            dto.setFacilityId(log.getFacility().getId());
            dto.setFacility(log.getFacility().getName());
        }

        if (log.getUser() != null) {
            dto.setUserId(log.getUser().getId());
        }
        dto.setUserName(log.getUserName());
        dto.setUserRole(log.getUserRole());
        dto.setModule(log.getModule());
        dto.setAction(log.getAction());
        dto.setActionLabel(log.getActionLabel());
        dto.setSeverity(log.getSeverity() != null ? log.getSeverity().name().toLowerCase() : "info");
        dto.setTarget(log.getTarget());
        dto.setTargetId(log.getTargetId());
        dto.setDescription(log.getDescription());

        if (log.getVisibleRoles() != null && !log.getVisibleRoles().isBlank()) {
            dto.setVisibleRoles(Arrays.stream(log.getVisibleRoles().split(","))
                    .map(String::trim)
                    .collect(Collectors.toList()));
        } else {
            dto.setVisibleRoles(Collections.emptyList());
        }

        dto.setCreatedAt(log.getCreatedAt());
        dto.setTimestamp(log.getCreatedAt() != null ? log.getCreatedAt().format(DATE_FORMATTER) : "");

        return dto;
    }

    public User getCurrentUser() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                String username = authentication.getName();
                return userRepository.findByUsername(username).orElse(null);
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
