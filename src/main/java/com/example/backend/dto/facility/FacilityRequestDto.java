package com.example.backend.dto.facility;

import com.example.backend.entity.Facility;
import jakarta.validation.constraints.Email;
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

    @NotBlank(message = "Facility Name is required")
    @Size(min = 2, max = 100, message = "Facility Name must be between 2 and 100 characters")
    private String name;

    @NotBlank(message = "Contact Person is required")
    private String contactPerson;

    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    private String email;

    @NotBlank(message = "Phone number is required")
    private String phone;

    @NotBlank(message = "Address is required")
    private String address;

    private Facility.Status status;

}
