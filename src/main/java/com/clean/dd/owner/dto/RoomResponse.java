package com.clean.dd.owner.dto;

import com.clean.dd.owner.Room;

import java.math.BigDecimal;
import java.util.UUID;

public record RoomResponse(UUID id, UUID propertyId, String number, BigDecimal price) {

	public static RoomResponse from(Room r) {
		return new RoomResponse(r.getId(), r.getProperty().getId(), r.getNumber(), r.getPrice());
	}
}
