package com.example.backend.dto.assign;

import com.example.backend.dto.pharmacy.PharmacyResponseDto;
import com.example.backend.dto.user.UserResponseDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PharmacyAssignResponseDto {

    private Long id;
    private UserResponseDto user;
    private PharmacyResponseDto pharmacy;
    private LocalDateTime assignedAt;

}

