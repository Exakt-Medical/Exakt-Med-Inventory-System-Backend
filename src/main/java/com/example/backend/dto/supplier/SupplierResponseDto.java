package com.example.backend.dto.supplier;

import com.example.backend.entity.Supplier;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SupplierResponseDto {

    private Long id;
    private Long facilityId;
    private String facilityName;
    private String name;
    private String contactPerson;
    private String email;
    private String phone;
    private String address;
    private String paymentTerms;
    private Supplier.Status status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String message;

}
