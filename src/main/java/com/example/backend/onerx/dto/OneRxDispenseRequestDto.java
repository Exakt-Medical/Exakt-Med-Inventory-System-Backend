package com.example.backend.onerx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OneRxDispenseRequestDto {

    private String event;

    @NotBlank(message = "Field 'rx_number' is required.")
    @JsonProperty("rx_number")
    private String rxNumber;

    @NotBlank(message = "Field 'facility_code' is required.")
    @JsonProperty("facility_code")
    private String facilityCode;

    private String dispensedBy;

    private String patientName;

    @NotEmpty(message = "Medicines list cannot be empty.")
    @Valid
    private List<MedicineDispenseDto> medicines;

    private String dispensedAt;

    private String notes;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MedicineDispenseDto {

        private String drugCode;

        @JsonProperty("drug_description")
        private String drugDescription;

        private String dosageForm;

        @NotEmpty(message = "Brands list cannot be empty.")
        @Valid
        private List<BrandDispenseDto> brands;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BrandDispenseDto {

        @NotNull(message = "SKU name is required.")
        private String skuName;

        private String brandName;

        @NotNull(message = "Dispensed units is required.")
        private Long dispensedUnits;
    }
}
