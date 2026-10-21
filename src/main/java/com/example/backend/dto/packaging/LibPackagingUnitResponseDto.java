package com.example.backend.dto.packaging;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LibPackagingUnitResponseDto {
    private Long id;
    private String code;
    private String name;
    private String category;
    private Integer defaultUnits;
}
