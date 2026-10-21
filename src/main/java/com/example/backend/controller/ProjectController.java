package com.example.backend.controller;

import com.example.backend.dto.project.ProjectRequestDto;
import com.example.backend.dto.project.ProjectResponseDto;
import com.example.backend.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@Tag(name = "Projects", description = "Endpoints for managing top-level health initiatives and operational programs")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    // 1. CREATE PROJECT
    @PostMapping
    @Operation(summary = "Create Project", description = "Creates a new organizational project to group facilities.")
    public ResponseEntity<ProjectResponseDto> createProject(@Valid @RequestBody ProjectRequestDto request) {
        ProjectResponseDto response = projectService.createProject(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // 2. GET ALL PROJECTS
    @GetMapping
    @Operation(summary = "Get All Projects", description = "Retrieves all projects.")
    public ResponseEntity<List<ProjectResponseDto>> getAllProjects() {
        return ResponseEntity.ok(projectService.getAllProjects());
    }

    // 3. UPDATE PROJECT
    @PutMapping("/{id}")
    @Operation(summary = "Update Project", description = "Updates details, name, or description of a project.")
    public ResponseEntity<ProjectResponseDto> updateProject(
            @Parameter(description = "Project ID", required = true)
            @PathVariable Long id,
            @Valid @RequestBody ProjectRequestDto request) {
        return ResponseEntity.ok(projectService.updateProject(id, request));
    }

    // 4. DELETE PROJECT
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete Project", description = "Deletes a project.")
    public ResponseEntity<String> deleteProject(
            @Parameter(description = "Project ID", required = true)
            @PathVariable Long id) {
        projectService.deleteProject(id);
        return ResponseEntity.ok("Project deleted successfully");
    }
}
