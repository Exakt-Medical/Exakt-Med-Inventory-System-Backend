package com.example.backend.service;

import com.example.backend.dto.common.PageResponseDto;
import com.example.backend.dto.order.OrderItemRequestDto;
import com.example.backend.dto.order.OrderResponseDto;
import com.example.backend.dto.reorderQuotation.*;
import com.example.backend.entity.*;
import com.example.backend.repository.*;
import com.example.backend.security.FacilityAccessGuard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReorderQuotationService {

    private final ReorderQuotationRepository quotationRepository;
    private final ReorderQuotationItemRepository quotationItemRepository;
    private final PharmacyRepository pharmacyRepository;
    private final SupplierRepository supplierRepository;
    private final GenericStockSettingRepository genericStockSettingRepository;
    private final SkuRepository skuRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final OrderedItemRepository orderedItemRepository;
    private final AuditLogService auditLogService;
    private final FacilityAccessGuard facilityAccessGuard;
    private final EmailNotificationService emailNotificationService;

    public ReorderQuotationService(ReorderQuotationRepository quotationRepository,
                                   ReorderQuotationItemRepository quotationItemRepository,
                                   PharmacyRepository pharmacyRepository,
                                   SupplierRepository supplierRepository,
                                   GenericStockSettingRepository genericStockSettingRepository,
                                   SkuRepository skuRepository,
                                   UserRepository userRepository,
                                   OrderRepository orderRepository,
                                   OrderedItemRepository orderedItemRepository,
                                   AuditLogService auditLogService,
                                   FacilityAccessGuard facilityAccessGuard,
                                   EmailNotificationService emailNotificationService) {
        this.quotationRepository = quotationRepository;
        this.quotationItemRepository = quotationItemRepository;
        this.pharmacyRepository = pharmacyRepository;
        this.supplierRepository = supplierRepository;
        this.genericStockSettingRepository = genericStockSettingRepository;
        this.skuRepository = skuRepository;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.orderedItemRepository = orderedItemRepository;
        this.auditLogService = auditLogService;
        this.facilityAccessGuard = facilityAccessGuard;
        this.emailNotificationService = emailNotificationService;
    }

    // 1. CREATE REORDER QUOTATION (Pharmacist / Admin / Procurement)
    @Transactional
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Pharmacist', 'Procurement')")
    public ReorderQuotationResponseDto createQuotation(ReorderQuotationRequestDto request) {
        if (request.getPharmacyId() == null) {
            throw new RuntimeException("Pharmacy ID is required");
        }
        facilityAccessGuard.verifyPharmacyAccess(request.getPharmacyId());

        Pharmacy pharmacy = pharmacyRepository.findById(request.getPharmacyId())
                .orElseThrow(() -> new RuntimeException("Pharmacy not found with id: " + request.getPharmacyId()));

        Supplier supplier = null;
        if (request.getSupplierId() != null) {
            supplier = supplierRepository.findById(request.getSupplierId())
                    .orElseThrow(() -> new RuntimeException("Supplier not found with id: " + request.getSupplierId()));
        }

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new RuntimeException("Quotation must contain at least one line item.");
        }

        User currentUser = getCurrentUser();
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Manila"));

        ReorderQuotation quotation = new ReorderQuotation();
        quotation.setPharmacy(pharmacy);
        quotation.setSupplier(supplier);
        quotation.setUser(currentUser);
        String tempQuotationNumber = "QT-TMP-" + System.currentTimeMillis() + "-" + java.util.UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        quotation.setQuotationNumber(tempQuotationNumber);
        quotation.setPriority(request.getPriority() != null && !request.getPriority().isBlank()
                ? request.getPriority()
                : "Normal");
        quotation.setNotes(request.getNotes());
        quotation.setStatus(ReorderQuotation.Status.Pending);

        ReorderQuotation savedQuotation = quotationRepository.saveAndFlush(quotation);

        // Generate Quotation Number format: QT-[year]-[month]-[id]
        String quotationNumber = String.format("QT-%d-%02d-%04d",
                now.getYear(),
                now.getMonthValue(),
                savedQuotation.getId());
        savedQuotation.setQuotationNumber(quotationNumber);

        long totalUnits = 0L;
        long totalEstimatedCost = 0L;

        for (ReorderQuotationItemRequestDto itemDto : request.getItems()) {
            Sku sku = null;
            GenericStockSetting setting = null;

            if (itemDto.getGenericStockSettingId() != null) {
                setting = genericStockSettingRepository.findByIdWithMedicine(itemDto.getGenericStockSettingId())
                        .orElseThrow(() -> new RuntimeException("Generic stock setting not found with id: " + itemDto.getGenericStockSettingId()));
            }
            if (itemDto.getSkuId() != null) {
                sku = skuRepository.findByIdWithSetting(itemDto.getSkuId())
                        .orElseThrow(() -> new RuntimeException("SKU not found with id: " + itemDto.getSkuId()));
                if (setting == null) {
                    setting = sku.getGenericStockSetting();
                }
            }
            if (setting == null) {
                throw new RuntimeException("Each quotation line item must specify a genericStockSettingId (or skuId).");
            }

            long units = itemDto.getRequestedUnits() != null ? itemDto.getRequestedUnits() : 1L;
            long estPrice = itemDto.getEstimatedPrice() != null ? itemDto.getEstimatedPrice() : 0L;

            ReorderQuotationItem qi = new ReorderQuotationItem();
            qi.setQuotation(savedQuotation);
            qi.setGenericStockSetting(setting);
            qi.setSku(sku);
            qi.setRequestedUnits(units);
            qi.setEstimatedPrice(estPrice);
            qi.setNotes(itemDto.getNotes());

            savedQuotation.getItems().add(qi);
            totalUnits += units;
            totalEstimatedCost += estPrice;
        }

        savedQuotation.setTotalUnits(totalUnits);
        savedQuotation.setEstimatedTotalCost(totalEstimatedCost);
        savedQuotation = quotationRepository.save(savedQuotation);

        String supplierLabel = supplier != null ? " for supplier '" + supplier.getName() + "'" : " (supplier TBD by Procurement)";
        auditLogService.logAction(
                pharmacy,
                "Purchasing",
                "QUOTATION_CREATED",
                "Reorder Quotation Created (" + quotationNumber + ")",
                AuditLog.Severity.INFO,
                quotationNumber,
                savedQuotation.getId(),
                "Created generic reorder quotation " + quotationNumber + " with " + savedQuotation.getItems().size() + " generic item(s) (" + totalUnits + " units)" + supplierLabel + ".",
                "Admin,Procurement,Pharmacist"
        );

        ReorderQuotationResponseDto response = mapToResponseDto(savedQuotation);
        response.setMessage("Reorder quotation created successfully.");

        // Asynchronously notify assigned procurement officers via email with the detached response DTO
        String facilityName = pharmacy.getFacility() != null ? pharmacy.getFacility().getName() : "";
        emailNotificationService.notifyProcurementOfNewQuotation(response, facilityName);

        return response;
    }

    // 2. GET QUOTATIONS PAGINATED
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public PageResponseDto<ReorderQuotationResponseDto> getQuotationsPaginated(
            Long pharmacyId,
            Long supplierId,
            String statusStr,
            String priority,
            String search,
            int page,
            int size,
            String sortBy,
            String sortDir) {

        if (pharmacyId == null) {
            throw new RuntimeException("Pharmacy ID is required");
        }
        facilityAccessGuard.verifyPharmacyAccess(pharmacyId);

        ReorderQuotation.Status status = null;
        if (statusStr != null && !statusStr.isBlank() && !statusStr.equalsIgnoreCase("ALL")) {
            try {
                status = ReorderQuotation.Status.valueOf(statusStr.trim());
            } catch (IllegalArgumentException ignored) {
            }
        }

        Sort.Direction direction = "ASC".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortProperty = (sortBy != null && !sortBy.isBlank()) ? sortBy : "createdAt";
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by(direction, sortProperty));

        String query = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        Page<ReorderQuotation> quotationPage = quotationRepository.findQuotations(
                pharmacyId, supplierId, status, priority, query, pageable);

        Page<ReorderQuotationResponseDto> dtoPage = quotationPage.map(this::mapToResponseDto);
        return PageResponseDto.from(dtoPage);
    }

    // 3. GET QUOTATION BY ID
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public ReorderQuotationResponseDto getQuotationById(Long id) {
        ReorderQuotation quotation = quotationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reorder quotation not found with id: " + id));

        facilityAccessGuard.verifyPharmacyAccess(quotation.getPharmacy().getId());
        return mapToResponseDto(quotation);
    }

    // 4. UPDATE REORDER QUOTATION (Pharmacist or Procurement while status is Pending)
    @Transactional
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Pharmacist', 'Procurement')")
    public ReorderQuotationResponseDto updateQuotation(Long id, ReorderQuotationRequestDto request) {
        ReorderQuotation quotation = quotationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reorder quotation not found with id: " + id));

        facilityAccessGuard.verifyPharmacyAccess(quotation.getPharmacy().getId());

        if (quotation.getStatus() != ReorderQuotation.Status.Pending) {
            throw new RuntimeException("Cannot modify quotation: status is already " + quotation.getStatus() + ".");
        }

        if (request.getSupplierId() != null) {
            Supplier supplier = supplierRepository.findById(request.getSupplierId())
                    .orElseThrow(() -> new RuntimeException("Supplier not found with id: " + request.getSupplierId()));
            quotation.setSupplier(supplier);
        } else {
            quotation.setSupplier(null);
        }

        if (request.getPriority() != null && !request.getPriority().isBlank()) {
            quotation.setPriority(request.getPriority());
        }

        if (request.getNotes() != null) {
            quotation.setNotes(request.getNotes());
        }

        if (request.getItems() != null && !request.getItems().isEmpty()) {
            quotation.getItems().clear();

            long totalUnits = 0L;
            long totalCost = 0L;

            for (ReorderQuotationItemRequestDto itemDto : request.getItems()) {
                Sku sku = null;
                GenericStockSetting setting = null;
                if (itemDto.getGenericStockSettingId() != null) {
                    setting = genericStockSettingRepository.findByIdWithMedicine(itemDto.getGenericStockSettingId())
                            .orElseThrow(() -> new RuntimeException("Generic stock setting not found with id: " + itemDto.getGenericStockSettingId()));
                }
                if (itemDto.getSkuId() != null) {
                    sku = skuRepository.findByIdWithSetting(itemDto.getSkuId())
                            .orElseThrow(() -> new RuntimeException("SKU not found with id: " + itemDto.getSkuId()));
                    if (setting == null) {
                        setting = sku.getGenericStockSetting();
                    }
                }
                if (setting == null) {
                    throw new RuntimeException("Each quotation line item must specify a genericStockSettingId (or skuId).");
                }

                long units = itemDto.getRequestedUnits() != null ? itemDto.getRequestedUnits() : 1L;
                long estPrice = itemDto.getEstimatedPrice() != null ? itemDto.getEstimatedPrice() : 0L;

                ReorderQuotationItem qi = new ReorderQuotationItem();
                qi.setQuotation(quotation);
                qi.setGenericStockSetting(setting);
                qi.setSku(sku);
                qi.setRequestedUnits(units);
                qi.setEstimatedPrice(estPrice);
                qi.setNotes(itemDto.getNotes());

                quotation.getItems().add(qi);
                totalUnits += units;
                totalCost += estPrice;
            }

            quotation.setTotalUnits(totalUnits);
            quotation.setEstimatedTotalCost(totalCost);
        }

        ReorderQuotation updated = quotationRepository.save(quotation);

        auditLogService.logAction(
                updated.getPharmacy(),
                "Purchasing",
                "QUOTATION_UPDATED",
                "Reorder Quotation Updated (" + updated.getQuotationNumber() + ")",
                AuditLog.Severity.INFO,
                updated.getQuotationNumber(),
                updated.getId(),
                "Updated reorder quotation " + updated.getQuotationNumber() + ".",
                "Admin,Procurement,Pharmacist"
        );

        ReorderQuotationResponseDto response = mapToResponseDto(updated);
        response.setMessage("Reorder quotation updated successfully.");
        return response;
    }

    // 5. CONVERT QUOTATION TO PURCHASE ORDER (Procurement / SuperAdmin)
    @Transactional
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Procurement', 'Admin')")
    public ReorderQuotationResponseDto convertQuotationToOrder(Long id, ConvertQuotationToOrderRequestDto convertRequest) {
        ReorderQuotation quotation = quotationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reorder quotation not found with id: " + id));

        facilityAccessGuard.verifyPharmacyAccess(quotation.getPharmacy().getId());

        if (quotation.getStatus() != ReorderQuotation.Status.Pending) {
            throw new RuntimeException("Quotation " + quotation.getQuotationNumber() + " cannot be converted: status is already " + quotation.getStatus() + ".");
        }

        Supplier supplier = quotation.getSupplier();
        if (convertRequest != null && convertRequest.getSupplierId() != null) {
            supplier = supplierRepository.findById(convertRequest.getSupplierId())
                    .orElseThrow(() -> new RuntimeException("Supplier not found with id: " + convertRequest.getSupplierId()));
        }
        if (supplier == null) {
            throw new RuntimeException("Supplier is required to convert a quotation into a Purchase Order.");
        }
        quotation.setSupplier(supplier);

        String priority = (convertRequest != null && convertRequest.getPriority() != null && !convertRequest.getPriority().isBlank())
                ? convertRequest.getPriority()
                : quotation.getPriority();

        String notes = (convertRequest != null && convertRequest.getNotes() != null && !convertRequest.getNotes().isBlank())
                ? convertRequest.getNotes()
                : quotation.getNotes();

        // Determine items to order: Procurement selects a concrete Brand SKU for each generic quotation line
        List<OrderedItem> orderedItemsToSave = new ArrayList<>();
        long totalOrderedUnits = 0L;
        long calculatedTotalPrice = 0L;

        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Manila"));

        Order order = new Order();
        order.setPharmacy(quotation.getPharmacy());
        order.setSupplier(supplier);
        String tempPoNumber = "PO-TMP-" + System.currentTimeMillis() + "-" + java.util.UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        order.setPurchaseOrderNum(tempPoNumber);
        order.setPriority(priority);
        order.setNotes(notes);
        order.setTotalOrderedUnits(0L);
        order.setTotalPrice(0L);
        order.setStatus(Order.Status.Pending);

        Order savedOrder = orderRepository.saveAndFlush(order);

        String poNumber = String.format("PO-%d-%02d-%04d",
                now.getYear(),
                now.getMonthValue(),
                savedOrder.getId());
        savedOrder.setPurchaseOrderNum(poNumber);

        java.util.Map<Long, ReorderQuotationItem> qiById = quotation.getItems().stream()
                .filter(qi -> qi.getId() != null)
                .collect(Collectors.toMap(ReorderQuotationItem::getId, qi -> qi, (a, b) -> a));
        java.util.Map<Long, ReorderQuotationItem> qiBySettingId = quotation.getItems().stream()
                .filter(qi -> qi.getGenericStockSetting() != null && qi.getGenericStockSetting().getId() != null)
                .collect(Collectors.toMap(qi -> qi.getGenericStockSetting().getId(), qi -> qi, (a, b) -> a));

        if (convertRequest != null && convertRequest.getItems() != null && !convertRequest.getItems().isEmpty()) {
            for (OrderItemRequestDto itemDto : convertRequest.getItems()) {
                if (itemDto.getSkuId() == null) {
                    throw new RuntimeException("Please select a Brand SKU for every line item before converting to a Purchase Order.");
                }
                Sku sku = skuRepository.findByIdWithSetting(itemDto.getSkuId())
                        .orElseThrow(() -> new RuntimeException("SKU not found with id: " + itemDto.getSkuId()));

                long units = itemDto.getOrderedUnits() != null ? itemDto.getOrderedUnits() : 1L;
                long price = itemDto.getPrice() != null ? itemDto.getPrice() : 0L;

                OrderedItem oi = new OrderedItem();
                oi.setOrder(savedOrder);
                oi.setSku(sku);
                oi.setOrderedUnits(units);
                oi.setReceivedUnits(0L);
                oi.setPrice(price);

                double calculatedPricePerUnit = units > 0
                        ? Math.round(((double) price / units) * 100.0) / 100.0
                        : 0.0;
                oi.setPricePerUnit(calculatedPricePerUnit);

                orderedItemsToSave.add(oi);
                totalOrderedUnits += units;
                calculatedTotalPrice += price;

                // Link chosen Brand SKU & negotiated pricing back onto the ReorderQuotationItem
                ReorderQuotationItem matchedQi = itemDto.getQuotationItemId() != null
                        ? qiById.get(itemDto.getQuotationItemId())
                        : (sku.getGenericStockSetting() != null ? qiBySettingId.get(sku.getGenericStockSetting().getId()) : null);
                if (matchedQi != null) {
                    matchedQi.setSku(sku);
                    matchedQi.setEstimatedPrice(price);
                    matchedQi.setRequestedUnits(units);
                }
            }
        } else {
            // Fallback when converting without custom items (only valid if every line already has a brand SKU)
            for (ReorderQuotationItem qi : quotation.getItems()) {
                if (qi.getSku() == null) {
                    String gName = qi.getGenericStockSetting() != null && qi.getGenericStockSetting().getLibMedicine() != null
                            ? qi.getGenericStockSetting().getLibMedicine().getDrugDescription() : "generic item";
                    throw new RuntimeException("Brand SKU must be selected for '" + gName + "' before converting to a Purchase Order.");
                }
                OrderedItem oi = new OrderedItem();
                oi.setOrder(savedOrder);
                oi.setSku(qi.getSku());
                long units = qi.getRequestedUnits() != null ? qi.getRequestedUnits() : 1L;
                long price = qi.getEstimatedPrice() != null ? qi.getEstimatedPrice() : 0L;

                oi.setOrderedUnits(units);
                oi.setReceivedUnits(0L);
                oi.setPrice(price);

                double calculatedPricePerUnit = units > 0
                        ? Math.round(((double) price / units) * 100.0) / 100.0
                        : 0.0;
                oi.setPricePerUnit(calculatedPricePerUnit);

                orderedItemsToSave.add(oi);
                totalOrderedUnits += units;
                calculatedTotalPrice += price;
            }
        }

        if (orderedItemsToSave.isEmpty()) {
            throw new RuntimeException("Cannot convert quotation: At least one item must be included in the purchase order.");
        }

        long finalTotalPrice = (convertRequest != null && convertRequest.getTotalPrice() != null && convertRequest.getTotalPrice() > 0)
                ? convertRequest.getTotalPrice()
                : calculatedTotalPrice;

        savedOrder.setTotalOrderedUnits(totalOrderedUnits);
        savedOrder.setTotalPrice(finalTotalPrice);
        savedOrder = orderRepository.save(savedOrder);

        orderedItemRepository.saveAll(orderedItemsToSave);

        quotation.setStatus(ReorderQuotation.Status.Approved);
        quotation.setOrder(savedOrder);
        quotation.setTotalUnits(totalOrderedUnits);
        quotation.setEstimatedTotalCost(finalTotalPrice);
        quotation = quotationRepository.save(quotation);

        auditLogService.logAction(
                quotation.getPharmacy(),
                "Purchasing",
                "QUOTATION_CONVERTED_TO_PO",
                "Quotation " + quotation.getQuotationNumber() + " Converted to " + poNumber,
                AuditLog.Severity.INFO,
                poNumber,
                savedOrder.getId(),
                "Procurement approved quotation " + quotation.getQuotationNumber() + " and created purchase order " + poNumber + " (" + totalOrderedUnits + " units).",
                "Admin,Procurement"
        );

        auditLogService.logAction(
                quotation.getPharmacy(),
                "Purchasing",
                "PURCHASE_ORDER_CREATED",
                "Purchase Order Created (" + poNumber + ")",
                AuditLog.Severity.INFO,
                poNumber,
                savedOrder.getId(),
                "Purchase order " + poNumber + " created from quotation " + quotation.getQuotationNumber() + ". Status: Pending.",
                "Admin,Procurement"
        );

        ReorderQuotationResponseDto response = mapToResponseDto(quotation);
        response.setMessage("Quotation successfully approved and converted into Purchase Order " + poNumber + ".");

        // Asynchronously notify assigned Admin users via email
        String facilityName = quotation.getPharmacy() != null && quotation.getPharmacy().getFacility() != null
                ? quotation.getPharmacy().getFacility().getName() : "";
        String creatorName = resolveCurrentUserName();
        OrderResponseDto orderDto = mapToOrderResponseDto(savedOrder, orderedItemsToSave);
        emailNotificationService.notifyAdminOfNewPurchaseOrder(orderDto, facilityName, creatorName);

        // Asynchronously notify Pharmacists that the quotation was approved & converted to PO
        emailNotificationService.notifyPharmacistOfQuotationStatusUpdate(response, facilityName, creatorName);

        return response;
    }

    // 6. REJECT QUOTATION (Procurement / SuperAdmin)
    @Transactional
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Procurement', 'Admin')")
    public ReorderQuotationResponseDto rejectQuotation(Long id, RejectQuotationRequestDto rejectRequest) {
        ReorderQuotation quotation = quotationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reorder quotation not found with id: " + id));

        facilityAccessGuard.verifyPharmacyAccess(quotation.getPharmacy().getId());

        if (quotation.getStatus() != ReorderQuotation.Status.Pending) {
            throw new RuntimeException("Cannot reject quotation: status is already " + quotation.getStatus() + ".");
        }

        quotation.setStatus(ReorderQuotation.Status.Rejected);
        quotation.setRejectionReason(rejectRequest != null ? rejectRequest.getRejectionReason() : "Rejected by procurement officer.");
        ReorderQuotation saved = quotationRepository.save(quotation);

        auditLogService.logAction(
                saved.getPharmacy(),
                "Purchasing",
                "QUOTATION_REJECTED",
                "Reorder Quotation Rejected (" + saved.getQuotationNumber() + ")",
                AuditLog.Severity.WARNING,
                saved.getQuotationNumber(),
                saved.getId(),
                "Procurement rejected quotation " + saved.getQuotationNumber() + ". Reason: " + saved.getRejectionReason(),
                "Admin,Procurement,Pharmacist"
        );

        ReorderQuotationResponseDto response = mapToResponseDto(saved);
        response.setMessage("Reorder quotation has been rejected.");

        // Asynchronously notify Pharmacists that the quotation was denied/rejected
        String facilityName = (saved.getPharmacy() != null && saved.getPharmacy().getFacility() != null)
                ? saved.getPharmacy().getFacility().getName() : "";
        String reviewerName = resolveCurrentUserName();
        emailNotificationService.notifyPharmacistOfQuotationStatusUpdate(response, facilityName, reviewerName);

        return response;
    }

    // 7. CANCEL QUOTATION (Creator / Pharmacist / Admin while Pending)
    @Transactional
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Pharmacist', 'Procurement')")
    public ReorderQuotationResponseDto cancelQuotation(Long id) {
        ReorderQuotation quotation = quotationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reorder quotation not found with id: " + id));

        facilityAccessGuard.verifyPharmacyAccess(quotation.getPharmacy().getId());

        if (quotation.getStatus() != ReorderQuotation.Status.Pending) {
            throw new RuntimeException("Cannot cancel quotation: status is already " + quotation.getStatus() + ".");
        }

        quotation.setStatus(ReorderQuotation.Status.Cancelled);
        ReorderQuotation saved = quotationRepository.save(quotation);

        auditLogService.logAction(
                saved.getPharmacy(),
                "Purchasing",
                "QUOTATION_CANCELLED",
                "Reorder Quotation Cancelled (" + saved.getQuotationNumber() + ")",
                AuditLog.Severity.INFO,
                saved.getQuotationNumber(),
                saved.getId(),
                "Reorder quotation " + saved.getQuotationNumber() + " was cancelled.",
                "Admin,Procurement,Pharmacist"
        );

        ReorderQuotationResponseDto response = mapToResponseDto(saved);
        response.setMessage("Reorder quotation cancelled successfully.");
        return response;
    }

    // Helper: Map Entity to Response DTO
    private ReorderQuotationResponseDto mapToResponseDto(ReorderQuotation q) {
        java.util.Map<Long, OrderedItem> orderItemsBySku = java.util.Collections.emptyMap();
        if (q.getOrder() != null && q.getOrder().getId() != null) {
            List<OrderedItem> ois = orderedItemRepository.findByOrderId(q.getOrder().getId());
            if (ois != null && !ois.isEmpty()) {
                orderItemsBySku = ois.stream()
                        .filter(oi -> oi.getSku() != null && oi.getSku().getId() != null)
                        .collect(java.util.stream.Collectors.toMap(oi -> oi.getSku().getId(), oi -> oi, (a, b) -> a));
            }
        }

        List<ReorderQuotationItemResponseDto> itemDtos = new ArrayList<>();
        if (q.getItems() != null) {
            for (ReorderQuotationItem item : q.getItems()) {
                Sku sku = item.getSku();
                GenericStockSetting setting = item.getGenericStockSetting() != null
                        ? item.getGenericStockSetting()
                        : (sku != null ? sku.getGenericStockSetting() : null);

                long units = item.getRequestedUnits() != null ? item.getRequestedUnits() : 0L;
                long estPrice = item.getEstimatedPrice() != null ? item.getEstimatedPrice() : 0L;

                OrderedItem linkedOi = sku != null ? orderItemsBySku.get(sku.getId()) : null;
                if (estPrice == 0L && linkedOi != null && linkedOi.getPrice() != null) {
                    estPrice = linkedOi.getPrice();
                    if (units == 0L && linkedOi.getOrderedUnits() != null) {
                        units = linkedOi.getOrderedUnits();
                    }
                }

                double perUnit = units > 0 ? Math.round(((double) estPrice / units) * 100.0) / 100.0 : 0.0;

                String genericName = setting != null && setting.getLibMedicine() != null
                        ? setting.getLibMedicine().getDrugDescription()
                        : (sku != null && sku.getLibMedicine() != null ? sku.getLibMedicine().getDrugDescription() : "");
                Long pooledStock = setting != null && setting.getTotalUnits() != null
                        ? setting.getTotalUnits()
                        : (sku != null && sku.getUnits() != null ? sku.getUnits() : 0L);

                ReorderQuotationItemResponseDto itemDto = ReorderQuotationItemResponseDto.builder()
                        .id(item.getId())
                        .genericStockSettingId(setting != null ? setting.getId() : null)
                        .medicineId(setting != null && setting.getLibMedicine() != null ? setting.getLibMedicine().getId() : null)
                        .skuId(sku != null ? sku.getId() : null)
                        .skuCode(sku != null ? sku.getName() : null)
                        .brandName(sku != null ? sku.getBrandName() : null)
                        .genericName(genericName)
                        .dosageForm(setting != null ? setting.getDosageForm() : (sku != null ? sku.getDosageForm() : ""))
                        .dosageStrength(setting != null ? setting.getDosageStrength() : (sku != null ? sku.getDosageStrength() : ""))
                        .packagingUnit(setting != null ? setting.getPackagingUnit() : (sku != null ? sku.getPackagingUnit() : ""))
                        .currentStock(pooledStock)
                        .requestedUnits(units)
                        .estimatedPrice(estPrice)
                        .estimatedPricePerUnit(perUnit)
                        .notes(item.getNotes())
                        .build();

                itemDtos.add(itemDto);
            }
        }

        long totalCost = q.getEstimatedTotalCost() != null ? q.getEstimatedTotalCost() : 0L;
        if (totalCost == 0L && q.getOrder() != null && q.getOrder().getTotalPrice() != null) {
            totalCost = q.getOrder().getTotalPrice();
        }

        String poStatus = (q.getOrder() != null && q.getOrder().getStatus() != null)
                ? q.getOrder().getStatus().name()
                : null;

        return ReorderQuotationResponseDto.builder()
                .id(q.getId())
                .quotationNumber(q.getQuotationNumber())
                .pharmacyId(q.getPharmacy() != null ? q.getPharmacy().getId() : null)
                .pharmacyName(q.getPharmacy() != null ? q.getPharmacy().getPharmacyName() : null)
                .supplierId(q.getSupplier() != null ? q.getSupplier().getId() : null)
                .supplierName(q.getSupplier() != null ? q.getSupplier().getName() : null)
                .userId(q.getUser() != null ? q.getUser().getId() : null)
                .userName(q.getUser() != null ? (q.getUser().getName() != null && !q.getUser().getName().isBlank() ? q.getUser().getName() : q.getUser().getUsername()) : null)
                .orderId(q.getOrder() != null ? q.getOrder().getId() : null)
                .purchaseOrderNum(q.getOrder() != null ? q.getOrder().getPurchaseOrderNum() : null)
                .purchaseOrderStatus(poStatus)
                .priority(q.getPriority())
                .status(q.getStatus())
                .totalUnits(q.getTotalUnits())
                .estimatedTotalCost(totalCost)
                .notes(q.getNotes())
                .rejectionReason(q.getRejectionReason())
                .items(itemDtos)
                .createdAt(q.getCreatedAt())
                .updatedAt(q.getUpdatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public ReorderQuotationSummaryDto getQuotationSummary(Long pharmacyId) {
        if (pharmacyId == null) {
            throw new RuntimeException("Pharmacy ID is strictly required.");
        }
        facilityAccessGuard.verifyPharmacyAccess(pharmacyId);
        if (!pharmacyRepository.existsById(pharmacyId)) {
            throw new RuntimeException("Pharmacy not found with id: " + pharmacyId);
        }

        long total = quotationRepository.countByPharmacyId(pharmacyId);
        long pending = quotationRepository.countByPharmacyIdAndStatus(pharmacyId, ReorderQuotation.Status.Pending);
        long approved = quotationRepository.countByPharmacyIdAndStatus(pharmacyId, ReorderQuotation.Status.Approved);
        long rejected = quotationRepository.countByPharmacyIdAndStatus(pharmacyId, ReorderQuotation.Status.Rejected);
        long cancelled = quotationRepository.countByPharmacyIdAndStatus(pharmacyId, ReorderQuotation.Status.Cancelled);

        return ReorderQuotationSummaryDto.builder()
                .totalQuotations(total)
                .pendingCount(pending)
                .approvedCount(approved)
                .rejectedCount(rejected)
                .cancelledCount(cancelled)
                .build();
    }

    private String resolveCurrentUserName() {
        User currentUser = getCurrentUser();
        if (currentUser != null) {
            return (currentUser.getName() != null && !currentUser.getName().isBlank())
                    ? currentUser.getName()
                    : currentUser.getUsername();
        }
        return "Procurement Officer";
    }

    private User getCurrentUser() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                String username = authentication.getName();
                return userRepository.findByUsername(username).orElse(null);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private OrderResponseDto mapToOrderResponseDto(Order order, List<OrderedItem> items) {
        List<com.example.backend.dto.order.OrderItemResponseDto> itemDtos = new ArrayList<>();
        if (items != null) {
            for (OrderedItem oi : items) {
                com.example.backend.dto.order.OrderItemResponseDto itemDto = new com.example.backend.dto.order.OrderItemResponseDto();
                itemDto.setId(oi.getId());
                if (oi.getSku() != null) {
                    itemDto.setSkuId(oi.getSku().getId());
                    itemDto.setSkuName(oi.getSku().getName());
                    itemDto.setBrandName(oi.getSku().getBrandName());
                    itemDto.setPackagingUnit(oi.getSku().getPackagingUnit());
                    if (oi.getSku().getLibMedicine() != null) {
                        itemDto.setGenericName(oi.getSku().getLibMedicine().getDrugDescription());
                    }
                }
                itemDto.setOrderedUnits(oi.getOrderedUnits());
                itemDto.setPrice(oi.getPrice());
                itemDto.setPricePerUnit(oi.getPricePerUnit());
                itemDtos.add(itemDto);
            }
        }

        OrderResponseDto dto = new OrderResponseDto();
        dto.setId(order.getId());
        dto.setPurchaseOrderNum(order.getPurchaseOrderNum());
        if (order.getPharmacy() != null) {
            dto.setPharmacyId(order.getPharmacy().getId());
            dto.setPharmacyName(order.getPharmacy().getPharmacyName());
        }
        if (order.getSupplier() != null) {
            dto.setSupplierId(order.getSupplier().getId());
            dto.setSupplierName(order.getSupplier().getName());
        }
        dto.setPriority(order.getPriority());
        dto.setTotalOrderedUnits(order.getTotalOrderedUnits());
        dto.setTotalPrice(order.getTotalPrice());
        dto.setNotes(order.getNotes());
        dto.setStatus(order.getStatus());
        dto.setItems(itemDtos);
        return dto;
    }
}

