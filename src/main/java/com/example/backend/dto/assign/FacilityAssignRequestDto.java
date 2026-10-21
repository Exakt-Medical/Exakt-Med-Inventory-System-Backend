package com.example.backend.dto.assign;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class FacilityAssignRequestDto {

    @NotEmpty(message = "Please provide at least one user ID")
    private List<Long> userIds;

}
