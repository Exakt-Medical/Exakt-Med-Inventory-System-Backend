package com.example.backend.sync.service;

import com.example.backend.entity.Facility;
import com.example.backend.entity.Project;
import com.example.backend.repository.FacilityRepository;
import com.example.backend.repository.ProjectRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "facility-project.sync.messaging.enabled", havingValue = "true")
public class FacilityProjectSyncConsumer {

    private final ProjectRepository projectRepository;
    private final FacilityRepository facilityRepository;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "${facility-project.sync.messaging.project-queue:ipkras.project.sync.queue}")
    @Transactional
    public void consumeProjectSyncEvent(String payload) {
        try {
            Map<String, Object> event = objectMapper.readValue(payload, new TypeReference<>() {});
            String eventType = asString(event.get("eventType"));
            Map<String, Object> data = asMap(event.get("data"));

            if (data == null) {
                log.warn("Received project sync event without data payload: {}", payload);
                return;
            }

            Long ipkrasProjectId = asLong(data.get("projectId"));
            String projectName = asString(data.get("projectName"));
            String projectTagging = asString(data.get("projectTagging"));

            if ("PROJECT_DELETED".equalsIgnoreCase(eventType)) {
                handleProjectDelete(ipkrasProjectId, projectTagging);
                return;
            }

            if (projectName == null || projectName.isBlank() || projectTagging == null || projectTagging.isBlank()) {
                log.warn("Skipping project sync event due to missing projectName or projectTagging: {}", payload);
                return;
            }

            upsertProject(ipkrasProjectId, projectName.trim(), projectTagging.trim(), eventType);
        } catch (Exception e) {
            log.error("Failed to process project sync event payload={}", payload, e);
            throw new RuntimeException("Failed to process project sync event", e);
        }
    }

    @RabbitListener(queues = "${facility-project.sync.messaging.facility-queue:ipkras.facility.sync.queue}")
    @Transactional
    public void consumeFacilitySyncEvent(String payload) {
        try {
            Map<String, Object> event = objectMapper.readValue(payload, new TypeReference<>() {});
            String eventType = asString(event.get("eventType"));
            Map<String, Object> data = asMap(event.get("data"));

            if (data == null) {
                log.warn("Received facility sync event without data payload: {}", payload);
                return;
            }

            Long ipkrasFacilityId = asLong(data.get("facilityId"));
            String groupTagging = asString(data.get("groupTagging"));
            String accreditation = asString(data.get("accreditation"));
            String hciName = asString(data.get("hciName"));

            if (accreditation == null || accreditation.isBlank() || hciName == null || hciName.isBlank()) {
                log.warn("Skipping facility sync event due to missing accreditation or hciName: {}", payload);
                return;
            }

            if (groupTagging == null || groupTagging.isBlank()) {
                log.warn("Skipping facility sync event due to missing groupTagging: {}", payload);
                return;
            }

            String address = buildAddress(
                    asString(data.get("streetHouseNo")),
                    asString(data.get("barangay")),
                    asString(data.get("city")),
                    asString(data.get("province")),
                    asString(data.get("region"))
            );

            upsertFacility(
                    ipkrasFacilityId,
                    groupTagging.trim(),
                    accreditation.trim(),
                    hciName.trim(),
                    address,
                    eventType
            );
        } catch (Exception e) {
            log.error("Failed to process facility sync event payload={}", payload, e);
            throw new RuntimeException("Failed to process facility sync event", e);
        }
    }

    private void upsertProject(Long ipkrasProjectId, String projectName, String projectCode, String eventType) {
        Optional<Project> existingOpt = Optional.empty();
        if (ipkrasProjectId != null) {
            existingOpt = projectRepository.findByIpkrasProjectId(ipkrasProjectId);
        }
        if (existingOpt.isEmpty()) {
            existingOpt = projectRepository.findByProjectCodeIgnoreCase(projectCode);
        }

        Project project = existingOpt.orElseGet(Project::new);
        if (ipkrasProjectId != null) {
            project.setIpkrasProjectId(ipkrasProjectId);
        }
        project.setName(projectName);
        project.setProjectCode(projectCode);

        Project saved = projectRepository.save(project);
        log.info("Synced PROJECT event={} localId={} ipkrasProjectId={} code='{}' name='{}'",
                eventType, saved.getId(), saved.getIpkrasProjectId(), saved.getProjectCode(), saved.getName());
    }

    private void handleProjectDelete(Long ipkrasProjectId, String projectCode) {
        Optional<Project> existingOpt = Optional.empty();
        if (ipkrasProjectId != null) {
            existingOpt = projectRepository.findByIpkrasProjectId(ipkrasProjectId);
        }
        if (existingOpt.isEmpty() && projectCode != null && !projectCode.isBlank()) {
            existingOpt = projectRepository.findByProjectCodeIgnoreCase(projectCode.trim());
        }

        if (existingOpt.isEmpty()) {
            log.info("Project to delete not found for ipkrasProjectId={} projectCode={}", ipkrasProjectId, projectCode);
            return;
        }

        Project project = existingOpt.get();
        List<Facility> linkedFacilities = facilityRepository.findByProjectId(project.getId());
        if (!linkedFacilities.isEmpty()) {
            log.warn("Cannot delete synced project id={} code='{}' because {} facilities are still linked",
                    project.getId(), project.getProjectCode(), linkedFacilities.size());
            return;
        }

        projectRepository.delete(project);
        log.info("Deleted synced PROJECT localId={} ipkrasProjectId={} code='{}'",
                project.getId(), project.getIpkrasProjectId(), project.getProjectCode());
    }

    private void upsertFacility(Long ipkrasFacilityId,
                                String groupTagging,
                                String facilityCode,
                                String facilityName,
                                String address,
                                String eventType) {
        // Resolve parent project by groupTagging (mapped to projectCode); auto-create if not yet synced
        Project project = projectRepository.findByProjectCodeIgnoreCase(groupTagging)
                .orElseGet(() -> {
                    Project placeholder = new Project();
                    placeholder.setName(groupTagging);
                    placeholder.setProjectCode(groupTagging);
                    Project created = projectRepository.save(placeholder);
                    log.info("Auto-created placeholder project localId={} code='{}' for incoming facility '{}'",
                            created.getId(), created.getProjectCode(), facilityName);
                    return created;
                });

        Optional<Facility> existingOpt = Optional.empty();
        if (ipkrasFacilityId != null) {
            existingOpt = facilityRepository.findByIpkrasFacilityId(ipkrasFacilityId);
        }
        if (existingOpt.isEmpty()) {
            existingOpt = facilityRepository.findByFacilityCodeIgnoreCase(facilityCode);
        }

        Facility facility = existingOpt.orElseGet(Facility::new);
        if (ipkrasFacilityId != null) {
            facility.setIpkrasFacilityId(ipkrasFacilityId);
        }
        facility.setProject(project);
        facility.setFacilityCode(facilityCode);
        facility.setName(facilityName);
        if (address != null && !address.isBlank()) {
            facility.setAddress(address);
        }
        if (facility.getStatus() == null) {
            facility.setStatus(Facility.Status.Active);
        }

        Facility saved = facilityRepository.save(facility);
        log.info("Synced FACILITY event={} localId={} ipkrasFacilityId={} code='{}' name='{}' projectCode='{}'",
                eventType, saved.getId(), saved.getIpkrasFacilityId(), saved.getFacilityCode(), saved.getName(), project.getProjectCode());
    }

    private String buildAddress(String streetHouseNo, String barangay, String city, String province, String region) {
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, streetHouseNo);
        addIfPresent(parts, barangay);
        addIfPresent(parts, city);
        addIfPresent(parts, province);
        addIfPresent(parts, region);
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private void addIfPresent(List<String> parts, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(value.trim());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object obj) {
        if (obj instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    private String asString(Object obj) {
        return obj != null ? String.valueOf(obj) : null;
    }

    private Long asLong(Object obj) {
        if (obj instanceof Number number) {
            return number.longValue();
        }
        if (obj instanceof String str && !str.isBlank()) {
            try {
                return Long.parseLong(str.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }
}

