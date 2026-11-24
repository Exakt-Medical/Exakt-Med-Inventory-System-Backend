package com.example.backend.onerx.messaging.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OneRxDispenseAckDto {

    private String eventId;
    private String originalEventId;
    private String sourceSystem;
    private String eventType;
    private String rxNumber;
    private String pharmacyCode;
    private Integer totalUnitsDeducted;
    private String status;
    private String message;
    private String errorMessage;
    private String timestamp;
}
