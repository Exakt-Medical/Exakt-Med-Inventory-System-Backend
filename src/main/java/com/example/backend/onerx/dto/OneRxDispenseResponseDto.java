package com.example.backend.onerx.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OneRxDispenseResponseDto {

    private String status;
    private String referenceNumber;
    private Integer processedSkus;
    private Integer processedSkuBrands;
    private Long totalUnitsDeducted;
    private LocalDateTime processedAt;

    private String reason;
    private String error;
}
