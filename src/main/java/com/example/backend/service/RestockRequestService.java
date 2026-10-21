package com.example.backend.service;

import com.example.backend.dto.restockRequest.RestockRequestDto;
import com.example.backend.dto.restockRequest.RestockResponseDto;
import com.example.backend.entity.*;
import com.example.backend.repository.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RestockRequestService {

    private final RestockRequestRepository restockRequestRepository;
    private final SkuRepository skuRepository;
    private final FacilityRepository facilityRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public RestockRequestService(RestockRequestRepository restockRequestRepository,
                                 SkuRepository skuRepository,
                                 FacilityRepository facilityRepository,
                                 UserRepository userRepository,
                                 AuditLogService auditLogService) {
        this.restockRequestRepository = restockRequestRepository;
        this.skuRepository = skuRepository;
        this.facilityRepository = facilityRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    // 1. CREATE RESTOCK REQUEST (Pharmacist / Admin / SuperAdmin)
    @Transactional
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Pharmacist')")
    public RestockResponseDto createRestockRequest(RestockRequestDto request) {
        if (request == null) {
            throw new RuntimeException("Request body cannot be null");
        }

        Sku sku = skuRepository.findById(request.getSkuId())
                .orElseThrow(() -> new RuntimeException("SKU not found with id: " + request.getSkuId()));

        if (request.getFacilityId() == null) {
            throw new RuntimeException("Facility ID is required");
        }

        Facility facility = facilityRepository.findById(request.getFacilityId())
                .orElseThrow(() -> new RuntimeException("Facility not found with id: " + request.getFacilityId()));

        User user;
        if (request.getUserId() != null) {
            user = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found with id: " + request.getUserId()));
        } else {
            user = getCurrentUser();
        }

        // Prevent duplicate restock requests until the previous request's order is Received
        List<RestockRequest> existingActive = restockRequestRepository
                .findActiveRestockRequestsByFacilityIdAndSkuId(facility.getId(), sku.getId());
        if (!existingActive.isEmpty()) {
            RestockRequest existing = existingActive.get(0);
            String currentStatus = existing.getOrder() == null ? "Requested" : existing.getOrder().getStatus().name();
            throw new RuntimeException("A restock request is already active for this SKU (" 
                    + existing.getRequestedUnits() + " units, Status: " + currentStatus 
                    + "). A new request can only be submitted once the current restock is Received.");
        }

        RestockRequest restockRequest = new RestockRequest();
        restockRequest.setFacility(facility);
        restockRequest.setSku(sku);
        restockRequest.setUser(user);
        restockRequest.setRequestedUnits(request.getRequestedUnits());
        restockRequest.setReason(request.getReason() != null ? request.getReason().trim() : null);
        restockRequest.setOrder(null);

        RestockRequest saved = restockRequestRepository.save(restockRequest);

        auditLogService.logAction(
                facility,
                user,
                "Inventory",
                "RESTOCK_REQUEST_CREATED",
                "Restock Request Submitted (" + saved.getRequestedUnits() + " units)",
                AuditLog.Severity.INFO,
                sku.getName(),
                saved.getId(),
                "Submitted restock request for " + saved.getRequestedUnits() + " units of '" + sku.getName() + "'. Reason: " + (saved.getReason() != null ? saved.getReason() : "None stated"),
                "Admin,Pharmacist,Procurement"
        );

        RestockResponseDto response = mapToResponseDto(saved);
        response.setMessage("Restock request submitted successfully.");
        return response;
    }

    // 2. GET ALL RESTOCK REQUESTS (optional facilityId, pendingOnly filter)
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Pharmacist', 'Procurement')")
    public List<RestockResponseDto> getAllRestockRequests(Long facilityId, Boolean pendingOnly) {
        List<RestockRequest> list;

        boolean isPending = Boolean.TRUE.equals(pendingOnly);

        if (facilityId != null) {
            if (isPending) {
                list = restockRequestRepository.findByFacilityIdAndOrderIsNullOrderByCreatedAtDesc(facilityId);
            } else {
                list = restockRequestRepository.findByFacilityIdOrderByCreatedAtDesc(facilityId);
            }
        } else {
            if (isPending) {
                list = restockRequestRepository.findByOrderIsNullOrderByCreatedAtDesc();
            } else {
                list = restockRequestRepository.findAllByOrderByCreatedAtDesc();
            }
        }

        return list.stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
    }

    // 3. GET RESTOCK REQUEST BY ID
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Pharmacist', 'Procurement')")
    public RestockResponseDto getRestockRequestById(Long id) {
        RestockRequest request = restockRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Restock request not found with id: " + id));
        return mapToResponseDto(request);
    }

    // Helper: Map Entity to Response DTO
    private RestockResponseDto mapToResponseDto(RestockRequest entity) {
        RestockResponseDto dto = new RestockResponseDto();
        dto.setId(entity.getId());

        if (entity.getFacility() != null) {
            dto.setFacilityId(entity.getFacility().getId());
            dto.setFacilityName(entity.getFacility().getName());
        }

        if (entity.getSku() != null) {
            Sku sku = entity.getSku();
            dto.setSkuId(sku.getId());
            dto.setSkuName(sku.getName());
            dto.setBrandName(sku.getBrandName());
            if (sku.getLibMedicine() != null) {
                dto.setGenericName(sku.getLibMedicine().getDrugDescription());
            }
            dto.setDosageForm(sku.getDosageForm());
            dto.setPackagingUnit(sku.getPackagingUnit());
        }

        if (entity.getUser() != null) {
            dto.setUserId(entity.getUser().getId());
            dto.setUserName(entity.getUser().getName());
        }

        dto.setRequestedUnits(entity.getRequestedUnits());
        dto.setReason(entity.getReason());

        if (entity.getOrder() != null) {
            dto.setOrderId(entity.getOrder().getId());
            dto.setPurchaseOrderNum(entity.getOrder().getPurchaseOrderNum());
            dto.setStatus(entity.getOrder().getStatus() != null ? entity.getOrder().getStatus().name() : "Ordered");
        } else {
            dto.setStatus("Requested");
        }

        if (entity.getOrderedItem() != null) {
            dto.setOrderedItemId(entity.getOrderedItem().getId());
        }

        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    // Helper: Retrieve authenticated user
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Unauthorized request");
        }
        String currentUsername = authentication.getName();
        return userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new AccessDeniedException("Authenticated user not found"));
    }
}
