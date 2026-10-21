package com.example.backend.service;

import com.example.backend.dto.facility.FacilityRequestDto;
import com.example.backend.dto.facility.FacilityResponseDto;
import com.example.backend.entity.Facility;
import com.example.backend.entity.Project;
import com.example.backend.repository.FacilityRepository;
import com.example.backend.repository.ProjectRepository;
import com.example.backend.repository.UserFacilityLinkRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import com.example.backend.entity.AuditLog;

@Service
public class FacilityService {

    private final FacilityRepository facilityRepository;
    private final ProjectRepository projectRepository;
    private final UserFacilityLinkRepository userFacilityLinkRepository;
    private final AuditLogService auditLogService;

    public FacilityService(FacilityRepository facilityRepository,
                           ProjectRepository projectRepository,
                           UserFacilityLinkRepository userFacilityLinkRepository,
                           AuditLogService auditLogService) {
        this.facilityRepository = facilityRepository;
        this.projectRepository = projectRepository;
        this.userFacilityLinkRepository = userFacilityLinkRepository;
        this.auditLogService = auditLogService;
    }

    // 1. CREATE FACILITY (SuperAdmin and Admin)
    @Transactional
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin')")
    public FacilityResponseDto createFacility(FacilityRequestDto request) {
        // Validate project exists
        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new RuntimeException("Project not found with id: " + request.getProjectId()));

        Facility facility = new Facility();
        facility.setProject(project);
        facility.setName(request.getName());
        facility.setContactPerson(request.getContactPerson());
        facility.setEmail(request.getEmail());
        facility.setPhone(request.getPhone());
        facility.setAddress(request.getAddress());
        facility.setStatus(request.getStatus() != null ? request.getStatus() : Facility.Status.Active);

        // Save initially to generate the auto-increment ID
        Facility savedFacility = facilityRepository.saveAndFlush(facility);

        // Auto-generate facilityCode format: FAC-001, FAC-010, etc.
        savedFacility.setFacilityCode(String.format("FAC-%03d", savedFacility.getId()));
        savedFacility = facilityRepository.save(savedFacility);

        auditLogService.logAction(
                savedFacility,
                "Facility Management",
                "FACILITY_CREATED",
                "Facility Registered (" + savedFacility.getName() + ")",
                AuditLog.Severity.SUCCESS,
                savedFacility.getName(),
                savedFacility.getId(),
                "Registered new facility '" + savedFacility.getName() + "' (" + savedFacility.getFacilityCode() + ") under project '" + project.getName() + "'.",
                "Admin"
        );

        return mapToResponseDto(savedFacility, "Facility Created Successfully");
    }

    // 2. GET ALL FACILITIES BY PROJECT ID (SuperAdmin and Admin)
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin')")
    public List<FacilityResponseDto> getFacilitiesByProjectId(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new RuntimeException("Project not found with id: " + projectId);
        }
        return facilityRepository.findByProjectId(projectId)
                .stream()
                .map(facility -> mapToResponseDto(facility, null))
                .collect(Collectors.toList());
    }

    // 4. UPDATE FACILITY (SuperAdmin and Admin)
    @Transactional
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin')")
    public FacilityResponseDto updateFacility(Long id, FacilityRequestDto request) {
        Facility existingFacility = facilityRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Facility not found with id: " + id));

        // If project reference changed
        if (!existingFacility.getProject().getId().equals(request.getProjectId())) {
            Project project = projectRepository.findById(request.getProjectId())
                    .orElseThrow(() -> new RuntimeException("Project not found with id: " + request.getProjectId()));
            existingFacility.setProject(project);
        }

        existingFacility.setName(request.getName());
        existingFacility.setContactPerson(request.getContactPerson());
        existingFacility.setEmail(request.getEmail());
        existingFacility.setPhone(request.getPhone());
        existingFacility.setAddress(request.getAddress());
        if (request.getStatus() != null) {
            existingFacility.setStatus(request.getStatus());
        }

        Facility updatedFacility = facilityRepository.save(existingFacility);

        auditLogService.logAction(
                updatedFacility,
                "Facility Management",
                "FACILITY_UPDATED",
                "Facility Details Updated (" + updatedFacility.getName() + ")",
                AuditLog.Severity.INFO,
                updatedFacility.getName(),
                updatedFacility.getId(),
                "Updated profile and contact information for facility '" + updatedFacility.getName() + "'.",
                "Admin"
        );

        return mapToResponseDto(updatedFacility, "Facility Updated Successfully");
    }

    // 5. DELETE FACILITY (SuperAdmin and Admin)
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin')")
    public void deleteFacility(Long id) {
        if (!facilityRepository.existsById(id)) {
            throw new RuntimeException("Facility not found with id: " + id);
        }
        facilityRepository.deleteById(id);
    }

    // Helper: Map Facility entity to FacilityResponseDto
    private FacilityResponseDto mapToResponseDto(Facility facility, String message) {
        FacilityResponseDto response = new FacilityResponseDto();
        response.setId(facility.getId());
        if (facility.getProject() != null) {
            response.setProjectId(facility.getProject().getId());
            response.setProjectName(facility.getProject().getName());
        }
        response.setFacilityCode(facility.getFacilityCode());
        response.setName(facility.getName());
        response.setContactPerson(facility.getContactPerson());
        response.setEmail(facility.getEmail());
        response.setPhone(facility.getPhone());
        response.setAddress(facility.getAddress());
        response.setStatus(facility.getStatus());
        response.setCreatedAt(facility.getCreatedAt());
        response.setUpdatedAt(facility.getUpdatedAt());
        response.setMessage(message);

        List<Long> assignedUserIds = userFacilityLinkRepository.findByFacilityId(facility.getId())
                .stream()
                .map(link -> link.getUser().getId())
                .collect(Collectors.toList());
        response.setAssignedUserIds(assignedUserIds);

        return response;
    }
}
