package com.example.backend.service;

import com.example.backend.dto.assign.PharmacyAssignRequestDto;
import com.example.backend.dto.pharmacy.PharmacyResponseDto;
import com.example.backend.dto.user.UserResponseDto;
import com.example.backend.entity.AuditLog;
import com.example.backend.entity.Facility;
import com.example.backend.entity.Pharmacy;
import com.example.backend.entity.User;
import com.example.backend.entity.UserFacilityLink;
import com.example.backend.entity.UserPharmacyLink;
import com.example.backend.repository.PharmacyRepository;
import com.example.backend.repository.UserFacilityLinkRepository;
import com.example.backend.repository.UserPharmacyLinkRepository;
import com.example.backend.repository.UserRepository;
import com.example.backend.security.FacilityAccessGuard;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PharmacyAssignService {

    private final UserRepository userRepository;
    private final PharmacyRepository pharmacyRepository;
    private final UserPharmacyLinkRepository userPharmacyLinkRepository;
    private final UserFacilityLinkRepository userFacilityLinkRepository;
    private final AuditLogService auditLogService;
    private final FacilityAccessGuard facilityAccessGuard;

    public PharmacyAssignService(UserRepository userRepository,
                                 PharmacyRepository pharmacyRepository,
                                 UserPharmacyLinkRepository userPharmacyLinkRepository,
                                 UserFacilityLinkRepository userFacilityLinkRepository,
                                 AuditLogService auditLogService,
                                 FacilityAccessGuard facilityAccessGuard) {
        this.userRepository = userRepository;
        this.pharmacyRepository = pharmacyRepository;
        this.userPharmacyLinkRepository = userPharmacyLinkRepository;
        this.userFacilityLinkRepository = userFacilityLinkRepository;
        this.auditLogService = auditLogService;
        this.facilityAccessGuard = facilityAccessGuard;
    }

    // 1. ASSIGN 1 OR MORE USERS (ADMIN / PHARMACIST / PROCUREMENT) TO A PHARMACY
    @Transactional
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin')")
    public String assignUsersToPharmacy(Long pharmacyId, PharmacyAssignRequestDto request) {
        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found with id: " + pharmacyId));
        facilityAccessGuard.verifyPharmacyAccess(pharmacyId);

        Facility facility = pharmacy.getFacility();
        User currentUser = getCurrentUser();
        List<String> assignedUsernames = new ArrayList<>();

        for (Long userId : request.getUserIds()) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

            boolean isAllowedRole = user.getRole() == User.Role.Admin
                    || user.getRole() == User.Role.Pharmacist
                    || user.getRole() == User.Role.Procurement;

            if (!isAllowedRole) {
                throw new RuntimeException("User '" + user.getUsername() + "' with role " + user.getRole() +
                        " cannot be assigned to a pharmacy. Only Admin, Pharmacist, and Procurement roles are allowed.");
            }

            if (currentUser.getRole() == User.Role.Admin && user.getRole() == User.Role.Admin) {
                throw new AccessDeniedException("Admins cannot assign fellow Admin accounts to pharmacies. Only SuperAdmin can manage Admin assignments.");
            }

            if (!userPharmacyLinkRepository.existsByUserIdAndPharmacyId(userId, pharmacyId)) {
                UserPharmacyLink link = new UserPharmacyLink();
                link.setUser(user);
                link.setPharmacy(pharmacy);
                userPharmacyLinkRepository.save(link);
                assignedUsernames.add(user.getUsername());
            }

            // Ensure user is also linked to the parent Facility if not already linked
            if (facility != null && !userFacilityLinkRepository.existsByUserIdAndFacilityId(userId, facility.getId())) {
                UserFacilityLink facilityLink = new UserFacilityLink();
                facilityLink.setUser(user);
                facilityLink.setFacility(facility);
                userFacilityLinkRepository.save(facilityLink);
            }
        }

        if (assignedUsernames.isEmpty()) {
            return "All specified users are already assigned to pharmacy '" + pharmacy.getPharmacyName() + "'";
        }

        auditLogService.logAction(
                facility,
                pharmacy,
                currentUser,
                "Security & Access",
                "PHARMACY_USERS_ASSIGNED",
                "Users Assigned to Pharmacy",
                AuditLog.Severity.INFO,
                pharmacy.getPharmacyName(),
                pharmacy.getId(),
                "Assigned users " + assignedUsernames + " to pharmacy '" + pharmacy.getPharmacyName() + "'.",
                "Admin"
        );

        return "Successfully assigned users " + assignedUsernames + " to pharmacy '" + pharmacy.getPharmacyName() + "'";
    }

    // 2. UNASSIGN 1 OR MORE USERS FROM A PHARMACY
    @Transactional
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin')")
    public String unassignUsersFromPharmacy(Long pharmacyId, PharmacyAssignRequestDto request) {
        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found with id: " + pharmacyId));
        facilityAccessGuard.verifyPharmacyAccess(pharmacyId);

        User currentUser = getCurrentUser();
        List<Long> unassignedUserIds = new ArrayList<>();

        for (Long userId : request.getUserIds()) {
            Optional<UserPharmacyLink> linkOpt = userPharmacyLinkRepository.findByUserIdAndPharmacyId(userId, pharmacyId);
            if (linkOpt.isPresent()) {
                User targetUser = linkOpt.get().getUser();

                if (currentUser.getRole() == User.Role.Admin && targetUser.getRole() == User.Role.Admin) {
                    throw new AccessDeniedException("Admins cannot unassign fellow Admin accounts from pharmacies. Only SuperAdmin can manage Admin assignments.");
                }

                userPharmacyLinkRepository.delete(linkOpt.get());
                unassignedUserIds.add(userId);
            }
        }

        if (unassignedUserIds.isEmpty()) {
            return "None of the specified users were assigned to pharmacy '" + pharmacy.getPharmacyName() + "'";
        }

        auditLogService.logAction(
                pharmacy.getFacility(),
                pharmacy,
                currentUser,
                "Security & Access",
                "PHARMACY_USERS_UNASSIGNED",
                "Users Unassigned from Pharmacy",
                AuditLog.Severity.WARNING,
                pharmacy.getPharmacyName(),
                pharmacy.getId(),
                "Unassigned " + unassignedUserIds.size() + " user(s) (IDs: " + unassignedUserIds + ") from pharmacy '" + pharmacy.getPharmacyName() + "'.",
                "Admin"
        );

        return "Successfully unassigned user IDs " + unassignedUserIds + " from pharmacy '" + pharmacy.getPharmacyName() + "'";
    }

    // 3. GET ASSIGNED PHARMACIES FOR CURRENT AUTHENTICATED USER
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<PharmacyResponseDto> getMyAssignedPharmacies() {
        User currentUser = getCurrentUser();
        List<UserPharmacyLink> links = userPharmacyLinkRepository.findByUserId(currentUser.getId());

        return links.stream()
                .map(UserPharmacyLink::getPharmacy)
                .distinct()
                .map(this::mapPharmacyToDtoWithAssignedUsers)
                .collect(Collectors.toList());
    }

    // 4. GET ALL USERS ASSIGNED TO A SPECIFIC PHARMACY
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin')")
    public List<UserResponseDto> getUsersByPharmacyId(Long pharmacyId) {
        facilityAccessGuard.verifyPharmacyAccess(pharmacyId);
        return userPharmacyLinkRepository.findByPharmacyId(pharmacyId)
                .stream()
                .map(link -> {
                    User u = link.getUser();
                    Long facilityId = link.getPharmacy() != null && link.getPharmacy().getFacility() != null
                            ? link.getPharmacy().getFacility().getId()
                            : null;
                    List<Long> assignedFacilityIds = userFacilityLinkRepository.findByUserId(u.getId())
                            .stream()
                            .map(fl -> fl.getFacility().getId())
                            .collect(Collectors.toList());
                    List<Long> assignedPharmacyIds = userPharmacyLinkRepository.findByUserId(u.getId())
                            .stream()
                            .map(pl -> pl.getPharmacy().getId())
                            .collect(Collectors.toList());

                    UserResponseDto dto = new UserResponseDto(
                            u.getId(),
                            u.getName(),
                            u.getUsername(),
                            u.getEmail(),
                            u.getPhone(),
                            u.getRole(),
                            u.getStatus(),
                            facilityId,
                            pharmacyId,
                            u.getCreatedAt(),
                            null
                    );
                    dto.setAssignedFacilityIds(assignedFacilityIds);
                    dto.setAssignedPharmacyIds(assignedPharmacyIds);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    private PharmacyResponseDto mapPharmacyToDtoWithAssignedUsers(Pharmacy p) {
        List<Long> assignedUserIds = userPharmacyLinkRepository.findByPharmacyId(p.getId())
                .stream()
                .map(link -> link.getUser().getId())
                .collect(Collectors.toList());

        PharmacyResponseDto dto = new PharmacyResponseDto();
        dto.setId(p.getId());
        dto.setFacilityId(p.getFacility() != null ? p.getFacility().getId() : null);
        dto.setFacilityName(p.getFacility() != null ? p.getFacility().getName() : null);
        dto.setPharmacyCode(p.getPharmacyCode());
        dto.setPharmacyName(p.getPharmacyName());
        dto.setLguId(p.getLguId());
        dto.setAddress(p.getAddress());
        dto.setLicenseNo(p.getLicenseNo());
        dto.setStatus(p.getStatus());
        dto.setCreatedAt(p.getCreatedAt());
        dto.setUpdatedAt(p.getUpdatedAt());
        dto.setAssignedUserIds(assignedUserIds);
        return dto;
    }

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

