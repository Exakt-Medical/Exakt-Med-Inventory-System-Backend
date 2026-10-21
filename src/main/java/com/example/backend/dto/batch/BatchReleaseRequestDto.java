package com.example.backend.dto.batch;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BatchReleaseRequestDto {

    @NotNull(message = "Accepted units is required")
    @Positive(message = "Accepted units must be greater than zero")
    private Long acceptedUnits;

    private String rejectionReason;

    private String notes;
}
