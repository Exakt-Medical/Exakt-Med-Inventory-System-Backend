package com.example.backend.service;

import com.example.backend.dto.packaging.LibPackagingUnitResponseDto;
import com.example.backend.entity.LibPackagingUnit;
import com.example.backend.repository.LibPackagingUnitRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class LibPackagingUnitService {

    private final LibPackagingUnitRepository libPackagingUnitRepository;

    public LibPackagingUnitService(LibPackagingUnitRepository libPackagingUnitRepository) {
        this.libPackagingUnitRepository = libPackagingUnitRepository;
    }

    public List<LibPackagingUnitResponseDto> getAllPackagingUnits() {
        return libPackagingUnitRepository.findAllByOrderByCategoryAscNameAsc()
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public Optional<LibPackagingUnit> findByCode(String code) {
        if (code == null || code.trim().isEmpty()) return Optional.empty();
        return libPackagingUnitRepository.findByCode(code.trim().toUpperCase());
    }

    public Optional<LibPackagingUnit> findByName(String name) {
        if (name == null || name.trim().isEmpty()) return Optional.empty();
        return libPackagingUnitRepository.findByNameIgnoreCase(name.trim());
    }

    @PostConstruct
    @Transactional
    public void seedPackagingUnitsIfEmpty() {
        if (libPackagingUnitRepository.count() > 0) {
            return;
        }

        List<LibPackagingUnit> units = new ArrayList<>();

        // 1. Oral Solids (Sheets, Blisters, Boxes, Bottles)
        units.add(new LibPackagingUnit("SH04", "Sheet of 4", "Oral Solids", 4));
        units.add(new LibPackagingUnit("SH10", "Sheet of 10", "Oral Solids", 10));
        units.add(new LibPackagingUnit("SH20", "Sheet of 20", "Oral Solids", 20));
        units.add(new LibPackagingUnit("BX10", "Box of 10", "Oral Solids", 10));
        units.add(new LibPackagingUnit("BX20", "Box of 20", "Oral Solids", 20));
        units.add(new LibPackagingUnit("BX30", "Box of 30", "Oral Solids", 30));
        units.add(new LibPackagingUnit("BX50", "Box of 50", "Oral Solids", 50));
        units.add(new LibPackagingUnit("BX100", "Box of 100", "Oral Solids", 100));
        units.add(new LibPackagingUnit("BX500", "Box of 500", "Oral Solids", 500));
        units.add(new LibPackagingUnit("BT30", "Bottle of 30 Tablets", "Oral Solids", 30));
        units.add(new LibPackagingUnit("BT60", "Bottle of 60 Tablets", "Oral Solids", 60));
        units.add(new LibPackagingUnit("BT100", "Bottle of 100 Tablets", "Oral Solids", 100));
        units.add(new LibPackagingUnit("BT500", "Bottle of 500 Tablets", "Oral Solids", 500));

        // 2. Oral Liquids (Syrups, Suspensions, Drops)
        units.add(new LibPackagingUnit("DR02", "Dropper Bottle of 2.5 mL", "Oral Liquids", 2));
        units.add(new LibPackagingUnit("DR05", "Dropper Bottle of 5 mL", "Oral Liquids", 5));
        units.add(new LibPackagingUnit("DR10", "Dropper Bottle of 10 mL", "Oral Liquids", 10));
        units.add(new LibPackagingUnit("DR15", "Dropper Bottle of 15 mL", "Oral Liquids", 15));
        units.add(new LibPackagingUnit("DR30", "Dropper Bottle of 30 mL", "Oral Liquids", 30));
        units.add(new LibPackagingUnit("BL01", "Bottle of 1 (Standard)", "Oral Liquids", 1));
        units.add(new LibPackagingUnit("BL05", "Bottle of 5 mL", "Oral Liquids", 5));
        units.add(new LibPackagingUnit("BL10", "Bottle of 10 mL", "Oral Liquids", 10));
        units.add(new LibPackagingUnit("BL15", "Bottle of 15 mL", "Oral Liquids", 15));
        units.add(new LibPackagingUnit("BL25", "Bottle of 25 mL", "Oral Liquids", 25));
        units.add(new LibPackagingUnit("BL30", "Bottle of 30 mL", "Oral Liquids", 30));
        units.add(new LibPackagingUnit("BL50", "Bottle of 50 mL", "Oral Liquids", 50));
        units.add(new LibPackagingUnit("BL60", "Bottle of 60 mL", "Oral Liquids", 60));
        units.add(new LibPackagingUnit("BL70", "Bottle of 70 mL", "Oral Liquids", 70));
        units.add(new LibPackagingUnit("BL100", "Bottle of 100 mL", "Oral Liquids", 100));
        units.add(new LibPackagingUnit("BL120", "Bottle of 120 mL", "Oral Liquids", 120));
        units.add(new LibPackagingUnit("BL150", "Bottle of 150 mL", "Oral Liquids", 150));
        units.add(new LibPackagingUnit("BL200", "Bottle of 200 mL", "Oral Liquids", 200));
        units.add(new LibPackagingUnit("BL240", "Bottle of 240 mL", "Oral Liquids", 240));
        units.add(new LibPackagingUnit("BL250", "Bottle of 250 mL", "Oral Liquids", 250));
        units.add(new LibPackagingUnit("BL500", "Bottle of 500 mL", "Oral Liquids", 500));
        units.add(new LibPackagingUnit("BL1L", "Bottle of 1 L", "Oral Liquids", 1000));
        units.add(new LibPackagingUnit("BL5L", "Bottle of 5 L", "Oral Liquids", 5000));
        units.add(new LibPackagingUnit("GL01", "Gallon of 1 (approx 4 L)", "Oral Liquids", 4000));

        // 3. Injectables & IV Fluids (Ampoules, Vials, Bags, Carpules, Syringes)
        units.add(new LibPackagingUnit("AM01", "Ampoule of 1 (1 mL)", "Injectables", 1));
        units.add(new LibPackagingUnit("AM02", "Ampoule of 1 (2 mL)", "Injectables", 1));
        units.add(new LibPackagingUnit("AM05", "Ampoule of 1 (5 mL)", "Injectables", 1));
        units.add(new LibPackagingUnit("AM10", "Ampoule of 1 (10 mL)", "Injectables", 1));
        units.add(new LibPackagingUnit("BA05", "Box of 5 Ampoules", "Injectables", 5));
        units.add(new LibPackagingUnit("BA10", "Box of 10 Ampoules", "Injectables", 10));
        units.add(new LibPackagingUnit("VL01", "Vial of 1 (Single Dose)", "Injectables", 1));
        units.add(new LibPackagingUnit("VL02", "Vial of 2 mL", "Injectables", 2));
        units.add(new LibPackagingUnit("VL03", "Vial of 3 mL", "Injectables", 3));
        units.add(new LibPackagingUnit("VL04", "Vial of 4 mL", "Injectables", 4));
        units.add(new LibPackagingUnit("VL05", "Vial of 5 mL", "Injectables", 5));
        units.add(new LibPackagingUnit("VL10", "Vial of 10 mL", "Injectables", 10));
        units.add(new LibPackagingUnit("VL15", "Vial of 15 mL", "Injectables", 15));
        units.add(new LibPackagingUnit("VL20", "Vial of 20 mL", "Injectables", 20));
        units.add(new LibPackagingUnit("VL25", "Vial of 25 mL", "Injectables", 25));
        units.add(new LibPackagingUnit("VL30", "Vial of 30 mL", "Injectables", 30));
        units.add(new LibPackagingUnit("VL50", "Vial of 50 mL", "Injectables", 50));
        units.add(new LibPackagingUnit("VL100", "Vial of 100 mL", "Injectables", 100));
        units.add(new LibPackagingUnit("VL200", "Vial of 200 mL", "Injectables", 200));
        units.add(new LibPackagingUnit("BV10", "Box of 10 Vials", "Injectables", 10));
        units.add(new LibPackagingUnit("PS01", "Pre-filled Syringe of 1", "Injectables", 1));
        units.add(new LibPackagingUnit("PS05", "Pre-filled Syringe of 0.5 mL", "Injectables", 1));
        units.add(new LibPackagingUnit("PS10", "Pre-filled Syringe of 1 mL", "Injectables", 1));
        units.add(new LibPackagingUnit("SY01", "Syringe of 1", "Injectables", 1));
        units.add(new LibPackagingUnit("CP01", "Dental Carpule of 1", "Injectables", 1));
        units.add(new LibPackagingUnit("CR01", "Cartridge of 1", "Injectables", 1));
        units.add(new LibPackagingUnit("BC50", "Box of 50 Carpules", "Injectables", 50));
        units.add(new LibPackagingUnit("BG100", "IV Bag of 100 mL", "IV Fluids", 100));
        units.add(new LibPackagingUnit("BG250", "IV Bag of 250 mL", "IV Fluids", 250));
        units.add(new LibPackagingUnit("BG500", "IV Bag of 500 mL", "IV Fluids", 500));
        units.add(new LibPackagingUnit("BG1L", "IV Bag of 1 L", "IV Fluids", 1000));
        units.add(new LibPackagingUnit("BB12", "Box of 12 IV Bags", "IV Fluids", 12));

        // 4. Topicals (Tubes, Jars)
        units.add(new LibPackagingUnit("TB01", "Tube of 1 (Standard)", "Topicals", 1));
        units.add(new LibPackagingUnit("TB02", "Tube of 2g", "Topicals", 2));
        units.add(new LibPackagingUnit("TB03", "Tube of 3.5g (Eye Ointment)", "Topicals", 3));
        units.add(new LibPackagingUnit("TB04", "Tube of 4.5g (Eye Ointment)", "Topicals", 4));
        units.add(new LibPackagingUnit("TB05", "Tube of 5g", "Topicals", 5));
        units.add(new LibPackagingUnit("TB10", "Tube of 10g", "Topicals", 10));
        units.add(new LibPackagingUnit("TB15", "Tube of 15g", "Topicals", 15));
        units.add(new LibPackagingUnit("TB20", "Tube of 20g", "Topicals", 20));
        units.add(new LibPackagingUnit("TB25", "Tube of 25g", "Topicals", 25));
        units.add(new LibPackagingUnit("TB30", "Tube of 30g", "Topicals", 30));
        units.add(new LibPackagingUnit("TB40", "Tube of 40g", "Topicals", 40));
        units.add(new LibPackagingUnit("TB50", "Tube of 50g", "Topicals", 50));
        units.add(new LibPackagingUnit("JR15", "Jar of 15g", "Topicals", 15));
        units.add(new LibPackagingUnit("JR30", "Jar of 30g", "Topicals", 30));
        units.add(new LibPackagingUnit("JR100", "Jar of 100g", "Topicals", 100));
        units.add(new LibPackagingUnit("JR450", "Jar of 450g / 500g", "Topicals", 500));

        // 5. Respiratory, Sachets & Others
        units.add(new LibPackagingUnit("IH01", "Inhaler Canister of 1", "Respiratory", 1));
        units.add(new LibPackagingUnit("NB01", "Nebule of 1 (2.5 mL)", "Respiratory", 1));
        units.add(new LibPackagingUnit("NB02", "Nebule of 2 mL", "Respiratory", 2));
        units.add(new LibPackagingUnit("BN20", "Box of 20 Nebules", "Respiratory", 20));
        units.add(new LibPackagingUnit("BN30", "Box of 30 Nebules", "Respiratory", 30));
        units.add(new LibPackagingUnit("NS10", "Nasal Spray of 10 mL", "Respiratory", 10));
        units.add(new LibPackagingUnit("SP50", "Spray Bottle of 50 mL", "Respiratory", 50));
        units.add(new LibPackagingUnit("SC01", "Sachet of 1", "Others", 1));
        units.add(new LibPackagingUnit("SC06", "Sachet of 6 mL", "Others", 6));
        units.add(new LibPackagingUnit("SC10", "Sachet of 10 mL", "Others", 10));
        units.add(new LibPackagingUnit("SC20", "Box of 20 Sachets", "Others", 20));
        units.add(new LibPackagingUnit("SP01", "Suppository of 1", "Others", 1));
        units.add(new LibPackagingUnit("SP10", "Box of 10 Suppositories", "Others", 10));
        units.add(new LibPackagingUnit("PT01", "Transdermal Patch of 1", "Others", 1));
        units.add(new LibPackagingUnit("DP01", "Dispenser of 1", "Others", 1));

        libPackagingUnitRepository.saveAll(units);
    }

    private LibPackagingUnitResponseDto mapToDto(LibPackagingUnit unit) {
        return new LibPackagingUnitResponseDto(
                unit.getId(),
                unit.getCode(),
                unit.getName(),
                unit.getCategory(),
                unit.getDefaultUnits()
        );
    }
}
