package com.example.backend.dto.dispensed;

import com.example.backend.entity.DispensedLog;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DispensedLogResponseDto {

    private Long id;
    private Long skuId;
    private String skuName;
    private String brandName;
    private String genericName;
    private Long facilityId;
    private String facilityName;
    private String patientName;
    private String contactNumber;
    private Long unitsDispensed;
    private DispensedLog.Status status;
    private LocalDateTime dispensedAt;
    private LocalDateTime receivedAt;
}
