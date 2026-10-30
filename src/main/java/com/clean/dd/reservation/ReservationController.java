package com.clean.dd.reservation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

	private static final Logger log = LoggerFactory.getLogger(ReservationController.class);
	private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)

    public List<Reservation> save(@RequestBody @NotEmpty List<@Valid Reservation> reservations) {

		log.info("Réception de {} réservation(s)", reservations.size());

		reservations.forEach(r -> log.info(
			"Réservation reçue : n°{} | logement={} | du {} au {} | prépayée={} ",
			r.getBookingNumber(), r.getPropertyName(), r.getCheckIn(), r.getCheckOut(),
			r.getGuestMessage()));

        return reservationService.saveAll(reservations);
    }
}
