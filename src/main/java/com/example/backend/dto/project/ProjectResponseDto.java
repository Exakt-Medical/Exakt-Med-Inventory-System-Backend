package com.example.backend.dto.project;

import com.example.backend.dto.facility.FacilityResponseDto;
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
public class ProjectResponseDto {
    private Long id;
    private String name;
    private String projectCode;
    private List<FacilityResponseDto> facilities;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String message;
}
