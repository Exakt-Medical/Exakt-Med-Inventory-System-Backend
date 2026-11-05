package com.example.backend.security;

import com.example.backend.entity.Pharmacy;
import com.example.backend.entity.User;
import com.example.backend.entity.UserFacilityLink;
import com.example.backend.entity.UserPharmacyLink;
import com.example.backend.repository.PharmacyRepository;
import com.example.backend.repository.UserFacilityLinkRepository;
import com.example.backend.repository.UserPharmacyLinkRepository;
import com.example.backend.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Enforces facility-level and pharmacy-level tenant isolation across services.
 *
 * Rules:
 * - SuperAdmin and OneRx API Key integration bypass user-facility and user-pharmacy link restrictions.
 * - Admin, Pharmacist, and Procurement users must have an active link in user_facility_links or user_pharmacy_links
 *   for the target facilityId / pharmacyId; otherwise AccessDeniedException (HTTP 403) is thrown.
 */
@Component
public class FacilityAccessGuard {

    private final UserRepository userRepository;
    private final UserFacilityLinkRepository userFacilityLinkRepository;
    private final UserPharmacyLinkRepository userPharmacyLinkRepository;
    private final PharmacyRepository pharmacyRepository;

    public FacilityAccessGuard(UserRepository userRepository,
                               UserFacilityLinkRepository userFacilityLinkRepository,
                               UserPharmacyLinkRepository userPharmacyLinkRepository,
                               PharmacyRepository pharmacyRepository) {
        this.userRepository = userRepository;
        this.userFacilityLinkRepository = userFacilityLinkRepository;
        this.userPharmacyLinkRepository = userPharmacyLinkRepository;
        this.pharmacyRepository = pharmacyRepository;
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

        boolean isAssignedToFacility = userFacilityLinkRepository.existsByUserIdAndFacilityId(user.getId(), facilityId);
        if (isAssignedToFacility) {
            return;
        }

        // Also allow if the user is assigned to any pharmacy inside this facility
        boolean isAssignedViaPharmacy = userPharmacyLinkRepository.findByUserId(user.getId())
                .stream()
                .anyMatch(link -> link.getPharmacy() != null
                        && link.getPharmacy().getFacility() != null
                        && facilityId.equals(link.getPharmacy().getFacility().getId()));

        if (!isAssignedViaPharmacy) {
            throw new AccessDeniedException("Access denied: You are not assigned to facility ID " + facilityId);
        }
    }

    /**
     * Validates that the currently authenticated user is authorized to access the given pharmacyId.
     */
    public void verifyPharmacyAccess(Long pharmacyId) {
        if (pharmacyId == null) {
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Unauthorized request");
        }

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

        boolean isAssignedToPharmacy = userPharmacyLinkRepository.existsByUserIdAndPharmacyId(user.getId(), pharmacyId);
        if (isAssignedToPharmacy) {
            return;
        }

        // Also allow if the user is assigned to the parent facility of this pharmacy
        Optional<Pharmacy> pharmacyOpt = pharmacyRepository.findById(pharmacyId);
        if (pharmacyOpt.isPresent() && pharmacyOpt.get().getFacility() != null) {
            Long parentFacilityId = pharmacyOpt.get().getFacility().getId();
            if (userFacilityLinkRepository.existsByUserIdAndFacilityId(user.getId(), parentFacilityId)) {
                return;
            }
        }

        throw new AccessDeniedException("Access denied: You are not assigned to pharmacy ID " + pharmacyId);
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

        Set<Long> facilityIds = userFacilityLinkRepository.findByUserId(user.getId())
                .stream()
                .map(link -> link.getFacility().getId())
                .collect(Collectors.toCollection(HashSet::new));

        // Include parent facilities of assigned pharmacies
        userPharmacyLinkRepository.findByUserId(user.getId())
                .stream()
                .filter(link -> link.getPharmacy() != null && link.getPharmacy().getFacility() != null)
                .forEach(link -> facilityIds.add(link.getPharmacy().getFacility().getId()));

        return facilityIds;
    }

    /**
     * Returns all pharmacy IDs assigned to the current user (used when pharmacyId parameter is optional).
     * Returns null if the user is a SuperAdmin (meaning unrestricted across all pharmacies).
     */
    public Set<Long> getAccessiblePharmacyIdsOrNullForSuperAdmin() {
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

        Set<Long> pharmacyIds = userPharmacyLinkRepository.findByUserId(user.getId())
                .stream()
                .map(link -> link.getPharmacy().getId())
                .collect(Collectors.toCollection(HashSet::new));

        // Include pharmacies under assigned facilities
        List<UserFacilityLink> facilityLinks = userFacilityLinkRepository.findByUserId(user.getId());
        for (UserFacilityLink fl : facilityLinks) {
            if (fl.getFacility() != null) {
                pharmacyRepository.findByFacilityId(fl.getFacility().getId())
                        .forEach(p -> pharmacyIds.add(p.getId()));
            }
        }

        return pharmacyIds;
    }
}
