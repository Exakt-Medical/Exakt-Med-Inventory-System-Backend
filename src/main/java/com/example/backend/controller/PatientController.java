package com.example.backend.controller;

import com.example.backend.dto.patient.PatientRequestDto;
import com.example.backend.dto.patient.PatientResponseDto;
import com.example.backend.service.PatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/patients")
@Tag(name = "Patients", description = "Endpoints for managing patients for medication dispensing and stock adjustments")
public class PatientController {

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    // 1. CREATE PATIENT
    @PostMapping
    @Operation(summary = "Create Patient", description = "Registers a new patient record scoped to a pharmacy.")
    public ResponseEntity<PatientResponseDto> createPatient(@Valid @RequestBody PatientRequestDto request) {
        PatientResponseDto response = patientService.createPatient(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // 2. GET ALL PATIENTS (Scoped to Pharmacy, optional search)
    @GetMapping
    @Operation(summary = "Get All Patients", description = "Retrieves all patients for a specified pharmacy, optionally filtered by name search.")
    public ResponseEntity<List<PatientResponseDto>> getPatients(
            @Parameter(description = "Pharmacy ID", required = true)
            @RequestParam Long pharmacyId,
            @Parameter(description = "Optional search query to filter patients by name")
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(patientService.getPatients(pharmacyId, search));
    }

    // 3. GET PATIENT BY ID
    @GetMapping("/{id}")
    @Operation(summary = "Get Patient by ID", description = "Retrieves details of a specific patient by ID.")
    public ResponseEntity<PatientResponseDto> getPatientById(
            @Parameter(description = "Patient ID", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(patientService.getPatientById(id));
    }

    // 4. UPDATE PATIENT
    @PutMapping("/{id}")
    @Operation(summary = "Update Patient", description = "Updates patient name or contact details.")
    public ResponseEntity<PatientResponseDto> updatePatient(
            @Parameter(description = "Patient ID", required = true)
            @PathVariable Long id,
            @Valid @RequestBody PatientRequestDto request) {
        return ResponseEntity.ok(patientService.updatePatient(id, request));
    }

    // 5. DELETE PATIENT
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete Patient", description = "Removes a patient record from the system.")
    public ResponseEntity<String> deletePatient(
            @Parameter(description = "Patient ID", required = true)
            @PathVariable Long id) {
        patientService.deletePatient(id);
        return ResponseEntity.ok("Patient deleted successfully");
    }
}

