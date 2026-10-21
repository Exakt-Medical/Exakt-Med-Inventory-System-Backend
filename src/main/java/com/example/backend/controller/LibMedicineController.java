package com.example.backend.controller;

import com.example.backend.dto.medicine.LibMedicineResponseDto;
import com.example.backend.service.LibMedicineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/medicines", "/api/lib-medicines"})
@Tag(name = "Medicine Library", description = "Endpoints for the standardized reference catalog of medicines and pharmaceuticals")
public class LibMedicineController {

    private final LibMedicineService libMedicineService;

    public LibMedicineController(LibMedicineService libMedicineService) {
        this.libMedicineService = libMedicineService;
    }

    // 1. GET ALL / SEARCH MEDICINES (Paginated for tables or infinite scroll)
    @GetMapping
    @Operation(summary = "Search Medicines (Paginated)", description = "Searches reference library medicines with pagination and sorting.")
    public ResponseEntity<Page<LibMedicineResponseDto>> searchMedicines(
            @Parameter(description = "Optional search query (drug description or category)")
            @RequestParam(required = false) String search,
            @Parameter(description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Field to sort by")
            @RequestParam(defaultValue = "drugDescription") String sortBy,
            @Parameter(description = "Sort direction (asc / desc)")
            @RequestParam(defaultValue = "asc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok(libMedicineService.searchMedicines(search, pageable));
    }

    // 2. SEARCH & AUTOCOMPLETE (Fast limited list for frontend dropdown selects)
    @GetMapping("/search")
    @Operation(summary = "Medicine Autocomplete", description = "Returns a quick list of matching medicines for frontend auto-suggest dropdowns.")
    public ResponseEntity<List<LibMedicineResponseDto>> getDropdownMedicines(
            @Parameter(description = "Optional search keyword")
            @RequestParam(required = false) String search,
            @Parameter(description = "Maximum results to return")
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(libMedicineService.getDropdownMedicines(search, limit));
    }

    // 3. GET MEDICINE BY ID
    @GetMapping("/{id}")
    @Operation(summary = "Get Medicine by ID", description = "Retrieves full details of a specific medicine from the library catalog.")
    public ResponseEntity<LibMedicineResponseDto> getMedicineById(
            @Parameter(description = "Medicine ID", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(libMedicineService.getMedicineById(id));
    }
}
