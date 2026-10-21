package com.example.backend.dto.supplier;

import com.example.backend.entity.Supplier;
import jakarta.validation.constraints.Email;
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
public class SupplierRequestDto {

    @NotNull(message = "Facility ID is required")
    private Long facilityId;

    @NotBlank(message = "Supplier name is required")
    @Size(min = 2, max = 150, message = "Supplier name must be between 2 and 150 characters")
    private String name;

    @NotBlank(message = "Contact person is required")
    private String contactPerson;

    @NotBlank(message = "Email address is required")
    @Email(message = "Please enter a valid email address")
    private String email;

    private String phone;

    private String address;

    @NotBlank(message = "Payment terms are required")
    private String paymentTerms;

    private Supplier.Status status;

}
