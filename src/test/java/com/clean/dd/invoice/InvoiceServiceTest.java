package com.clean.dd.invoice;

import com.clean.dd.common.ConflictException;
import com.clean.dd.common.NotFoundException;
import com.clean.dd.customer.Customer;
import com.clean.dd.invoice.dto.InvoiceLineResponse;
import com.clean.dd.invoice.dto.InvoiceResponse;
import com.clean.dd.invoice.dto.InvoiceSummaryResponse;
import com.clean.dd.owner.Owner;
import com.clean.dd.owner.OwnerRepository;
import com.clean.dd.owner.Property;
import com.clean.dd.reservation.Reservation;
import com.clean.dd.shared.security.Caller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

	private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);
	/** 2026-11-08 est un dimanche, 2026-11-10 un mardi. */
	private static final LocalDate SUNDAY = LocalDate.of(2026, 11, 8);
	private static final LocalDate TUESDAY = LocalDate.of(2026, 11, 10);

	private static final Caller ADMIN = new Caller("admin-sub", true);
	private static final Caller OWNING_CUSTOMER = new Caller("sub-1", false);
	private static final Caller OTHER_CUSTOMER = new Caller("sub-2", false);

	@Mock
	private OwnerRepository ownerRepository;
	@Mock
	private InvoiceRepository invoiceRepository;
	@Mock
	private InvoiceLineRepository invoiceLineRepository;

	/** « Aujourd'hui » = 15 novembre 2026 à Berlin : novembre est en cours, octobre est terminé. */
	private static final Clock CLOCK = Clock.fixed(
			LocalDate.of(2026, 11, 15).atStartOfDay(ZoneId.of("Europe/Berlin")).toInstant(), ZoneId.of("Europe/Berlin"));

	private InvoiceService invoiceService;

	private UUID ownerId;
	private Owner owner;
	private Property property;

	@BeforeEach
	void setUp() {
		invoiceService = new InvoiceService(ownerRepository, invoiceRepository, invoiceLineRepository, CLOCK);
		ownerId = UUID.randomUUID();
		owner = Owner.create(Customer.register("sub-1", "c1@example.com", "c1"), "Max Muster", null, null);
		ReflectionTestUtils.setField(owner, "id", ownerId);
		property = Property.create(owner, "Loft", "Loft Nürnberg", null, null, null);
		ReflectionTestUtils.setField(property, "id", UUID.randomUUID());
	}

	private Reservation reservation(long id, String bookingNumber, BigDecimal price, LocalDate checkIn) {
		Reservation r = Reservation.create(property, "101", price, bookingNumber,
				checkIn, null, checkIn.plusDays(2), "Booking.com", "CONFIRMED");
		ReflectionTestUtils.setField(r, "id", id);
		return r;
	}

	private void ownerFoundAndNotYetInvoiced() {
		when(ownerRepository.findById(ownerId)).thenReturn(Optional.of(owner));
		when(invoiceRepository.findByOwnerIdAndBillingMonth(ownerId, "2026-11")).thenReturn(Optional.empty());
	}

	private void reservationsOfNovember(List<Reservation> reservations) {
		when(invoiceLineRepository.findUninvoicedReservations(ownerId, "CONFIRMED",
				LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))).thenReturn(reservations);
	}

	@Test
	void sundaySurcharge_isTenOnSundayAndZeroOtherwise() {
		assertThat(InvoiceService.sundaySurcharge(SUNDAY)).isEqualByComparingTo("10.00");
		assertThat(InvoiceService.sundaySurcharge(TUESDAY)).isEqualByComparingTo("0");
		assertThat(InvoiceService.sundaySurcharge(LocalDate.of(2026, 11, 7))).isEqualByComparingTo("0");
	}

	@Test
	void generate_computesLinesWithSundaySurchargeAndTotal() {
		ownerFoundAndNotYetInvoiced();
		reservationsOfNovember(List.of(
				reservation(1L, "BK-SUN", new BigDecimal("45.00"), SUNDAY),
				reservation(2L, "BK-TUE", new BigDecimal("55.50"), TUESDAY)));
		when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
		when(invoiceLineRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

		InvoiceResponse response = invoiceService.generate(ADMIN, ownerId, NOVEMBER);

		InvoiceSummaryResponse header = response.invoice();
		assertThat(header.invoiceNumber()).isEqualTo("F-2026-11-" + ownerId);
		assertThat(header.billingMonth()).isEqualTo("2026-11");
		assertThat(header.total()).isEqualByComparingTo("110.50");
		assertThat(header.ownerId()).isEqualTo(ownerId);
		assertThat(header.ownerCustomerId()).isEqualTo("sub-1");

		assertThat(response.lines()).hasSize(2);
		InvoiceLineResponse sunday = response.lines().get(0);
		assertThat(sunday.reservationId()).isEqualTo(1L);
		assertThat(sunday.bookingReference()).isEqualTo("BK-SUN");
		assertThat(sunday.propertyName()).isEqualTo("Loft");
		assertThat(sunday.roomNumber()).isEqualTo("101");
		assertThat(sunday.arrivalDate()).isEqualTo(SUNDAY);
		assertThat(sunday.departureDate()).isEqualTo(SUNDAY.plusDays(2));
		assertThat(sunday.basePrice()).isEqualByComparingTo("45.00");
		assertThat(sunday.sundaySurcharge()).isEqualByComparingTo("10.00");
		assertThat(sunday.lineTotal()).isEqualByComparingTo("55.00");
		InvoiceLineResponse tuesday = response.lines().get(1);
		assertThat(tuesday.bookingReference()).isEqualTo("BK-TUE");
		assertThat(tuesday.sundaySurcharge()).isEqualByComparingTo("0");
		assertThat(tuesday.lineTotal()).isEqualByComparingTo("55.50");
	}

	@Test
	void generate_whenAlreadyInvoiced_recalculatesSameInvoiceWithCurrentReservations() {
		Invoice existing = Invoice.create(owner, NOVEMBER, new BigDecimal("55.00"));
		ReflectionTestUtils.setField(existing, "id", 7L);
		when(ownerRepository.findById(ownerId)).thenReturn(Optional.of(owner));
		when(invoiceRepository.findByOwnerIdAndBillingMonth(ownerId, "2026-11")).thenReturn(Optional.of(existing));
		// après suppression des anciennes lignes : la réservation déjà facturée + une nouvelle
		reservationsOfNovember(List.of(
				reservation(1L, "BK-SUN", new BigDecimal("45.00"), SUNDAY),
				reservation(2L, "BK-NEW", new BigDecimal("55.50"), TUESDAY)));
		when(invoiceLineRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

		InvoiceResponse response = invoiceService.generate(ADMIN, ownerId, NOVEMBER);

		assertThat(response.invoice().id()).isEqualTo(7L);
		assertThat(response.invoice().invoiceNumber()).isEqualTo("F-2026-11-" + ownerId);
		assertThat(response.invoice().total()).isEqualByComparingTo("110.50");
		assertThat(existing.getTotal()).isEqualByComparingTo("110.50");
		assertThat(response.lines()).extracting(InvoiceLineResponse::bookingReference)
				.containsExactly("BK-SUN", "BK-NEW");
		InOrder inOrder = inOrder(invoiceLineRepository);
		inOrder.verify(invoiceLineRepository).deleteByInvoiceId(7L);
		inOrder.verify(invoiceLineRepository).findUninvoicedReservations(any(), any(), any(), any());
		verify(invoiceRepository, never()).save(any());
	}

	@Test
	void generate_whenMonthIsOver_returnsFrozenInvoiceWithoutRecalculating() {
		YearMonth october = YearMonth.of(2026, 10);
		Invoice frozen = Invoice.create(owner, october, new BigDecimal("55.00"));
		ReflectionTestUtils.setField(frozen, "id", 6L);
		InvoiceLine line = InvoiceLine.create(frozen,
				reservation(1L, "BK-OCT", new BigDecimal("45.00"), LocalDate.of(2026, 10, 4)), new BigDecimal("10.00"));
		when(ownerRepository.findById(ownerId)).thenReturn(Optional.of(owner));
		when(invoiceRepository.findByOwnerIdAndBillingMonth(ownerId, "2026-10")).thenReturn(Optional.of(frozen));
		when(invoiceLineRepository.findByInvoiceIdOrderByCheckIn(6L)).thenReturn(List.of(line));

		InvoiceResponse response = invoiceService.generate(ADMIN, ownerId, october);

		assertThat(response.invoice().id()).isEqualTo(6L);
		assertThat(response.invoice().total()).isEqualByComparingTo("55.00");
		assertThat(response.lines()).extracting(InvoiceLineResponse::bookingReference).containsExactly("BK-OCT");
		verify(invoiceLineRepository, never()).deleteByInvoiceId(any());
		verify(invoiceLineRepository, never()).findUninvoicedReservations(any(), any(), any(), any());
		verify(invoiceLineRepository, never()).saveAll(anyList());
	}

	@Test
	void generate_withoutMonth_usesCurrentMonthOfTheClock() {
		ownerFoundAndNotYetInvoiced();
		reservationsOfNovember(List.of(reservation(1L, "BK-TUE", new BigDecimal("55.50"), TUESDAY)));
		when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
		when(invoiceLineRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

		assertThat(invoiceService.generate(ADMIN, ownerId, null).invoice().billingMonth()).isEqualTo("2026-11");
	}

	@Test
	void generate_whenAlreadyInvoicedAndAllReservationsCancelled_keepsInvoiceWithZeroTotal() {
		Invoice existing = Invoice.create(owner, NOVEMBER, new BigDecimal("55.00"));
		ReflectionTestUtils.setField(existing, "id", 7L);
		when(ownerRepository.findById(ownerId)).thenReturn(Optional.of(owner));
		when(invoiceRepository.findByOwnerIdAndBillingMonth(ownerId, "2026-11")).thenReturn(Optional.of(existing));
		reservationsOfNovember(List.of());
		when(invoiceLineRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

		InvoiceResponse response = invoiceService.generate(ADMIN, ownerId, NOVEMBER);

		assertThat(response.invoice().id()).isEqualTo(7L);
		assertThat(response.invoice().total()).isEqualByComparingTo("0");
		assertThat(response.lines()).isEmpty();
		verify(invoiceLineRepository).deleteByInvoiceId(7L);
	}

	@Test
	void generate_withUnknownOwner_throwsNotFound() {
		when(ownerRepository.findById(ownerId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> invoiceService.generate(ADMIN, ownerId, NOVEMBER))
				.isInstanceOf(NotFoundException.class)
				.hasMessageContaining(ownerId.toString());
		verifyNoInteractions(invoiceRepository, invoiceLineRepository);
	}

	@Test
	void generate_withoutConfirmedReservation_throwsNotFound() {
		ownerFoundAndNotYetInvoiced();
		reservationsOfNovember(List.of());

		assertThatThrownBy(() -> invoiceService.generate(ADMIN, ownerId, NOVEMBER))
				.isInstanceOf(NotFoundException.class)
				.hasMessageContaining("2026-11");
		verify(invoiceRepository, never()).save(any());
		verify(invoiceLineRepository, never()).saveAll(anyList());
	}

	@Test
	void generate_withReservationWithoutPrice_throwsConflictListingBookingNumberAndSavesNothing() {
		ownerFoundAndNotYetInvoiced();
		reservationsOfNovember(List.of(
				reservation(1L, "BK-OK", new BigDecimal("45.00"), TUESDAY),
				reservation(2L, "BK-NOPRICE", null, SUNDAY)));

		assertThatThrownBy(() -> invoiceService.generate(ADMIN, ownerId, NOVEMBER))
				.isInstanceOf(ConflictException.class)
				.hasMessageContaining("BK-NOPRICE")
				.hasMessageNotContaining("BK-OK");
		verify(invoiceRepository, never()).save(any());
		verify(invoiceLineRepository, never()).saveAll(anyList());
	}

	@Test
	void listByOwner_returnsSummaries() {
		Invoice invoice = Invoice.create(owner, NOVEMBER, new BigDecimal("100.00"));
		ReflectionTestUtils.setField(invoice, "id", 7L);
		when(ownerRepository.findById(ownerId)).thenReturn(Optional.of(owner));
		when(invoiceRepository.findByOwnerIdOrderByBillingMonthDesc(ownerId)).thenReturn(List.of(invoice));

		List<InvoiceSummaryResponse> result = invoiceService.listByOwner(ADMIN, ownerId);

		assertThat(result).hasSize(1);
		assertThat(result.getFirst().id()).isEqualTo(7L);
		assertThat(result.getFirst().invoiceNumber()).isEqualTo("F-2026-11-" + ownerId);
		assertThat(result.getFirst().total()).isEqualByComparingTo("100.00");
	}

	@Test
	void listByOwner_withUnknownOwner_throwsNotFound() {
		when(ownerRepository.findById(ownerId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> invoiceService.listByOwner(ADMIN, ownerId)).isInstanceOf(NotFoundException.class);
		verifyNoInteractions(invoiceRepository);
	}

	@Test
	void get_returnsHeaderAndLines() {
		Invoice invoice = Invoice.create(owner, NOVEMBER, new BigDecimal("55.00"));
		ReflectionTestUtils.setField(invoice, "id", 7L);
		InvoiceLine line = InvoiceLine.create(invoice,
				reservation(1L, "BK-SUN", new BigDecimal("45.00"), SUNDAY), new BigDecimal("10.00"));
		when(invoiceRepository.findWithOwnerById(7L)).thenReturn(Optional.of(invoice));
		when(invoiceLineRepository.findByInvoiceIdOrderByCheckIn(7L)).thenReturn(List.of(line));

		InvoiceResponse response = invoiceService.get(ADMIN, 7L);

		assertThat(response.invoice().id()).isEqualTo(7L);
		assertThat(response.lines()).hasSize(1);
		assertThat(response.lines().getFirst().bookingReference()).isEqualTo("BK-SUN");
		assertThat(response.lines().getFirst().lineTotal()).isEqualByComparingTo("55.00");
	}

	@Test
	void get_withUnknownId_throwsNotFound() {
		when(invoiceRepository.findWithOwnerById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> invoiceService.get(ADMIN, 99L))
				.isInstanceOf(NotFoundException.class)
				.hasMessageContaining("99");
		verifyNoInteractions(invoiceLineRepository);
	}

	@Test
	void listByOwner_asOwningCustomer_returnsSummaries() {
		when(ownerRepository.findById(ownerId)).thenReturn(Optional.of(owner));
		when(invoiceRepository.findByOwnerIdOrderByBillingMonthDesc(ownerId)).thenReturn(List.of());

		assertThat(invoiceService.listByOwner(OWNING_CUSTOMER, ownerId)).isEmpty();
	}

	@Test
	void listByOwner_asOtherCustomer_throwsNotFound() {
		when(ownerRepository.findById(ownerId)).thenReturn(Optional.of(owner));

		assertThatThrownBy(() -> invoiceService.listByOwner(OTHER_CUSTOMER, ownerId))
				.isInstanceOf(NotFoundException.class)
				.hasMessageContaining(ownerId.toString());
		verifyNoInteractions(invoiceRepository);
	}

	@Test
	void get_asOwningCustomer_returnsInvoice() {
		Invoice invoice = Invoice.create(owner, NOVEMBER, new BigDecimal("55.00"));
		ReflectionTestUtils.setField(invoice, "id", 7L);
		when(invoiceRepository.findWithOwnerById(7L)).thenReturn(Optional.of(invoice));
		when(invoiceLineRepository.findByInvoiceIdOrderByCheckIn(7L)).thenReturn(List.of());

		assertThat(invoiceService.get(OWNING_CUSTOMER, 7L).invoice().id()).isEqualTo(7L);
	}

	@Test
	void get_asOtherCustomer_throwsNotFound() {
		Invoice invoice = Invoice.create(owner, NOVEMBER, new BigDecimal("55.00"));
		when(invoiceRepository.findWithOwnerById(7L)).thenReturn(Optional.of(invoice));

		assertThatThrownBy(() -> invoiceService.get(OTHER_CUSTOMER, 7L))
				.isInstanceOf(NotFoundException.class)
				.hasMessageContaining("7");
		verifyNoInteractions(invoiceLineRepository);
	}
}
