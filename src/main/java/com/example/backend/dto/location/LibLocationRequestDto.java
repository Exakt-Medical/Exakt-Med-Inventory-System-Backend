package com.example.backend.dto.location;

import com.example.backend.entity.LibLocation.StorageType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LibLocationRequestDto {

    @Schema(description = "Location code (e.g. 'SHELF-A1', 'REF-01'). If left blank, it is auto-generated based on storageType.", example = "SHELF-A1")
    private String code;

    @Schema(description = "Storage condition / environmental type", example = "ROOM_TEMPERATURE")
    private StorageType storageType;

    @Schema(description = "Description or location details", example = "Dispensary counter - Top Shelf A-1")
    private String description;

    @Schema(description = "Optional facility ID to scope this location to a specific branch/facility")
    private Long facilityId;

    @Schema(description = "Whether the location is active for batch assignments", example = "true")
    private Boolean isActive;
}
