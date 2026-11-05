package com.example.backend.dto.pharmacy;

import com.example.backend.entity.Pharmacy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PharmacyRequestDto {

    @NotNull(message = "Facility ID is required")
    private Long facilityId;

    @NotBlank(message = "Pharmacy Code is required")
    @Size(min = 1, max = 100, message = "Pharmacy Code must be between 1 and 100 characters")
    private String pharmacyCode;

    @NotBlank(message = "Pharmacy Name is required")
    @Size(min = 2, max = 150, message = "Pharmacy Name must be between 2 and 150 characters")
    private String pharmacyName;

    private Long lguId;

    @NotBlank(message = "Address is required")
    private String address;

    private String licenseNo;

    private Pharmacy.Status status;

}

