package com.example.backend.onerx.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
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
    @JsonAlias({"rxNumber"})
    private String rxNumber;

    @NotBlank(message = "Field 'pharmacy_code' is required.")
    @JsonProperty("pharmacy_code")
    @JsonAlias({"pharmacyCode", "pharmacyId"})
    private String pharmacyCode;

    @JsonProperty("dispensed_by")
    @JsonAlias({"dispensedBy"})
    private String dispensedBy;

    @JsonProperty("patient_name")
    @JsonAlias({"patientName"})
    private String patientName;

    @JsonProperty("contact_number")
    @JsonAlias({"contactNumber", "patientContact"})
    private String contactNumber;

    @NotEmpty(message = "Medicines list cannot be empty.")
    @Valid
    private List<MedicineDispenseDto> medicines;

    @JsonProperty("dispensed_at")
    @JsonAlias({"dispensedAt"})
    private String dispensedAt;

    private String notes;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MedicineDispenseDto {

        @JsonProperty("drug_code")
        @JsonAlias({"drugCode"})
        private String drugCode;

        @JsonProperty("drug_description")
        @JsonAlias({"drugDescription"})
        private String drugDescription;

        @JsonProperty("dosage_form")
        @JsonAlias({"dosageForm"})
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
        @JsonProperty("sku_name")
        @JsonAlias({"skuName", "name"})
        private String skuName;

        @JsonProperty("brand_name")
        @JsonAlias({"brandName"})
        private String brandName;

        @NotNull(message = "Dispensed units is required.")
        @JsonProperty("dispensed_units")
        @JsonAlias({"dispensedUnits", "units"})
        private Long dispensedUnits;
    }
}
