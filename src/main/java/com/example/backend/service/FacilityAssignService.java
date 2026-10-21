package com.example.backend.service;

import com.example.backend.dto.assign.FacilityAssignRequestDto;
import com.example.backend.dto.facility.FacilityResponseDto;
import com.example.backend.dto.user.UserResponseDto;
import com.example.backend.entity.Facility;
import com.example.backend.entity.User;
import com.example.backend.entity.UserFacilityLink;
import com.example.backend.repository.FacilityRepository;
import com.example.backend.repository.UserFacilityLinkRepository;
import com.example.backend.repository.UserRepository;
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

import com.example.backend.entity.AuditLog;

@Service
public class FacilityAssignService {
    
    private final UserRepository userRepository;
    private final FacilityRepository facilityRepository;
    private final UserFacilityLinkRepository userFacilityLinkRepository;
    private final AuditLogService auditLogService;

    public FacilityAssignService(UserRepository userRepository,
                                 FacilityRepository facilityRepository,
                                 UserFacilityLinkRepository userFacilityLinkRepository,
                                 AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.facilityRepository = facilityRepository;
        this.userFacilityLinkRepository = userFacilityLinkRepository;
        this.auditLogService = auditLogService;
    }

    // 1. ASSIGN 1 OR MORE USERS (ADMIN / PHARMACIST / PROCUREMENT) TO A FACILITY
    @Transactional
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin')")
    public String assignUsersToFacility(Long facilityId, FacilityAssignRequestDto request) {
        Facility facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new RuntimeException("Facility not found with id: " + facilityId));

        User currentUser = getCurrentUser();
        List<String> assignedUsernames = new ArrayList<>();

        for (Long userId : request.getUserIds()) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

            // Enforce: Admin, Pharmacist, and Procurement can be assigned to facilities
            boolean isAllowedRole = user.getRole() == User.Role.Admin
                    || user.getRole() == User.Role.Pharmacist
                    || user.getRole() == User.Role.Procurement;

            if (!isAllowedRole) {
                throw new RuntimeException("User '" + user.getUsername() + "' with role " + user.getRole() +
                        " cannot be assigned to a facility. Only Admin, Pharmacist, and Procurement roles are allowed.");
            }

            // Role hierarchy: An Admin cannot assign fellow Admin accounts
            if (currentUser.getRole() == User.Role.Admin && user.getRole() == User.Role.Admin) {
                throw new AccessDeniedException("Admins cannot assign fellow Admin accounts to facilities. Only SuperAdmin can manage Admin assignments.");
            }

            if (!userFacilityLinkRepository.existsByUserIdAndFacilityId(userId, facilityId)) {
                UserFacilityLink link = new UserFacilityLink();
                link.setUser(user);
                link.setFacility(facility);
                userFacilityLinkRepository.save(link);
                assignedUsernames.add(user.getUsername());
            }
        }

        if (assignedUsernames.isEmpty()) {
            return "All specified users are already assigned to facility '" + facility.getName() + "'";
        }

        auditLogService.logAction(
                facility,
                currentUser,
                "Security & Access",
                "FACILITY_USERS_ASSIGNED",
                "Users Assigned to Facility",
                AuditLog.Severity.INFO,
                facility.getName(),
                facility.getId(),
                "Assigned users " + assignedUsernames + " to facility '" + facility.getName() + "'.",
                "Admin"
        );

        return "Successfully assigned users " + assignedUsernames + " to facility '" + facility.getName() + "'";
    }

    // 2. UNASSIGN 1 OR MORE USERS FROM A FACILITY
    @Transactional
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin')")
    public String unassignUsersFromFacility(Long facilityId, FacilityAssignRequestDto request) {
        Facility facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new RuntimeException("Facility not found with id: " + facilityId));

        User currentUser = getCurrentUser();
        List<Long> unassignedUserIds = new ArrayList<>();

        for (Long userId : request.getUserIds()) {
            Optional<UserFacilityLink> linkOpt = userFacilityLinkRepository.findByUserIdAndFacilityId(userId, facilityId);
            if (linkOpt.isPresent()) {
                User targetUser = linkOpt.get().getUser();

                // Role hierarchy: An Admin cannot unassign fellow Admin accounts
                if (currentUser.getRole() == User.Role.Admin && targetUser.getRole() == User.Role.Admin) {
                    throw new AccessDeniedException("Admins cannot unassign fellow Admin accounts from facilities. Only SuperAdmin can manage Admin assignments.");
                }

                userFacilityLinkRepository.delete(linkOpt.get());
                unassignedUserIds.add(userId);
            }
        }

        if (unassignedUserIds.isEmpty()) {
            return "None of the specified users were assigned to facility '" + facility.getName() + "'";
        }

        auditLogService.logAction(
                facility,
                currentUser,
                "Security & Access",
                "FACILITY_USERS_UNASSIGNED",
                "Users Unassigned from Facility",
                AuditLog.Severity.WARNING,
                facility.getName(),
                facility.getId(),
                "Unassigned " + unassignedUserIds.size() + " user(s) (IDs: " + unassignedUserIds + ") from facility '" + facility.getName() + "'.",
                "Admin"
        );

        return "Successfully unassigned user IDs " + unassignedUserIds + " from facility '" + facility.getName() + "'";
    }



    // 5. GET ASSIGNED FACILITIES FOR CURRENT AUTHENTICATED USER
    @PreAuthorize("isAuthenticated()")
    public List<FacilityResponseDto> getMyAssignedFacilities() {
        User currentUser = getCurrentUser();
        List<UserFacilityLink> links = userFacilityLinkRepository.findByUserId(currentUser.getId());

        return links.stream()
                .map(UserFacilityLink::getFacility)
                .distinct()
                .map(this::mapFacilityToDtoWithAssignedUsers)
                .collect(Collectors.toList());
    }

    // 6. GET ALL USERS ASSIGNED TO A SPECIFIC FACILITY
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin')")
    public List<UserResponseDto> getUsersByFacilityId(Long facilityId) {
        return userFacilityLinkRepository.findByFacilityId(facilityId)
                .stream()
                .map(link -> {
                    User u = link.getUser();
                    return new UserResponseDto(
                            u.getId(),
                            u.getName(),
                            u.getUsername(),
                            u.getEmail(),
                            u.getPhone(),
                            u.getRole(),
                            u.getStatus(),
                            facilityId,
                            u.getCreatedAt(),
                            null
                    );
                })
                .collect(Collectors.toList());
    }

    // Helper: Map Facility to FacilityResponseDto with populated assignedUserIds
    private FacilityResponseDto mapFacilityToDtoWithAssignedUsers(Facility f) {
        List<Long> assignedUserIds = userFacilityLinkRepository.findByFacilityId(f.getId())
                .stream()
                .map(link -> link.getUser().getId())
                .collect(Collectors.toList());

        FacilityResponseDto dto = new FacilityResponseDto();
        dto.setId(f.getId());
        dto.setProjectId(f.getProject() != null ? f.getProject().getId() : null);
        dto.setProjectName(f.getProject() != null ? f.getProject().getName() : null);
        dto.setFacilityCode(f.getFacilityCode());
        dto.setName(f.getName());
        dto.setContactPerson(f.getContactPerson());
        dto.setEmail(f.getEmail());
        dto.setPhone(f.getPhone());
        dto.setAddress(f.getAddress());
        dto.setStatus(f.getStatus());
        dto.setCreatedAt(f.getCreatedAt());
        dto.setUpdatedAt(f.getUpdatedAt());
        dto.setAssignedUserIds(assignedUserIds);
        return dto;
    }

    // Helper: Retrieve the currently authenticated User entity
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
