package com.clean.dd.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Contenu utile d'un e-mail Smoobu (simulé). */
public record SimulateReservationRequest(
		@NotBlank @Size(max = 200) String emailLabel,
		/** Facultatif : "unique" si l'e-mail Smoobu ne donne pas de numéro de chambre. */
		@Size(max = 20) String roomNumber,
		@NotBlank @Size(max = 255) String bookingReference,
		@NotNull LocalDate arrivalDate,
		@Size(max = 20) String arrivalDay,
		@NotNull LocalDate departureDate,
		@NotBlank @Size(max = 50) String channel,
		@NotBlank @Size(max = 255) String status) {
}
