package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "user_facility_links",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "facility_id"})
    }
)
@Getter
@Setter
public class UserFacilityLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facilityId", nullable = false)
    private Facility facility;

    @Column(updatable = false)
    private LocalDateTime assignedAt;

    @PrePersist
    private void onAssign() {
        assignedAt = LocalDateTime.now();
    }

}
