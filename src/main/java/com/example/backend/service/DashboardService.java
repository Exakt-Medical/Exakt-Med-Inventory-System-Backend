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
    private final PharmacyRepository pharmacyRepository;
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
                            PharmacyRepository pharmacyRepository,
                            FacilityAccessGuard facilityAccessGuard) {
        this.skuRepository = skuRepository;
        this.batchRepository = batchRepository;
        this.orderRepository = orderRepository;
        this.orderedItemRepository = orderedItemRepository;
        this.supplierRepository = supplierRepository;
        this.pharmacyRepository = pharmacyRepository;
        this.facilityAccessGuard = facilityAccessGuard;
    }

    // 1. GET PHARMACIST DASHBOARD (Generic-based via GenericStockSetting)
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Pharmacist')")
    public PharmacistDashboardResponseDto getPharmacistDashboard(Long pharmacyId) {
        if (pharmacyId == null) {
            throw new RuntimeException("Pharmacy ID is required.");
        }
        facilityAccessGuard.verifyPharmacyAccess(pharmacyId);

        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found with id: " + pharmacyId));
        Long facilityId = pharmacy.getFacility() != null ? pharmacy.getFacility().getId() : null;
        String facilityName = pharmacy.getFacility() != null ? pharmacy.getFacility().getName() : null;
        String pharmacyName = pharmacy.getPharmacyName();

        List<Sku> skus = skuRepository.findByPharmacyIdWithSettingAndMedicine(pharmacyId);
        List<Batch> batches = batchRepository.findByPharmacyIdOrderByReceivedAtDesc(pharmacyId);

        LocalDate today = LocalDate.now();
        LocalDate nearExpiryLimit = today.plusDays(90);

        // Distinct active GenericStockSettings (having at least 1 Active brand SKU)
        Map<Long, GenericStockSetting> activeSettingsMap = new LinkedHashMap<>();
        for (Sku s : skus) {
            if ((s.getStatus() == null || s.getStatus() == Sku.Status.Active) && s.getGenericStockSetting() != null) {
                activeSettingsMap.putIfAbsent(s.getGenericStockSetting().getId(), s.getGenericStockSetting());
            }
        }
        List<GenericStockSetting> activeSettings = new ArrayList<>(activeSettingsMap.values());

        long totalSkus = activeSettings.size();
        long totalStockQuantity = activeSettings.stream().mapToLong(this::getPooledUnits).sum();
        long totalLowStock = 0L;
        long totalOverStock = 0L;
        long outOfStockGroups = 0L;

        for (GenericStockSetting setting : activeSettings) {
            long pooled = getPooledUnits(setting);
            Long reorder = setting.getReorderLevel();
            Long min = setting.getMinimumLevel();
            Long max = setting.getMaximumLevel();

            if (pooled <= 0) {
                outOfStockGroups++;
            }
            if ((reorder != null && pooled <= reorder) || (reorder == null && min != null && pooled <= min)) {
                totalLowStock++;
            }
            if (max != null && pooled >= max) {
                totalOverStock++;
            }
        }

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

        // 2. Stock Distribution by Normalized Dosage Form
        Map<String, Long> categoryStockMap = activeSettings.stream()
                .collect(Collectors.groupingBy(
                        this::getCategoryName,
                        Collectors.summingLong(this::getPooledUnits)
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

        // 3. Category Health Bar Data (Generic-based, normalized case)
        Map<String, List<GenericStockSetting>> categoryGroupsMap = activeSettings.stream()
                .collect(Collectors.groupingBy(this::getCategoryName));

        List<PharmacistDashboardResponseDto.CategoryHealthBar> healthBarData = new ArrayList<>();
        for (Map.Entry<String, List<GenericStockSetting>> entry : categoryGroupsMap.entrySet()) {
            String category = entry.getKey();
            long low = 0L;
            long over = 0L;
            long out = 0L;
            long optimal = 0L;

            for (GenericStockSetting setting : entry.getValue()) {
                long pooled = getPooledUnits(setting);
                Long min = setting.getMinimumLevel();
                Long reorder = setting.getReorderLevel();
                Long max = setting.getMaximumLevel();

                if (pooled <= 0) {
                    out++;
                } else if ((reorder != null && pooled <= reorder) || (reorder == null && min != null && pooled <= min)) {
                    low++;
                } else if (max != null && pooled >= max) {
                    over++;
                } else {
                    optimal++;
                }
            }

            healthBarData.add(PharmacistDashboardResponseDto.CategoryHealthBar.builder()
                    .category(category)
                    .lowStock(low)
                    .overStock(over)
                    .outOfStock(out)
                    .optimal(optimal)
                    .build());
        }
        healthBarData.sort(Comparator.comparing(PharmacistDashboardResponseDto.CategoryHealthBar::getCategory));

        // 4. Urgent Operational Alerts (Generic-based)
        List<PharmacistDashboardResponseDto.UrgentAlert> urgentAlerts = generatePharmacistAlerts(
                batches, today, totalExpiry, totalNearExpiry, totalQuarantined, outOfStockGroups, totalLowStock);

        return PharmacistDashboardResponseDto.builder()
                .facilityId(facilityId)
                .facilityName(facilityName)
                .pharmacyId(pharmacyId)
                .pharmacyName(pharmacyName)
                .metrics(metrics)
                .skuStockDistribution(stockDistribution)
                .skuHealthBarData(healthBarData)
                .urgentAlerts(urgentAlerts)
                .build();
    }

    // 2. GET PROCUREMENT DASHBOARD (Generic-based via GenericStockSetting)
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Procurement')")
    public ProcurementDashboardResponseDto getProcurementDashboard(Long pharmacyId) {
        if (pharmacyId == null) {
            throw new RuntimeException("Pharmacy ID is required.");
        }
        facilityAccessGuard.verifyPharmacyAccess(pharmacyId);

        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found with id: " + pharmacyId));
        Long facilityId = pharmacy.getFacility() != null ? pharmacy.getFacility().getId() : null;
        String facilityName = pharmacy.getFacility() != null ? pharmacy.getFacility().getName() : null;
        String pharmacyName = pharmacy.getPharmacyName();

        List<Sku> skus = skuRepository.findByPharmacyIdWithSettingAndMedicine(pharmacyId);
        List<Batch> batches = batchRepository.findByPharmacyIdOrderByReceivedAtDesc(pharmacyId);
        List<Order> orders = orderRepository.findByPharmacyIdOrderByCreatedAtDesc(pharmacyId);
        List<Supplier> suppliers = fetchSuppliers(facilityId, pharmacyId);

        LocalDate today = LocalDate.now();

        Set<Long> settingIdsWithActiveOrders = new HashSet<>();
        List<OrderedItem> activeItems = orderedItemRepository.findActiveOrderedItemsByPharmacyId(pharmacyId);
        for (OrderedItem oi : activeItems) {
            if (oi.getSku() != null && oi.getSku().getGenericStockSetting() != null) {
                settingIdsWithActiveOrders.add(oi.getSku().getGenericStockSetting().getId());
            }
        }

        Map<Long, List<Sku>> activeBySetting = skus.stream()
                .filter(s -> (s.getStatus() == null || s.getStatus() == Sku.Status.Active) && s.getGenericStockSetting() != null)
                .collect(Collectors.groupingBy(s -> s.getGenericStockSetting().getId(), LinkedHashMap::new, Collectors.toList()));

        long totalReorder = 0L;
        long totalMinimumSkus = 0L;
        for (Map.Entry<Long, List<Sku>> entry : activeBySetting.entrySet()) {
            GenericStockSetting setting = entry.getValue().get(0).getGenericStockSetting();
            long pooled = getPooledUnits(setting);
            Long min = setting.getMinimumLevel();
            Long reorder = setting.getReorderLevel();

            if (reorder != null && pooled <= reorder && !settingIdsWithActiveOrders.contains(entry.getKey())) {
                totalReorder++;
            }
            if (min != null && pooled <= min) {
                totalMinimumSkus++;
            }
        }

        long totalBatches = batches.size();
        long totalPendingOrders = orders.stream()
                .filter(o -> o.getStatus() == Order.Status.Pending)
                .count();
        long activeSuppliers = suppliers.stream()
                .filter(s -> s.getStatus() == Supplier.Status.Active)
                .count();

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

        String defaultSupplierName = suppliers.stream()
                .filter(s -> s.getStatus() == Supplier.Status.Active)
                .map(Supplier::getName)
                .findFirst()
                .orElse("Registered Vendor");

        // Priority Restock Groups (1 entry per generic + packaging group)
        List<ProcurementDashboardResponseDto.PriorityRestockSku> priorityRestockSkus = activeBySetting.entrySet().stream()
                .filter(entry -> !settingIdsWithActiveOrders.contains(entry.getKey()))
                .map(entry -> entry.getValue().get(0).getGenericStockSetting())
                .filter(g -> g.getReorderLevel() != null && getPooledUnits(g) <= g.getReorderLevel())
                .sorted(Comparator
                        .comparingInt((GenericStockSetting g) -> {
                            long pooled = getPooledUnits(g);
                            long min = g.getMinimumLevel() != null ? g.getMinimumLevel() : 0L;
                            return pooled <= 0 ? 0 : (pooled <= min ? 1 : 2);
                        })
                        .thenComparingLong(this::getPooledUnits))
                .limit(10)
                .map(g -> {
                    long currentStock = getPooledUnits(g);
                    long min = g.getMinimumLevel() != null ? g.getMinimumLevel() : 0L;
                    long reorder = g.getReorderLevel() != null ? g.getReorderLevel() : 0L;
                    String urgency = (currentStock <= 0 || currentStock <= min) ? "CRITICAL" : "REORDER";
                    String generic = (g.getLibMedicine() != null && g.getLibMedicine().getDrugDescription() != null)
                            ? g.getLibMedicine().getDrugDescription()
                            : "Generic Medicine";
                    String dosage = (g.getDosageStrength() != null && !g.getDosageStrength().isBlank())
                            ? g.getDosageStrength()
                            : (g.getDosageForm() != null ? g.getDosageForm() : "Standard");
                    int brandCount = activeBySetting.getOrDefault(g.getId(), Collections.emptyList()).size();

                    return ProcurementDashboardResponseDto.PriorityRestockSku.builder()
                            .id(g.getId())
                            .sku(g.getPackagingUnit())
                            .brandName(brandCount + (brandCount == 1 ? " Active Brand" : " Active Brands"))
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
                    String dest = o.getPharmacy() != null ? o.getPharmacy().getPharmacyName() : pharmacyName;
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
                .pharmacyId(pharmacyId)
                .pharmacyName(pharmacyName)
                .metrics(metrics)
                .priorityRestockSkus(priorityRestockSkus)
                .recentOrderRequests(recentOrders)
                .build();
    }

    // 3. GET DASHBOARD SUMMARY (Combined)
    @PreAuthorize("isAuthenticated()")
    public DashboardResponseDto getDashboardSummary(Long pharmacyId) {
        if (pharmacyId == null) {
            throw new RuntimeException("Pharmacy ID is required.");
        }
        facilityAccessGuard.verifyPharmacyAccess(pharmacyId);

        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> new RuntimeException("Pharmacy not found with id: " + pharmacyId));
        Long facilityId = pharmacy.getFacility() != null ? pharmacy.getFacility().getId() : null;
        String facilityName = pharmacy.getFacility() != null ? pharmacy.getFacility().getName() : null;
        String pharmacyName = pharmacy.getPharmacyName();

        return DashboardResponseDto.builder()
                .facilityId(facilityId)
                .facilityName(facilityName)
                .pharmacyId(pharmacyId)
                .pharmacyName(pharmacyName)
                .pharmacistDashboard(getPharmacistDashboard(pharmacyId))
                .procurementDashboard(getProcurementDashboard(pharmacyId))
                .build();
    }

    // --- HELPER METHODS ---

    private long getPooledUnits(GenericStockSetting setting) {
        return setting != null && setting.getTotalUnits() != null ? setting.getTotalUnits() : 0L;
    }

    private List<Supplier> fetchSuppliers(Long facilityId, Long pharmacyId) {
        if (pharmacyId != null) {
            List<Supplier> byPharmacy = supplierRepository.findByPharmacyId(pharmacyId);
            if (!byPharmacy.isEmpty() || facilityId == null) {
                return byPharmacy;
            }
        }
        if (facilityId != null) {
            return supplierRepository.findByFacilityId(facilityId);
        }
        return Collections.emptyList();
    }

    private String getCategoryName(GenericStockSetting setting) {
        String raw = null;
        if (setting != null && setting.getDosageForm() != null && !setting.getDosageForm().trim().isEmpty()) {
            raw = setting.getDosageForm().trim();
        } else if (setting != null && setting.getLibMedicine() != null && setting.getLibMedicine().getFormCode() != null) {
            raw = setting.getLibMedicine().getFormCode().trim();
        }
        if (raw == null || raw.isEmpty()) {
            return "General";
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private List<PharmacistDashboardResponseDto.UrgentAlert> generatePharmacistAlerts(List<Batch> batches,
                                                                                      LocalDate today,
                                                                                      long totalExpiry,
                                                                                      long totalNearExpiry,
                                                                                      long totalQuarantined,
                                                                                      long outOfStockGroups,
                                                                                      long totalLowStock) {
        List<PharmacistDashboardResponseDto.UrgentAlert> alerts = new ArrayList<>();
        long alertId = 1L;

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

        if (outOfStockGroups > 0) {
            alerts.add(PharmacistDashboardResponseDto.UrgentAlert.builder()
                    .id(alertId++)
                    .type("LOW_STOCK")
                    .title(outOfStockGroups + " Generic Group(s) Out of Stock")
                    .description("Pooled generic inventory exhausted. Immediate purchase order needed.")
                    .severity("critical")
                    .time("Urgent")
                    .build());
        } else if (totalLowStock > 0) {
            alerts.add(PharmacistDashboardResponseDto.UrgentAlert.builder()
                    .id(alertId++)
                    .type("LOW_STOCK")
                    .title(totalLowStock + " Generic Group(s) Below Reorder Threshold")
                    .description("Pooled stock dropped to or below reorder threshold. Please restock.")
                    .severity("warning")
                    .time("Today")
                    .build());
        }

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
