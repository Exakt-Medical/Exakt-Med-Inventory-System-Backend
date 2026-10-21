package com.example.backend.dto.restockRequest;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RestockRequestDto {

    @NotNull(message = "SKU ID is required")
    private Long skuId;

    @NotNull(message = "Facility ID is required")
    private Long facilityId;

    private Long userId;

    @NotNull(message = "Requested units is required")
    @Min(value = 1, message = "Requested units must be at least 1")
    private Long requestedUnits;

    private String reason;
}
