package com.clean.dd.owner;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID> {

	List<Room> findByPropertyIdOrderByNumber(UUID propertyId);

	boolean existsByPropertyIdAndNumber(UUID propertyId, String number);

	Optional<Room> findByPropertyIdAndNumber(UUID propertyId, String number);
}
