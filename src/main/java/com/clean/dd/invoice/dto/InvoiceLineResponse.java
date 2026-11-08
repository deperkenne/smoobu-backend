package com.clean.dd.invoice.dto;

import com.clean.dd.invoice.InvoiceLine;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InvoiceLineResponse(
		Long id,
		Long reservationId,
		String bookingReference,
		String propertyName,
		String roomNumber,
		LocalDate arrivalDate,
		LocalDate departureDate,
		BigDecimal basePrice,
		BigDecimal sundaySurcharge,
		BigDecimal lineTotal) {

	public static InvoiceLineResponse from(InvoiceLine l) {
		return new InvoiceLineResponse(l.getId(), l.getReservation().getId(), l.getBookingNumber(),
				l.getPropertyName(), l.getRoomNumber(), l.getCheckIn(), l.getCheckOut(),
				l.getBasePrice(), l.getSundaySurcharge(), l.getLineTotal());
	}
}
