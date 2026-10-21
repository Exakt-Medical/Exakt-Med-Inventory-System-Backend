package com.example.backend.dto.batch;

import com.example.backend.entity.Batch;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BatchRequestDto {

    private Long facilityId;

    @NotNull(message = "Ordered item ID is required")
    private Long orderedItemId;

    @NotBlank(message = "Batch number is required")
    private String batchNum;

    private Long units;

    @NotNull(message = "Manufacture date is required")
    private LocalDate manufactureDate;

    @NotNull(message = "Expiry date is required")
    private LocalDate expiryDate;

    private Batch.Status status = Batch.Status.Available;

    @NotNull(message = "Storage location ID is required")
    private Long locationId;

    private String notes;
}
