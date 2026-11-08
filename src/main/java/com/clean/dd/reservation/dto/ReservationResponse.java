package com.clean.dd.reservation.dto;

import com.clean.dd.reservation.Reservation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ReservationResponse(
		Long id,
		String bookingReference,
		LocalDate arrivalDate,
		String arrivalDay,
		LocalDate departureDate,
		String channel,
		String status,
		Instant cancelledAt,
		BigDecimal price,
		UUID propertyId,
		String propertyName,
		String roomNumber,
		String ownerCustomerId) {

	public static ReservationResponse from(Reservation r) {
		return new ReservationResponse(r.getId(), r.getBookingNumber(), r.getCheckIn(), r.getArrivalDay(), r.getCheckOut(),
				r.getChannel(), r.getStatus(), r.getCancelledAt(), r.getPrice(), r.getProperty().getId(), r.getProperty().getName(),
				r.getRoomNumber(),
				r.getCustomerId());
	}
}
