package com.example.backend.dto.user;

import com.example.backend.entity.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDto {
    private Long id;
    private String name;
    private String username;
    private String email;
    private String phone;
    private User.Role role;
    private Boolean status;
    private Long facilityId;
    private LocalDateTime createdAt;
    private String message;
    private List<Long> assignedFacilityIds;

    public UserResponseDto(Long id, String name, String username, String email, String phone,
                           User.Role role, Boolean status, Long facilityId,
                           LocalDateTime createdAt, String message) {
        this.id = id;
        this.name = name;
        this.username = username;
        this.email = email;
        this.phone = phone;
        this.role = role;
        this.status = status;
        this.facilityId = facilityId;
        this.createdAt = createdAt;
        this.message = message;
    }
}
