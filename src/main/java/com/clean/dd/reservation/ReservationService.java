package com.clean.dd.reservation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;

    public ReservationService(ReservationRepository reservationRepository) {
        this.reservationRepository = reservationRepository;
    }

    @Transactional
    public List<Reservation> saveAll(List<Reservation> reservations) {
        return reservationRepository.saveAll(reservations);
    }
}
