package com.clean.dd.reservation;

import com.clean.dd.common.NotFoundException;
import com.clean.dd.owner.Property;
import com.clean.dd.owner.PropertyRepository;
import com.clean.dd.owner.Room;
import com.clean.dd.owner.RoomRepository;
import com.clean.dd.reservation.dto.ReservationResponse;
import com.clean.dd.reservation.dto.SimulateReservationRequest;
import com.clean.dd.shared.security.Caller;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ReservationService {

	/** Numéro de chambre par défaut quand l'e-mail Smoobu n'en donne pas : le logement n'a qu'une chambre. */
	static final String SINGLE_ROOM = "unique";

	private final ReservationRepository reservationRepository;
	private final PropertyRepository propertyRepository;
	private final RoomRepository roomRepository;

	public ReservationService(ReservationRepository reservationRepository, PropertyRepository propertyRepository,
							  RoomRepository roomRepository) {
		this.reservationRepository = reservationRepository;
		this.propertyRepository = propertyRepository;
		this.roomRepository = roomRepository;
	}

	/**
	 * Crée chaque réservation, ou la met à jour si bookingReference existe déjà (jamais de doublon).
	 * Sans roomNumber, le logement n'a qu'une chambre : roomNumber = "unique" et prix de sa seule chambre
	 * (vide si le logement a 0 ou plusieurs chambres).
	 * Statut CANCELLED : la réservation existante (retrouvée par son Buchungsnummer) passe en CANCELLED,
	 * rien d'autre ne change ; Buchungsnummer inconnu → annulation ignorée (absente de la réponse), rien n'est inséré.
	 * Tout ou rien : un logement introuvable, ou une chambre donnée mais introuvable, annule toute la liste.
	 */
	@Transactional
	public List<ReservationResponse> simulate(List<SimulateReservationRequest> requests) {
		return requests.stream().map(this::simulateOne).flatMap(Optional::stream).toList();
	}

	private Optional<ReservationResponse> simulateOne(SimulateReservationRequest request) {
		String bookingReference = request.bookingReference().strip();
		if (Reservation.CANCELLED.equalsIgnoreCase(request.status().strip())) {
			return reservationRepository.findByBookingNumber(bookingReference)
					.map(reservation -> {
						reservation.cancel();
						return ReservationResponse.from(reservation);
					});
		}

		String emailLabel = request.emailLabel().strip();
		Property property = propertyRepository.findByEmailLabel(emailLabel)
				.orElseThrow(() -> new NotFoundException("Aucun logement avec l'emailLabel '" + emailLabel + "'"));
		String roomNumber;
		BigDecimal price;
		if (request.roomNumber() != null && !request.roomNumber().isBlank()) {
			roomNumber = request.roomNumber().strip();
			price = roomRepository.findByPropertyIdAndNumber(property.getId(), roomNumber)
					.map(Room::getPrice)
					.orElseThrow(() -> new NotFoundException(
							"Aucune chambre '" + roomNumber + "' dans le logement '" + emailLabel + "'"));
		} else {
			// Logement à une seule chambre : on prend le prix de cette chambre.
			roomNumber = SINGLE_ROOM;
			List<Room> rooms = roomRepository.findByPropertyIdOrderByNumber(property.getId());
			price = rooms.size() == 1 ? rooms.getFirst().getPrice() : null;
		}

		Reservation reservation = reservationRepository.findByBookingNumber(bookingReference)
				.orElse(null);
		if (reservation == null) {
			reservation = reservationRepository.save(Reservation.create(property, roomNumber, price, bookingReference,
					request.arrivalDate(), request.arrivalDay(), request.departureDate(),
					request.channel(), request.status()));
		} else {
			reservation.update(property, roomNumber, price, request.arrivalDate(), request.arrivalDay(), request.departureDate(),
					request.channel(), request.status());
		}
		return Optional.of(ReservationResponse.from(reservation));
	}

	@Transactional(readOnly = true)
	public List<ReservationResponse> listCancelled(Caller caller) {
		List<Reservation> reservations = caller.admin()
				? reservationRepository.findByStatusOrderByCancelledAtDesc(Reservation.CANCELLED)
				: reservationRepository.findByStatusAndAccount(Reservation.CANCELLED, caller.customerId());
		return reservations.stream()
				.map(ReservationResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<ReservationResponse> listAll() {
		return reservationRepository.findAllByOrderByCheckInAsc().stream()
				.map(ReservationResponse::from)
				.toList();
	}

	/** Réservations du customer connecté (lu dans le jeton), triées par date d'arrivée. */
	@Transactional(readOnly = true)
	public List<ReservationResponse> listMine(Caller caller) {
		return reservationRepository.findByCustomer(caller.customerId()).stream()
				.map(ReservationResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<ReservationResponse> listByProperty(Caller caller, UUID propertyId, LocalDate from, LocalDate to) {
		if (from != null && to != null && from.isAfter(to)) {
			throw new IllegalArgumentException("'from' doit être antérieur ou égal à 'to'");
		}
		propertyRepository.findById(propertyId)
				.filter(p -> caller.canAccess(p.getOwner().getCustomer()))
				.orElseThrow(() -> new NotFoundException("Logement introuvable : " + propertyId));
		return reservationRepository.findByPropertyInPeriod(propertyId, from, to).stream()
				.map(ReservationResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public ReservationResponse get(Caller caller, Long id) {
		return reservationRepository.findById(id)
				.filter(r -> caller.canAccess(r.getProperty().getOwner().getCustomer()))
				.map(ReservationResponse::from)
				.orElseThrow(() -> new NotFoundException("Réservation introuvable : " + id));
	}
}
