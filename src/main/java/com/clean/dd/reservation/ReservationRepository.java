package com.clean.dd.reservation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * N'expose volontairement qu'une seule méthode : l'insertion d'une liste de réservations.
 * Spring Data fournit l'implémentation (SimpleJpaRepository#saveAll).
 */
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

//    List<Reservation> saveAll(List<Reservation> reservations);
}
