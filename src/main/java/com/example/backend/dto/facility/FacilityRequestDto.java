package com.example.backend.dto.facility;

import com.example.backend.entity.Facility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FacilityRequestDto {

    @NotNull(message = "Project ID is required")
    private Long projectId;

    @NotBlank(message = "Facility Code is required")
    @Size(min = 1, max = 100, message = "Facility Code must be between 1 and 100 characters")
    private String facilityCode;

    @NotBlank(message = "Facility Name is required")
    @Size(min = 2, max = 100, message = "Facility Name must be between 2 and 100 characters")
    private String name;

    @NotBlank(message = "Address is required")
    private String address;

    private Facility.Status status;

}
