package com.example.backend.service;

import com.example.backend.dto.dispensed.DispensedLogResponseDto;
import com.example.backend.entity.AuditLog;
import com.example.backend.entity.DispensedLog;
import com.example.backend.repository.DispensedLogRepository;
import com.example.backend.security.FacilityAccessGuard;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DispensedLogService {

    private final DispensedLogRepository dispensedLogRepository;
    private final AuditLogService auditLogService;
    private final FacilityAccessGuard facilityAccessGuard;

    public DispensedLogService(DispensedLogRepository dispensedLogRepository,
                               AuditLogService auditLogService,
                               FacilityAccessGuard facilityAccessGuard) {
        this.dispensedLogRepository = dispensedLogRepository;
        this.auditLogService = auditLogService;
        this.facilityAccessGuard = facilityAccessGuard;
    }

    // 1. UPDATE STATUS TO RECEIVED (When button is pressed)
    @Transactional
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Pharmacist')")
    public DispensedLogResponseDto updateStatusToReceived(Long id) {
        DispensedLog log = dispensedLogRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Dispensed log not found with id: " + id));

        if (log.getSku() != null && log.getSku().getFacility() != null) {
            facilityAccessGuard.verifyFacilityAccess(log.getSku().getFacility().getId());
        }

        log.setStatus(DispensedLog.Status.Received);
        log.setReceivedAt(LocalDateTime.now());
        DispensedLog saved = dispensedLogRepository.save(log);

        if (saved.getSku() != null && saved.getSku().getFacility() != null) {
            auditLogService.logAction(
                    saved.getSku().getFacility(),
                    "Dispense",
                    "DISPENSE_CONFIRMED",
                    "Dispensed Medicine Received by Patient",
                    AuditLog.Severity.SUCCESS,
                    saved.getPatientName(),
                    saved.getId(),
                    String.format("Patient '%s' (Contact: %s) confirmed received %d units of SKU '%s'.",
                            saved.getPatientName(),
                            saved.getContactNumber(),
                            saved.getUnitsDispensed(),
                            saved.getSku().getName()),
                    "Admin,Pharmacist"
            );
        }

        return mapToResponseDto(saved);
    }

    // 2. GET ALL DISPENSED LOGS (Optional facilityId and status filter)
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Pharmacist')")
    public List<DispensedLogResponseDto> getAllDispensedLogs(Long facilityId, DispensedLog.Status status) {
        List<DispensedLog> logs;
        if (facilityId != null) {
            facilityAccessGuard.verifyFacilityAccess(facilityId);
            if (status != null) {
                logs = dispensedLogRepository.findBySkuFacilityIdAndStatusOrderByDispensedAtDesc(facilityId, status);
            } else {
                logs = dispensedLogRepository.findBySkuFacilityIdOrderByDispensedAtDesc(facilityId);
            }
        } else {
            Set<Long> accessibleIds = facilityAccessGuard.getAccessibleFacilityIdsOrNullForSuperAdmin();
            List<DispensedLog> all = (status != null)
                    ? dispensedLogRepository.findByStatusOrderByDispensedAtDesc(status)
                    : dispensedLogRepository.findAllByOrderByDispensedAtDesc();
            if (accessibleIds != null) {
                logs = all.stream()
                        .filter(l -> l.getSku() != null && l.getSku().getFacility() != null
                                && accessibleIds.contains(l.getSku().getFacility().getId()))
                        .collect(Collectors.toList());
            } else {
                logs = all;
            }
        }
        return logs.stream().map(this::mapToResponseDto).collect(Collectors.toList());
    }

    // 3. GET DISPENSED LOG BY ID
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Pharmacist')")
    public DispensedLogResponseDto getDispensedLogById(Long id) {
        DispensedLog log = dispensedLogRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Dispensed log not found with id: " + id));
        if (log.getSku() != null && log.getSku().getFacility() != null) {
            facilityAccessGuard.verifyFacilityAccess(log.getSku().getFacility().getId());
        }
        return mapToResponseDto(log);
    }

    // MAPPER HELPER
    public DispensedLogResponseDto mapToResponseDto(DispensedLog log) {
        DispensedLogResponseDto dto = new DispensedLogResponseDto();
        dto.setId(log.getId());
        dto.setPatientName(log.getPatientName());
        dto.setContactNumber(log.getContactNumber());
        dto.setUnitsDispensed(log.getUnitsDispensed());
        dto.setStatus(log.getStatus());
        dto.setDispensedAt(log.getDispensedAt());
        dto.setReceivedAt(log.getReceivedAt());

        if (log.getSku() != null) {
            dto.setSkuId(log.getSku().getId());
            dto.setSkuName(log.getSku().getName());
            dto.setBrandName(log.getSku().getBrandName());
            if (log.getSku().getLibMedicine() != null) {
                dto.setGenericName(log.getSku().getLibMedicine().getDrugDescription());
            }
            if (log.getSku().getFacility() != null) {
                dto.setFacilityId(log.getSku().getFacility().getId());
                dto.setFacilityName(log.getSku().getFacility().getName());
            }
        }

        return dto;
    }
}
