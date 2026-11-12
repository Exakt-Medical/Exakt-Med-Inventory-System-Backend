package com.example.backend.dto.reorderQuotation;

import jakarta.validation.constraints.NotBlank;
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
public class RejectQuotationRequestDto {

    @NotBlank(message = "Rejection reason is required")
    private String rejectionReason;
}

