package com.example.backend.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponseDto {
    private Long facilityId;
    private String facilityName;
    private PharmacistDashboardResponseDto pharmacistDashboard;
    private ProcurementDashboardResponseDto procurementDashboard;
}
