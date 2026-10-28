package com.example.backend.service;

import com.example.backend.dto.dashboard.DashboardResponseDto;
import com.example.backend.dto.dashboard.PharmacistDashboardResponseDto;
import com.example.backend.dto.dashboard.ProcurementDashboardResponseDto;
import com.example.backend.entity.*;
import com.example.backend.repository.*;
import com.example.backend.security.FacilityAccessGuard;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final SkuRepository skuRepository;
    private final BatchRepository batchRepository;
    private final OrderRepository orderRepository;
    private final OrderedItemRepository orderedItemRepository;
    private final SupplierRepository supplierRepository;
    private final FacilityRepository facilityRepository;
    private final RestockRequestRepository restockRequestRepository;
    private final FacilityAccessGuard facilityAccessGuard;

    private static final String[] PALETTE = {
            "#3B82F6", "#10B981", "#8B5CF6", "#F59E0B",
            "#06B6D4", "#EC4899", "#6366F1", "#14B8A6",
            "#F97316", "#84CC16"
    };

    public DashboardService(SkuRepository skuRepository,
                            BatchRepository batchRepository,
                            OrderRepository orderRepository,
                            OrderedItemRepository orderedItemRepository,
                            SupplierRepository supplierRepository,
                            FacilityRepository facilityRepository,
                            RestockRequestRepository restockRequestRepository,
                            FacilityAccessGuard facilityAccessGuard) {
        this.skuRepository = skuRepository;
        this.batchRepository = batchRepository;
        this.orderRepository = orderRepository;
        this.orderedItemRepository = orderedItemRepository;
        this.supplierRepository = supplierRepository;
        this.facilityRepository = facilityRepository;
        this.restockRequestRepository = restockRequestRepository;
        this.facilityAccessGuard = facilityAccessGuard;
    }

    // 1. GET PHARMACIST DASHBOARD
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Pharmacist')")
    public PharmacistDashboardResponseDto getPharmacistDashboard(Long facilityId) {
        facilityAccessGuard.verifyFacilityAccess(facilityId);
        String facilityName = resolveFacilityName(facilityId);
        List<Sku> skus = fetchSkus(facilityId);
        List<Batch> batches = fetchBatches(facilityId);

        LocalDate today = LocalDate.now();
        LocalDate nearExpiryLimit = today.plusDays(90);

        // 1. Compute Metrics
        long totalSkus = skus.size();
        long totalStockQuantity = skus.stream()
                .mapToLong(s -> s.getUnits() != null ? s.getUnits() : 0L)
                .sum();
        long totalLowStock = skus.stream()
                .filter(s -> s.getUnits() != null && s.getMinimumLevel() != null &&
                        s.getUnits() <= s.getMinimumLevel() && s.getUnits() > 0)
                .count();
        long totalOverStock = skus.stream()
                .filter(s -> s.getUnits() != null && s.getMaximumLevel() != null &&
                        s.getUnits() >= s.getMaximumLevel())
                .count();
        long totalExpiry = batches.stream()
                .filter(b -> b.getExpiryDate() != null && b.getExpiryDate().isBefore(today))
                .count();
        long totalNearExpiry = batches.stream()
                .filter(b -> b.getExpiryDate() != null &&
                        !b.getExpiryDate().isBefore(today) &&
                        !b.getExpiryDate().isAfter(nearExpiryLimit))
                .count();
        long totalBatches = batches.size();
        long totalQuarantined = batches.stream()
                .filter(b -> b.getStatus() == Batch.Status.Quarantined)
                .count();

        PharmacistDashboardResponseDto.Metrics metrics = PharmacistDashboardResponseDto.Metrics.builder()
                .totalSkus(totalSkus)
                .totalStockQuantity(totalStockQuantity)
                .totalLowStock(totalLowStock)
                .totalOverStock(totalOverStock)
                .totalExpiry(totalExpiry)
                .totalNearExpiry(totalNearExpiry)
                .totalBatches(totalBatches)
                .totalQuarantined(totalQuarantined)
                .build();

        // 2. Stock Distribution by Category / Dosage Form
        Map<String, Long> categoryStockMap = skus.stream()
                .collect(Collectors.groupingBy(
                        this::getCategoryName,
                        Collectors.summingLong(s -> s.getUnits() != null ? s.getUnits() : 0L)
                ));

        List<PharmacistDashboardResponseDto.CategoryStockDistribution> stockDistribution = new ArrayList<>();
        int colorIdx = 0;
        for (Map.Entry<String, Long> entry : categoryStockMap.entrySet()) {
            stockDistribution.add(PharmacistDashboardResponseDto.CategoryStockDistribution.builder()
                    .name(entry.getKey())
                    .value(entry.getValue())
                    .color(PALETTE[colorIdx % PALETTE.length])
                    .build());  
            colorIdx++;
        }
        stockDistribution.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

        // 3. Category Health Bar Data
        Map<String, List<Sku>> categorySkusMap = skus.stream()
                .collect(Collectors.groupingBy(this::getCategoryName));

        List<PharmacistDashboardResponseDto.CategoryHealthBar> healthBarData = new ArrayList<>();
        for (Map.Entry<String, List<Sku>> entry : categorySkusMap.entrySet()) {
            String category = entry.getKey();
            List<Sku> catSkus = entry.getValue();

            long low = catSkus.stream()
                    .filter(s -> s.getUnits() != null && s.getMinimumLevel() != null &&
                            s.getUnits() <= s.getMinimumLevel() && s.getUnits() > 0)
                    .count();
            long over = catSkus.stream()
                    .filter(s -> s.getUnits() != null && s.getMaximumLevel() != null &&
                            s.getUnits() >= s.getMaximumLevel())
                    .count();
            long out = catSkus.stream()
                    .filter(s -> s.getUnits() == null || s.getUnits() <= 0)
                    .count();
            long optimal = catSkus.stream()
                    .filter(s -> s.getUnits() != null && s.getMinimumLevel() != null && s.getMaximumLevel() != null &&
                            s.getUnits() > s.getMinimumLevel() && s.getUnits() < s.getMaximumLevel())
                    .count();

            healthBarData.add(PharmacistDashboardResponseDto.CategoryHealthBar.builder()
                    .category(category)
                    .lowStock(low)
                    .overStock(over)
                    .outOfStock(out)
                    .optimal(optimal)
                    .build());
        }

        // 4. Urgent Operational Alerts
        List<PharmacistDashboardResponseDto.UrgentAlert> urgentAlerts = generatePharmacistAlerts(batches, skus, today, totalExpiry, totalNearExpiry, totalQuarantined, totalLowStock);

        return PharmacistDashboardResponseDto.builder()
                .facilityId(facilityId)
                .facilityName(facilityName)
                .metrics(metrics)
                .skuStockDistribution(stockDistribution)
                .skuHealthBarData(healthBarData)
                .urgentAlerts(urgentAlerts)
                .build();
    }

    // 2. GET PROCUREMENT DASHBOARD
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Procurement')")
    public ProcurementDashboardResponseDto getProcurementDashboard(Long facilityId) {
        facilityAccessGuard.verifyFacilityAccess(facilityId);
        String facilityName = resolveFacilityName(facilityId);
        List<Sku> skus = fetchSkus(facilityId);
        List<Batch> batches = fetchBatches(facilityId);
        List<Order> orders = fetchOrders(facilityId);
        List<Supplier> suppliers = fetchSuppliers(facilityId);

        LocalDate today = LocalDate.now();

        // Collect SKUs that already have open/in-progress orders or restock requests
        // (Pending, Approved, Partially_Received) to prevent duplicate requisitions
        Set<Long> skusWithActiveOrders = new HashSet<>();

        List<OrderedItem> activeItems = (facilityId != null)
                ? orderedItemRepository.findActiveOrderedItemsByFacilityId(facilityId)
                : orderedItemRepository.findAllActiveOrderedItems();
        for (OrderedItem oi : activeItems) {
            if (oi.getSku() != null && oi.getSku().getId() != null) {
                skusWithActiveOrders.add(oi.getSku().getId());
            }
        }

        List<RestockRequest> activeRestocks = (facilityId != null)
                ? restockRequestRepository.findActiveRestockRequestsByFacilityId(facilityId)
                : restockRequestRepository.findAllActiveRestockRequests();
        for (RestockRequest rr : activeRestocks) {
            if (rr.getSku() != null && rr.getSku().getId() != null) {
                skusWithActiveOrders.add(rr.getSku().getId());
            }
        }

        // 1. Metrics
        long totalReorder = skus.stream()
                .filter(s -> s.getUnits() != null && s.getReorderLevel() != null &&
                        s.getUnits() <= s.getReorderLevel() &&
                        !skusWithActiveOrders.contains(s.getId()))
                .count();
        long totalMinimumSkus = skus.stream()
                .filter(s -> s.getUnits() != null && s.getMinimumLevel() != null &&
                        s.getUnits() <= s.getMinimumLevel())
                .count();
        long totalBatches = batches.size();
        long totalPendingOrders = orders.stream()
                .filter(o -> o.getStatus() == Order.Status.Pending)
                .count();
        long activeSuppliers = suppliers.stream()
                .filter(s -> s.getStatus() == Supplier.Status.Active)
                .count();

        // Total ordered units in current month
        long unitsThisMonth = orders.stream()
                .filter(o -> o.getCreatedAt() != null &&
                        o.getCreatedAt().getMonth() == today.getMonth() &&
                        o.getCreatedAt().getYear() == today.getYear() &&
                        o.getStatus() != Order.Status.Denied)
                .mapToLong(o -> o.getTotalOrderedUnits() != null ? o.getTotalOrderedUnits() : 0L)
                .sum();
        if (unitsThisMonth == 0L) {
            unitsThisMonth = orders.stream()
                    .filter(o -> o.getStatus() != Order.Status.Denied)
                    .mapToLong(o -> o.getTotalOrderedUnits() != null ? o.getTotalOrderedUnits() : 0L)
                    .sum();
        }

        ProcurementDashboardResponseDto.Metrics metrics = ProcurementDashboardResponseDto.Metrics.builder()
                .totalReorder(totalReorder)
                .totalMinimumSkus(totalMinimumSkus)
                .totalBatches(totalBatches)
                .totalPendingOrders(totalPendingOrders)
                .activeSuppliers(activeSuppliers)
                .totalUnitsOrderedMonth(unitsThisMonth)
                .build();

        // 2. Priority Restock SKUs (excluding SKUs with orders already Pending, Approved, or Partially Receiving)
        String defaultSupplierName = suppliers.stream()
                .filter(s -> s.getStatus() == Supplier.Status.Active)
                .map(Supplier::getName)
                .findFirst()
                .orElse("Registered Vendor");

        List<ProcurementDashboardResponseDto.PriorityRestockSku> priorityRestockSkus = skus.stream()
                .filter(s -> s.getUnits() != null && s.getReorderLevel() != null &&
                        s.getUnits() <= s.getReorderLevel() &&
                        !skusWithActiveOrders.contains(s.getId()))
                .sorted(Comparator.comparing((Sku s) -> s.getUnits() <= 0 ? 0 : (s.getUnits() <= s.getMinimumLevel() ? 1 : 2))
                        .thenComparing(s -> s.getUnits() != null ? s.getUnits() : 0L))
                .limit(10)
                .map(s -> {
                    long currentStock = s.getUnits() != null ? s.getUnits() : 0L;
                    long min = s.getMinimumLevel() != null ? s.getMinimumLevel() : 0L;
                    long reorder = s.getReorderLevel() != null ? s.getReorderLevel() : 0L;
                    String urgency = (currentStock <= 0 || currentStock <= min) ? "CRITICAL" : "REORDER";

                    String generic = (s.getLibMedicine() != null && s.getLibMedicine().getDrugDescription() != null)
                            ? s.getLibMedicine().getDrugDescription()
                            : s.getName();
                    String dosage = (s.getLibMedicine() != null && s.getLibMedicine().getStrengthCode() != null)
                            ? s.getLibMedicine().getStrengthCode()
                            : (s.getDosageForm() != null ? s.getDosageForm() : "Standard");

                    return ProcurementDashboardResponseDto.PriorityRestockSku.builder()
                            .id(s.getId())
                            .sku(s.getName())
                            .brandName(s.getBrandName() != null ? s.getBrandName() : s.getName())
                            .genericName(generic)
                            .dosage(dosage)
                            .currentStock(currentStock)
                            .minimumLevel(min)
                            .reorderLevel(reorder)
                            .urgency(urgency)
                            .suggestedSupplier(defaultSupplierName)
                            .build();
                })
                .collect(Collectors.toList());

        // 3. Recent Order Requests
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        List<ProcurementDashboardResponseDto.RecentOrderRequest> recentOrders = orders.stream()
                .limit(10)
                .map(o -> {
                    List<OrderedItem> items = orderedItemRepository.findByOrderId(o.getId());
                    String skuCode = o.getPurchaseOrderNum();
                    String brand = "Purchase Requisition";
                    if (!items.isEmpty()) {
                        OrderedItem item = items.get(0);
                        if (item.getSku() != null) {
                            skuCode = item.getSku().getName();
                            brand = item.getSku().getBrandName() != null ? item.getSku().getBrandName() : item.getSku().getName();
                        }
                    }

                    String supplierName = o.getSupplier() != null ? o.getSupplier().getName() : "Unassigned Supplier";
                    String dest = o.getFacility() != null ? o.getFacility().getName() : facilityName;
                    String dateStr = o.getCreatedAt() != null ? o.getCreatedAt().format(dateFormatter) : today.format(dateFormatter);
                    String statusStr = o.getStatus() == Order.Status.Pending
                            ? "Pending Approval"
                            : (o.getStatus() == Order.Status.Partially_Received ? "Partially Received" : o.getStatus().name());

                    return ProcurementDashboardResponseDto.RecentOrderRequest.builder()
                            .orderId(o.getPurchaseOrderNum())
                            .sku(skuCode)
                            .brandName(brand)
                            .supplier(supplierName)
                            .quantity(o.getTotalOrderedUnits() != null ? o.getTotalOrderedUnits() : 0L)
                            .priority(o.getPriority() != null ? o.getPriority() : "Normal")
                            .status(statusStr)
                            .destination(dest)
                            .requestDate(dateStr)
                            .build();
                })
                .collect(Collectors.toList());

        return ProcurementDashboardResponseDto.builder()
                .facilityId(facilityId)
                .facilityName(facilityName)
                .metrics(metrics)
                .priorityRestockSkus(priorityRestockSkus)
                .recentOrderRequests(recentOrders)
                .build();
    }

    // 3. GET DASHBOARD SUMMARY (Combined)
    @PreAuthorize("isAuthenticated()")
    public DashboardResponseDto getDashboardSummary(Long facilityId) {
        facilityAccessGuard.verifyFacilityAccess(facilityId);
        String facilityName = resolveFacilityName(facilityId);
        return DashboardResponseDto.builder()
                .facilityId(facilityId)
                .facilityName(facilityName)
                .pharmacistDashboard(getPharmacistDashboard(facilityId))
                .procurementDashboard(getProcurementDashboard(facilityId))
                .build();
    }

    // --- HELPER METHODS ---

    private String resolveFacilityName(Long facilityId) {
        if (facilityId == null) {
            return "All Facilities";
        }
        return facilityRepository.findById(facilityId)
                .map(Facility::getName)
                .orElse("Facility #" + facilityId);
    }

    private List<Sku> fetchSkus(Long facilityId) {
        if (facilityId != null) {
            return skuRepository.findByFacilityId(facilityId);
        }
        Set<Long> accessibleIds = facilityAccessGuard.getAccessibleFacilityIdsOrNullForSuperAdmin();
        if (accessibleIds == null) {
            return skuRepository.findAll();
        }
        return skuRepository.findAll().stream()
                .filter(s -> s.getFacility() != null && accessibleIds.contains(s.getFacility().getId()))
                .collect(Collectors.toList());
    }

    private List<Batch> fetchBatches(Long facilityId) {
        if (facilityId != null) {
            return batchRepository.findByFacilityIdOrderByReceivedAtDesc(facilityId);
        }
        Set<Long> accessibleIds = facilityAccessGuard.getAccessibleFacilityIdsOrNullForSuperAdmin();
        if (accessibleIds == null) {
            return batchRepository.findAll();
        }
        return batchRepository.findAll().stream()
                .filter(b -> b.getFacility() != null && accessibleIds.contains(b.getFacility().getId()))
                .collect(Collectors.toList());
    }

    private List<Order> fetchOrders(Long facilityId) {
        if (facilityId != null) {
            return orderRepository.findByFacilityIdOrderByCreatedAtDesc(facilityId);
        }
        Set<Long> accessibleIds = facilityAccessGuard.getAccessibleFacilityIdsOrNullForSuperAdmin();
        if (accessibleIds == null) {
            return orderRepository.findAllByOrderByCreatedAtDesc();
        }
        return orderRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(o -> o.getFacility() != null && accessibleIds.contains(o.getFacility().getId()))
                .collect(Collectors.toList());
    }

    private List<Supplier> fetchSuppliers(Long facilityId) {
        if (facilityId != null) {
            return supplierRepository.findByFacilityId(facilityId);
        }
        Set<Long> accessibleIds = facilityAccessGuard.getAccessibleFacilityIdsOrNullForSuperAdmin();
        if (accessibleIds == null) {
            return supplierRepository.findAll();
        }
        return supplierRepository.findAll().stream()
                .filter(s -> s.getFacility() != null && accessibleIds.contains(s.getFacility().getId()))
                .collect(Collectors.toList());
    }

    private String getCategoryName(Sku sku) {
        if (sku.getDosageForm() != null && !sku.getDosageForm().trim().isEmpty()) {
            return sku.getDosageForm().trim();
        }
        if (sku.getLibMedicine() != null && sku.getLibMedicine().getFormCode() != null) {
            return sku.getLibMedicine().getFormCode().trim();
        }
        return "General";
    }

    private List<PharmacistDashboardResponseDto.UrgentAlert> generatePharmacistAlerts(List<Batch> batches,
                                                                                      List<Sku> skus,
                                                                                      LocalDate today,
                                                                                      long totalExpiry,
                                                                                      long totalNearExpiry,
                                                                                      long totalQuarantined,
                                                                                      long totalLowStock) {
        List<PharmacistDashboardResponseDto.UrgentAlert> alerts = new ArrayList<>();
        long alertId = 1L;

        // 1. Expired batches
        if (totalExpiry > 0) {
            Batch firstExpired = batches.stream()
                    .filter(b -> b.getExpiryDate() != null && b.getExpiryDate().isBefore(today))
                    .findFirst()
                    .orElse(null);
            String desc = firstExpired != null
                    ? "Batch " + firstExpired.getBatchNum() + " has expired. Immediate disposal/audit needed."
                    : totalExpiry + " batch(es) have expired.";
            alerts.add(PharmacistDashboardResponseDto.UrgentAlert.builder()
                    .id(alertId++)
                    .type("EXPIRY")
                    .title("Batch Expiration Detected (" + totalExpiry + ")")
                    .description(desc)
                    .severity("critical")
                    .time("Action Required")
                    .build());
        }

        // 2. Quarantined batches
        if (totalQuarantined > 0) {
            alerts.add(PharmacistDashboardResponseDto.UrgentAlert.builder()
                    .id(alertId++)
                    .type("QUARANTINE")
                    .title(totalQuarantined + " Batch(es) in Quarantine Lock")
                    .description("Pending QA cold-chain audit or regulatory clearance.")
                    .severity("warning")
                    .time("Active Hold")
                    .build());
        }

        // 3. Out of stock / Low stock
        long outOfStock = skus.stream()
                .filter(s -> s.getUnits() == null || s.getUnits() <= 0)
                .count();
        if (outOfStock > 0) {
            alerts.add(PharmacistDashboardResponseDto.UrgentAlert.builder()
                    .id(alertId++)
                    .type("LOW_STOCK")
                    .title(outOfStock + " SKU(s) Out of Stock")
                    .description("Inventory exhausted. Immediate purchase order needed.")
                    .severity("critical")
                    .time("Urgent")
                    .build());
        } else if (totalLowStock > 0) {
            alerts.add(PharmacistDashboardResponseDto.UrgentAlert.builder()
                    .id(alertId++)
                    .type("LOW_STOCK")
                    .title(totalLowStock + " SKU(s) Below Minimum Threshold")
                    .description("Stock dropped below safety buffer. Please reorder.")
                    .severity("warning")
                    .time("Today")
                    .build());
        }

        // 4. Near expiry
        if (totalNearExpiry > 0 && alerts.size() < 3) {
            alerts.add(PharmacistDashboardResponseDto.UrgentAlert.builder()
                    .id(alertId++)
                    .type("EXPIRY")
                    .title(totalNearExpiry + " Batch(es) Expiring within 90 Days")
                    .description("Stock rotation and FEFO dispensing advised.")
                    .severity("warning")
                    .time("Monitoring")
                    .build());
        }

        return alerts;
    }
}
