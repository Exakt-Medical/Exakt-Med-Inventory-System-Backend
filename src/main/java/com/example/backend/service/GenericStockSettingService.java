package com.example.backend.service;

import com.example.backend.dto.common.PageResponseDto;
import com.example.backend.dto.sku.*;
import com.example.backend.entity.*;
import com.example.backend.repository.*;
import com.example.backend.security.FacilityAccessGuard;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GenericStockSettingService {

    private final GenericStockSettingRepository genericStockSettingRepository;
    private final SkuRepository skuRepository;
    private final PharmacyRepository pharmacyRepository;
    private final LibMedicineRepository libMedicineRepository;
    private final OrderedItemRepository orderedItemRepository;
    private final BatchRepository batchRepository;
    private final FacilityAccessGuard facilityAccessGuard;
    private final AuditLogService auditLogService;

    public GenericStockSettingService(
            GenericStockSettingRepository genericStockSettingRepository,
            SkuRepository skuRepository,
            PharmacyRepository pharmacyRepository,
            LibMedicineRepository libMedicineRepository,
            OrderedItemRepository orderedItemRepository,
            BatchRepository batchRepository,
            FacilityAccessGuard facilityAccessGuard,
            AuditLogService auditLogService) {
        this.genericStockSettingRepository = genericStockSettingRepository;
        this.skuRepository = skuRepository;
        this.pharmacyRepository = pharmacyRepository;
        this.libMedicineRepository = libMedicineRepository;
        this.orderedItemRepository = orderedItemRepository;
        this.batchRepository = batchRepository;
        this.facilityAccessGuard = facilityAccessGuard;
        this.auditLogService = auditLogService;
    }

    // -------------------------------------------------------------------------
    // INTERNAL SYNC & RESOLUTION HELPERS (called by SkuService, BatchService, OneRx)
    // -------------------------------------------------------------------------

    @Transactional
    public void recalculateTotalUnits(Long settingId) {
        if (settingId == null) return;
        genericStockSettingRepository.recalculateTotalUnits(settingId);
    }

    @Transactional
    public GenericStockSetting resolveOrCreateSetting(
            Pharmacy pharmacy,
            LibMedicine med,
            String dosageForm,
            String dosageStrength,
            String packagingUnit,
            Long minLevel,
            Long reorderLevel,
            Long maxLevel) {

        Optional<GenericStockSetting> existing = genericStockSettingRepository
                .findByPharmacyIdAndLibMedicineIdAndPackagingUnitIgnoreCase(
                        pharmacy.getId(), med.getId(), packagingUnit);

        if (existing.isPresent()) {
            return existing.get();
        }

        long min = minLevel != null ? minLevel : 10L;
        long reorder = reorderLevel != null ? reorderLevel : Math.max(min + 1, 50L);
        long max = maxLevel != null ? maxLevel : Math.max(reorder + 1, 500L);
        validateThresholds(min, reorder, max);

        GenericStockSetting created = new GenericStockSetting();
        created.setPharmacy(pharmacy);
        created.setLibMedicine(med);
        created.setDosageForm((dosageForm != null && !dosageForm.isBlank()) ? dosageForm.trim().toUpperCase() : "TABLET");
        created.setDosageStrength(dosageStrength != null ? dosageStrength.trim() : null);
        created.setPackagingUnit(packagingUnit.trim());
        created.setMinimumLevel(min);
        created.setReorderLevel(reorder);
        created.setMaximumLevel(max);
        created.setTotalUnits(0L);
        return genericStockSettingRepository.save(created);
    }

    // -------------------------------------------------------------------------
    // 1. GROUPED GENERIC + BRAND SKUS (SKU Management Table)
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public PageResponseDto<GenericStockGroupDto> getGroupedSkus(
            Long pharmacyId,
            String search,
            String status,
            String stockStatus,
            int page,
            int size) {

        if (pharmacyId == null) {
            throw new RuntimeException("Pharmacy ID is strictly required.");
        }
        facilityAccessGuard.verifyPharmacyAccess(pharmacyId);

        List<Sku> allSkus = skuRepository.findByPharmacyIdWithSettingAndMedicine(pharmacyId);
        Map<Long, OrderPipelineMetrics> pipelineMap = buildOrderPipelineMetrics(pharmacyId);
        Map<Long, ExpiredMetrics> expiredMap = buildExpiredMetrics(pharmacyId);

        // Group SKUs by GenericStockSetting ID preserving DB sort order
        Map<Long, List<Sku>> skusBySetting = new LinkedHashMap<>();
        for (Sku s : allSkus) {
            if (s.getGenericStockSetting() == null) continue;
            skusBySetting.computeIfAbsent(s.getGenericStockSetting().getId(), k -> new ArrayList<>()).add(s);
        }

        String q = (search != null && !search.trim().isEmpty()) ? search.trim().toLowerCase(Locale.ROOT) : null;
        String statusFilter = (status != null && !status.trim().isEmpty()) ? status.trim().toUpperCase(Locale.ROOT) : "ALL";
        String stockFilter = (stockStatus != null && !stockStatus.trim().isEmpty()) ? stockStatus.trim().toUpperCase(Locale.ROOT) : "ALL";

        List<GenericStockGroupDto> groups = new ArrayList<>();
        for (List<Sku> groupSkus : skusBySetting.values()) {
            GenericStockSetting setting = groupSkus.get(0).getGenericStockSetting();

            // Filter child brands by status if requested
            List<Sku> filteredBrands = groupSkus.stream()
                    .filter(s -> {
                        boolean isActive = s.getStatus() == null || s.getStatus() == Sku.Status.Active;
                        if ("ACTIVE".equals(statusFilter)) return isActive;
                        if ("INACTIVE".equals(statusFilter)) return !isActive;
                        return true;
                    })
                    .collect(Collectors.toList());

            if (filteredBrands.isEmpty()) continue;

            // Search matches generic name, packaging unit, or any brand name / SKU code
            if (q != null) {
                String genericName = setting.getLibMedicine() != null && setting.getLibMedicine().getDrugDescription() != null
                        ? setting.getLibMedicine().getDrugDescription().toLowerCase(Locale.ROOT) : "";
                String pack = setting.getPackagingUnit() != null ? setting.getPackagingUnit().toLowerCase(Locale.ROOT) : "";
                boolean groupMatches = genericName.contains(q) || pack.contains(q);
                if (!groupMatches) {
                    boolean brandMatches = filteredBrands.stream().anyMatch(s ->
                            (s.getBrandName() != null && s.getBrandName().toLowerCase(Locale.ROOT).contains(q))
                                    || (s.getName() != null && s.getName().toLowerCase(Locale.ROOT).contains(q)));
                    if (!brandMatches) continue;
                }
            }

            long pooledTotal = setting.getTotalUnits() != null ? setting.getTotalUnits() : 0L;
            String health = computeStockStatus(pooledTotal, setting.getMinimumLevel(), setting.getReorderLevel());

            if (!"ALL".equals(stockFilter) && !matchesStockFilter(health, pooledTotal, setting.getMinimumLevel(), setting.getReorderLevel(), stockFilter)) {
                continue;
            }

            groups.add(buildGroupDto(setting, filteredBrands, groupSkus, pipelineMap, expiredMap));
        }

        return paginateList(groups, page, size);
    }

    // -------------------------------------------------------------------------
    // 2. GENERIC SUMMARY KPIs (SKU Management Header)
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public SkuSummaryDto getSummary(Long pharmacyId) {
        if (pharmacyId == null) {
            throw new RuntimeException("Pharmacy ID is strictly required.");
        }
        facilityAccessGuard.verifyPharmacyAccess(pharmacyId);

        long totalGroups = genericStockSettingRepository.countActiveGroupsByPharmacyId(pharmacyId);
        long optimalCount = genericStockSettingRepository.countOptimalGroupsByPharmacyId(pharmacyId);
        long reorderCount = genericStockSettingRepository.countReorderGroupsByPharmacyId(pharmacyId);
        long criticalCount = genericStockSettingRepository.countCriticalGroupsByPharmacyId(pharmacyId);

        return SkuSummaryDto.builder()
                .totalSkus(totalGroups)
                .optimalCount(optimalCount)
                .reorderCount(reorderCount)
                .criticalCount(criticalCount)
                .build();
    }

    // -------------------------------------------------------------------------
    // 3. GENERIC REORDER-NEEDED LIST & SUMMARY (Pharmacist Quotation + Procurement Order Request)
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public ReorderNeededSummaryDto getReorderNeededSummary(Long pharmacyId) {
        if (pharmacyId == null) {
            throw new RuntimeException("Pharmacy ID is strictly required.");
        }
        facilityAccessGuard.verifyPharmacyAccess(pharmacyId);

        long totalNeeded = genericStockSettingRepository.countTotalReorderNeededGroupsByPharmacyId(pharmacyId);
        long criticalCount = genericStockSettingRepository.countCriticalReorderNeededGroupsByPharmacyId(pharmacyId);
        long reorderOnlyCount = Math.max(0L, totalNeeded - criticalCount);

        return ReorderNeededSummaryDto.builder()
                .totalReorderCount(totalNeeded)
                .criticalCount(criticalCount)
                .reorderOnlyCount(reorderOnlyCount)
                .build();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public PageResponseDto<GenericStockGroupDto> getReorderNeededGroups(
            Long pharmacyId,
            String search,
            String stockStatus,
            int page,
            int size) {

        if (pharmacyId == null) {
            throw new RuntimeException("Pharmacy ID is strictly required.");
        }
        facilityAccessGuard.verifyPharmacyAccess(pharmacyId);

        List<GenericStockSetting> reorderSettings = genericStockSettingRepository.findReorderNeededGroupsByPharmacyId(pharmacyId);
        if (reorderSettings.isEmpty()) {
            return PageResponseDto.<GenericStockGroupDto>builder()
                    .content(Collections.emptyList())
                    .currentPage(Math.max(0, page) + 1)
                    .pageSize(size > 0 ? size : 10)
                    .totalElements(0)
                    .totalPages(0)
                    .isLast(true)
                    .build();
        }

        List<Sku> allSkus = skuRepository.findByPharmacyIdWithSettingAndMedicine(pharmacyId);
        Map<Long, List<Sku>> skusBySetting = allSkus.stream()
                .filter(s -> s.getGenericStockSetting() != null)
                .collect(Collectors.groupingBy(s -> s.getGenericStockSetting().getId()));
        Map<Long, OrderPipelineMetrics> pipelineMap = buildOrderPipelineMetrics(pharmacyId);
        Map<Long, ExpiredMetrics> expiredMap = buildExpiredMetrics(pharmacyId);

        String q = (search != null && !search.trim().isEmpty()) ? search.trim().toLowerCase(Locale.ROOT) : null;
        String filter = (stockStatus != null && !stockStatus.trim().isEmpty()) ? stockStatus.trim().toUpperCase(Locale.ROOT) : "ALL";

        List<GenericStockGroupDto> results = new ArrayList<>();
        for (GenericStockSetting setting : reorderSettings) {
            long pooledTotal = setting.getTotalUnits() != null ? setting.getTotalUnits() : 0L;
            String health = computeStockStatus(pooledTotal, setting.getMinimumLevel(), setting.getReorderLevel());

            if ("CRITICAL".equals(filter) || "MINIMUM".equals(filter)) {
                if (pooledTotal > setting.getMinimumLevel()) continue;
            } else if ("REORDER".equals(filter) || "REORDER_ONLY".equals(filter)) {
                if (pooledTotal <= setting.getMinimumLevel() || pooledTotal > setting.getReorderLevel()) continue;
            }

            List<Sku> groupSkus = skusBySetting.getOrDefault(setting.getId(), Collections.emptyList());
            List<Sku> activeBrands = groupSkus.stream()
                    .filter(s -> s.getStatus() == null || s.getStatus() == Sku.Status.Active)
                    .collect(Collectors.toList());

            if (q != null) {
                String genericName = setting.getLibMedicine() != null && setting.getLibMedicine().getDrugDescription() != null
                        ? setting.getLibMedicine().getDrugDescription().toLowerCase(Locale.ROOT) : "";
                String pack = setting.getPackagingUnit() != null ? setting.getPackagingUnit().toLowerCase(Locale.ROOT) : "";
                boolean matches = genericName.contains(q) || pack.contains(q)
                        || activeBrands.stream().anyMatch(s ->
                                (s.getBrandName() != null && s.getBrandName().toLowerCase(Locale.ROOT).contains(q))
                                        || (s.getName() != null && s.getName().toLowerCase(Locale.ROOT).contains(q)));
                if (!matches) continue;
            }

            results.add(buildGroupDto(setting, activeBrands, groupSkus, pipelineMap, expiredMap));
        }

        // Sort critical / out-of-stock first, then by generic name
        results.sort(Comparator
                .comparingInt((GenericStockGroupDto g) -> "OUT_OF_STOCK".equals(g.getStockStatus()) ? 0 : ("CRITICAL".equals(g.getStockStatus()) ? 1 : 2))
                .thenComparing(g -> g.getGenericName() != null ? g.getGenericName() : "", String.CASE_INSENSITIVE_ORDER));

        return paginateList(results, page, size);
    }

    // -------------------------------------------------------------------------
    // 4. DROPDOWN & BRAND PICKER (Pharmacist Quotation & Procurement Conversion)
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<GenericStockSettingDropdownDto> getDropdownSettings(Long pharmacyId) {
        if (pharmacyId == null) return Collections.emptyList();
        facilityAccessGuard.verifyPharmacyAccess(pharmacyId);

        List<GenericStockSetting> settings = genericStockSettingRepository.findByPharmacyIdWithMedicine(pharmacyId);
        List<Sku> allSkus = skuRepository.findByPharmacyIdWithSettingAndMedicine(pharmacyId);
        Map<Long, Long> activeBrandCounts = allSkus.stream()
                .filter(s -> s.getGenericStockSetting() != null && (s.getStatus() == null || s.getStatus() == Sku.Status.Active))
                .collect(Collectors.groupingBy(s -> s.getGenericStockSetting().getId(), Collectors.counting()));

        List<GenericStockSettingDropdownDto> result = new ArrayList<>();
        for (GenericStockSetting g : settings) {
            long activeCount = activeBrandCounts.getOrDefault(g.getId(), 0L);
            if (activeCount == 0L) continue;
            result.add(GenericStockSettingDropdownDto.builder()
                    .settingId(g.getId())
                    .medicineId(g.getLibMedicine() != null ? g.getLibMedicine().getId() : null)
                    .drugCode(g.getLibMedicine() != null ? g.getLibMedicine().getDrugCode() : null)
                    .genericName(g.getLibMedicine() != null ? g.getLibMedicine().getDrugDescription() : "")
                    .dosageForm(g.getDosageForm())
                    .dosageStrength(g.getDosageStrength())
                    .packagingUnit(g.getPackagingUnit())
                    .totalUnits(g.getTotalUnits() != null ? g.getTotalUnits() : 0L)
                    .minimumLevel(g.getMinimumLevel())
                    .reorderLevel(g.getReorderLevel())
                    .maximumLevel(g.getMaximumLevel())
                    .activeBrandCount((int) activeCount)
                    .build());
        }
        return result;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<SkuResponseDto> getActiveBrandsBySetting(Long settingId) {
        GenericStockSetting setting = genericStockSettingRepository.findByIdWithMedicine(settingId)
                .orElseThrow(() -> new RuntimeException("Generic stock setting not found with id: " + settingId));
        facilityAccessGuard.verifyPharmacyAccess(setting.getPharmacy().getId());

        List<Sku> activeBrands = skuRepository.findActiveByGenericStockSettingId(settingId);
        return activeBrands.stream()
                .map(s -> mapBrandSkuDto(s, null, null))
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // 5. UPDATE / UPSERT SHARED THRESHOLDS
    // -------------------------------------------------------------------------

    @Transactional
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Pharmacist')")
    public GenericStockGroupDto updateThresholds(Long settingId, GenericStockSettingRequestDto request) {
        GenericStockSetting setting = genericStockSettingRepository.findByIdWithMedicine(settingId)
                .orElseThrow(() -> new RuntimeException("Generic stock setting not found with id: " + settingId));
        facilityAccessGuard.verifyPharmacyAccess(setting.getPharmacy().getId());

        validateThresholds(request.getMinimumLevel(), request.getReorderLevel(), request.getMaximumLevel());

        setting.setMinimumLevel(request.getMinimumLevel());
        setting.setReorderLevel(request.getReorderLevel());
        setting.setMaximumLevel(request.getMaximumLevel());
        GenericStockSetting saved = genericStockSettingRepository.save(setting);

        String genericName = saved.getLibMedicine() != null ? saved.getLibMedicine().getDrugDescription() : "Generic Medicine";
        auditLogService.logAction(
                saved.getPharmacy(),
                "SKU Catalog",
                "GENERIC_THRESHOLDS_UPDATED",
                "Updated Shared Thresholds (" + genericName + ")",
                AuditLog.Severity.INFO,
                genericName,
                saved.getId(),
                "Updated shared generic thresholds for '" + genericName + "' (" + saved.getPackagingUnit() + "). Min: "
                        + saved.getMinimumLevel() + ", Reorder: " + saved.getReorderLevel() + ", Max: " + saved.getMaximumLevel() + ".",
                "Admin,Pharmacist,Procurement"
        );

        List<Sku> groupSkus = skuRepository.findActiveByGenericStockSettingId(saved.getId());
        return buildGroupDto(saved, groupSkus, groupSkus, Collections.emptyMap(), Collections.emptyMap());
    }

    @Transactional
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Pharmacist')")
    public GenericStockGroupDto upsertThresholds(GenericStockSettingRequestDto request) {
        if (request.getPharmacyId() == null || request.getMedicineId() == null || request.getPackagingUnit() == null || request.getPackagingUnit().isBlank()) {
            throw new RuntimeException("pharmacyId, medicineId, and packagingUnit are required.");
        }
        facilityAccessGuard.verifyPharmacyAccess(request.getPharmacyId());
        validateThresholds(request.getMinimumLevel(), request.getReorderLevel(), request.getMaximumLevel());

        Pharmacy pharmacy = pharmacyRepository.findById(request.getPharmacyId())
                .orElseThrow(() -> new RuntimeException("Pharmacy not found with id: " + request.getPharmacyId()));
        LibMedicine med = libMedicineRepository.findById(request.getMedicineId())
                .orElseThrow(() -> new RuntimeException("Medicine not found with id: " + request.getMedicineId()));

        GenericStockSetting setting = genericStockSettingRepository
                .findByPharmacyIdAndLibMedicineIdAndPackagingUnitIgnoreCase(
                        pharmacy.getId(), med.getId(), request.getPackagingUnit().trim())
                .orElseGet(() -> {
                    GenericStockSetting created = new GenericStockSetting();
                    created.setPharmacy(pharmacy);
                    created.setLibMedicine(med);
                    created.setPackagingUnit(request.getPackagingUnit().trim());
                    created.setDosageForm(request.getDosageForm() != null ? request.getDosageForm().trim().toUpperCase() : "TABLET");
                    created.setDosageStrength(request.getDosageStrength());
                    created.setTotalUnits(0L);
                    return created;
                });

        setting.setMinimumLevel(request.getMinimumLevel());
        setting.setReorderLevel(request.getReorderLevel());
        setting.setMaximumLevel(request.getMaximumLevel());
        GenericStockSetting saved = genericStockSettingRepository.save(setting);

        List<Sku> groupSkus = skuRepository.findActiveByGenericStockSettingId(saved.getId());
        return buildGroupDto(saved, groupSkus, groupSkus, Collections.emptyMap(), Collections.emptyMap());
    }

    // -------------------------------------------------------------------------
    // PRIVATE HELPERS
    // -------------------------------------------------------------------------

    private void validateThresholds(Long min, Long reorder, Long max) {
        if (min == null || reorder == null || max == null) {
            throw new RuntimeException("Minimum, reorder, and maximum thresholds are required.");
        }
        if (min < 0) {
            throw new RuntimeException("Minimum level cannot be negative.");
        }
        if (reorder <= min) {
            throw new RuntimeException("Reorder level must be greater than minimum level.");
        }
        if (max <= reorder) {
            throw new RuntimeException("Maximum level must be greater than reorder level.");
        }
    }

    private String computeStockStatus(long totalUnits, Long min, Long reorder) {
        if (totalUnits <= 0) return "OUT_OF_STOCK";
        if (min != null && totalUnits <= min) return "CRITICAL";
        if (reorder != null && totalUnits <= reorder) return "REORDER";
        return "OPTIMAL";
    }

    private boolean matchesStockFilter(String health, long totalUnits, Long min, Long reorder, String filter) {
        return switch (filter) {
            case "OUT_OF_STOCK" -> totalUnits <= 0;
            case "CRITICAL" -> min != null && totalUnits <= min;
            case "REORDER" -> reorder != null && min != null && totalUnits <= reorder && totalUnits > min;
            case "OPTIMAL" -> reorder != null && totalUnits > reorder;
            default -> true;
        };
    }

    private GenericStockGroupDto buildGroupDto(
            GenericStockSetting setting,
            List<Sku> visibleBrands,
            List<Sku> allGroupBrands,
            Map<Long, OrderPipelineMetrics> pipelineMap,
            Map<Long, ExpiredMetrics> expiredMap) {

        long pendingTotal = 0L;
        long toReceiveTotal = 0L;
        long expiredBatchesTotal = 0L;
        long expiredUnitsTotal = 0L;
        int activeCount = 0;

        for (Sku s : allGroupBrands) {
            if (s.getStatus() == null || s.getStatus() == Sku.Status.Active) {
                activeCount++;
            }
            OrderPipelineMetrics pm = pipelineMap.get(s.getId());
            if (pm != null) {
                pendingTotal += pm.pendingUnits;
                toReceiveTotal += pm.toReceiveUnits;
            }
            ExpiredMetrics em = expiredMap.get(s.getId());
            if (em != null) {
                expiredBatchesTotal += em.batchCount;
                expiredUnitsTotal += em.units;
            }
        }

        List<SkuResponseDto> brandDtos = visibleBrands.stream()
                .map(s -> mapBrandSkuDto(s, pipelineMap.get(s.getId()), expiredMap.get(s.getId())))
                .collect(Collectors.toList());

        long pooledTotal = setting.getTotalUnits() != null ? setting.getTotalUnits() : 0L;

        return GenericStockGroupDto.builder()
                .settingId(setting.getId())
                .pharmacyId(setting.getPharmacy() != null ? setting.getPharmacy().getId() : null)
                .pharmacyName(setting.getPharmacy() != null ? setting.getPharmacy().getPharmacyName() : null)
                .medicineId(setting.getLibMedicine() != null ? setting.getLibMedicine().getId() : null)
                .drugCode(setting.getLibMedicine() != null ? setting.getLibMedicine().getDrugCode() : null)
                .genericName(setting.getLibMedicine() != null ? setting.getLibMedicine().getDrugDescription() : "")
                .classification(setting.getLibMedicine() != null ? setting.getLibMedicine().getClassification() : null)
                .dosageForm(setting.getDosageForm())
                .dosageStrength(setting.getDosageStrength())
                .packagingUnit(setting.getPackagingUnit())
                .totalUnits(pooledTotal)
                .minimumLevel(setting.getMinimumLevel())
                .reorderLevel(setting.getReorderLevel())
                .maximumLevel(setting.getMaximumLevel())
                .stockStatus(computeStockStatus(pooledTotal, setting.getMinimumLevel(), setting.getReorderLevel()))
                .brandCount(allGroupBrands.size())
                .activeBrandCount(activeCount)
                .pendingUnits(pendingTotal)
                .toReceiveUnits(toReceiveTotal)
                .expiredBatchesCount(expiredBatchesTotal)
                .expiredUnits(expiredUnitsTotal)
                .brands(brandDtos)
                .build();
    }

    private SkuResponseDto mapBrandSkuDto(Sku sku, OrderPipelineMetrics pm, ExpiredMetrics em) {
        SkuResponseDto dto = new SkuResponseDto();
        dto.setId(sku.getId());
        dto.setGenericStockSettingId(sku.getGenericStockSetting() != null ? sku.getGenericStockSetting().getId() : null);
        if (sku.getPharmacy() != null) {
            dto.setPharmacyId(sku.getPharmacy().getId());
            dto.setPharmacyName(sku.getPharmacy().getPharmacyName());
        }
        if (sku.getLibMedicine() != null) {
            dto.setMedicineId(sku.getLibMedicine().getId());
            dto.setDrugCode(sku.getLibMedicine().getDrugCode());
            dto.setDrugDescription(sku.getLibMedicine().getDrugDescription());
            dto.setClassification(sku.getLibMedicine().getClassification());
        }
        dto.setName(sku.getName());
        dto.setBrandName(sku.getBrandName());
        dto.setDosageForm(sku.getDosageForm());
        dto.setDosageStrength(sku.getDosageStrength());
        dto.setPackagingUnit(sku.getPackagingUnit());
        dto.setUnits(sku.getUnits() != null ? sku.getUnits() : 0L);
        dto.setGroupTotalUnits(sku.getGroupTotalUnits() != null ? sku.getGroupTotalUnits() : dto.getUnits());
        dto.setMinimumLevel(sku.getMinimumLevel());
        dto.setReorderLevel(sku.getReorderLevel());
        dto.setMaximumLevel(sku.getMaximumLevel());
        dto.setStatus(sku.getStatus() != null ? sku.getStatus() : Sku.Status.Active);
        dto.setCreatedAt(sku.getCreatedAt());
        dto.setUpdatedAt(sku.getUpdatedAt());
        dto.setPendingUnits(pm != null ? pm.pendingUnits : 0L);
        dto.setToReceiveUnits(pm != null ? pm.toReceiveUnits : 0L);
        dto.setExpiredBatchesCount(em != null ? em.batchCount : 0L);
        dto.setExpiredUnits(em != null ? em.units : 0L);
        return dto;
    }

    private Map<Long, OrderPipelineMetrics> buildOrderPipelineMetrics(Long pharmacyId) {
        Map<Long, OrderPipelineMetrics> map = new HashMap<>();
        List<OrderedItem> activeItems = orderedItemRepository.findActiveOrderedItemsByPharmacyId(pharmacyId);
        for (OrderedItem oi : activeItems) {
            if (oi.getSku() == null || oi.getOrder() == null) continue;
            long ordered = oi.getOrderedUnits() != null ? oi.getOrderedUnits() : 0L;
            long received = oi.getReceivedUnits() != null ? oi.getReceivedUnits() : 0L;
            long remaining = Math.max(0L, ordered - received);
            OrderPipelineMetrics m = map.computeIfAbsent(oi.getSku().getId(), k -> new OrderPipelineMetrics());
            if (oi.getOrder().getStatus() == Order.Status.Pending) {
                m.pendingUnits += ordered;
            } else if (oi.getOrder().getStatus() == Order.Status.Approved || oi.getOrder().getStatus() == Order.Status.Partially_Received) {
                m.toReceiveUnits += remaining;
            }
        }
        return map;
    }

    private Map<Long, ExpiredMetrics> buildExpiredMetrics(Long pharmacyId) {
        Map<Long, ExpiredMetrics> map = new HashMap<>();
        List<Object[]> rows = batchRepository.findExpiredBatchMetricsByPharmacyId(pharmacyId, LocalDate.now());
        for (Object[] row : rows) {
            if (row == null || row.length < 3 || row[0] == null) continue;
            Long skuId = ((Number) row[0]).longValue();
            long count = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            long units = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            map.put(skuId, new ExpiredMetrics(count, units));
        }
        return map;
    }

    private <T> PageResponseDto<T> paginateList(List<T> list, int page, int size) {
        int pageIndex = Math.max(0, page);
        int pageSize = size > 0 ? size : 10;
        int totalElements = list.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / pageSize);
        int fromIndex = Math.min(pageIndex * pageSize, totalElements);
        int toIndex = Math.min(fromIndex + pageSize, totalElements);
        List<T> slice = list.subList(fromIndex, toIndex);
        return PageResponseDto.<T>builder()
                .content(slice)
                .currentPage(pageIndex + 1)
                .pageSize(pageSize)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .isLast(pageIndex + 1 >= totalPages)
                .build();
    }

    private static class OrderPipelineMetrics {
        long pendingUnits = 0L;
        long toReceiveUnits = 0L;
    }

    private record ExpiredMetrics(long batchCount, long units) {}
}
