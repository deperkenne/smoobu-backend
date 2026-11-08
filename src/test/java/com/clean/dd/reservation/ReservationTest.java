package com.clean.dd.reservation;

import com.clean.dd.customer.Customer;
import com.clean.dd.owner.Owner;
import com.clean.dd.owner.Property;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationTest {

	private static final LocalDate CHECK_IN = LocalDate.of(2026, 11, 10);

	private static Reservation reservationOn(Owner owner) {
		Property property = Property.create(owner, "Loft", "Loft Nürnberg", null, null, null);
		return Reservation.create(property, "101", new BigDecimal("45.00"), "BK-1",
				CHECK_IN, null, CHECK_IN.plusDays(2), "Airbnb", "CONFIRMED");
	}

	@Test
	void create_copiesCustomerOfPropertyOwner() {
		Customer customer = Customer.register("sub-1", "c1@example.com", "c1");
		Owner owner = Owner.create(customer, "Max", null, null);

		Reservation reservation = reservationOn(owner);

		assertThat(reservation.getCustomerId()).isEqualTo("sub-1");
	}

	@Test
	void create_withOwnerWithoutCustomer_hasNullCustomerId() {
		Owner legacy = Owner.create(Customer.register("sub-1", "c1@example.com", "c1"), "Legacy", null, null);
		ReflectionTestUtils.setField(legacy, "customer", null);

		Reservation reservation = reservationOn(legacy);

		assertThat(reservation.getCustomerId()).isNull();
	}
}
