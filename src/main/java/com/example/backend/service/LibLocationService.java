package com.example.backend.service;

import com.example.backend.dto.location.LibLocationRequestDto;
import com.example.backend.dto.location.LibLocationResponseDto;
import com.example.backend.entity.Facility;
import com.example.backend.entity.LibLocation;
import com.example.backend.repository.BatchRepository;
import com.example.backend.repository.FacilityRepository;
import com.example.backend.repository.LibLocationRepository;
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
    private final FacilityRepository facilityRepository;
    private final BatchRepository batchRepository;

    public LibLocationService(LibLocationRepository libLocationRepository,
                              FacilityRepository facilityRepository,
                              @Lazy BatchRepository batchRepository) {
        this.libLocationRepository = libLocationRepository;
        this.facilityRepository = facilityRepository;
        this.batchRepository = batchRepository;
    }

    // 1. CREATE LOCATION (Option 1: Custom code with auto-generation fallback)
    @Transactional
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Pharmacist')")
    public LibLocationResponseDto createLocation(LibLocationRequestDto request) {
        if (request == null) {
            throw new RuntimeException("Request body cannot be null.");
        }

        Facility facility = null;
        if (request.getFacilityId() != null) {
            facility = facilityRepository.findById(request.getFacilityId())
                    .orElseThrow(() -> new RuntimeException("Facility not found with ID: " + request.getFacilityId()));
        }

        LibLocation.StorageType storageType = request.getStorageType() != null
                ? request.getStorageType()
                : LibLocation.StorageType.ROOM_TEMPERATURE;

        String code;
        if (request.getCode() != null && !request.getCode().trim().isEmpty()) {
            code = request.getCode().trim().toUpperCase();
            boolean exists = facility != null
                    ? libLocationRepository.existsByCodeIgnoreCaseAndFacilityId(code, facility.getId())
                    : libLocationRepository.existsByCodeIgnoreCase(code);
            if (exists) {
                throw new RuntimeException("Location code '" + code + "' already exists.");
            }
        } else {
            code = generateNextCode(storageType, facility != null ? facility.getId() : null);
        }

        LibLocation location = LibLocation.builder()
                .code(code)
                .storageType(storageType)
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .facility(facility)
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();

        LibLocation saved = libLocationRepository.save(location);
        return mapToDto(saved);
    }

    // 2. GET ALL LOCATIONS (Filtered by facility, active status, storage type)
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Pharmacist')")
    public List<LibLocationResponseDto> getAllLocations(Long facilityId, Boolean activeOnly, LibLocation.StorageType storageType) {
        List<LibLocation> locations;

        if (facilityId != null) {
            locations = (activeOnly != null && activeOnly)
                    ? libLocationRepository.findByFacilityIdAndIsActiveTrue(facilityId)
                    : libLocationRepository.findByFacilityId(facilityId);
        } else {
            locations = (activeOnly != null && activeOnly)
                    ? libLocationRepository.findByIsActiveTrue()
                    : libLocationRepository.findAll();
        }

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
        return mapToDto(location);
    }

    // 4. UPDATE LOCATION
    @Transactional
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Pharmacist')")
    public LibLocationResponseDto updateLocation(Long id, LibLocationRequestDto request) {
        LibLocation location = libLocationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Location not found with ID: " + id));

        Facility facility = location.getFacility();
        if (request.getFacilityId() != null) {
            facility = facilityRepository.findById(request.getFacilityId())
                    .orElseThrow(() -> new RuntimeException("Facility not found with ID: " + request.getFacilityId()));
            location.setFacility(facility);
        }

        if (request.getCode() != null && !request.getCode().trim().isEmpty()) {
            String newCode = request.getCode().trim().toUpperCase();
            if (!newCode.equalsIgnoreCase(location.getCode())) {
                boolean exists = facility != null
                        ? libLocationRepository.existsByCodeIgnoreCaseAndFacilityId(newCode, facility.getId())
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

        boolean isReferenced = batchRepository.existsByLocationId(id);
        if (isReferenced) {
            location.setIsActive(false);
            libLocationRepository.save(location);
            throw new RuntimeException("Location is in use by existing batches. It has been deactivated instead of deleted.");
        }

        libLocationRepository.delete(location);
    }

    // 6. GENERATE CODE HELPER
    public String generateNextCode(LibLocation.StorageType storageType, Long facilityId) {
        String prefix = resolvePrefix(storageType);
        List<String> existingCodes = facilityId != null
                ? libLocationRepository.findCodesByPrefixAndFacilityId(prefix, facilityId)
                : libLocationRepository.findCodesByPrefix(prefix);

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
                .facilityId(location.getFacility() != null ? location.getFacility().getId() : null)
                .facilityName(location.getFacility() != null ? location.getFacility().getName() : null)
                .isActive(location.getIsActive())
                .createdAt(location.getCreatedAt())
                .updatedAt(location.getUpdatedAt())
                .build();
    }
}
