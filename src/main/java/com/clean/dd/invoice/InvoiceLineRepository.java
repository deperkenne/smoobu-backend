package com.clean.dd.invoice;

import com.clean.dd.reservation.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface InvoiceLineRepository extends JpaRepository<InvoiceLine, Long> {

	List<InvoiceLine> findByInvoiceIdOrderByCheckIn(Long invoiceId);

	/** Supprime les lignes d'une facture avant son recalcul. */
	@Modifying(flushAutomatically = true)
	@Query("delete from InvoiceLine l where l.invoice.id = :invoiceId")
	void deleteByInvoiceId(@Param("invoiceId") Long invoiceId);

	/**
	 * Réservations à facturer : du propriétaire, du statut donné (casse ignorée), arrivant dans [start, end],
	 * et jamais facturées (une réservation n'apparaît que dans une seule facture).
	 */
	@Query("""
			select r from Reservation r
			where r.property.owner.id = :ownerId
			  and upper(r.status) = :status
			  and r.checkIn between :start and :end
			  and not exists (select 1 from InvoiceLine l where l.reservation = r)
			order by r.checkIn
			""")
	List<Reservation> findUninvoicedReservations(@Param("ownerId") UUID ownerId,
												 @Param("status") String status,
												 @Param("start") LocalDate start,
												 @Param("end") LocalDate end);
}
