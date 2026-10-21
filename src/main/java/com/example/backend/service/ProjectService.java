package com.example.backend.service;

import com.example.backend.dto.facility.FacilityResponseDto;
import com.example.backend.dto.project.ProjectRequestDto;
import com.example.backend.dto.project.ProjectResponseDto;
import com.example.backend.entity.Facility;
import com.example.backend.entity.Project;
import com.example.backend.repository.FacilityRepository;
import com.example.backend.repository.ProjectRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final FacilityRepository facilityRepository;

    public ProjectService(ProjectRepository projectRepository, FacilityRepository facilityRepository) {
        this.projectRepository = projectRepository;
        this.facilityRepository = facilityRepository;
    }

    // 1. CREATE PROJECT (SuperAdmin only)
    @Transactional
    @PreAuthorize("hasRole('SuperAdmin')")
    public ProjectResponseDto createProject(ProjectRequestDto request) {
        Project project = new Project();
        project.setName(request.getName());

        // 1. Save and flush so H2 assigns the auto-increment ID immediately
        Project savedProject = projectRepository.saveAndFlush(project);

        // 2. Set the formatted code: PRJ-001, PRJ-010, etc.
        savedProject.setProjectCode(String.format("PRJ-%03d", savedProject.getId()));
        savedProject = projectRepository.save(savedProject);

        return mapToResponseDto(savedProject, "Project Created Successfully");
    }

    // 2. GET ALL PROJECTS (SuperAdmin only) - Includes associated facilities
    @PreAuthorize("hasRole('SuperAdmin')")
    public List<ProjectResponseDto> getAllProjects() {
        return projectRepository.findAll()
                .stream()
                .map(project -> {
                    List<FacilityResponseDto> facilities = facilityRepository.findByProjectId(project.getId())
                            .stream()
                            .map(this::mapFacilityToDto)
                            .collect(Collectors.toList());

                    ProjectResponseDto dto = mapToResponseDto(project, null);
                    dto.setFacilities(facilities);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    // 4. UPDATE PROJECT (SuperAdmin only)
    @PreAuthorize("hasRole('SuperAdmin')")
    public ProjectResponseDto updateProject(Long id, ProjectRequestDto request) {
        Project existingProject = projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Project not found with id: " + id));

        existingProject.setName(request.getName());

        Project updatedProject = projectRepository.save(existingProject);
        return mapToResponseDto(updatedProject, "Project Updated Successfully");
    }

    // 5. DELETE PROJECT (SuperAdmin only)
    @PreAuthorize("hasRole('SuperAdmin')")
    public void deleteProject(Long id) {
        if (!projectRepository.existsById(id)) {
            throw new RuntimeException("Project not found with id: " + id);
        }
        projectRepository.deleteById(id);
    }

    // Helper: Map Project entity to ProjectResponseDto
    private ProjectResponseDto mapToResponseDto(Project project, String message) {
        ProjectResponseDto response = new ProjectResponseDto();
        response.setId(project.getId());
        response.setName(project.getName());
        response.setProjectCode(project.getProjectCode());
        response.setCreatedAt(project.getCreatedAt());
        response.setUpdatedAt(project.getUpdatedAt());
        response.setMessage(message);
        return response;
    }

    // Helper: Map Facility entity to FacilityResponseDto
    private FacilityResponseDto mapFacilityToDto(Facility f) {
        return new FacilityResponseDto(
                f.getId(),
                f.getProject() != null ? f.getProject().getId() : null,
                f.getProject() != null ? f.getProject().getName() : null,
                f.getFacilityCode(),
                f.getName(),
                f.getContactPerson(),
                f.getEmail(),
                f.getPhone(),
                f.getAddress(),
                f.getStatus(),
                f.getCreatedAt(),
                f.getUpdatedAt(),
                null
        );
    }
}
