package com.example.backend.dto.pharmacy;

import com.example.backend.entity.Pharmacy;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PharmacyResponseDto {

    private Long id;
    private Long facilityId;
    private String facilityName;
    private String pharmacyCode;
    private String pharmacyName;
    private Long lguId;
    private String address;
    private String licenseNo;
    private Pharmacy.Status status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String message;
    private List<Long> assignedUserIds;

    public PharmacyResponseDto(Long id, Long facilityId, String facilityName, String pharmacyCode,
                               String pharmacyName, Long lguId, String address, String licenseNo,
                               Pharmacy.Status status, LocalDateTime createdAt, LocalDateTime updatedAt,
                               String message) {
        this.id = id;
        this.facilityId = facilityId;
        this.facilityName = facilityName;
        this.pharmacyCode = pharmacyCode;
        this.pharmacyName = pharmacyName;
        this.lguId = lguId;
        this.address = address;
        this.licenseNo = licenseNo;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.message = message;
    }

}

