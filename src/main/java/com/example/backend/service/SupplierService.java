package com.example.backend.service;

import com.example.backend.dto.supplier.SupplierRequestDto;
import com.example.backend.dto.supplier.SupplierResponseDto;
import com.example.backend.entity.Facility;
import com.example.backend.entity.Supplier;
import com.example.backend.entity.AuditLog;
import com.example.backend.repository.FacilityRepository;
import com.example.backend.repository.SupplierRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final FacilityRepository facilityRepository;
    private final AuditLogService auditLogService;

    public SupplierService(SupplierRepository supplierRepository,
                           FacilityRepository facilityRepository,
                           AuditLogService auditLogService) {
        this.supplierRepository = supplierRepository;
        this.facilityRepository = facilityRepository;
        this.auditLogService = auditLogService;
    }

    // 1. CREATE SUPPLIER (SuperAdmin only)
    @Transactional
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin')")
    public SupplierResponseDto createSupplier(SupplierRequestDto request) {
        Facility facility = facilityRepository.findById(request.getFacilityId())
                .orElseThrow(() -> new RuntimeException("Facility not found with id: " + request.getFacilityId()));

        if (supplierRepository.existsByFacilityIdAndEmail(facility.getId(), request.getEmail().trim())) {
            throw new RuntimeException("Email address is already used by another supplier in this facility: " + request.getEmail());
        }

        Supplier supplier = new Supplier();
        supplier.setFacility(facility);
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

    // 2. GET ALL SUPPLIERS (SuperAdmin and Admin) - optionally filtered by facilityId
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Procurement')")
    public List<SupplierResponseDto> getAllSuppliers(Long facilityId) {
        List<Supplier> suppliers;
        if (facilityId != null) {
            if (!facilityRepository.existsById(facilityId)) {
                throw new RuntimeException("Facility not found with id: " + facilityId);
            }
            suppliers = supplierRepository.findByFacilityId(facilityId);
        } else {
            suppliers = supplierRepository.findAll();
        }
        return suppliers.stream()
                .map(s -> mapToResponseDto(s, null))
                .collect(Collectors.toList());
    }

    // 3. GET SUPPLIER BY ID (SuperAdmin and Admin)
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin')")
    public SupplierResponseDto getSupplierById(Long id) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Supplier not found with id: " + id));
        return mapToResponseDto(supplier, null);
    }

    // 4. UPDATE SUPPLIER (SuperAdmin and Admin)
    @Transactional
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin')")
    public SupplierResponseDto updateSupplier(Long id, SupplierRequestDto request) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Supplier not found with id: " + id));

        // If facility changed
        if (request.getFacilityId() != null && !supplier.getFacility().getId().equals(request.getFacilityId())) {
            Facility newFacility = facilityRepository.findById(request.getFacilityId())
                    .orElseThrow(() -> new RuntimeException("Facility not found with id: " + request.getFacilityId()));
            supplier.setFacility(newFacility);
        }

        Long facilityId = supplier.getFacility().getId();

        if (supplierRepository.existsByFacilityIdAndEmailAndIdNot(facilityId, request.getEmail().trim(), id)) {
            throw new RuntimeException("Email address is already used by another supplier in this facility: " + request.getEmail());
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

        supplierRepository.delete(supplier);

        auditLogService.logAction(
                supplier.getFacility(),
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
