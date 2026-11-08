package com.clean.dd.reservation;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

	Optional<Reservation> findByBookingNumber(String bookingNumber);

	/** Toutes les réservations, avec logement et propriétaire chargés en une requête (pas de N+1). */
	@EntityGraph(attributePaths = {"property", "property.owner"})
	List<Reservation> findAllByOrderByCheckInAsc();

	/** Réservations rattachées au customer, triées par date d'arrivée. */
	@EntityGraph(attributePaths = {"property", "property.owner"})
	@Query("select r from Reservation r where r.customer.id = :customerId order by r.checkIn")
	List<Reservation> findByCustomer(@Param("customerId") String customerId);

	/** Réservations d'un statut donné (ex. CANCELLED), les plus récemment annulées d'abord. */
	@EntityGraph(attributePaths = {"property", "property.owner"})
	List<Reservation> findByStatusOrderByCancelledAtDesc(String status);

	@EntityGraph(attributePaths = {"property", "property.owner"})
	@Query("""
			select r from Reservation r
			where r.status = :status and r.property.owner.customer.id = :accountId
			order by r.cancelledAt desc
			""")
	List<Reservation> findByStatusAndAccount(@Param("status") String status, @Param("accountId") String accountId);

	/** Réservations du logement qui chevauchent [from, to] ; chaque borne est facultative. */
	@Query("""
			select r from Reservation r
			where r.property.id = :propertyId
			  and (cast(:from as LocalDate) is null or r.checkOut >= :from)
			  and (cast(:to as LocalDate) is null or r.checkIn <= :to)
			order by r.checkIn
			""")
	List<Reservation> findByPropertyInPeriod(@Param("propertyId") UUID propertyId,
											 @Param("from") LocalDate from,
											 @Param("to") LocalDate to);
}
