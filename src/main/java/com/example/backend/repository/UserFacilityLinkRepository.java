package com.example.backend.repository;

import com.example.backend.entity.UserFacilityLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserFacilityLinkRepository extends JpaRepository<UserFacilityLink, Long> {

    Optional<UserFacilityLink> findByUserIdAndFacilityId(Long userId, Long facilityId);

    boolean existsByUserIdAndFacilityId(Long userId, Long facilityId);

    void deleteByUserIdAndFacilityId(Long userId, Long facilityId);

    List<UserFacilityLink> findByUserId(Long userId);

    List<UserFacilityLink> findByFacilityId(Long facilityId);

    void deleteByUserId(Long userId);
}
