package com.example.backend.controller;

import com.example.backend.dto.dashboard.DashboardResponseDto;
import com.example.backend.dto.dashboard.PharmacistDashboardResponseDto;
import com.example.backend.dto.dashboard.ProcurementDashboardResponseDto;
import com.example.backend.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "Real-time metrics, inventory alerts, stock distributions, and purchase order tracking")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    // 1. GET PHARMACIST DASHBOARD METRICS & REAL-TIME DATA
    @GetMapping("/pharmacist")
    @Operation(summary = "Get Pharmacist Dashboard Data", description = "Retrieves real-time pharmacy metrics including total inventory units, critically low stock count, near expiry count, unverified stock, category distributions, health bars, and urgent alerts.")
    public ResponseEntity<PharmacistDashboardResponseDto> getPharmacistDashboard(
            @Parameter(description = "Pharmacy ID", required = true)
            @RequestParam Long pharmacyId) {
        PharmacistDashboardResponseDto response = dashboardService.getPharmacistDashboard(pharmacyId);
        return ResponseEntity.ok(response);
    }

    // 2. GET PROCUREMENT OFFICER DASHBOARD METRICS & REAL-TIME DATA
    @GetMapping("/procurement")
    @Operation(summary = "Get Procurement Dashboard Data", description = "Retrieves real-time procurement KPIs including pending requests, low stock alert count, POs in transit, monthly spend, priority restock SKUs, and recent order requests.")
    public ResponseEntity<ProcurementDashboardResponseDto> getProcurementDashboard(
            @Parameter(description = "Pharmacy ID", required = true)
            @RequestParam Long pharmacyId) {
        ProcurementDashboardResponseDto response = dashboardService.getProcurementDashboard(pharmacyId);
        return ResponseEntity.ok(response);
    }

    // 3. GET FULL SYSTEM / COMBINED DASHBOARD SUMMARY
    @GetMapping("/summary")
    @Operation(summary = "Get Combined Dashboard Summary", description = "Retrieves both Pharmacist and Procurement dashboard datasets in a single response.")
    public ResponseEntity<DashboardResponseDto> getDashboardSummary(
            @Parameter(description = "Pharmacy ID", required = true)
            @RequestParam Long pharmacyId) {
        DashboardResponseDto response = dashboardService.getDashboardSummary(pharmacyId);
        return ResponseEntity.ok(response);
    }
}
