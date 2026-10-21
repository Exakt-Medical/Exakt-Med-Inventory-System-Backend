package com.example.backend.dto.audit;

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
public class AuditLogResponseDto {

    private Long id;
    private String logCode;
    private Long facilityId;
    private String facility;
    private Long userId;
    private String userName;
    private String userRole;
    private String module;
    private String action;
    private String actionLabel;
    private String severity;
    private String target;
    private Long targetId;
    private String description;
    private List<String> visibleRoles;
    private LocalDateTime createdAt;
    private String timestamp;
}
