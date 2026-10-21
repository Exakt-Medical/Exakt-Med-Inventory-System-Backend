package com.example.backend.dto.batch;

import com.example.backend.entity.Batch;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BatchResponseDto {

    private Long id;
    private String batchNum;
    private Long facilityId;
    private String facilityName;
    private Long orderedItemId;
    private Long orderId;
    private String poNumber;
    private String supplierName;
    private Long skuId;
    private String skuName;
    private String brandName;
    private String genericName;
    private String dosageForm;
    private String packagingUnit;
    private Long quantity;
    private Long units;
    private LocalDate manufactureDate;
    private LocalDate expiryDate;
    private Batch.Status status;
    private String notes;
    private Boolean skuDeducted;
    private LocalDateTime receivedAt;
    private Long locationId;
    private String locationCode;
    private com.example.backend.entity.LibLocation.StorageType locationStorageType;
}
