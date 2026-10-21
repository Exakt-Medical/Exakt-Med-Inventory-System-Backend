package com.example.backend.dto.location;

import com.example.backend.entity.LibLocation.StorageType;
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
public class LibLocationResponseDto {

    private Long id;
    private String code;
    private StorageType storageType;
    private String description;
    private Long facilityId;
    private String facilityName;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
