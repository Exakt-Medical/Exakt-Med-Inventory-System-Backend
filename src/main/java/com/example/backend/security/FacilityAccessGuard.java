package com.example.backend.security;

import com.example.backend.entity.User;
import com.example.backend.entity.UserFacilityLink;
import com.example.backend.repository.UserFacilityLinkRepository;
import com.example.backend.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Enforces facility-level tenant isolation across services.
 *
 * Rules:
 * - SuperAdmin and OneRx API Key integration bypass user-facility link restrictions.
 * - Admin, Pharmacist, and Procurement users must have an active link in user_facility_links
 *   for the target facilityId; otherwise AccessDeniedException (HTTP 403) is thrown.
 */
@Component
public class FacilityAccessGuard {

    private final UserRepository userRepository;
    private final UserFacilityLinkRepository userFacilityLinkRepository;

    public FacilityAccessGuard(UserRepository userRepository,
                               UserFacilityLinkRepository userFacilityLinkRepository) {
        this.userRepository = userRepository;
        this.userFacilityLinkRepository = userFacilityLinkRepository;
    }

    /**
     * Validates that the currently authenticated user is authorized to access the given facilityId.
     */
    public void verifyFacilityAccess(Long facilityId) {
        if (facilityId == null) {
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Unauthorized request");
        }

        // 1. Check if authenticated via OneRx API Key or SuperAdmin authority
        for (GrantedAuthority authority : auth.getAuthorities()) {
            String role = authority.getAuthority();
            if ("ROLE_SuperAdmin".equals(role) || "ROLE_ONERX_INTEGRATION".equals(role)) {
                return;
            }
        }

        String username = auth.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AccessDeniedException("Authenticated user not found"));

        if (!Boolean.TRUE.equals(user.getStatus())) {
            throw new AccessDeniedException("User account is deactivated");
        }

        if (user.getRole() == User.Role.SuperAdmin) {
            return;
        }

        boolean isAssigned = userFacilityLinkRepository.existsByUserIdAndFacilityId(user.getId(), facilityId);
        if (!isAssigned) {
            throw new AccessDeniedException("Access denied: You are not assigned to facility ID " + facilityId);
        }
    }

    /**
     * Returns all facility IDs assigned to the current user (used when facilityId parameter is optional).
     * Returns null if the user is a SuperAdmin (meaning unrestricted across all facilities).
     */
    public Set<Long> getAccessibleFacilityIdsOrNullForSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Unauthorized request");
        }

        for (GrantedAuthority authority : auth.getAuthorities()) {
            String role = authority.getAuthority();
            if ("ROLE_SuperAdmin".equals(role) || "ROLE_ONERX_INTEGRATION".equals(role)) {
                return null;
            }
        }

        User user = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new AccessDeniedException("Authenticated user not found"));

        if (user.getRole() == User.Role.SuperAdmin) {
            return null;
        }

        return userFacilityLinkRepository.findByUserId(user.getId())
                .stream()
                .map(link -> link.getFacility().getId())
                .collect(Collectors.toSet());
    }
}

