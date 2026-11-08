package com.clean.dd.invoice;

import com.clean.dd.common.ConflictException;
import com.clean.dd.common.NotFoundException;
import com.clean.dd.invoice.dto.InvoiceLineResponse;
import com.clean.dd.invoice.dto.InvoiceResponse;
import com.clean.dd.invoice.dto.InvoiceSummaryResponse;
import com.clean.dd.owner.Owner;
import com.clean.dd.owner.OwnerRepository;
import com.clean.dd.reservation.Reservation;
import com.clean.dd.shared.security.Caller;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Service
public class InvoiceService {

	/** Statut des réservations facturées. */
	static final String CONFIRMED = "CONFIRMED";

	/** Supplément appliqué quand l'arrivée tombe un dimanche. */
	static final BigDecimal SUNDAY_SURCHARGE = new BigDecimal("10.00");

	private final OwnerRepository ownerRepository;
	private final InvoiceRepository invoiceRepository;
	private final InvoiceLineRepository invoiceLineRepository;
	private final Clock clock;

	public InvoiceService(OwnerRepository ownerRepository, InvoiceRepository invoiceRepository,
						  InvoiceLineRepository invoiceLineRepository, Clock clock) {
		this.ownerRepository = ownerRepository;
		this.invoiceRepository = invoiceRepository;
		this.invoiceLineRepository = invoiceLineRepository;
		this.clock = clock;
	}

	/**
	 * Facture du mois d'un propriétaire du client (ou de n'importe lequel pour ADMIN).
	 * Calculée à chaque appel : réservations CONFIRMED dont l'arrivée tombe dans le mois et qui ne sont pas
	 * dans une autre facture, prix de la réservation + 10 € le dimanche.
	 * Déjà établie et mois en cours → recalculée (mêmes id et numéro) : nouvelles réservations ajoutées,
	 * annulées retirées. Mois terminé (heure de Berlin) → facture figée, renvoyée telle quelle.
	 * month null → mois en cours.
	 */
	@Transactional
	public InvoiceResponse generate(Caller caller, UUID ownerId, YearMonth requestedMonth) {
		YearMonth month = requestedMonth != null ? requestedMonth : YearMonth.now(clock);
		Owner owner = findAccessibleOwner(caller, ownerId);
		Invoice existing = invoiceRepository.findByOwnerIdAndBillingMonth(ownerId, month.toString()).orElse(null);
		if (existing != null) {
			if (month.isBefore(YearMonth.now(clock))) {
				return toResponse(existing, invoiceLineRepository.findByInvoiceIdOrderByCheckIn(existing.getId()));
			}
			invoiceLineRepository.deleteByInvoiceId(existing.getId());
		}
		List<Reservation> reservations = invoiceLineRepository.findUninvoicedReservations(
				ownerId, CONFIRMED, month.atDay(1), month.atEndOfMonth());
		if (existing == null && reservations.isEmpty()) {
			throw new NotFoundException("Aucune réservation confirmée à facturer en " + month + " pour " + owner.getName());
		}
		List<String> withoutPrice = reservations.stream()
				.filter(r -> r.getPrice() == null)
				.map(Reservation::getBookingNumber)
				.toList();
		if (!withoutPrice.isEmpty()) {
			throw new ConflictException("Facture impossible : réservations sans prix " + String.join(", ", withoutPrice));
		}

		BigDecimal total = reservations.stream()
				.map(r -> r.getPrice().add(sundaySurcharge(r.getCheckIn())))
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		Invoice invoice;
		if (existing == null) {
			invoice = invoiceRepository.save(Invoice.create(owner, month, total));
		} else {
			existing.recalculate(total);
			invoice = existing;
		}
		List<InvoiceLine> lines = invoiceLineRepository.saveAll(reservations.stream()
				.map(r -> InvoiceLine.create(invoice, r, sundaySurcharge(r.getCheckIn())))
				.toList());
		return toResponse(invoice, lines);
	}

	@Transactional(readOnly = true)
	public List<InvoiceSummaryResponse> listByOwner(Caller caller, UUID ownerId) {
		findAccessibleOwner(caller, ownerId);
		return invoiceRepository.findByOwnerIdOrderByBillingMonthDesc(ownerId).stream()
				.map(InvoiceSummaryResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public InvoiceResponse get(Caller caller, Long id) {
		Invoice invoice = invoiceRepository.findWithOwnerById(id)
				.filter(i -> caller.canAccess(i.getOwner().getCustomer()))
				.orElseThrow(() -> new NotFoundException("Facture introuvable : " + id));
		return toResponse(invoice, invoiceLineRepository.findByInvoiceIdOrderByCheckIn(id));
	}

	static BigDecimal sundaySurcharge(LocalDate arrival) {
		return arrival.getDayOfWeek() == DayOfWeek.SUNDAY ? SUNDAY_SURCHARGE : BigDecimal.ZERO;
	}

	private static InvoiceResponse toResponse(Invoice invoice, List<InvoiceLine> lines) {
		return new InvoiceResponse(InvoiceSummaryResponse.from(invoice),
				lines.stream().map(InvoiceLineResponse::from).toList());
	}

	private Owner findAccessibleOwner(Caller caller, UUID ownerId) {
		return ownerRepository.findById(ownerId)
				.filter(o -> caller.canAccess(o.getCustomer()))
				.orElseThrow(() -> new NotFoundException("Propriétaire introuvable : " + ownerId));
	}
}
