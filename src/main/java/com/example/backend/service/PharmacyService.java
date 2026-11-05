package com.example.backend.service;

import com.example.backend.dto.pharmacy.PharmacyRequestDto;
import com.example.backend.dto.pharmacy.PharmacyResponseDto;
import com.example.backend.entity.AuditLog;
import com.example.backend.entity.Facility;
import com.example.backend.entity.Pharmacy;
import com.example.backend.repository.FacilityRepository;
import com.example.backend.repository.PharmacyRepository;
import com.example.backend.repository.UserPharmacyLinkRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PharmacyService {

    private final PharmacyRepository pharmacyRepository;
    private final FacilityRepository facilityRepository;
    private final UserPharmacyLinkRepository userPharmacyLinkRepository;
    private final AuditLogService auditLogService;

    public PharmacyService(PharmacyRepository pharmacyRepository,
                           FacilityRepository facilityRepository,
                           UserPharmacyLinkRepository userPharmacyLinkRepository,
                           AuditLogService auditLogService) {
        this.pharmacyRepository = pharmacyRepository;
        this.facilityRepository = facilityRepository;
        this.userPharmacyLinkRepository = userPharmacyLinkRepository;
        this.auditLogService = auditLogService;
    }

    // 1. CREATE PHARMACY (SuperAdmin)
    @Transactional
    @PreAuthorize("hasRole('SuperAdmin')")
    public PharmacyResponseDto createPharmacy(PharmacyRequestDto request) {
        Facility facility = facilityRepository.findById(request.getFacilityId())
                .orElseThrow(() -> new RuntimeException("Facility not found with id: " + request.getFacilityId()));

        String trimmedCode = request.getPharmacyCode().trim();
        if (pharmacyRepository.existsByPharmacyCodeIgnoreCase(trimmedCode)) {
            throw new RuntimeException("Pharmacy code '" + trimmedCode + "' already exists");
        }

        Pharmacy pharmacy = new Pharmacy();
        pharmacy.setFacility(facility);
        pharmacy.setPharmacyCode(trimmedCode);
        pharmacy.setPharmacyName(request.getPharmacyName().trim());
        pharmacy.setLguId(request.getLguId());
        pharmacy.setAddress(request.getAddress());
        pharmacy.setLicenseNo(request.getLicenseNo());
        pharmacy.setStatus(request.getStatus() != null ? request.getStatus() : Pharmacy.Status.Active);

        Pharmacy savedPharmacy = pharmacyRepository.save(pharmacy);

        auditLogService.logAction(
                facility,
                savedPharmacy,
                "Pharmacy Management",
                "PHARMACY_CREATED",
                "Pharmacy Registered (" + savedPharmacy.getPharmacyName() + ")",
                AuditLog.Severity.SUCCESS,
                savedPharmacy.getPharmacyName(),
                savedPharmacy.getId(),
                "Registered new pharmacy '" + savedPharmacy.getPharmacyName() + "' (" + savedPharmacy.getPharmacyCode() + ") under facility '" + facility.getName() + "'.",
                "Admin"
        );

        return mapToResponseDto(savedPharmacy, "Pharmacy Created Successfully");
    }

    // 2. GET ALL PHARMACIES (Optional facilityId filter)
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Pharmacist', 'Procurement')")
    public List<PharmacyResponseDto> getPharmacies(Long facilityId) {
        List<Pharmacy> pharmacies;
        if (facilityId != null) {
            if (!facilityRepository.existsById(facilityId)) {
                throw new RuntimeException("Facility not found with id: " + facilityId);
            }
            pharmacies = pharmacyRepository.findByFacilityId(facilityId);
        } else {
            pharmacies = pharmacyRepository.findAll();
        }
        return pharmacies.stream()
                .map(p -> mapToResponseDto(p, null))
                .collect(Collectors.toList());
    }

    // 3. GET PHARMACY BY ID
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Pharmacist', 'Procurement')")
    public PharmacyResponseDto getPharmacyById(Long id) {
        Pharmacy pharmacy = pharmacyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found with id: " + id));
        return mapToResponseDto(pharmacy, null);
    }

    // 4. UPDATE PHARMACY (SuperAdmin)
    @Transactional
    @PreAuthorize("hasRole('SuperAdmin')")
    public PharmacyResponseDto updatePharmacy(Long id, PharmacyRequestDto request) {
        Pharmacy existingPharmacy = pharmacyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found with id: " + id));

        String trimmedCode = request.getPharmacyCode().trim();
        pharmacyRepository.findByPharmacyCodeIgnoreCase(trimmedCode)
                .filter(p -> !p.getId().equals(id))
                .ifPresent(p -> {
                    throw new RuntimeException("Pharmacy code '" + trimmedCode + "' already exists");
                });

        if (!existingPharmacy.getFacility().getId().equals(request.getFacilityId())) {
            Facility facility = facilityRepository.findById(request.getFacilityId())
                    .orElseThrow(() -> new RuntimeException("Facility not found with id: " + request.getFacilityId()));
            existingPharmacy.setFacility(facility);
        }

        existingPharmacy.setPharmacyCode(trimmedCode);
        existingPharmacy.setPharmacyName(request.getPharmacyName().trim());
        existingPharmacy.setLguId(request.getLguId());
        existingPharmacy.setAddress(request.getAddress());
        existingPharmacy.setLicenseNo(request.getLicenseNo());
        if (request.getStatus() != null) {
            existingPharmacy.setStatus(request.getStatus());
        }

        Pharmacy updatedPharmacy = pharmacyRepository.save(existingPharmacy);

        auditLogService.logAction(
                updatedPharmacy.getFacility(),
                updatedPharmacy,
                "Pharmacy Management",
                "PHARMACY_UPDATED",
                "Pharmacy Details Updated (" + updatedPharmacy.getPharmacyName() + ")",
                AuditLog.Severity.INFO,
                updatedPharmacy.getPharmacyName(),
                updatedPharmacy.getId(),
                "Updated profile and license details for pharmacy '" + updatedPharmacy.getPharmacyName() + "'.",
                "Admin"
        );

        return mapToResponseDto(updatedPharmacy, "Pharmacy Updated Successfully");
    }

    // 5. DELETE PHARMACY (SuperAdmin)
    @Transactional
    @PreAuthorize("hasRole('SuperAdmin')")
    public void deletePharmacy(Long id) {
        if (!pharmacyRepository.existsById(id)) {
            throw new RuntimeException("Pharmacy not found with id: " + id);
        }
        pharmacyRepository.deleteById(id);
    }

    public PharmacyResponseDto mapToResponseDto(Pharmacy pharmacy, String message) {
        PharmacyResponseDto response = new PharmacyResponseDto();
        response.setId(pharmacy.getId());
        if (pharmacy.getFacility() != null) {
            response.setFacilityId(pharmacy.getFacility().getId());
            response.setFacilityName(pharmacy.getFacility().getName());
        }
        response.setPharmacyCode(pharmacy.getPharmacyCode());
        response.setPharmacyName(pharmacy.getPharmacyName());
        response.setLguId(pharmacy.getLguId());
        response.setAddress(pharmacy.getAddress());
        response.setLicenseNo(pharmacy.getLicenseNo());
        response.setStatus(pharmacy.getStatus());
        response.setCreatedAt(pharmacy.getCreatedAt());
        response.setUpdatedAt(pharmacy.getUpdatedAt());
        response.setMessage(message);

        List<Long> assignedUserIds = userPharmacyLinkRepository.findByPharmacyId(pharmacy.getId())
                .stream()
                .map(link -> link.getUser().getId())
                .collect(Collectors.toList());
        response.setAssignedUserIds(assignedUserIds);

        return response;
    }
}

