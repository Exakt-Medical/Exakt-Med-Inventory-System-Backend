package com.example.backend.onerx.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OneRxBrandResponseDto {

    @JsonProperty("drug_code")
    private String drugCode;

    @JsonProperty("drug_name")
    private String drugName;

    @JsonProperty("facility_code")
    private String facilityCode;

    @JsonProperty("SKU")
    @Builder.Default
    private List<SkuBrandItemDto> sku = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SkuBrandItemDto {

        @JsonProperty("name")
        private String name;

        @JsonProperty("brandName")
        private String brandName;

        @JsonProperty("quantity")
        private Long quantity;

        @JsonProperty("packagingUnit")
        private String packagingUnit;

        @JsonProperty("dosageForm")
        private String dosageForm;

        @JsonProperty("dosageStrength")
        private String dosageStrength;
    }
}
