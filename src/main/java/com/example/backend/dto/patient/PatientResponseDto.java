package com.example.backend.dto.patient;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientResponseDto {

    private Long id;
    private Long pharmacyId;
    private String pharmacyName;
    private String name;
    private String contactNumber;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

