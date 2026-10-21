package com.example.backend.dto.restockRequest;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RestockResponseDto {

    private Long id;
    private Long facilityId;
    private String facilityName;
    private Long skuId;
    private String skuName;
    private String brandName;
    private String genericName;
    private String dosageForm;
    private String packagingUnit;
    private Long userId;
    private String userName;
    private Long requestedUnits;
    private String reason;
    private Long orderId;
    private String purchaseOrderNum;
    private Long orderedItemId;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String message;
}
