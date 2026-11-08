package com.clean.dd.reservation;

import com.clean.dd.reservation.dto.ReservationResponse;
import com.clean.dd.reservation.dto.SimulateReservationRequest;
import com.clean.dd.shared.security.Caller;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** simulate : ADMIN (lecture des e-mails Smoobu) ; lectures : CUSTOMER (ses réservations) ou ADMIN (toutes). */
@RestController
@RequestMapping("/api")
public class ReservationController {

	private final ReservationService reservationService;

	public ReservationController(ReservationService reservationService) {
		this.reservationService = reservationService;
	}

	/** Simule la réception d'e-mails Smoobu : création, ou mise à jour si bookingReference existe. */
	@PostMapping("/reservations/simulate")
	public List<ReservationResponse> simulate(
			@RequestBody @NotEmpty List<@Valid SimulateReservationRequest> requests) {
		return reservationService.simulate(requests);
	}

	/** Toutes les réservations de la base, sans jeton (route publique). */
	@GetMapping("/reservations")
	public List<ReservationResponse> listAll() {
		return reservationService.listAll();
	}

	/** Réservations du customer connecté ; le customer vient du jeton, jamais de la requête. */
	@GetMapping("/reservations/mine")
	@PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
	public List<ReservationResponse> listMine(JwtAuthenticationToken authentication) {
		return reservationService.listMine(Caller.of(authentication));
	}

	/** Réservations annulées (statut CANCELLED), les plus récemment annulées d'abord. */
	@GetMapping("/reservations/cancelled")
	@PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
	public List<ReservationResponse> listCancelled(JwtAuthenticationToken authentication) {
		return reservationService.listCancelled(Caller.of(authentication));
	}

	@GetMapping("/properties/{propertyId}/reservations")
	@PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
	public List<ReservationResponse> listByProperty(
			JwtAuthenticationToken authentication,
			@PathVariable UUID propertyId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return reservationService.listByProperty(Caller.of(authentication), propertyId, from, to);
	}

	@GetMapping("/reservations/{id}")
	@PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
	public ReservationResponse get(JwtAuthenticationToken authentication, @PathVariable Long id) {
		return reservationService.get(Caller.of(authentication), id);
	}
}
