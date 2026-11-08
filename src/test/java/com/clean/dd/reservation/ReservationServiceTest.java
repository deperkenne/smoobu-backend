package com.clean.dd.reservation;

import com.clean.dd.common.NotFoundException;
import com.clean.dd.customer.Customer;
import com.clean.dd.owner.Owner;
import com.clean.dd.owner.Property;
import com.clean.dd.owner.PropertyRepository;
import com.clean.dd.owner.Room;
import com.clean.dd.owner.RoomRepository;
import com.clean.dd.reservation.dto.ReservationResponse;
import com.clean.dd.reservation.dto.SimulateReservationRequest;
import com.clean.dd.shared.security.Caller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

	private static final LocalDate ARRIVAL = LocalDate.of(2026, 11, 10);
	private static final LocalDate DEPARTURE = LocalDate.of(2026, 11, 13);

	private static final Caller ADMIN = new Caller("admin-sub", true);
	private static final Caller OWNING_CUSTOMER = new Caller("sub-1", false);
	private static final Caller OTHER_CUSTOMER = new Caller("sub-2", false);

	@Mock
	private ReservationRepository reservationRepository;
	@Mock
	private PropertyRepository propertyRepository;
	@Mock
	private RoomRepository roomRepository;

	@InjectMocks
	private ReservationService reservationService;

	private UUID propertyId;
	private Property property;
	private UUID roomId;
	private Room room;

	@BeforeEach
	void setUp() {
		Owner owner = Owner.create(Customer.register("sub-1", "c1@example.com", "c1"), "Max Muster", null, null);
		ReflectionTestUtils.setField(owner, "id", UUID.randomUUID());
		propertyId = UUID.randomUUID();
		property = Property.create(owner, "Loft", "Loft Nürnberg", null, null, null);
		ReflectionTestUtils.setField(property, "id", propertyId);
		roomId = UUID.randomUUID();
		room = Room.create(property, "101", new BigDecimal("45.00"));
		ReflectionTestUtils.setField(room, "id", roomId);
	}

	private void propertyAndRoomFound() {
		when(propertyRepository.findByEmailLabel("Loft Nürnberg")).thenReturn(Optional.of(property));
		when(roomRepository.findByPropertyIdAndNumber(propertyId, "101")).thenReturn(Optional.of(room));
	}

	private static SimulateReservationRequest requestWithRoom(String roomNumber, String bookingReference) {
		return new SimulateReservationRequest("Loft Nürnberg", roomNumber, bookingReference,
				ARRIVAL, "Montag", DEPARTURE, "Booking.com", "confirmed");
	}

	private void saveReturnsArgument() {
		when(reservationRepository.findByBookingNumber("6041336634")).thenReturn(Optional.empty());
		when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));
	}

	private static SimulateReservationRequest request(String bookingReference, LocalDate arrival,
													  LocalDate departure, String status) {
		return new SimulateReservationRequest(" Loft Nürnberg ", " 101 ", bookingReference,
				arrival, "Montag", departure, "Booking.com", status);
	}

	@Test
	void simulate_createsReservationWhenBookingReferenceIsNew() {
		propertyAndRoomFound();
		when(reservationRepository.findByBookingNumber("6041336634")).thenReturn(Optional.empty());
		when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> {
			Reservation saved = inv.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 42L);
			return saved;
		});

		ReservationResponse response = reservationService.simulate(
				List.of(request(" 6041336634 ", ARRIVAL, DEPARTURE, "confirmed"))).getFirst();

		assertThat(response.id()).isEqualTo(42L);
		assertThat(response.bookingReference()).isEqualTo("6041336634");
		assertThat(response.arrivalDate()).isEqualTo(ARRIVAL);
		assertThat(response.arrivalDay()).isEqualTo("Montag");
		assertThat(response.price()).isEqualByComparingTo(room.getPrice());
		assertThat(response.departureDate()).isEqualTo(DEPARTURE);
		assertThat(response.channel()).isEqualTo("Booking.com");
		assertThat(response.status()).isEqualTo("confirmed");
		assertThat(response.propertyId()).isEqualTo(propertyId);
		assertThat(response.propertyName()).isEqualTo("Loft");
		assertThat(response.roomNumber()).isEqualTo("101");
		assertThat(response.ownerCustomerId()).isEqualTo("sub-1");
	}

	@Test
	void simulate_throwsNotFoundWhenRoomUnknownInProperty() {
		when(propertyRepository.findByEmailLabel("Loft Nürnberg")).thenReturn(Optional.of(property));
		when(roomRepository.findByPropertyIdAndNumber(propertyId, "101")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> reservationService.simulate(
				List.of(request("6041336634", ARRIVAL, DEPARTURE, "confirmed"))))
				.isInstanceOf(NotFoundException.class)
				.hasMessageContaining("Aucune chambre")
				.hasMessageContaining("101");
		verifyNoInteractions(reservationRepository);
	}

	@Test
	void simulate_copiesRoomPriceAtBookingTime() {
		propertyAndRoomFound();
		when(reservationRepository.findByBookingNumber("6041336634")).thenReturn(Optional.empty());
		when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

		ReservationResponse response = reservationService.simulate(
				List.of(request("6041336634", ARRIVAL, DEPARTURE, "confirmed"))).getFirst();
		ArgumentCaptor<Reservation> saved = ArgumentCaptor.forClass(Reservation.class);
		verify(reservationRepository).save(saved.capture());
		room.changePrice(new BigDecimal("99.00"));

		assertThat(response.price()).isEqualByComparingTo("45.00");
		assertThat(saved.getValue().getPrice()).isEqualByComparingTo("45.00");
		assertThat(saved.getValue().getRoomNumber()).isEqualTo("101");
		assertThat(saved.getValue().getProperty()).isSameAs(property);
	}

	@Test
	void simulate_updatesExistingReservationWithoutExtraSave() {
		Reservation existing = Reservation.create(property, "101", new BigDecimal("45.00"), "6041336634",
				ARRIVAL, "Montag", DEPARTURE, "Booking.com", "confirmed");
		ReflectionTestUtils.setField(existing, "id", 7L);
		propertyAndRoomFound();
		when(reservationRepository.findByBookingNumber("6041336634")).thenReturn(Optional.of(existing));
		LocalDate newArrival = ARRIVAL.plusDays(1);
		LocalDate newDeparture = DEPARTURE.plusDays(2);

		ReservationResponse response = reservationService.simulate(
				List.of(request("6041336634", newArrival, newDeparture, "modified"))).getFirst();

		assertThat(response.id()).isEqualTo(7L);
		assertThat(response.arrivalDate()).isEqualTo(newArrival);
		assertThat(response.departureDate()).isEqualTo(newDeparture);
		assertThat(response.status()).isEqualTo("modified");
		assertThat(existing.getCheckIn()).isEqualTo(newArrival);
		assertThat(existing.getStatus()).isEqualTo("modified");
		verify(reservationRepository, never()).save(any());
	}

	@Test
	void simulate_throwsNotFoundWhenPropertyUnknown() {
		when(propertyRepository.findByEmailLabel("Loft Nürnberg"))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> reservationService.simulate(
				List.of(request("6041336634", ARRIVAL, DEPARTURE, "confirmed"))))
				.isInstanceOf(NotFoundException.class)
				.hasMessageContaining("Loft Nürnberg");
		verifyNoInteractions(roomRepository, reservationRepository);
	}

	@Test
	void simulate_rejectsDepartureNotAfterArrival() {
		propertyAndRoomFound();
		when(reservationRepository.findByBookingNumber("6041336634")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> reservationService.simulate(
				List.of(request("6041336634", ARRIVAL, ARRIVAL, "confirmed"))))
				.isInstanceOf(IllegalArgumentException.class);
		verify(reservationRepository, never()).save(any());
	}

	@Test
	void simulate_handlesEveryItemOfTheList() {
		propertyAndRoomFound();
		when(reservationRepository.findByBookingNumber(any())).thenReturn(Optional.empty());
		when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

		List<ReservationResponse> responses = reservationService.simulate(List.of(
				request("A", ARRIVAL, DEPARTURE, "confirmed"),
				request("B", ARRIVAL, DEPARTURE, "confirmed")));

		assertThat(responses).extracting(ReservationResponse::bookingReference).containsExactly("A", "B");
		verify(reservationRepository, times(2)).save(any());
	}

	@Test
	void simulate_withoutRoomNumber_usesUniqueAndPriceOfTheSingleRoom() {
		when(propertyRepository.findByEmailLabel("Loft Nürnberg")).thenReturn(Optional.of(property));
		when(roomRepository.findByPropertyIdOrderByNumber(propertyId))
				.thenReturn(List.of(Room.create(property, "1", new BigDecimal("80.00"))));
		saveReturnsArgument();

		ReservationResponse response = reservationService.simulate(
				List.of(requestWithRoom(null, "6041336634"))).getFirst();

		assertThat(ReservationService.SINGLE_ROOM).isEqualTo("unique");
		assertThat(response.roomNumber()).isEqualTo("unique");
		assertThat(response.price()).isEqualByComparingTo("80.00");
		ArgumentCaptor<Reservation> saved = ArgumentCaptor.forClass(Reservation.class);
		verify(reservationRepository).save(saved.capture());
		assertThat(saved.getValue().getRoomNumber()).isEqualTo("unique");
	}

	@Test
	void simulate_withoutRoomNumber_hasNoPriceWhenPropertyHasNoRoom() {
		when(propertyRepository.findByEmailLabel("Loft Nürnberg")).thenReturn(Optional.of(property));
		when(roomRepository.findByPropertyIdOrderByNumber(propertyId)).thenReturn(List.of());
		saveReturnsArgument();

		ReservationResponse response = reservationService.simulate(
				List.of(requestWithRoom(null, "6041336634"))).getFirst();

		assertThat(response.roomNumber()).isEqualTo("unique");
		assertThat(response.price()).isNull();
	}

	@Test
	void simulate_withoutRoomNumber_hasNoPriceWhenPropertyHasSeveralRooms() {
		when(propertyRepository.findByEmailLabel("Loft Nürnberg")).thenReturn(Optional.of(property));
		when(roomRepository.findByPropertyIdOrderByNumber(propertyId)).thenReturn(List.of(
				Room.create(property, "1", new BigDecimal("40.00")),
				Room.create(property, "2", new BigDecimal("50.00"))));
		saveReturnsArgument();

		ReservationResponse response = reservationService.simulate(
				List.of(requestWithRoom(null, "6041336634"))).getFirst();

		assertThat(response.roomNumber()).isEqualTo("unique");
		assertThat(response.price()).isNull();
	}

	@Test
	void simulate_treatsBlankRoomNumberAsMissing() {
		when(propertyRepository.findByEmailLabel("Loft Nürnberg")).thenReturn(Optional.of(property));
		when(roomRepository.findByPropertyIdOrderByNumber(propertyId)).thenReturn(List.of());
		when(reservationRepository.findByBookingNumber(any())).thenReturn(Optional.empty());
		when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

		List<ReservationResponse> responses = reservationService.simulate(List.of(
				requestWithRoom("", "A"),
				requestWithRoom("   ", "B")));

		assertThat(responses).extracting(ReservationResponse::roomNumber).containsExactly("unique", "unique");
		verify(roomRepository, never()).findByPropertyIdAndNumber(any(), any());
	}

	private static SimulateReservationRequest withStatus(String bookingReference, String status) {
		return new SimulateReservationRequest("Loft Nürnberg", "101", bookingReference,
				ARRIVAL, "Montag", DEPARTURE, "Booking.com", status);
	}

	@Test
	void simulate_cancelled_onlyChangesStatusOfExistingReservation() {
		Reservation existing = Reservation.create(property, "101", new BigDecimal("45.00"), "6041336634",
				ARRIVAL, "Montag", DEPARTURE, "Booking.com", "CONFIRMED");
		when(reservationRepository.findByBookingNumber("6041336634")).thenReturn(Optional.of(existing));
		SimulateReservationRequest cancel = new SimulateReservationRequest("Autre logement", "999", "6041336634",
				ARRIVAL.plusDays(5), "Freitag", DEPARTURE.plusDays(5), "Airbnb", "cancelled");

		ReservationResponse response = reservationService.simulate(List.of(cancel)).getFirst();

		assertThat(response.status()).isEqualTo("CANCELLED");
		assertThat(response.cancelledAt()).isNotNull();
		assertThat(existing.getCheckIn()).isEqualTo(ARRIVAL);
		assertThat(existing.getCheckOut()).isEqualTo(DEPARTURE);
		assertThat(existing.getRoomNumber()).isEqualTo("101");
		assertThat(existing.getChannel()).isEqualTo("Booking.com");
		verifyNoInteractions(propertyRepository, roomRepository);
		verify(reservationRepository, never()).save(any());
	}

	@Test
	void simulate_cancelledUnknownBookingNumber_isIgnoredAndInsertsNothing() {
		when(reservationRepository.findByBookingNumber("6041336634")).thenReturn(Optional.empty());

		assertThat(reservationService.simulate(List.of(withStatus("6041336634", "CANCELLED")))).isEmpty();
		verify(reservationRepository, never()).save(any());
		verifyNoInteractions(propertyRepository, roomRepository);
	}

	@Test
	void simulate_cancelledTwice_keepsFirstCancellationDate() {
		Reservation existing = Reservation.create(property, "101", new BigDecimal("45.00"), "6041336634",
				ARRIVAL, "Montag", DEPARTURE, "Booking.com", "CONFIRMED");
		existing.cancel();
		Instant firstCancellation = existing.getCancelledAt();
		when(reservationRepository.findByBookingNumber("6041336634")).thenReturn(Optional.of(existing));

		reservationService.simulate(List.of(withStatus("6041336634", "CANCELLED")));

		assertThat(firstCancellation).isNotNull();
		assertThat(existing.getCancelledAt()).isEqualTo(firstCancellation);
	}

	@Test
	void simulate_reactivated_clearsCancelledAt() {
		Reservation existing = Reservation.create(property, "101", new BigDecimal("45.00"), "6041336634",
				ARRIVAL, "Montag", DEPARTURE, "Booking.com", "CONFIRMED");
		existing.cancel();
		propertyAndRoomFound();
		when(reservationRepository.findByBookingNumber("6041336634")).thenReturn(Optional.of(existing));

		ReservationResponse response = reservationService.simulate(List.of(withStatus("6041336634", "CONFIRMED"))).getFirst();

		assertThat(response.status()).isEqualTo("CONFIRMED");
		assertThat(response.cancelledAt()).isNull();
	}

	@Test
	void simulate_notCancelled_hasNoCancelledAt() {
		propertyAndRoomFound();
		when(reservationRepository.findByBookingNumber("6041336634")).thenReturn(Optional.empty());
		when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

		ReservationResponse response = reservationService.simulate(List.of(withStatus("6041336634", "CONFIRMED"))).getFirst();

		assertThat(response.cancelledAt()).isNull();
	}

	@Test
	void listCancelled_returnsReservationsWithCancelledStatus() {
		when(reservationRepository.findByStatusOrderByCancelledAtDesc("CANCELLED")).thenReturn(List.of(
				Reservation.create(property, "101", null, "A", ARRIVAL, "Montag", DEPARTURE, "Airbnb", "CANCELLED")));

		assertThat(reservationService.listCancelled(ADMIN))
				.extracting(ReservationResponse::bookingReference)
				.containsExactly("A");
	}

	@Test
	void simulate_throwsNotFoundWhenGivenRoomNumberUnknown() {
		when(propertyRepository.findByEmailLabel("Loft Nürnberg")).thenReturn(Optional.of(property));
		when(roomRepository.findByPropertyIdAndNumber(propertyId, "999")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> reservationService.simulate(List.of(requestWithRoom("999", "6041336634"))))
				.isInstanceOf(NotFoundException.class)
				.hasMessageContaining("Aucune chambre")
				.hasMessageContaining("999");
		verifyNoInteractions(reservationRepository);
	}

	@Test
	void listByProperty_returnsReservationsInPeriod() {
		LocalDate from = LocalDate.of(2026, 11, 1);
		LocalDate to = LocalDate.of(2026, 11, 30);
		when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));
		when(reservationRepository.findByPropertyInPeriod(propertyId, from, to)).thenReturn(List.of(
				Reservation.create(property, "101", null, "A", ARRIVAL, "Montag", DEPARTURE, "Airbnb", "confirmed"),
				Reservation.create(property, "101", null, "B", DEPARTURE, "Montag", DEPARTURE.plusDays(2), "Airbnb", "confirmed")));

		assertThat(reservationService.listByProperty(ADMIN, propertyId, from, to))
				.extracting(ReservationResponse::bookingReference).containsExactly("A", "B");
	}

	@Test
	void listByProperty_acceptsMissingBounds() {
		when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));
		when(reservationRepository.findByPropertyInPeriod(propertyId, null, null)).thenReturn(List.of());

		assertThat(reservationService.listByProperty(ADMIN, propertyId, null, null)).isEmpty();
	}

	@Test
	void listByProperty_throwsWhenFromAfterTo() {
		assertThatThrownBy(() -> reservationService.listByProperty(ADMIN, propertyId,
				LocalDate.of(2026, 12, 1), LocalDate.of(2026, 11, 1)))
				.isInstanceOf(IllegalArgumentException.class);
		verifyNoInteractions(propertyRepository, reservationRepository);
	}

	@Test
	void listByProperty_throwsNotFoundWhenPropertyMissing() {
		when(propertyRepository.findById(propertyId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> reservationService.listByProperty(ADMIN, propertyId, null, null))
				.isInstanceOf(NotFoundException.class)
				.hasMessageContaining(propertyId.toString());
		verifyNoInteractions(reservationRepository);
	}

	@Test
	void listAll_returnsEveryReservation() {
		when(reservationRepository.findAllByOrderByCheckInAsc()).thenReturn(List.of(
				Reservation.create(property, "101", null, "A", ARRIVAL, "Montag", DEPARTURE, "Airbnb", "confirmed"),
				Reservation.create(property, "101", null, "B", DEPARTURE, "Montag", DEPARTURE.plusDays(2), "Airbnb", "confirmed")));

		assertThat(reservationService.listAll())
				.extracting(ReservationResponse::bookingReference)
				.containsExactly("A", "B");
	}

	@Test
	void get_returnsReservation() {
		Reservation r = Reservation.create(property, "101", null, "A", ARRIVAL, "Montag", DEPARTURE, "Airbnb", "confirmed");
		ReflectionTestUtils.setField(r, "id", 5L);
		when(reservationRepository.findById(5L)).thenReturn(Optional.of(r));

		assertThat(reservationService.get(ADMIN, 5L).id()).isEqualTo(5L);
	}

	@Test
	void get_throwsNotFoundWhenReservationMissing() {
		when(reservationRepository.findById(5L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> reservationService.get(ADMIN, 5L))
				.isInstanceOf(NotFoundException.class)
				.hasMessageContaining("5");
	}

	@Test
	void listCancelled_asCustomer_returnsOnlyCancelledReservationsOfTheAccount() {
		when(reservationRepository.findByStatusAndAccount("CANCELLED", "sub-1")).thenReturn(List.of(
				Reservation.create(property, "101", null, "A", ARRIVAL, "Montag", DEPARTURE, "Airbnb", "CANCELLED")));

		assertThat(reservationService.listCancelled(OWNING_CUSTOMER))
				.extracting(ReservationResponse::bookingReference)
				.containsExactly("A");
		verify(reservationRepository, never()).findByStatusOrderByCancelledAtDesc(any());
	}

	@Test
	void listMine_returnsReservationsOfTheCallerCustomer() {
		when(reservationRepository.findByCustomer("sub-1")).thenReturn(List.of(
				Reservation.create(property, "101", null, "A", ARRIVAL, "Montag", DEPARTURE, "Airbnb", "CONFIRMED")));

		assertThat(reservationService.listMine(OWNING_CUSTOMER))
				.extracting(ReservationResponse::bookingReference)
				.containsExactly("A");
		verify(reservationRepository, never()).findAllByOrderByCheckInAsc();
	}

	@Test
	void listByProperty_asOwningCustomer_returnsReservations() {
		when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));
		when(reservationRepository.findByPropertyInPeriod(propertyId, null, null)).thenReturn(List.of());

		assertThat(reservationService.listByProperty(OWNING_CUSTOMER, propertyId, null, null)).isEmpty();
	}

	@Test
	void listByProperty_asOtherCustomer_throwsNotFound() {
		when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));

		assertThatThrownBy(() -> reservationService.listByProperty(OTHER_CUSTOMER, propertyId, null, null))
				.isInstanceOf(NotFoundException.class)
				.hasMessageContaining(propertyId.toString());
		verifyNoInteractions(reservationRepository);
	}

	@Test
	void get_asOwningCustomer_returnsReservation() {
		Reservation r = Reservation.create(property, "101", null, "A", ARRIVAL, "Montag", DEPARTURE, "Airbnb", "confirmed");
		ReflectionTestUtils.setField(r, "id", 5L);
		when(reservationRepository.findById(5L)).thenReturn(Optional.of(r));

		assertThat(reservationService.get(OWNING_CUSTOMER, 5L).id()).isEqualTo(5L);
	}

	@Test
	void get_asOtherCustomer_throwsNotFound() {
		Reservation r = Reservation.create(property, "101", null, "A", ARRIVAL, "Montag", DEPARTURE, "Airbnb", "confirmed");
		when(reservationRepository.findById(5L)).thenReturn(Optional.of(r));

		assertThatThrownBy(() -> reservationService.get(OTHER_CUSTOMER, 5L))
				.isInstanceOf(NotFoundException.class)
				.hasMessageContaining("5");
	}
}
