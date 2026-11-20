package com.example.backend.onerx.messaging.dto;

import com.example.backend.onerx.dto.OneRxDispenseRequestDto;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OneRxDispenseMessageDto {

    @JsonProperty("eventId")
    @JsonAlias({"event_id"})
    private String eventId;

    @JsonProperty("sourceSystem")
    @JsonAlias({"source_system"})
    private String sourceSystem;

    @JsonProperty("eventType")
    @JsonAlias({"event_type"})
    private String eventType;

    private String event;

    @JsonProperty("rx_number")
    @JsonAlias({"rxNumber", "reference_number", "referenceNumber"})
    private String rxNumber;

    @JsonProperty("pharmacy_code")
    @JsonAlias({"pharmacyCode", "pharmacyId", "facility_code", "facilityCode"})
    private String pharmacyCode;

    @JsonProperty("dispensedBy")
    @JsonAlias({"dispensed_by"})
    private String dispensedBy;

    @JsonProperty("patientName")
    @JsonAlias({"patient_name"})
    private String patientName;

    @JsonProperty("contact_number")
    @JsonAlias({"contactNumber", "phone_number", "patientContact"})
    private String contactNumber;

    @JsonProperty("dispensedAt")
    @JsonAlias({"dispensed_at"})
    private String dispensedAt;

    private String notes;

    private List<MedicineItem> medicines;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MedicineItem {

        @JsonProperty("drugCode")
        @JsonAlias({"drug_code"})
        private String drugCode;

        @JsonProperty("dosageForm")
        @JsonAlias({"dosage_form"})
        private String dosageForm;

        @JsonProperty("drug_description")
        @JsonAlias({"drugDescription"})
        private String drugDescription;

        private List<BrandItem> brands;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BrandItem {

        @JsonProperty("skuName")
        @JsonAlias({"sku_name", "name"})
        private String skuName;

        @JsonProperty("brandName")
        @JsonAlias({"brand_name"})
        private String brandName;

        @JsonProperty("dispensedUnits")
        @JsonAlias({"dispensed_units", "units"})
        private Long dispensedUnits;
    }

    /**
     * Converts the inbound RabbitMQ payload into the standard OneRxDispenseRequestDto
     * so we reuse OneRxDispenseService's atomic FEFO deduction and ledger logic.
     */
    public OneRxDispenseRequestDto toDispenseRequestDto() {
        List<OneRxDispenseRequestDto.MedicineDispenseDto> mappedMedicines = medicines == null
                ? Collections.emptyList()
                : medicines.stream()
                .map(med -> OneRxDispenseRequestDto.MedicineDispenseDto.builder()
                        .drugCode(med.getDrugCode())
                        .drugDescription(med.getDrugDescription())
                        .dosageForm(med.getDosageForm())
                        .brands(med.getBrands() == null
                                ? Collections.emptyList()
                                : med.getBrands().stream()
                                .map(brand -> OneRxDispenseRequestDto.BrandDispenseDto.builder()
                                        .skuName(brand.getSkuName())
                                        .brandName(brand.getBrandName())
                                        .dispensedUnits(brand.getDispensedUnits())
                                        .build())
                                .collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());

        return OneRxDispenseRequestDto.builder()
                .event(event != null ? event : eventType)
                .rxNumber(rxNumber)
                .pharmacyCode(pharmacyCode)
                .dispensedBy(dispensedBy)
                .patientName(patientName)
                .contactNumber(contactNumber)
                .dispensedAt(dispensedAt)
                .notes(notes)
                .medicines(mappedMedicines)
                .build();
    }
}
