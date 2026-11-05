package com.example.backend.service;

import com.example.backend.dto.supplier.SupplierRequestDto;
import com.example.backend.dto.supplier.SupplierResponseDto;
import com.example.backend.entity.Facility;
import com.example.backend.entity.Pharmacy;
import com.example.backend.entity.Supplier;
import com.example.backend.entity.AuditLog;
import com.example.backend.repository.FacilityRepository;
import com.example.backend.repository.PharmacyRepository;
import com.example.backend.repository.SupplierRepository;
import com.example.backend.security.FacilityAccessGuard;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final FacilityRepository facilityRepository;
    private final PharmacyRepository pharmacyRepository;
    private final AuditLogService auditLogService;
    private final FacilityAccessGuard facilityAccessGuard;

    public SupplierService(SupplierRepository supplierRepository,
                           FacilityRepository facilityRepository,
                           PharmacyRepository pharmacyRepository,
                           AuditLogService auditLogService,
                           FacilityAccessGuard facilityAccessGuard) {
        this.supplierRepository = supplierRepository;
        this.facilityRepository = facilityRepository;
        this.pharmacyRepository = pharmacyRepository;
        this.auditLogService = auditLogService;
        this.facilityAccessGuard = facilityAccessGuard;
    }

    // 1. CREATE SUPPLIER
    @Transactional
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin')")
    public SupplierResponseDto createSupplier(SupplierRequestDto request) {
        Pharmacy pharmacy = null;
        if (request.getPharmacyId() != null) {
            facilityAccessGuard.verifyPharmacyAccess(request.getPharmacyId());
            pharmacy = pharmacyRepository.findById(request.getPharmacyId())
                    .orElseThrow(() -> new RuntimeException("Pharmacy not found with id: " + request.getPharmacyId()));
        }

        Facility facility;
        if (request.getFacilityId() != null) {
            facilityAccessGuard.verifyFacilityAccess(request.getFacilityId());
            facility = facilityRepository.findById(request.getFacilityId())
                    .orElseThrow(() -> new RuntimeException("Facility not found with id: " + request.getFacilityId()));
        } else if (pharmacy != null && pharmacy.getFacility() != null) {
            facility = pharmacy.getFacility();
        } else {
            throw new RuntimeException("Facility ID or Pharmacy ID is required.");
        }

        if (pharmacy != null) {
            if (supplierRepository.existsByPharmacyIdAndEmail(pharmacy.getId(), request.getEmail().trim())) {
                throw new RuntimeException("Email address is already used by another supplier in this pharmacy: " + request.getEmail());
            }
        } else if (supplierRepository.existsByFacilityIdAndEmail(facility.getId(), request.getEmail().trim())) {
            throw new RuntimeException("Email address is already used by another supplier in this facility: " + request.getEmail());
        }

        Supplier supplier = new Supplier();
        supplier.setFacility(facility);
        supplier.setPharmacy(pharmacy);
        supplier.setName(request.getName().trim());
        supplier.setContactPerson(request.getContactPerson().trim());
        supplier.setEmail(request.getEmail().trim());
        supplier.setPhone(request.getPhone() != null ? request.getPhone().trim() : null);
        supplier.setAddress(request.getAddress() != null ? request.getAddress().trim() : null);
        supplier.setPaymentTerms(request.getPaymentTerms());
        supplier.setStatus(request.getStatus() != null ? request.getStatus() : Supplier.Status.Active);

        Supplier savedSupplier = supplierRepository.save(supplier);

        auditLogService.logAction(
                facility,
                pharmacy,
                "Supplier Management",
                "SUPPLIER_CREATED",
                "Supplier Added (" + savedSupplier.getName() + ")",
                AuditLog.Severity.SUCCESS,
                savedSupplier.getName(),
                savedSupplier.getId(),
                "Registered vendor '" + savedSupplier.getName() + "' (Contact: " + savedSupplier.getContactPerson() + ", " + savedSupplier.getEmail() + "). Terms: " + savedSupplier.getPaymentTerms() + ".",
                "Admin,Procurement"
        );

        return mapToResponseDto(savedSupplier, "Supplier created successfully");
    }

    // 2. GET ALL SUPPLIERS - optionally filtered by facilityId / pharmacyId
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Procurement')")
    public List<SupplierResponseDto> getAllSuppliers(Long facilityId) {
        return getAllSuppliers(facilityId, null);
    }

    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Procurement')")
    public List<SupplierResponseDto> getAllSuppliers(Long facilityId, Long pharmacyId) {
        List<Supplier> suppliers;
        if (pharmacyId != null) {
            if (!pharmacyRepository.existsById(pharmacyId)) {
                throw new RuntimeException("Pharmacy not found with id: " + pharmacyId);
            }
            facilityAccessGuard.verifyPharmacyAccess(pharmacyId);
            suppliers = supplierRepository.findByPharmacyId(pharmacyId);
        } else if (facilityId != null) {
            if (!facilityRepository.existsById(facilityId)) {
                throw new RuntimeException("Facility not found with id: " + facilityId);
            }
            facilityAccessGuard.verifyFacilityAccess(facilityId);
            suppliers = supplierRepository.findByFacilityId(facilityId);
        } else {
            Set<Long> accessibleFacilityIds = facilityAccessGuard.getAccessibleFacilityIdsOrNullForSuperAdmin();
            suppliers = supplierRepository.findAll().stream()
                    .filter(s -> accessibleFacilityIds == null ||
                            (s.getFacility() != null && accessibleFacilityIds.contains(s.getFacility().getId())))
                    .collect(Collectors.toList());
        }
        return suppliers.stream()
                .map(s -> mapToResponseDto(s, null))
                .collect(Collectors.toList());
    }

    // 3. GET SUPPLIER BY ID
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin')")
    public SupplierResponseDto getSupplierById(Long id) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Supplier not found with id: " + id));
        if (supplier.getPharmacy() != null) {
            facilityAccessGuard.verifyPharmacyAccess(supplier.getPharmacy().getId());
        } else if (supplier.getFacility() != null) {
            facilityAccessGuard.verifyFacilityAccess(supplier.getFacility().getId());
        }
        return mapToResponseDto(supplier, null);
    }

    // 4. UPDATE SUPPLIER
    @Transactional
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin')")
    public SupplierResponseDto updateSupplier(Long id, SupplierRequestDto request) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Supplier not found with id: " + id));
        if (supplier.getPharmacy() != null) {
            facilityAccessGuard.verifyPharmacyAccess(supplier.getPharmacy().getId());
        } else if (supplier.getFacility() != null) {
            facilityAccessGuard.verifyFacilityAccess(supplier.getFacility().getId());
        }

        if (request.getPharmacyId() != null) {
            facilityAccessGuard.verifyPharmacyAccess(request.getPharmacyId());
            Pharmacy newPharmacy = pharmacyRepository.findById(request.getPharmacyId())
                    .orElseThrow(() -> new RuntimeException("Pharmacy not found with id: " + request.getPharmacyId()));
            supplier.setPharmacy(newPharmacy);
            if (request.getFacilityId() == null && newPharmacy.getFacility() != null) {
                supplier.setFacility(newPharmacy.getFacility());
            }
        }

        // If facility changed
        if (request.getFacilityId() != null && (supplier.getFacility() == null || !supplier.getFacility().getId().equals(request.getFacilityId()))) {
            facilityAccessGuard.verifyFacilityAccess(request.getFacilityId());
            Facility newFacility = facilityRepository.findById(request.getFacilityId())
                    .orElseThrow(() -> new RuntimeException("Facility not found with id: " + request.getFacilityId()));
            supplier.setFacility(newFacility);
        }

        if (supplier.getPharmacy() != null) {
            if (supplierRepository.existsByPharmacyIdAndEmailAndIdNot(supplier.getPharmacy().getId(), request.getEmail().trim(), id)) {
                throw new RuntimeException("Email address is already used by another supplier in this pharmacy: " + request.getEmail());
            }
        } else if (supplier.getFacility() != null) {
            Long facilityId = supplier.getFacility().getId();
            if (supplierRepository.existsByFacilityIdAndEmailAndIdNot(facilityId, request.getEmail().trim(), id)) {
                throw new RuntimeException("Email address is already used by another supplier in this facility: " + request.getEmail());
            }
        }

        supplier.setName(request.getName().trim());
        supplier.setContactPerson(request.getContactPerson().trim());
        supplier.setEmail(request.getEmail().trim());
        supplier.setPhone(request.getPhone() != null ? request.getPhone().trim() : null);
        supplier.setAddress(request.getAddress() != null ? request.getAddress().trim() : null);
        supplier.setPaymentTerms(request.getPaymentTerms());
        if (request.getStatus() != null) {
            supplier.setStatus(request.getStatus());
        }

        Supplier updatedSupplier = supplierRepository.save(supplier);

        auditLogService.logAction(
                updatedSupplier.getFacility(),
                updatedSupplier.getPharmacy(),
                "Supplier Management",
                "SUPPLIER_UPDATED",
                "Supplier Details Updated (" + updatedSupplier.getName() + ")",
                AuditLog.Severity.INFO,
                updatedSupplier.getName(),
                updatedSupplier.getId(),
                "Updated profile and payment terms for vendor '" + updatedSupplier.getName() + "'.",
                "Admin,Procurement"
        );

        return mapToResponseDto(updatedSupplier, "Supplier updated successfully");
    }

    // 5. DELETE SUPPLIER (SuperAdmin only)
    @PreAuthorize("hasRole('SuperAdmin')")
    public void deleteSupplier(Long id) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Supplier not found with id: " + id));
        if (supplier.getPharmacy() != null) {
            facilityAccessGuard.verifyPharmacyAccess(supplier.getPharmacy().getId());
        } else if (supplier.getFacility() != null) {
            facilityAccessGuard.verifyFacilityAccess(supplier.getFacility().getId());
        }

        supplierRepository.delete(supplier);

        auditLogService.logAction(
                supplier.getFacility(),
                supplier.getPharmacy(),
                "Supplier Management",
                "SUPPLIER_DELETED",
                "Supplier Removed (" + supplier.getName() + ")",
                AuditLog.Severity.WARNING,
                supplier.getName(),
                supplier.getId(),
                "Removed vendor '" + supplier.getName() + "' from facility records.",
                "Admin,Procurement"
        );
    }

    // Helper: Map Supplier entity to SupplierResponseDto
    private SupplierResponseDto mapToResponseDto(Supplier supplier, String message) {
        SupplierResponseDto dto = new SupplierResponseDto();
        dto.setId(supplier.getId());
        if (supplier.getFacility() != null) {
            dto.setFacilityId(supplier.getFacility().getId());
            dto.setFacilityName(supplier.getFacility().getName());
        }
        if (supplier.getPharmacy() != null) {
            dto.setPharmacyId(supplier.getPharmacy().getId());
            dto.setPharmacyName(supplier.getPharmacy().getPharmacyName());
        }
        dto.setName(supplier.getName());
        dto.setContactPerson(supplier.getContactPerson());
        dto.setEmail(supplier.getEmail());
        dto.setPhone(supplier.getPhone());
        dto.setAddress(supplier.getAddress());
        dto.setPaymentTerms(supplier.getPaymentTerms());
        dto.setStatus(supplier.getStatus());
        dto.setCreatedAt(supplier.getCreatedAt());
        dto.setUpdatedAt(supplier.getUpdatedAt());
        dto.setMessage(message);
        return dto;
    }

}
