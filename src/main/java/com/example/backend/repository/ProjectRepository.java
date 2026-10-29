package com.example.backend.repository;

import com.example.backend.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {
    Optional<Project> findByProjectCode(String projectCode);
    Optional<Project> findByProjectCodeIgnoreCase(String projectCode);
    Optional<Project> findByIpkrasProjectId(Long ipkrasProjectId);
    boolean existsByProjectCodeIgnoreCase(String projectCode);
}
