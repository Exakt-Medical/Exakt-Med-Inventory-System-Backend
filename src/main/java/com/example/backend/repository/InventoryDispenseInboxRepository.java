package com.example.backend.repository;

import com.example.backend.entity.InventoryDispenseInbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryDispenseInboxRepository extends JpaRepository<InventoryDispenseInbox, Long> {

    boolean existsByEventId(String eventId);

    Optional<InventoryDispenseInbox> findByEventId(String eventId);
}

