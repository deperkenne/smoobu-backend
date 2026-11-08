package com.clean.dd.customer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

	private static final CustomerIdentity IDENTITY = new CustomerIdentity("sub-1", "new@example.com", "max",
			"Muster GmbH", "Hauptstr. 1", "90402", "Nürnberg", "DE", "DE123456789");

	@Mock
	private CustomerRepository customerRepository;

	@InjectMocks
	private CustomerService customerService;

	private static void assertProfileIs(Customer customer, CustomerIdentity identity) {
		assertThat(customer.getId()).isEqualTo(identity.id());
		assertThat(customer.getEmail()).isEqualTo(identity.email());
		assertThat(customer.getUsername()).isEqualTo(identity.username());
		assertThat(customer.getCompanyName()).isEqualTo(identity.companyName());
		assertThat(customer.getStreet()).isEqualTo(identity.street());
		assertThat(customer.getPostalCode()).isEqualTo(identity.postalCode());
		assertThat(customer.getCity()).isEqualTo(identity.city());
		assertThat(customer.getCountry()).isEqualTo(identity.country());
		assertThat(customer.getVatNumber()).isEqualTo(identity.vatNumber());
	}

	@Test
	void getOrCreate_existingCustomer_isUpdatedFromToken() {
		Customer existing = Customer.register("sub-1", "old@example.com", "old");
		when(customerRepository.findById("sub-1")).thenReturn(Optional.of(existing));

		Customer result = customerService.getOrCreate(IDENTITY);

		assertThat(result).isSameAs(existing);
		assertProfileIs(result, IDENTITY);
		verify(customerRepository, never()).insertIfAbsent(any(), any(), any());
		verify(customerRepository, never()).save(any());
	}

	@Test
	void getOrCreate_missingCustomer_isInsertedThenReadAndUpdated() {
		Customer inserted = Customer.register("sub-1", "new@example.com", "max");
		when(customerRepository.findById("sub-1")).thenReturn(Optional.empty(), Optional.of(inserted));

		Customer result = customerService.getOrCreate(IDENTITY);

		assertThat(result).isSameAs(inserted);
		assertProfileIs(result, IDENTITY);
		InOrder order = inOrder(customerRepository);
		order.verify(customerRepository).findById("sub-1");
		order.verify(customerRepository).insertIfAbsent("sub-1", "new@example.com", "max");
		order.verify(customerRepository).findById("sub-1");
		verify(customerRepository, never()).save(any());
	}

	@Test
	void getOrCreate_missingClaims_clearProfileFields() {
		Customer existing = Customer.register("sub-1", "old@example.com", "old");
		existing.updateProfile(IDENTITY);
		when(customerRepository.findById("sub-1")).thenReturn(Optional.of(existing));
		CustomerIdentity bare = new CustomerIdentity("sub-1", null, null);

		assertProfileIs(customerService.getOrCreate(bare), bare);
	}
}
