package com.example.backend.dto.facility;

import com.example.backend.entity.Facility;
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
public class FacilityResponseDto {

    private Long id;
    private Long projectId;
    private String projectName;
    private String facilityCode;
    private String name;
    private String contactPerson;
    private String email;
    private String phone;
    private String address;
    private Facility.Status status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String message;
    private List<Long> assignedUserIds;

    // Backward-compatible constructor for existing callers
    public FacilityResponseDto(Long id, Long projectId, String projectName, String facilityCode,
                               String name, String contactPerson, String email, String phone,
                               String address, Facility.Status status, LocalDateTime createdAt,
                               LocalDateTime updatedAt, String message) {
        this.id = id;
        this.projectId = projectId;
        this.projectName = projectName;
        this.facilityCode = facilityCode;
        this.name = name;
        this.contactPerson = contactPerson;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.message = message;
    }

}
