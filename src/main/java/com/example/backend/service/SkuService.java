package com.example.backend.service;

import com.example.backend.dto.sku.SkuRequestDto;
import com.example.backend.dto.sku.SkuResponseDto;
import com.example.backend.dto.sku.SkuStockAdjustmentDto;
import com.example.backend.entity.AuditLog;
import com.example.backend.entity.Batch;
import com.example.backend.entity.Facility;
import com.example.backend.entity.InventoryTransaction;
import com.example.backend.entity.LibMedicine;
import com.example.backend.entity.Sku;
import com.example.backend.entity.User;
import com.example.backend.entity.RestockRequest;
import com.example.backend.entity.Order;
import com.example.backend.entity.OrderedItem;
import com.example.backend.entity.DispensedLog;
import com.example.backend.repository.DispensedLogRepository;
import com.example.backend.repository.FacilityRepository;
import com.example.backend.repository.LibMedicineRepository;
import com.example.backend.repository.OrderedItemRepository;
import com.example.backend.entity.LibPackagingUnit;
import com.example.backend.repository.LibPackagingUnitRepository;
import com.example.backend.repository.RestockRequestRepository;
import com.example.backend.repository.SkuRepository;
import com.example.backend.repository.UserRepository;
import com.example.backend.dto.common.PageResponseDto;
import com.example.backend.dto.sku.SkuDropdownDto;
import com.example.backend.dto.sku.SkuSummaryDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class SkuService {

    private final SkuRepository skuRepository;
    private final FacilityRepository facilityRepository;
    private final LibMedicineRepository libMedicineRepository;
    private final BatchService batchService;
    private final InventoryTransactionService inventoryTransactionService;
    private final AuditLogService auditLogService;
    private final UserRepository userRepository;
    private final RestockRequestRepository restockRequestRepository;
    private final OrderedItemRepository orderedItemRepository;
    private final LibPackagingUnitRepository libPackagingUnitRepository;
    private final DispensedLogRepository dispensedLogRepository;

    public SkuService(SkuRepository skuRepository,
                      FacilityRepository facilityRepository,
                      LibMedicineRepository libMedicineRepository,
                      BatchService batchService,
                      InventoryTransactionService inventoryTransactionService,
                      AuditLogService auditLogService,
                      UserRepository userRepository,
                      RestockRequestRepository restockRequestRepository,
                      OrderedItemRepository orderedItemRepository,
                      LibPackagingUnitRepository libPackagingUnitRepository,
                      DispensedLogRepository dispensedLogRepository) {
        this.skuRepository = skuRepository;
        this.facilityRepository = facilityRepository;
        this.libMedicineRepository = libMedicineRepository;
        this.batchService = batchService;
        this.inventoryTransactionService = inventoryTransactionService;
        this.auditLogService = auditLogService;
        this.userRepository = userRepository;
        this.restockRequestRepository = restockRequestRepository;
        this.orderedItemRepository = orderedItemRepository;
        this.libPackagingUnitRepository = libPackagingUnitRepository;
        this.dispensedLogRepository = dispensedLogRepository;
    }

    // --- AUTOMATED ATTRIBUTE EXTRACTION & SKU GENERATION ---

    public String extractDosageForm(LibMedicine med) {
        if (med == null) return "TABLET";
        String formCode = med.getFormCode();
        if (formCode != null && !formCode.trim().isEmpty()) {
            String upper = formCode.trim().toUpperCase();
            if (upper.startsWith("TAB")) return "TABLET";
            if (upper.startsWith("CAP")) return "CAPSULE";
            if (upper.startsWith("SYR")) return "SYRUP";
            if (upper.startsWith("SUS")) return "SUSPENSION";
            if (upper.startsWith("INJ")) return "INJECTION";
            if (upper.startsWith("SOL")) return "SOLUTION";
            if (upper.startsWith("OIN") || upper.startsWith("EYO")) return "OINTMENT";
            if (upper.startsWith("CRM")) return "CREAM";
            if (upper.startsWith("DRP")) return "DROPS";
            if (upper.startsWith("INH")) return "INHALER";
            if (upper.startsWith("SUP")) return "SUPPOSITORY";
            if (upper.startsWith("POW")) return "POWDER";
            if (upper.startsWith("SAC")) return "SACHET";
            if (upper.startsWith("PAT")) return "PATCH";
        }

        String desc = med.getDrugDescription() != null ? med.getDrugDescription().toUpperCase() : "";
        if (desc.contains("TABLET")) return "TABLET";
        if (desc.contains("CAPSULE")) return "CAPSULE";
        if (desc.contains("SYRUP")) return "SYRUP";
        if (desc.contains("SUSPENSION")) return "SUSPENSION";
        if (desc.contains("SOLUTION")) return "SOLUTION";
        if (desc.contains("INJECTION") || desc.contains("INJECTABLE")) return "INJECTION";
        if (desc.contains("OINTMENT")) return "OINTMENT";
        if (desc.contains("CREAM")) return "CREAM";
        if (desc.contains("DROPS")) return "DROPS";
        if (desc.contains("INHALER")) return "INHALER";
        if (desc.contains("SUPPOSITORY")) return "SUPPOSITORY";
        if (desc.contains("POWDER")) return "POWDER";
        if (desc.contains("SACHET")) return "SACHET";
        if (desc.contains("PATCH")) return "PATCH";
        if (desc.contains("LOTION")) return "LOTION";
        if (desc.contains("GEL")) return "GEL";

        return "TABLET";
    }

    public String getDosageFormCode(String dosageForm) {
        if (dosageForm == null || dosageForm.trim().isEmpty()) return "TAB";
        String upper = dosageForm.trim().toUpperCase();
        if (upper.contains("TABLET") || upper.equals("TAB")) return "TAB";
        if (upper.contains("CAPSULE") || upper.equals("CAP")) return "CAP";
        if (upper.contains("SYRUP") || upper.equals("SYR")) return "SYR";
        if (upper.contains("SUSPENSION") || upper.equals("SUS")) return "SUS";
        if (upper.contains("INJECTION") || upper.contains("INJECTABLE") || upper.equals("INJ")) return "INJ";
        if (upper.contains("SOLUTION") || upper.equals("SOL")) return "SOL";
        if (upper.contains("OINTMENT") || upper.equals("OIN")) return "OIN";
        if (upper.contains("CREAM") || upper.equals("CRM")) return "CRM";
        if (upper.contains("DROPS") || upper.equals("DRP")) return "DRP";
        if (upper.contains("INHALER") || upper.equals("INH")) return "INH";
        if (upper.contains("SUPPOSITORY") || upper.equals("SUP")) return "SUP";
        if (upper.contains("POWDER") || upper.equals("POW")) return "POW";
        if (upper.contains("SACHET") || upper.equals("SAC")) return "SAC";
        if (upper.contains("PATCH") || upper.equals("PAT")) return "PAT";
        if (upper.contains("LOTION") || upper.equals("LOT")) return "LOT";
        if (upper.contains("GEL")) return "GEL";

        String clean = upper.replaceAll("[^A-Z]", "");
        return clean.length() >= 3 ? clean.substring(0, 3) : String.format("%-3s", clean).replace(' ', 'X');
    }

    public String extractStrength(LibMedicine med) {
        if (med == null) return "";

        // 1. Primary: Extract clinical dosage from drugDescription (e.g. "10 mg/mL", "500 mg", "250 mg/5 mL")
        String desc = med.getDrugDescription() != null ? med.getDrugDescription() : "";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "\\b\\d+(?:\\.\\d+)?%|\\b\\d+(?:\\.\\d+)?\\s*(?:mg|mcg|µg|g|iu|units?|u|meq|mmol)(?:\\s*/\\s*\\d*(?:\\.\\d+)?\\s*(?:ml|l|g|dose|drop|actuation))?(?:\\s*\\+\\s*\\d+(?:\\.\\d+)?\\s*(?:mg|mcg|µg|g|iu|units?|u|meq|mmol)(?:\\s*/\\s*\\d*(?:\\.\\d+)?\\s*(?:ml|l|g))?)*",
                java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher matcher = pattern.matcher(desc);
        if (matcher.find()) {
            return matcher.group().trim();
        }

        // 2. Fallback: Check if strengthCode is non-zero
        if (med.getStrengthCode() != null && !med.getStrengthCode().trim().isEmpty() && !med.getStrengthCode().equals("00000")) {
            String str = med.getStrengthCode().trim().replaceFirst("^0+(?!$)", "");
            String unit = med.getUnitCode() != null ? med.getUnitCode().trim() : "";
            if (!str.isEmpty()) {
                return (str + " " + unit).trim();
            }
        }

        return "";
    }

    public String getStrengthDigits(String strength) {
        if (strength == null || strength.trim().isEmpty()) return "";
        if (strength.contains("+")) {
            String[] parts = strength.split("\\+");
            StringBuilder sb = new StringBuilder();
            for (String p : parts) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+(?:\\.\\d+)?").matcher(p);
                if (m.find()) {
                    if (sb.length() > 0) sb.append("+");
                    sb.append(m.group().replace(".", ""));
                }
            }
            return sb.toString();
        } else {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+(?:\\.\\d+)?").matcher(strength);
            if (m.find()) {
                return m.group().replace(".", "");
            }
        }
        return "";
    }

    public String extractGenericCode(LibMedicine med) {
        if (med == null) return "MEDIC";
        String desc = med.getDrugDescription() != null ? med.getDrugDescription().trim() : "";

        // Remove parenthetical salt notes like "( as TRIHYDRATE)"
        String cleaned = desc.replaceAll("(?i)\\(\\s*as\\s+[^)]+\\)", "").trim();

        // Check combination with "+"
        if (cleaned.contains("+")) {
            String insideParens = "";
            java.util.regex.Matcher pm = java.util.regex.Pattern.compile("\\(([^)]+\\+[^)]+)\\)").matcher(cleaned);
            if (pm.find()) {
                insideParens = pm.group(1);
            }
            String sourceToUse = !insideParens.isEmpty() ? insideParens : cleaned;
            String[] parts = sourceToUse.split("\\+");
            List<String> codes = new ArrayList<>();
            for (String part : parts) {
                String word = part.trim().split("\\s+")[0].replaceAll("[^A-Za-z]", "").toUpperCase();
                if (!word.isEmpty()) {
                    codes.add(word.length() >= 5 ? word.substring(0, 5) : word);
                }
            }
            if (!codes.isEmpty()) {
                return String.join("+", codes);
            }
        }

        // Single active ingredient: extract the first clinical word (5 letters)
        String textNoLeadingNums = cleaned.replaceFirst("^\\s*\\d+(?:\\.\\d+)?%?\\s*", "");
        String[] words = textNoLeadingNums.split("\\s+");
        for (String w : words) {
            String wordClean = w.replaceAll("[^A-Za-z]", "").toUpperCase();
            if (wordClean.length() >= 3) {
                return wordClean.length() >= 5 ? wordClean.substring(0, 5) : wordClean;
            }
        }

        return "MEDIC";
    }

    public static class PackagingInfo {
        public final String name;
        public final String code;
        public PackagingInfo(String name, String code) {
            this.name = name;
            this.code = code;
        }
    }

    public PackagingInfo resolvePackaging(SkuRequestDto request, LibMedicine med) {
        // 1. If packagingUnitCode provided, look up in lib_packaging_unit
        if (request.getPackagingUnitCode() != null && !request.getPackagingUnitCode().trim().isEmpty()) {
            Optional<LibPackagingUnit> opt = libPackagingUnitRepository.findByCode(request.getPackagingUnitCode().trim().toUpperCase());
            if (opt.isPresent()) {
                return new PackagingInfo(opt.get().getName(), opt.get().getCode());
            }
        }

        // 2. If packagingUnit name provided, look up in lib_packaging_unit
        if (request.getPackagingUnit() != null && !request.getPackagingUnit().trim().isEmpty()) {
            Optional<LibPackagingUnit> opt = libPackagingUnitRepository.findByNameIgnoreCase(request.getPackagingUnit().trim());
            if (opt.isPresent()) {
                return new PackagingInfo(opt.get().getName(), opt.get().getCode());
            }
            String customName = request.getPackagingUnit().trim();
            String digits = customName.replaceAll("\\D", "");
            String letters = customName.replaceAll("[^A-Za-z]", "").toUpperCase();
            String prefix = letters.length() >= 2 ? letters.substring(0, 2) : "PK";
            String code = prefix + (digits.length() >= 2 ? digits.substring(0, 2) : (digits.isEmpty() ? "01" : String.format("%02d", Integer.parseInt(digits))));
            return new PackagingInfo(customName, code);
        }

        // 3. Fallback: Parse from drugDescription in libMedicine
        String desc = (med != null && med.getDrugDescription() != null) ? med.getDrugDescription().toUpperCase() : "";

        // Syringes
        if (desc.contains("0.5 ML PRE-FILLED SYRINGE") || desc.contains("0.5ML PRE-FILLED SYRINGE")) return new PackagingInfo("Pre-filled Syringe of 0.5 mL", "PS05");
        if (desc.contains("1 ML PRE-FILLED SYRINGE") || desc.contains("1ML PRE-FILLED SYRINGE")) return new PackagingInfo("Pre-filled Syringe of 1 mL", "PS10");
        if (desc.contains("PRE-FILLED SYRINGE")) return new PackagingInfo("Pre-filled Syringe of 1", "PS01");
        if (desc.contains("SYRINGE")) return new PackagingInfo("Syringe of 1", "SY01");

        // Drops
        if (desc.contains("2.5 ML") && (desc.contains("DROPS") || desc.contains("OPHTHALMIC"))) return new PackagingInfo("Dropper Bottle of 2.5 mL", "DR02");
        if (desc.contains("5 ML") && (desc.contains("DROPS") || desc.contains("OPHTHALMIC"))) return new PackagingInfo("Dropper Bottle of 5 mL", "DR05");
        if (desc.contains("10 ML DROPS") || desc.contains("DROPS 10 ML")) return new PackagingInfo("Dropper Bottle of 10 mL", "DR10");
        if (desc.contains("15 ML DROPS") || desc.contains("DROPS 15 ML")) return new PackagingInfo("Dropper Bottle of 15 mL", "DR15");
        if (desc.contains("30 ML DROPS") || desc.contains("DROPS 30 ML")) return new PackagingInfo("Dropper Bottle of 30 mL", "DR30");

        // Vials
        if (desc.contains("200 ML VIAL") || desc.contains("200ML VIAL")) return new PackagingInfo("Vial of 200 mL", "VL200");
        if (desc.contains("100 ML VIAL") || desc.contains("100ML VIAL")) return new PackagingInfo("Vial of 100 mL", "VL100");
        if (desc.contains("50 ML VIAL") || desc.contains("50ML VIAL")) return new PackagingInfo("Vial of 50 mL", "VL50");
        if (desc.contains("30 ML VIAL") || desc.contains("30ML VIAL")) return new PackagingInfo("Vial of 30 mL", "VL30");
        if (desc.contains("25 ML VIAL") || desc.contains("25ML VIAL")) return new PackagingInfo("Vial of 25 mL", "VL25");
        if (desc.contains("20 ML VIAL") || desc.contains("20ML VIAL")) return new PackagingInfo("Vial of 20 mL", "VL20");
        if (desc.contains("15 ML VIAL") || desc.contains("15ML VIAL")) return new PackagingInfo("Vial of 15 mL", "VL15");
        if (desc.contains("10 ML VIAL") || desc.contains("10ML VIAL")) return new PackagingInfo("Vial of 10 mL", "VL10");
        if (desc.contains("5 ML VIAL") || desc.contains("5ML VIAL")) return new PackagingInfo("Vial of 5 mL", "VL05");
        if (desc.contains("4 ML VIAL") || desc.contains("4ML VIAL")) return new PackagingInfo("Vial of 4 mL", "VL04");
        if (desc.contains("3 ML VIAL") || desc.contains("3ML VIAL")) return new PackagingInfo("Vial of 3 mL", "VL03");
        if (desc.contains("2 ML VIAL") || desc.contains("2ML VIAL")) return new PackagingInfo("Vial of 2 mL", "VL02");
        if (desc.contains("1 ML VIAL") || desc.contains("1ML VIAL")) return new PackagingInfo("Vial of 1 (Single Dose)", "VL01");

        // Ampoules
        if (desc.contains("10 ML AMPULE") || desc.contains("10 ML AMP") || desc.contains("10ML AMP")) return new PackagingInfo("Ampoule of 1 (10 mL)", "AM10");
        if (desc.contains("5 ML AMPULE") || desc.contains("5 ML AMP") || desc.contains("5ML AMP")) return new PackagingInfo("Ampoule of 1 (5 mL)", "AM05");
        if (desc.contains("2 ML AMPULE") || desc.contains("2 ML AMP") || desc.contains("2ML AMP")) return new PackagingInfo("Ampoule of 1 (2 mL)", "AM02");
        if (desc.contains("1 ML AMPULE") || desc.contains("1 ML AMP") || desc.contains("1ML AMP")) return new PackagingInfo("Ampoule of 1 (1 mL)", "AM01");

        // Bottles
        if (desc.contains("GALLON") || desc.contains("GL")) return new PackagingInfo("Gallon of 1 (approx 4 L)", "GL01");
        if (desc.contains("5 L BOTTLE") || desc.contains("5L BOTTLE")) return new PackagingInfo("Bottle of 5 L", "BL5L");
        if (desc.contains("1 L BOTTLE") || desc.contains("1L BOTTLE")) return new PackagingInfo("Bottle of 1 L", "BL1L");
        if (desc.contains("500 ML BOTTLE") || desc.contains("500ML BOTTLE")) return new PackagingInfo("Bottle of 500 mL", "BL500");
        if (desc.contains("250 ML BOTTLE") || desc.contains("250ML BOTTLE")) return new PackagingInfo("Bottle of 250 mL", "BL250");
        if (desc.contains("240 ML BOTTLE") || desc.contains("240ML BOTTLE")) return new PackagingInfo("Bottle of 240 mL", "BL240");
        if (desc.contains("200 ML BOTTLE") || desc.contains("200ML BOTTLE")) return new PackagingInfo("Bottle of 200 mL", "BL200");
        if (desc.contains("150 ML BOTTLE") || desc.contains("150ML BOTTLE")) return new PackagingInfo("Bottle of 150 mL", "BL150");
        if (desc.contains("120 ML BOTTLE") || desc.contains("120ML BOTTLE")) return new PackagingInfo("Bottle of 120 mL", "BL120");
        if (desc.contains("100 ML BOTTLE") || desc.contains("100ML BOTTLE")) return new PackagingInfo("Bottle of 100 mL", "BL100");
        if (desc.contains("70 ML BOTTLE") || desc.contains("70ML BOTTLE")) return new PackagingInfo("Bottle of 70 mL", "BL70");
        if (desc.contains("60 ML BOTTLE") || desc.contains("60ML BOTTLE")) return new PackagingInfo("Bottle of 60 mL", "BL60");
        if (desc.contains("50 ML BOTTLE") || desc.contains("50ML BOTTLE")) return new PackagingInfo("Bottle of 50 mL", "BL50");
        if (desc.contains("30 ML BOTTLE") || desc.contains("30ML BOTTLE")) return new PackagingInfo("Bottle of 30 mL", "BL30");
        if (desc.contains("25 ML BOTTLE") || desc.contains("25ML BOTTLE")) return new PackagingInfo("Bottle of 25 mL", "BL25");
        if (desc.contains("15 ML BOTTLE") || desc.contains("15ML BOTTLE")) return new PackagingInfo("Bottle of 15 mL", "BL15");
        if (desc.contains("10 ML BOTTLE") || desc.contains("10ML BOTTLE")) return new PackagingInfo("Bottle of 10 mL", "BL10");
        if (desc.contains("5 ML BOTTLE") || desc.contains("5ML BOTTLE")) return new PackagingInfo("Bottle of 5 mL", "BL05");

        // IV Bags
        if (desc.contains("1 L BAG") || desc.contains("1L BAG")) return new PackagingInfo("IV Bag of 1 L", "BG1L");
        if (desc.contains("500 ML BAG") || desc.contains("500ML BAG")) return new PackagingInfo("IV Bag of 500 mL", "BG500");
        if (desc.contains("250 ML BAG") || desc.contains("250ML BAG")) return new PackagingInfo("IV Bag of 250 mL", "BG250");
        if (desc.contains("100 ML BAG") || desc.contains("100ML BAG")) return new PackagingInfo("IV Bag of 100 mL", "BG100");

        // Topical Tubes & Jars
        if (desc.contains("500 G JAR") || desc.contains("450 G JAR")) return new PackagingInfo("Jar of 450g / 500g", "JR450");
        if (desc.contains("100 G JAR")) return new PackagingInfo("Jar of 100g", "JR100");
        if (desc.contains("30 G JAR")) return new PackagingInfo("Jar of 30g", "JR30");
        if (desc.contains("15 G JAR")) return new PackagingInfo("Jar of 15g", "JR15");
        if (desc.contains("50 G TUBE") || desc.contains("50G TUBE")) return new PackagingInfo("Tube of 50g", "TB50");
        if (desc.contains("40 G TUBE") || desc.contains("40G TUBE")) return new PackagingInfo("Tube of 40g", "TB40");
        if (desc.contains("30 G TUBE") || desc.contains("30G TUBE")) return new PackagingInfo("Tube of 30g", "TB30");
        if (desc.contains("25 G TUBE") || desc.contains("25G TUBE")) return new PackagingInfo("Tube of 25g", "TB25");
        if (desc.contains("20 G TUBE") || desc.contains("20G TUBE")) return new PackagingInfo("Tube of 20g", "TB20");
        if (desc.contains("15 G TUBE") || desc.contains("15G TUBE")) return new PackagingInfo("Tube of 15g", "TB15");
        if (desc.contains("10 G TUBE") || desc.contains("10G TUBE")) return new PackagingInfo("Tube of 10g", "TB10");
        if (desc.contains("5 G TUBE") || desc.contains("5G TUBE")) return new PackagingInfo("Tube of 5g", "TB05");
        if (desc.contains("4.5 G TUBE") || desc.contains("4G TUBE")) return new PackagingInfo("Tube of 4.5g (Eye Ointment)", "TB04");
        if (desc.contains("3.5 G TUBE") || desc.contains("3.5G TUBE")) return new PackagingInfo("Tube of 3.5g (Eye Ointment)", "TB03");
        if (desc.contains("2.5 G TUBE") || desc.contains("2G TUBE")) return new PackagingInfo("Tube of 2g", "TB02");

        // Nebules, Sachets & Others
        if (desc.contains("2.5 ML NEBULE") || desc.contains("NEBULE")) return new PackagingInfo("Nebule of 1 (2.5 mL)", "NB01");
        if (desc.contains("2 ML NEBULE")) return new PackagingInfo("Nebule of 2 mL", "NB02");
        if (desc.contains("10 ML SACHET") || desc.contains("10ML SACHET")) return new PackagingInfo("Sachet of 10 mL", "SC10");
        if (desc.contains("6 ML SACHET") || desc.contains("6ML SACHET")) return new PackagingInfo("Sachet of 6 mL", "SC06");
        if (desc.contains("SACHET")) return new PackagingInfo("Sachet of 1", "SC01");
        if (desc.contains("CARTRIDGE")) return new PackagingInfo("Cartridge of 1", "CR01");
        if (desc.contains("CARPULE")) return new PackagingInfo("Dental Carpule of 1", "CP01");
        if (desc.contains("CANISTER") || desc.contains("INHALER")) return new PackagingInfo("Inhaler Canister of 1", "IH01");
        if (desc.contains("DISPENSER")) return new PackagingInfo("Dispenser of 1", "DP01");
        if (desc.contains("SPRAY")) return new PackagingInfo("Spray Bottle of 50 mL", "SP50");
        if (desc.contains("PATCH")) return new PackagingInfo("Transdermal Patch of 1", "PT01");
        if (desc.contains("SUPPOSITORY")) return new PackagingInfo("Suppository of 1", "SP01");

        // Standalone keywords
        if (desc.contains("VIAL")) return new PackagingInfo("Vial of 1 (Single Dose)", "VL01");
        if (desc.contains("AMPOULE") || desc.contains("AMP")) return new PackagingInfo("Ampoule of 1 (1 mL)", "AM01");
        if (desc.contains("BOTTLE")) return new PackagingInfo("Bottle of 1 (Standard)", "BL01");
        if (desc.contains("TUBE")) return new PackagingInfo("Tube of 1 (Standard)", "TB01");

        // 4. Default for solids
        return new PackagingInfo("Box of 100", "BX100");
    }

    public String generateSkuCode(String brandName, LibMedicine med, String formCode, String packCode) {
        String cleanBrand = (brandName != null ? brandName.replaceAll("[^A-Za-z0-9]", "").toUpperCase() : "");
        if (cleanBrand.isEmpty() || cleanBrand.equalsIgnoreCase("GENERIC")) {
            cleanBrand = "GENE";
        } else {
            cleanBrand = cleanBrand.length() >= 4 ? cleanBrand.substring(0, 4) : String.format("%-4s", cleanBrand).replace(' ', 'X');
        }

        String genericPart = extractGenericCode(med);
        String strengthStr = extractStrength(med);
        String strengthDigits = getStrengthDigits(strengthStr);

        return cleanBrand + "-" + genericPart + strengthDigits + "-" + formCode + "-" + packCode;
    }

    // 1. CREATE SKU (Units field is automatically defaulted to 0 by @PrePersist in Sku entity)
    @Transactional
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Pharmacist')")
    public SkuResponseDto createSku(SkuRequestDto request) {
        Facility facility = facilityRepository.findById(request.getFacilityId())
                .orElseThrow(() -> new RuntimeException("Facility not found with id: " + request.getFacilityId()));

        LibMedicine libMedicine = libMedicineRepository.findById(request.getMedicineId())
                .orElseThrow(() -> new RuntimeException("Medicine not found with id: " + request.getMedicineId()));

        if (request.getReorderLevel() <= request.getMinimumLevel()) {
            throw new RuntimeException("Reorder level must be greater than minimum level threshold");
        }

        if (request.getMaximumLevel() <= request.getReorderLevel()) {
            throw new RuntimeException("Maximum capacity must be greater than reorder level threshold");
        }

        // 1. Resolve Dosage Form
        String dosageForm = (request.getDosageForm() != null && !request.getDosageForm().trim().isEmpty())
                ? request.getDosageForm().trim()
                : extractDosageForm(libMedicine);
        String formCode = getDosageFormCode(dosageForm);

        // Resolve Dosage Strength
        String dosageStrength = (request.getDosageStrength() != null && !request.getDosageStrength().trim().isEmpty())
                ? request.getDosageStrength().trim()
                : extractStrength(libMedicine);

        // 2. Resolve Packaging Unit
        PackagingInfo packagingInfo = resolvePackaging(request, libMedicine);

        // 3. Generate Clean Deterministic 5-Letter SKU Identifier (or use requested name if provided)
        String skuName = (request.getName() != null && !request.getName().trim().isEmpty())
                ? request.getName().trim().toUpperCase()
                : generateSkuCode(request.getBrandName(), libMedicine, formCode, packagingInfo.code);

        // Check uniqueness in facility
        if (skuRepository.existsByFacilityIdAndName(facility.getId(), skuName)) {
            throw new RuntimeException("SKU code '" + skuName + "' already exists in this facility.");
        }

        Sku sku = new Sku();
        sku.setFacility(facility);
        sku.setLibMedicine(libMedicine);
        sku.setName(skuName);
        sku.setBrandName(request.getBrandName().trim());
        sku.setDosageForm(dosageForm);
        sku.setDosageStrength(dosageStrength);
        sku.setPackagingUnit(packagingInfo.name);
        sku.setMinimumLevel(request.getMinimumLevel());
        sku.setReorderLevel(request.getReorderLevel());
        sku.setMaximumLevel(request.getMaximumLevel());

        Sku savedSku = skuRepository.save(sku);

        auditLogService.logAction(
                facility,
                getCurrentUser(),
                "SKU Catalog",
                "SKU_CREATED",
                "New SKU Created (" + savedSku.getName() + ")",
                AuditLog.Severity.SUCCESS,
                savedSku.getName(),
                savedSku.getId(),
                "Registered new SKU '" + savedSku.getName() + "' (" + (savedSku.getBrandName() != null ? savedSku.getBrandName() : "") + ") in catalog.",
                "Admin,Pharmacist"
        );

        return mapToResponseDto(savedSku, "SKU created successfully");
    }

    // 2. GET SKUS PAGINATED (with debounced search & stock status filter)
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public PageResponseDto<SkuResponseDto> getSkusPaginated(
            Long facilityId,
            String search,
            String status,
            int page,
            int size,
            String sortBy,
            String sortDir) {

        if (facilityId == null) {
            throw new RuntimeException("Facility ID is strictly required.");
        }
        if (!facilityRepository.existsById(facilityId)) {
            throw new RuntimeException("Facility not found with id: " + facilityId);
        }

        // Ensure past-due batches have their status marked Expired
        batchService.markPastDueBatchesAsExpired(facilityId);

        int pageIndex = Math.max(0, page);
        int pageSize = size > 0 ? size : 10;

        Sort.Direction direction = "DESC".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        String sortProperty = (sortBy != null && !sortBy.isBlank()) ? sortBy.trim() : "brandName";

        if ("brandName".equalsIgnoreCase(sortProperty)) sortProperty = "brandName";
        else if ("sku".equalsIgnoreCase(sortProperty) || "name".equalsIgnoreCase(sortProperty)) sortProperty = "name";
        else if ("currentStock".equalsIgnoreCase(sortProperty) || "units".equalsIgnoreCase(sortProperty)) sortProperty = "units";
        else if ("minimumLevel".equalsIgnoreCase(sortProperty)) sortProperty = "minimumLevel";
        else if ("reorderLevel".equalsIgnoreCase(sortProperty)) sortProperty = "reorderLevel";
        else if ("maximumLevel".equalsIgnoreCase(sortProperty)) sortProperty = "maximumLevel";
        else if ("createdAt".equalsIgnoreCase(sortProperty)) sortProperty = "createdAt";
        else sortProperty = "brandName";

        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by(direction, sortProperty));

        String trimmedSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        String trimmedStatus = (status != null && !status.trim().isEmpty()) ? status.trim().toUpperCase() : "ALL";

        Page<Sku> skuPage = skuRepository.findSkusPaginated(facilityId, trimmedSearch, trimmedStatus, pageable);
        Map<Long, SkuOrderMetrics> activeMetricsMap = getActiveOrderMetricsMap(facilityId);

        Page<SkuResponseDto> dtoPage = skuPage.map(sku -> mapToResponseDto(sku, null, activeMetricsMap.get(sku.getId())));
        return PageResponseDto.from(dtoPage);
    }

    // 2b. GET SKU SUMMARY KPIS
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public SkuSummaryDto getSkuSummary(Long facilityId) {
        if (facilityId == null) {
            throw new RuntimeException("Facility ID is strictly required.");
        }

        long totalSkus = skuRepository.countByFacilityId(facilityId);
        long optimalCount = skuRepository.countOptimalByFacilityId(facilityId);
        long reorderCount = skuRepository.countReorderNeededByFacilityId(facilityId);
        long criticalCount = skuRepository.countCriticalByFacilityId(facilityId);

        return SkuSummaryDto.builder()
                .totalSkus(totalSkus)
                .optimalCount(optimalCount)
                .reorderCount(reorderCount)
                .criticalCount(criticalCount)
                .build();
    }

    // 2c. GET LIGHTWEIGHT SKU DROPDOWN LIST (For modal selectors)
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<SkuDropdownDto> getDropdownSkus(Long facilityId) {
        if (facilityId == null) {
            return Collections.emptyList();
        }

        List<Sku> skus = skuRepository.findDropdownSkusByFacilityId(facilityId);
        Map<Long, SkuOrderMetrics> activeMetricsMap = getActiveOrderMetricsMap(facilityId);

        return skus.stream().map(sku -> {
            SkuOrderMetrics metrics = activeMetricsMap.get(sku.getId());
            boolean hasActiveRestock = metrics != null && metrics.hasActiveRestockRequest;
            String restockStatus = null;
            if (hasActiveRestock && metrics.latestRestockRequest != null) {
                restockStatus = metrics.latestRestockRequest.getOrder() == null
                        ? "Requested"
                        : metrics.latestRestockRequest.getOrder().getStatus().name();
            }
            return SkuDropdownDto.builder()
                    .id(sku.getId())
                    .sku(sku.getName())
                    .brandName(sku.getBrandName())
                    .genericName(sku.getLibMedicine() != null ? sku.getLibMedicine().getDrugDescription() : "")
                    .dosage(sku.getDosageStrength())
                    .currentStock(sku.getUnits() != null ? sku.getUnits() : 0L)
                    .maximumLevel(sku.getMaximumLevel())
                    .hasPendingRestock(hasActiveRestock)
                    .pendingRestockUnits(hasActiveRestock ? metrics.activeRestockUnits : 0L)
                    .pendingRestockStatus(restockStatus)
                    .build();
        }).collect(Collectors.toList());
    }

    // 3. GET SKU BY ID
    @PreAuthorize("isAuthenticated()")
    public SkuResponseDto getSkuById(Long id) {
        Sku sku = skuRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("SKU not found with id: " + id));
        return mapToResponseDto(sku, null);
    }

    // 4. UPDATE SKU
    @Transactional
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Pharmacist')")
    public SkuResponseDto updateSku(Long id, SkuRequestDto request) {
        Sku existingSku = skuRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("SKU not found with id: " + id));

        // If facility changed
        if (!existingSku.getFacility().getId().equals(request.getFacilityId())) {
            Facility newFacility = facilityRepository.findById(request.getFacilityId())
                    .orElseThrow(() -> new RuntimeException("Facility not found with id: " + request.getFacilityId()));
            existingSku.setFacility(newFacility);
        }

        // If medicine changed
        if (!existingSku.getLibMedicine().getId().equals(request.getMedicineId())) {
            LibMedicine newMedicine = libMedicineRepository.findById(request.getMedicineId())
                    .orElseThrow(() -> new RuntimeException("Medicine not found with id: " + request.getMedicineId()));
            existingSku.setLibMedicine(newMedicine);
        }

        if (request.getReorderLevel() <= request.getMinimumLevel()) {
            throw new RuntimeException("Reorder level must be greater than minimum level threshold");
        }

        if (request.getMaximumLevel() <= request.getReorderLevel()) {
            throw new RuntimeException("Maximum capacity must be greater than reorder level threshold");
        }

        // Dosage Form
        if (request.getDosageForm() != null && !request.getDosageForm().trim().isEmpty()) {
            existingSku.setDosageForm(request.getDosageForm().trim());
        }

        // Dosage Strength
        if (request.getDosageStrength() != null && !request.getDosageStrength().trim().isEmpty()) {
            existingSku.setDosageStrength(request.getDosageStrength().trim());
        }

        // Packaging Unit
        if ((request.getPackagingUnitCode() != null && !request.getPackagingUnitCode().trim().isEmpty())
                || (request.getPackagingUnit() != null && !request.getPackagingUnit().trim().isEmpty())) {
            PackagingInfo packInfo = resolvePackaging(request, existingSku.getLibMedicine());
            existingSku.setPackagingUnit(packInfo.name);
        }

        if (request.getBrandName() != null && !request.getBrandName().trim().isEmpty()) {
            existingSku.setBrandName(request.getBrandName().trim());
        }

        // SKU Name
        String newName = existingSku.getName();
        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            newName = request.getName().trim().toUpperCase();
        }
        if (skuRepository.existsByFacilityIdAndNameAndIdNot(existingSku.getFacility().getId(), newName, existingSku.getId())) {
            throw new RuntimeException("SKU code '" + newName + "' already exists in this facility.");
        }
        existingSku.setName(newName);

        existingSku.setMinimumLevel(request.getMinimumLevel());
        existingSku.setReorderLevel(request.getReorderLevel());
        existingSku.setMaximumLevel(request.getMaximumLevel());

        Sku updatedSku = skuRepository.save(existingSku);

        auditLogService.logAction(
                updatedSku.getFacility(),
                getCurrentUser(),
                "SKU Catalog",
                "SKU_UPDATED",
                "SKU Details Updated (" + updatedSku.getName() + ")",
                AuditLog.Severity.INFO,
                updatedSku.getName(),
                updatedSku.getId(),
                "Updated SKU details for '" + updatedSku.getName() + "'. Dosage: " + updatedSku.getDosageForm() + ", Reorder: " + updatedSku.getReorderLevel() + ", Max: " + updatedSku.getMaximumLevel() + ".",
                "Admin,Pharmacist"
        );

        return mapToResponseDto(updatedSku, "SKU updated successfully");
    }

    // 5. DELETE SKU
    @Transactional
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Pharmacist')")
    public void deleteSku(Long id) {
        Sku sku = skuRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("SKU not found with id: " + id));

        skuRepository.delete(sku);

        auditLogService.logAction(
                sku.getFacility(),
                getCurrentUser(),
                "SKU Catalog",
                "SKU_DELETED",
                "SKU Deleted (" + sku.getName() + ")",
                AuditLog.Severity.WARNING,
                sku.getName(),
                sku.getId(),
                "Deleted SKU '" + sku.getName() + "' from inventory catalog.",
                "Admin,Pharmacist"
        );
    }

    // 5b. ADJUST SKU STOCK (Physical count / write-off / correction)
    @Transactional
    @PreAuthorize("hasAnyRole('SuperAdmin', 'Admin', 'Pharmacist')")
    public SkuResponseDto adjustStock(Long id, SkuStockAdjustmentDto request) {
        Sku sku = skuRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("SKU not found with id: " + id));

        long currentUnits = sku.getUnits() != null ? sku.getUnits() : 0L;
        long newUnits;
        long unitsToDeduct = 0L;
        Batch targetBatch = null;

        switch (request.getType()) {
            case ADD:
                newUnits = currentUnits + request.getAmount();
                if (request.getBatchId() != null) {
                    targetBatch = batchService.addUnitsToBatch(request.getBatchId(), sku.getId(), request.getAmount());
                }
                break;
            case SUBTRACT:
                Long facilityId = sku.getFacility() != null ? sku.getFacility().getId() : null;
                if (facilityId != null && batchService.hasUnprocessedExpiredBatches(facilityId, sku.getId())) {
                    throw new RuntimeException("This SKU has unprocessed expired batches. Please deduct expired batches first before adjusting stock.");
                }
                if (request.getAmount() > currentUnits) {
                    throw new RuntimeException("Cannot deduct more than current stock (" + currentUnits + " units).");
                }
                newUnits = currentUnits - request.getAmount();
                unitsToDeduct = request.getAmount();
                break;
            default:
                throw new IllegalArgumentException("Unknown adjustment type: " + request.getType());
        }

        sku.setUnits(newUnits);
        Sku savedSku = skuRepository.save(sku);

        boolean isDispensed = request.getReason() != null && "DISPENSED".equalsIgnoreCase(request.getReason().trim());

        List<Batch> deductedBatches = new ArrayList<>();
        if (unitsToDeduct > 0 && savedSku.getFacility() != null) {
            if (isDispensed) {
                // When reason is Dispensed, patient name and contact number are required
                if (request.getPatientName() == null || request.getPatientName().trim().isEmpty()) {
                    throw new RuntimeException("Patient name is required when adjustment reason is 'Dispensed'.");
                }
                if (request.getContactNumber() == null || request.getContactNumber().trim().isEmpty()) {
                    throw new RuntimeException("Contact number is required when adjustment reason is 'Dispensed'.");
                }

                // FEFO: Deduct automatically from the nearest expiring available batches
                deductedBatches = batchService.deductBatchesFEFO(savedSku.getId(), savedSku.getFacility().getId(), unitsToDeduct).stream()
                        .map(BatchService.BatchDeductionDetail::getBatch)
                        .collect(Collectors.toList());

                // Create DispensedLog with status PENDING
                DispensedLog dispensedLog = new DispensedLog();
                dispensedLog.setSku(savedSku);
                dispensedLog.setPatientName(request.getPatientName().trim());
                dispensedLog.setContactNumber(request.getContactNumber().trim());
                dispensedLog.setUnitsDispensed(unitsToDeduct);
                dispensedLog.setStatus(DispensedLog.Status.Pending);
                dispensedLogRepository.save(dispensedLog);
            } else {
                // When reason is other than Dispensed, require and deduct from the specific target batch
                boolean hasBatches = batchService.hasBatchesForSku(savedSku.getFacility().getId(), savedSku.getId());
                if (hasBatches && request.getBatchId() == null) {
                    throw new RuntimeException("Target batch is required when adjustment reason is not 'Dispensed'. Please select which batch to deduct from.");
                }
                if (request.getBatchId() != null) {
                    Batch deducted = batchService.deductUnitsFromBatch(request.getBatchId(), savedSku.getId(), unitsToDeduct);
                    if (deducted != null) {
                        deductedBatches.add(deducted);
                        targetBatch = deducted;
                    }
                }
            }
        }

        // Build batch metadata string for both additions and deductions
        String batchDetails = null;
        if (targetBatch != null) {
            batchDetails = String.format("(Batch: %s, Expiry: %s)", targetBatch.getBatchNum(), targetBatch.getExpiryDate());
        } else if (!deductedBatches.isEmpty()) {
            if (deductedBatches.size() == 1) {
                Batch b = deductedBatches.get(0);
                batchDetails = String.format("(Batch: %s, Expiry: %s)", b.getBatchNum(), b.getExpiryDate());
            } else {
                batchDetails = "(Batches: " + deductedBatches.stream()
                        .map(b -> b.getBatchNum() + " [Exp: " + b.getExpiryDate() + "]")
                        .collect(Collectors.joining(", ")) + ")";
            }
        }

        String userNotes = (request.getNotes() != null && !request.getNotes().isBlank()) ? request.getNotes().trim() : null;
        String logNotes;
        if (userNotes != null && batchDetails != null) {
            logNotes = userNotes + " " + batchDetails;
        } else if (batchDetails != null) {
            logNotes = batchDetails;
        } else {
            logNotes = userNotes;
        }
        if (request.getPatientName() != null && !request.getPatientName().isBlank()) {
            String patientInfo = String.format("[Patient: %s, Contact: %s]", request.getPatientName().trim(),
                    request.getContactNumber() != null ? request.getContactNumber().trim() : "N/A");
            logNotes = logNotes != null ? logNotes + " " + patientInfo : patientInfo;
        }

        // Record inventory transaction entry
        InventoryTransaction.Type txType;
        if (request.getType() == SkuStockAdjustmentDto.Type.ADD) {
            txType = InventoryTransaction.Type.ADJUSTMENT_ADD;
        } else if (isDispensed) {
            txType = InventoryTransaction.Type.DISPENSE;
        } else {
            txType = InventoryTransaction.Type.ADJUSTMENT_SUB;
        }

        inventoryTransactionService.recordTransaction(
                savedSku,
                savedSku.getFacility(),
                targetBatch,
                getCurrentUser(),
                txType,
                "ADJ-" + System.currentTimeMillis(),
                targetBatch != null ? targetBatch.getBatchNum() : null,
                targetBatch != null ? targetBatch.getExpiryDate() : null,
                request.getType() == SkuStockAdjustmentDto.Type.ADD ? request.getAmount() : null,
                request.getType() == SkuStockAdjustmentDto.Type.SUBTRACT ? request.getAmount() : null,
                currentUnits,
                newUnits,
                request.getReason(),
                logNotes,
                java.time.LocalDateTime.now()
        );

        // Record central Audit Log entry
        long delta = newUnits - currentUnits;
        AuditLog.Severity severity = delta < 0 ? AuditLog.Severity.WARNING : AuditLog.Severity.INFO;
        String action;
        String actionLabel;
        if (request.getType() == SkuStockAdjustmentDto.Type.ADD) {
            action = "STOCK_ADDITION";
            actionLabel = "Stock Addition (+" + request.getAmount() + " units)";
        } else {
            action = "STOCK_DEDUCTION";
            actionLabel = "Stock Deduction (-" + request.getAmount() + " units)";
        }
        String batchInfo = "";
        if (targetBatch != null) {
            batchInfo = String.format(" [Target Batch: %s, Expiry: %s, New Batch Units: %d]",
                    targetBatch.getBatchNum(), targetBatch.getExpiryDate(), targetBatch.getUnits());
        } else if (!deductedBatches.isEmpty()) {
            batchInfo = " [Deducted from: " + deductedBatches.stream()
                    .map(b -> b.getBatchNum() + " (Exp: " + b.getExpiryDate() + ")")
                    .collect(Collectors.joining(", ")) + "]";
        }

        String patientLog = (request.getPatientName() != null && !request.getPatientName().isBlank())
                ? String.format(" Dispensed to: %s (Tel: %s).", request.getPatientName().trim(), request.getContactNumber() != null ? request.getContactNumber().trim() : "")
                : "";

        String description = String.format(
                "Stock adjusted for SKU '%s' (%s). Previous: %d units, Change: %+d units, New Stock: %d units. Reason: %s.%s%s%s",
                savedSku.getName(),
                savedSku.getBrandName() != null ? savedSku.getBrandName() : "",
                currentUnits,
                delta,
                newUnits,
                request.getReason(),
                patientLog,
                (request.getNotes() != null && !request.getNotes().isBlank()) ? " Notes: " + request.getNotes().trim() : "",
                batchInfo
        );

        auditLogService.logAction(
                savedSku.getFacility(),
                getCurrentUser(),
                "Inventory",
                action,
                actionLabel,
                severity,
                savedSku.getName(),
                savedSku.getId(),
                description,
                "Admin,Pharmacist"
        );

        return mapToResponseDto(savedSku, "Stock adjusted successfully");
    }

    // 7. GET SKUS THAT NEED REORDERING (units <= reorderLevel, excluding active Pending/Approved orders)
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public List<SkuResponseDto> getReorderNeededSkus(Long facilityId, String search) {
        if (facilityId == null) {
            throw new RuntimeException("Facility ID is strictly required.");
        }
        if (!facilityRepository.existsById(facilityId)) {
            throw new RuntimeException("Facility not found with id: " + facilityId);
        }

        batchService.markPastDueBatchesAsExpired(facilityId);
        String query = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        List<Sku> skus = skuRepository.findReorderNeededSkus(facilityId, query);
        Map<Long, SkuOrderMetrics> activeMetricsMap = getActiveOrderMetricsMap(facilityId);
        return skus.stream()
                .map(sku -> mapToResponseDto(sku, null, activeMetricsMap.get(sku.getId())))
                .collect(Collectors.toList());
    }

    private static class SkuOrderMetrics {
        long pendingUnits = 0L;
        long toReceiveUnits = 0L;
        boolean hasActiveRestockRequest = false;
        long activeRestockUnits = 0L;
        RestockRequest latestRestockRequest = null;
        long expiredBatchesCount = 0L;
        long expiredUnits = 0L;
    }

    private Map<Long, SkuOrderMetrics> getActiveOrderMetricsMap(Long facilityId) {
        if (facilityId == null) {
            return Collections.emptyMap();
        }
        Map<Long, SkuOrderMetrics> map = new HashMap<>();

        // 1. Active Pharmacist Restock Requests
        List<RestockRequest> activeRestocks = restockRequestRepository.findActiveRestockRequestsByFacilityId(facilityId);
        for (RestockRequest rr : activeRestocks) {
            if (rr.getSku() != null && rr.getSku().getId() != null) {
                SkuOrderMetrics m = map.computeIfAbsent(rr.getSku().getId(), k -> new SkuOrderMetrics());
                long units = rr.getRequestedUnits() != null ? rr.getRequestedUnits() : 0L;
                m.hasActiveRestockRequest = true;
                m.activeRestockUnits += units;
                if (m.latestRestockRequest == null) {
                    m.latestRestockRequest = rr;
                }
                // If it has not been assigned to a purchase order yet, add to pending units
                if (rr.getOrder() == null) {
                    m.pendingUnits += units;
                }
            }
        }

        // 2. Active Ordered Items (from POs, whether automated threshold PO or pharmacist requested PO)
        List<OrderedItem> activeItems = orderedItemRepository.findActiveOrderedItemsByFacilityId(facilityId);
        for (OrderedItem oi : activeItems) {
            if (oi.getSku() != null && oi.getSku().getId() != null && oi.getOrder() != null) {
                SkuOrderMetrics m = map.computeIfAbsent(oi.getSku().getId(), k -> new SkuOrderMetrics());
                long units = oi.getOrderedUnits() != null ? oi.getOrderedUnits() : 0L;
                if (oi.getOrder().getStatus() == Order.Status.Pending) {
                    m.pendingUnits += units;
                } else if (oi.getOrder().getStatus() == Order.Status.Approved || oi.getOrder().getStatus() == Order.Status.Partially_Received) {
                    long remaining = Math.max(0, units - (oi.getReceivedUnits() != null ? oi.getReceivedUnits() : 0L));
                    m.toReceiveUnits += remaining;
                }
            }
        }

        // 3. Expired batches needing deduction
        Map<Long, long[]> expiredMetrics = batchService.getExpiredBatchMetrics(facilityId);
        for (Map.Entry<Long, long[]> entry : expiredMetrics.entrySet()) {
            SkuOrderMetrics m = map.computeIfAbsent(entry.getKey(), k -> new SkuOrderMetrics());
            m.expiredBatchesCount = entry.getValue()[0];
            m.expiredUnits = entry.getValue()[1];
        }

        return map;
    }

    private SkuOrderMetrics getSingleSkuOrderMetrics(Long facilityId, Long skuId) {
        if (facilityId == null || skuId == null) {
            return new SkuOrderMetrics();
        }
        SkuOrderMetrics m = new SkuOrderMetrics();
        List<RestockRequest> activeRestocks = restockRequestRepository.findActiveRestockRequestsByFacilityIdAndSkuId(facilityId, skuId);
        for (RestockRequest rr : activeRestocks) {
            long units = rr.getRequestedUnits() != null ? rr.getRequestedUnits() : 0L;
            m.hasActiveRestockRequest = true;
            m.activeRestockUnits += units;
            if (m.latestRestockRequest == null) {
                m.latestRestockRequest = rr;
            }
            if (rr.getOrder() == null) {
                m.pendingUnits += units;
            }
        }
        List<OrderedItem> activeItems = orderedItemRepository.findActiveOrderedItemsByFacilityIdAndSkuId(facilityId, skuId);
        for (OrderedItem oi : activeItems) {
            if (oi.getOrder() != null) {
                long units = oi.getOrderedUnits() != null ? oi.getOrderedUnits() : 0L;
                if (oi.getOrder().getStatus() == Order.Status.Pending) {
                    m.pendingUnits += units;
                } else if (oi.getOrder().getStatus() == Order.Status.Approved || oi.getOrder().getStatus() == Order.Status.Partially_Received) {
                    long remaining = Math.max(0, units - (oi.getReceivedUnits() != null ? oi.getReceivedUnits() : 0L));
                    m.toReceiveUnits += remaining;
                }
            }
        }

        Map<Long, long[]> expiredMetrics = batchService.getExpiredBatchMetrics(facilityId);
        long[] skuExpired = expiredMetrics.get(skuId);
        if (skuExpired != null) {
            m.expiredBatchesCount = skuExpired[0];
            m.expiredUnits = skuExpired[1];
        }

        return m;
    }

    // Helper: Map Sku entity to SkuResponseDto with restock enrichment
    private SkuResponseDto mapToResponseDto(Sku sku, String message, SkuOrderMetrics metrics) {
        SkuResponseDto dto = new SkuResponseDto();
        dto.setId(sku.getId());

        if (sku.getFacility() != null) {
            dto.setFacilityId(sku.getFacility().getId());
            dto.setFacilityName(sku.getFacility().getName());
        }

        if (sku.getLibMedicine() != null) {
            dto.setMedicineId(sku.getLibMedicine().getId());
            dto.setDrugCode(sku.getLibMedicine().getDrugCode());
            dto.setDrugDescription(sku.getLibMedicine().getDrugDescription());
        }

        dto.setName(sku.getName());
        dto.setBrandName(sku.getBrandName());
        dto.setDosageForm(sku.getDosageForm());
        dto.setDosageStrength(sku.getDosageStrength());
        dto.setPackagingUnit(sku.getPackagingUnit());
        dto.setUnits(sku.getUnits() != null ? sku.getUnits() : 0L);
        dto.setMinimumLevel(sku.getMinimumLevel());
        dto.setReorderLevel(sku.getReorderLevel());
        dto.setMaximumLevel(sku.getMaximumLevel());
        dto.setCreatedAt(sku.getCreatedAt());
        dto.setUpdatedAt(sku.getUpdatedAt());
        dto.setMessage(message);

        long pending = metrics != null ? metrics.pendingUnits : 0L;
        long toReceive = metrics != null ? metrics.toReceiveUnits : 0L;

        dto.setPendingUnits(pending);
        dto.setToReceiveUnits(toReceive);

        // hasPendingRestock is strictly for pharmacist restock requests
        // System threshold orders DO NOT block a pharmacist from requesting restock!
        boolean hasActiveRestock = metrics != null && metrics.hasActiveRestockRequest;
        dto.setHasPendingRestock(hasActiveRestock);
        dto.setPendingRestockUnits(hasActiveRestock ? metrics.activeRestockUnits : 0L);

        if (hasActiveRestock && metrics.latestRestockRequest != null) {
            dto.setPendingRestockRequestId(metrics.latestRestockRequest.getId());
            dto.setPendingRestockCreatedAt(metrics.latestRestockRequest.getCreatedAt());
            String status = metrics.latestRestockRequest.getOrder() == null
                    ? "Requested"
                    : metrics.latestRestockRequest.getOrder().getStatus().name();
            dto.setPendingRestockStatus(status);
        } else {
            dto.setPendingRestockRequestId(null);
            dto.setPendingRestockCreatedAt(null);
            dto.setPendingRestockStatus(null);
        }

        long expiredCount = metrics != null ? metrics.expiredBatchesCount : 0L;
        long expiredUnits = metrics != null ? metrics.expiredUnits : 0L;
        dto.setExpiredBatchesCount(expiredCount);
        dto.setExpiredUnits(expiredUnits);

        return dto;
    }

    // Overloaded helper for single-entity callers
    private SkuResponseDto mapToResponseDto(Sku sku, String message) {
        SkuOrderMetrics metrics = null;
        if (sku.getFacility() != null && sku.getId() != null) {
            metrics = getSingleSkuOrderMetrics(sku.getFacility().getId(), sku.getId());
        }
        return mapToResponseDto(sku, message, metrics);
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
}
