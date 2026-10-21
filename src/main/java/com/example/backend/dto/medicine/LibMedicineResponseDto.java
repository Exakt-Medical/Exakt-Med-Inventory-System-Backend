package com.example.backend.dto.medicine;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LibMedicineResponseDto {
    private Long id;
    private String drugCode;
    private String drugDescription;
    private String genCode;
    private String saltCode;
    private String formCode;
    private String strengthCode;
    private String unitCode;
    private String packageCode;
}
