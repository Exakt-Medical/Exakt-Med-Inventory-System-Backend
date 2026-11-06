package com.example.backend.dto.patient;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientRequestDto {

    @NotNull(message = "Pharmacy ID is required")
    @Schema(description = "Pharmacy ID to scope this patient to a specific pharmacy", example = "1")
    private Long pharmacyId;

    @NotBlank(message = "Patient name is required")
    @Size(min = 2, max = 255, message = "Patient name must be between 2 and 255 characters")
    @Schema(description = "Full name of the patient", example = "Juan Dela Cruz")
    private String name;

    @Schema(description = "Contact phone or mobile number of the patient", example = "09171234567")
    private String contactNumber;
}

