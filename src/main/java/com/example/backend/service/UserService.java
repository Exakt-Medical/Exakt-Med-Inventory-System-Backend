package com.example.backend.service;

import com.example.backend.dto.user.UserRequestDto;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import com.example.backend.entity.AuditLog;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final FacilityRepository facilityRepository;
    private final UserFacilityLinkRepository userFacilityLinkRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public UserService(UserRepository userRepository,
                       FacilityRepository facilityRepository,
                       UserFacilityLinkRepository userFacilityLinkRepository,
                       PasswordEncoder passwordEncoder,
                       AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.facilityRepository = facilityRepository;
        this.userFacilityLinkRepository = userFacilityLinkRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    // 1. CREATE USER (SuperAdmin only)
    @Transactional
    @PreAuthorize("hasRole('SuperAdmin')")
    public UserResponseDto createUser(UserRequestDto request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new RuntimeException("Username already exists: " + request.getUsername());
        }

        User user = new User();
        user.setName(request.getName());
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setRole(request.getRole());
        user.setStatus(request.getStatus() != null ? request.getStatus() : true);

        User savedUser = userRepository.save(user);

        // If role is Pharmacist or Procurement and facilityId is provided, link the user to the specified facility
        Long linkedFacilityId = null;
        boolean isFacilityRole = request.getRole() == User.Role.Pharmacist || request.getRole() == User.Role.Procurement;
        if (isFacilityRole && request.getFacilityId() != null) {
            Facility facility = facilityRepository.findById(request.getFacilityId())
                    .orElseThrow(() -> new RuntimeException("Facility not found with id: " + request.getFacilityId()));

            UserFacilityLink link = new UserFacilityLink();
            link.setUser(savedUser);
            link.setFacility(facility);
            userFacilityLinkRepository.save(link);
            linkedFacilityId = facility.getId();

            auditLogService.logAction(
                    facility,
                    "Security & Access",
                    "USER_CREATED",
                    "User Account Created (" + savedUser.getUsername() + ")",
                    AuditLog.Severity.SUCCESS,
                    savedUser.getUsername(),
                    savedUser.getId(),
                    "Created user account for '" + savedUser.getName() + "' (@" + savedUser.getUsername() + ") with role " + savedUser.getRole() + " assigned to facility '" + facility.getName() + "'.",
                    "Admin"
            );
        }

        return mapToResponseDto(savedUser, linkedFacilityId, "User created successfully");
    }

    // 2. GET ALL USERS (SuperAdmin only)
    @PreAuthorize("hasRole('SuperAdmin')")
    public List<UserResponseDto> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(user -> mapToResponseDto(user, null))
                .collect(Collectors.toList());
    }

    // 4. UPDATE USER (SuperAdmin only)
    @Transactional
    @PreAuthorize("hasRole('SuperAdmin')")
    public UserResponseDto updateUser(Long id, UserRequestDto request) {
        User targetUser = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));

        User currentUser = getCurrentUser();

        // SuperAdmin accounts cannot be updated by other SuperAdmins (unless updating themselves)
        if (targetUser.getRole() == User.Role.SuperAdmin && !targetUser.getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("SuperAdmin accounts cannot be edited by other users");
        }

        // Update fields
        targetUser.setName(request.getName());
        targetUser.setEmail(request.getEmail());
        targetUser.setPhone(request.getPhone());
        if (request.getRole() != null) {
            targetUser.setRole(request.getRole());
        }
        if (request.getStatus() != null) {
            targetUser.setStatus(request.getStatus());
        }

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            targetUser.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        User updatedUser = userRepository.save(targetUser);

        // Update facility link if facilityId was provided
        if (request.getFacilityId() != null) {
            Facility facility = facilityRepository.findById(request.getFacilityId())
                    .orElseThrow(() -> new RuntimeException("Facility not found with id: " + request.getFacilityId()));

            List<UserFacilityLink> existingLinks = userFacilityLinkRepository.findByUserId(updatedUser.getId());
            UserFacilityLink link;
            if (!existingLinks.isEmpty()) {
                link = existingLinks.get(0);
            } else {
                link = new UserFacilityLink();
                link.setUser(updatedUser);
            }
            link.setFacility(facility);
            userFacilityLinkRepository.save(link);
        }

        Facility targetFacility = null;
        List<UserFacilityLink> links = userFacilityLinkRepository.findByUserId(updatedUser.getId());
        if (!links.isEmpty()) {
            targetFacility = links.get(0).getFacility();
        }

        if (targetFacility != null) {
            auditLogService.logAction(
                    targetFacility,
                    "Security & Access",
                    "USER_UPDATED",
                    "User Account Updated (" + updatedUser.getUsername() + ")",
                    AuditLog.Severity.INFO,
                    updatedUser.getUsername(),
                    updatedUser.getId(),
                    "Updated user account profile for @" + updatedUser.getUsername() + " (" + updatedUser.getName() + ").",
                    "Admin"
            );
        }

        return mapToResponseDto(updatedUser, "User updated successfully");
    }

    // 5. DELETE USER (SuperAdmin only)
    @Transactional
    @PreAuthorize("hasRole('SuperAdmin')")
    public void deleteUser(Long id) {
        User targetUser = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));

        // SuperAdmin accounts can never be deleted
        if (targetUser.getRole() == User.Role.SuperAdmin) {
            throw new AccessDeniedException("SuperAdmin accounts cannot be deleted");
        }

        Facility targetFacility = null;
        List<UserFacilityLink> links = userFacilityLinkRepository.findByUserId(targetUser.getId());
        if (!links.isEmpty()) {
            targetFacility = links.get(0).getFacility();
        }

        // Remove any facility links first
        userFacilityLinkRepository.deleteByUserId(id);
        userRepository.delete(targetUser);

        if (targetFacility != null) {
            auditLogService.logAction(
                    targetFacility,
                    "Security & Access",
                    "USER_DELETED",
                    "User Account Deleted (" + targetUser.getUsername() + ")",
                    AuditLog.Severity.WARNING,
                    targetUser.getUsername(),
                    targetUser.getId(),
                    "Deleted user account @" + targetUser.getUsername() + " (" + targetUser.getName() + ").",
                    "Admin"
            );
        }
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

    // Helper: Map User entity to UserResponseDto (queries facility link if present)
    private UserResponseDto mapToResponseDto(User user, String message) {
        List<UserFacilityLink> links = userFacilityLinkRepository.findByUserId(user.getId());
        Long facilityId = links.isEmpty() ? null : links.get(0).getFacility().getId();
        List<Long> assignedFacilityIds = links.stream()
                .map(link -> link.getFacility().getId())
                .collect(Collectors.toList());

        UserResponseDto dto = mapToResponseDto(user, facilityId, message);
        dto.setAssignedFacilityIds(assignedFacilityIds);
        return dto;
    }

    // Helper: Map User entity with known facilityId to UserResponseDto
    private UserResponseDto mapToResponseDto(User user, Long facilityId, String message) {
        List<Long> assignedFacilityIds = userFacilityLinkRepository.findByUserId(user.getId())
                .stream()
                .map(link -> link.getFacility().getId())
                .collect(Collectors.toList());

        UserResponseDto dto = new UserResponseDto(
                user.getId(),
                user.getName(),
                user.getUsername(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                user.getStatus(),
                facilityId,
                user.getCreatedAt(),
                message
        );
        dto.setAssignedFacilityIds(assignedFacilityIds);
        return dto;
    }
}
