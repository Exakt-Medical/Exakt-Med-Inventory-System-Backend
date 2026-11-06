package com.example.backend.service;

import com.example.backend.dto.patient.PatientRequestDto;
import com.example.backend.dto.patient.PatientResponseDto;
import com.example.backend.entity.AuditLog;
import com.example.backend.entity.Patient;
import com.example.backend.entity.Pharmacy;
import com.example.backend.repository.PatientRepository;
import com.example.backend.repository.PharmacyRepository;
import com.example.backend.security.FacilityAccessGuard;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PatientService {

    private final PatientRepository patientRepository;
    private final PharmacyRepository pharmacyRepository;
    private final FacilityAccessGuard facilityAccessGuard;
    private final AuditLogService auditLogService;

    public PatientService(PatientRepository patientRepository,
                          PharmacyRepository pharmacyRepository,
                          FacilityAccessGuard facilityAccessGuard,
                          AuditLogService auditLogService) {
        this.patientRepository = patientRepository;
        this.pharmacyRepository = pharmacyRepository;
        this.facilityAccessGuard = facilityAccessGuard;
        this.auditLogService = auditLogService;
    }

    // 1. CREATE PATIENT
    @Transactional
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Pharmacist')")
    public PatientResponseDto createPatient(PatientRequestDto request) {
        if (request.getPharmacyId() == null) {
            throw new RuntimeException("Pharmacy ID is required.");
        }
        facilityAccessGuard.verifyPharmacyAccess(request.getPharmacyId());

        Pharmacy pharmacy = pharmacyRepository.findById(request.getPharmacyId())
                .orElseThrow(() -> new RuntimeException("Pharmacy not found with id: " + request.getPharmacyId()));

        String trimmedName = request.getName().trim().toUpperCase();
        if (patientRepository.existsByPharmacyIdAndNameIgnoreCase(pharmacy.getId(), trimmedName)) {
            throw new RuntimeException("Patient with name '" + trimmedName + "' already exists in this pharmacy.");
        }

        Patient patient = new Patient();
        patient.setPharmacy(pharmacy);
        patient.setName(trimmedName);
        patient.setContactNumber(request.getContactNumber() != null ? request.getContactNumber().trim() : null);

        Patient saved = patientRepository.save(patient);

        auditLogService.logAction(
                pharmacy,
                "Patient Management",
                "PATIENT_CREATED",
                "Patient Registered (" + saved.getName() + ")",
                AuditLog.Severity.INFO,
                saved.getName(),
                saved.getId(),
                "Registered patient '" + saved.getName() + "' for pharmacy '" + pharmacy.getPharmacyName() + "'.",
                "Admin,Pharmacist"
        );

        return mapToResponseDto(saved);
    }

    // 2. GET ALL PATIENTS (Scoped to Pharmacy, optional search)
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Pharmacist')")
    public List<PatientResponseDto> getPatients(Long pharmacyId, String search) {
        if (pharmacyId == null) {
            throw new RuntimeException("Pharmacy ID is required.");
        }
        facilityAccessGuard.verifyPharmacyAccess(pharmacyId);

        List<Patient> list;
        if (search != null && !search.trim().isEmpty()) {
            list = patientRepository.findByPharmacyIdAndNameContainingIgnoreCaseOrderByNameAsc(pharmacyId, search.trim());
        } else {
            list = patientRepository.findByPharmacyIdOrderByNameAsc(pharmacyId);
        }

        return list.stream().map(this::mapToResponseDto).collect(Collectors.toList());
    }

    // 3. GET PATIENT BY ID
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Pharmacist')")
    public PatientResponseDto getPatientById(Long id) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Patient not found with id: " + id));

        facilityAccessGuard.verifyPharmacyAccess(patient.getPharmacy().getId());

        return mapToResponseDto(patient);
    }

    // 4. UPDATE PATIENT
    @Transactional
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Pharmacist')")
    public PatientResponseDto updatePatient(Long id, PatientRequestDto request) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Patient not found with id: " + id));

        facilityAccessGuard.verifyPharmacyAccess(patient.getPharmacy().getId());

        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            String trimmedName = request.getName().trim().toUpperCase();
            if (patientRepository.existsByPharmacyIdAndNameIgnoreCaseAndIdNot(patient.getPharmacy().getId(), trimmedName, id)) {
                throw new RuntimeException("Another patient with name '" + trimmedName + "' already exists in this pharmacy.");
            }
            patient.setName(trimmedName);
        }

        if (request.getContactNumber() != null) {
            patient.setContactNumber(request.getContactNumber().trim().isEmpty() ? null : request.getContactNumber().trim());
        }

        Patient updated = patientRepository.save(patient);

        auditLogService.logAction(
                updated.getPharmacy(),
                "Patient Management",
                "PATIENT_UPDATED",
                "Patient Details Updated (" + updated.getName() + ")",
                AuditLog.Severity.INFO,
                updated.getName(),
                updated.getId(),
                "Updated patient profile details for '" + updated.getName() + "'.",
                "Admin,Pharmacist"
        );

        return mapToResponseDto(updated);
    }

    // 5. DELETE PATIENT
    @Transactional
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Pharmacist')")
    public void deletePatient(Long id) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Patient not found with id: " + id));

        facilityAccessGuard.verifyPharmacyAccess(patient.getPharmacy().getId());

        patientRepository.delete(patient);

        auditLogService.logAction(
                patient.getPharmacy(),
                "Patient Management",
                "PATIENT_DELETED",
                "Patient Record Deleted (" + patient.getName() + ")",
                AuditLog.Severity.WARNING,
                patient.getName(),
                patient.getId(),
                "Deleted patient profile '" + patient.getName() + "' (ID: " + patient.getId() + ").",
                "Admin,Pharmacist"
        );
    }

    // MAPPER HELPER
    public PatientResponseDto mapToResponseDto(Patient patient) {
        return PatientResponseDto.builder()
                .id(patient.getId())
                .pharmacyId(patient.getPharmacy() != null ? patient.getPharmacy().getId() : null)
                .pharmacyName(patient.getPharmacy() != null ? patient.getPharmacy().getPharmacyName() : null)
                .name(patient.getName())
                .contactNumber(patient.getContactNumber())
                .createdAt(patient.getCreatedAt())
                .updatedAt(patient.getUpdatedAt())
                .build();
    }
}

