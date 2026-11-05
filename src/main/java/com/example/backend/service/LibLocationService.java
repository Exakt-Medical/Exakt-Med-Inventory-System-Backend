package com.example.backend.service;

import com.example.backend.dto.location.LibLocationRequestDto;
import com.example.backend.dto.location.LibLocationResponseDto;
import com.example.backend.entity.LibLocation;
import com.example.backend.entity.Pharmacy;
import com.example.backend.repository.BatchRepository;
import com.example.backend.repository.LibLocationRepository;
import com.example.backend.repository.PharmacyRepository;
import com.example.backend.security.FacilityAccessGuard;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class LibLocationService {

    private final LibLocationRepository libLocationRepository;
    private final PharmacyRepository pharmacyRepository;
    private final BatchRepository batchRepository;
    private final FacilityAccessGuard facilityAccessGuard;

    public LibLocationService(LibLocationRepository libLocationRepository,
                              PharmacyRepository pharmacyRepository,
                              @Lazy BatchRepository batchRepository,
                              FacilityAccessGuard facilityAccessGuard) {
        this.libLocationRepository = libLocationRepository;
        this.pharmacyRepository = pharmacyRepository;
        this.batchRepository = batchRepository;
        this.facilityAccessGuard = facilityAccessGuard;
    }

    // 1. CREATE LOCATION (Option 1: Custom code with auto-generation fallback)
    @Transactional
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Pharmacist')")
    public LibLocationResponseDto createLocation(LibLocationRequestDto request) {
        if (request == null) {
            throw new RuntimeException("Request body cannot be null.");
        }

        if (request.getPharmacyId() == null) {
            throw new RuntimeException("Pharmacy ID is required.");
        }
        facilityAccessGuard.verifyPharmacyAccess(request.getPharmacyId());
        Pharmacy pharmacy = pharmacyRepository.findById(request.getPharmacyId())
                .orElseThrow(() -> new RuntimeException("Pharmacy not found with ID: " + request.getPharmacyId()));

        LibLocation.StorageType storageType = request.getStorageType() != null
                ? request.getStorageType()
                : LibLocation.StorageType.ROOM_TEMPERATURE;

        String code;
        if (request.getCode() != null && !request.getCode().trim().isEmpty()) {
            code = request.getCode().trim().toUpperCase();
            if (libLocationRepository.existsByCodeIgnoreCaseAndPharmacyId(code, pharmacy.getId())) {
                throw new RuntimeException("Location code '" + code + "' already exists in this pharmacy.");
            }
        } else {
            code = generateNextCode(storageType, pharmacy.getId());
        }

        LibLocation location = LibLocation.builder()
                .code(code)
                .storageType(storageType)
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .pharmacy(pharmacy)
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();

        LibLocation saved = libLocationRepository.save(location);
        return mapToDto(saved);
    }

    // 2. GET ALL LOCATIONS (Filtered by pharmacy, active status, storage type)
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Pharmacist')")
    public List<LibLocationResponseDto> getAllLocations(Long pharmacyId, Boolean activeOnly, LibLocation.StorageType storageType) {
        if (pharmacyId == null) {
            throw new RuntimeException("Pharmacy ID is required.");
        }
        facilityAccessGuard.verifyPharmacyAccess(pharmacyId);

        List<LibLocation> locations = (activeOnly != null && activeOnly)
                ? libLocationRepository.findByPharmacyIdAndIsActiveTrue(pharmacyId)
                : libLocationRepository.findByPharmacyId(pharmacyId);

        return locations.stream()
                .filter(loc -> storageType == null || loc.getStorageType() == storageType)
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    // 3. GET BY ID
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Pharmacist')")
    public LibLocationResponseDto getLocationById(Long id) {
        LibLocation location = libLocationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Location not found with ID: " + id));
        if (location.getPharmacy() != null) {
            facilityAccessGuard.verifyPharmacyAccess(location.getPharmacy().getId());
        }
        return mapToDto(location);
    }

    // 4. UPDATE LOCATION
    @Transactional
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Pharmacist')")
    public LibLocationResponseDto updateLocation(Long id, LibLocationRequestDto request) {
        LibLocation location = libLocationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Location not found with ID: " + id));
        if (location.getPharmacy() != null) {
            facilityAccessGuard.verifyPharmacyAccess(location.getPharmacy().getId());
        }

        Pharmacy pharmacy = location.getPharmacy();
        if (request.getPharmacyId() != null) {
            facilityAccessGuard.verifyPharmacyAccess(request.getPharmacyId());
            pharmacy = pharmacyRepository.findById(request.getPharmacyId())
                    .orElseThrow(() -> new RuntimeException("Pharmacy not found with ID: " + request.getPharmacyId()));
            location.setPharmacy(pharmacy);
        }

        if (request.getCode() != null && !request.getCode().trim().isEmpty()) {
            String newCode = request.getCode().trim().toUpperCase();
            if (!newCode.equalsIgnoreCase(location.getCode())) {
                boolean exists = pharmacy != null
                        ? libLocationRepository.existsByCodeIgnoreCaseAndPharmacyId(newCode, pharmacy.getId())
                        : libLocationRepository.existsByCodeIgnoreCase(newCode);
                if (exists) {
                    throw new RuntimeException("Location code '" + newCode + "' already exists.");
                }
                location.setCode(newCode);
            }
        }

        if (request.getStorageType() != null) {
            location.setStorageType(request.getStorageType());
        }

        if (request.getDescription() != null) {
            location.setDescription(request.getDescription().trim());
        }

        if (request.getIsActive() != null) {
            location.setIsActive(request.getIsActive());
        }

        LibLocation updated = libLocationRepository.save(location);
        return mapToDto(updated);
    }

    // 5. DELETE LOCATION (Safe delete: deactivates if referenced by batches)
    @Transactional
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Pharmacist')")
    public void deleteLocation(Long id) {
        LibLocation location = libLocationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Location not found with ID: " + id));
        if (location.getPharmacy() != null) {
            facilityAccessGuard.verifyPharmacyAccess(location.getPharmacy().getId());
        }

        boolean isReferenced = batchRepository.existsByLocationId(id);
        if (isReferenced) {
            location.setIsActive(false);
            libLocationRepository.save(location);
            throw new RuntimeException("Location is in use by existing batches. It has been deactivated instead of deleted.");
        }

        libLocationRepository.delete(location);
    }

    // 6. GENERATE CODE HELPER
    public String generateNextCode(LibLocation.StorageType storageType, Long pharmacyId) {
        if (pharmacyId == null) {
            throw new RuntimeException("Pharmacy ID is required to generate location code.");
        }
        facilityAccessGuard.verifyPharmacyAccess(pharmacyId);

        String prefix = resolvePrefix(storageType);
        List<String> existingCodes = libLocationRepository.findCodesByPrefixAndPharmacyId(prefix, pharmacyId);

        int maxSeq = 0;
        Pattern pattern = Pattern.compile("^" + Pattern.quote(prefix) + "-(\\d+)$", Pattern.CASE_INSENSITIVE);

        for (String code : existingCodes) {
            if (code != null) {
                Matcher matcher = pattern.matcher(code.trim());
                if (matcher.matches()) {
                    try {
                        int seq = Integer.parseInt(matcher.group(1));
                        if (seq > maxSeq) {
                            maxSeq = seq;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        return String.format("%s-%03d", prefix, maxSeq + 1);
    }

    private String resolvePrefix(LibLocation.StorageType storageType) {
        if (storageType == null) {
            return "LOC";
        }
        switch (storageType) {
            case ROOM_TEMPERATURE:
                return "SHF";
            case COLD_STORAGE:
                return "REF";
            case FREEZER:
                return "FRZ";
            case HAZARDOUS:
                return "HAZ";
            default:
                return "LOC";
        }
    }

    private LibLocationResponseDto mapToDto(LibLocation location) {
        return LibLocationResponseDto.builder()
                .id(location.getId())
                .code(location.getCode())
                .storageType(location.getStorageType())
                .description(location.getDescription())
                .pharmacyId(location.getPharmacy() != null ? location.getPharmacy().getId() : null)
                .pharmacyName(location.getPharmacy() != null ? location.getPharmacy().getPharmacyName() : null)
                .isActive(location.getIsActive())
                .createdAt(location.getCreatedAt())
                .updatedAt(location.getUpdatedAt())
                .build();
    }
}
