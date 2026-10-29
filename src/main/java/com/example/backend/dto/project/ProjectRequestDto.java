package com.example.backend.dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectRequestDto {

    @NotBlank(message = "Project Name is required")
    @Size(min = 2, max = 100, message = "Project Name must be between 2 and 100 characters")
    private String name;

    @NotBlank(message = "Project Code is required")
    @Size(min = 1, max = 100, message = "Project Code must be between 1 and 100 characters")
    private String projectCode;

}
