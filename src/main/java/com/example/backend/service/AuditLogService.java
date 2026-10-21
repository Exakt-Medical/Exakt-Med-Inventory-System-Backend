package com.example.backend.service;

import com.example.backend.dto.audit.AuditLogResponseDto;
import com.example.backend.entity.AuditLog;
import com.example.backend.entity.Facility;
import com.example.backend.entity.User;
import com.example.backend.repository.AuditLogRepository;
import com.example.backend.repository.FacilityRepository;
import com.example.backend.repository.UserRepository;
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

    public AuditLogService(AuditLogRepository auditLogRepository,
                           FacilityRepository facilityRepository,
                           UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.facilityRepository = facilityRepository;
        this.userRepository = userRepository;
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

    // 2. QUERY AUDIT LOGS BY FACILITY (with multi-tenant isolation and role-based visibility)
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<AuditLogResponseDto> getAuditLogs(Long facilityId,
                                                  String module,
                                                  String severity,
                                                  String search) {
        if (facilityId == null) {
            throw new RuntimeException("Facility ID is strictly required to query audit logs.");
        }
        if (!facilityRepository.existsById(facilityId)) {
            throw new RuntimeException("Facility not found with id: " + facilityId);
        }

        List<AuditLog> logs = auditLogRepository.filterAuditLogs(facilityId, module, severity, search);
        User currentUser = getCurrentUser();
        String userRole = (currentUser != null && currentUser.getRole() != null)
                ? currentUser.getRole().name()
                : "";

        boolean isAdmin = userRole.equalsIgnoreCase("Admin") ||
                          userRole.equalsIgnoreCase("SuperAdmin") ||
                          userRole.toLowerCase().contains("admin");

        return logs.stream()
                .filter(log -> {
                    if (isAdmin) return true; // Admins have full audit visibility across their facility
                    if (log.getVisibleRoles() == null || log.getVisibleRoles().isBlank()) return true;
                    // Check if current user's role is in visibleRoles list
                    String[] roles = log.getVisibleRoles().split(",");
                    return Arrays.stream(roles)
                            .map(String::trim)
                            .anyMatch(r -> r.equalsIgnoreCase(userRole));
                })
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
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
