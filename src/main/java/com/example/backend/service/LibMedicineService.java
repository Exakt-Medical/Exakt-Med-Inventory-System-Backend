package com.example.backend.service;

import com.example.backend.dto.medicine.LibMedicineResponseDto;
import com.example.backend.entity.LibMedicine;
import com.example.backend.repository.LibMedicineRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class LibMedicineService {

    private final LibMedicineRepository libMedicineRepository;

    public LibMedicineService(LibMedicineRepository libMedicineRepository) {
        this.libMedicineRepository = libMedicineRepository;
    }

    // 1. SEARCH MEDICINES (Paginated - for tables, lists, or infinite scroll)
    @PreAuthorize("isAuthenticated()")
    public Page<LibMedicineResponseDto> searchMedicines(String search, Pageable pageable) {
        String query = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        return libMedicineRepository.searchMedicines(query, pageable)
                .map(this::mapToResponseDto);
    }

    // 2. DROPDOWN SEARCH (Fast limited results - for dropdowns and autocomplete selects)
    @PreAuthorize("isAuthenticated()")
    public List<LibMedicineResponseDto> getDropdownMedicines(String search, int limit) {
        int effectiveLimit = (limit <= 0) ? 50 : Math.min(limit, 200);
        Pageable pageable = PageRequest.of(0, effectiveLimit, Sort.by("drugDescription").ascending());
        String query = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        return libMedicineRepository.searchMedicines(query, pageable)
                .map(this::mapToResponseDto)
                .getContent();
    }

    // 3. GET MEDICINE BY ID
    @PreAuthorize("isAuthenticated()")
    public LibMedicineResponseDto getMedicineById(Long id) {
        LibMedicine medicine = libMedicineRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Medicine not found with id: " + id));
        return mapToResponseDto(medicine);
    }

    // 4. GET MEDICINE BY DRUG CODE
    @PreAuthorize("isAuthenticated()")
    public LibMedicineResponseDto getMedicineByDrugCode(String drugCode) {
        LibMedicine medicine = libMedicineRepository.findByDrugCode(drugCode)
                .orElseThrow(() -> new RuntimeException("Medicine not found with drug code: " + drugCode));
        return mapToResponseDto(medicine);
    }

    // Helper: Map Entity to Response DTO
    private LibMedicineResponseDto mapToResponseDto(LibMedicine medicine) {
        return new LibMedicineResponseDto(
                medicine.getId(),
                medicine.getDrugCode(),
                medicine.getDrugDescription(),
                medicine.getGenCode(),
                medicine.getSaltCode(),
                medicine.getFormCode(),
                medicine.getStrengthCode(),
                medicine.getUnitCode(),
                medicine.getPackageCode()
        );
    }
}
