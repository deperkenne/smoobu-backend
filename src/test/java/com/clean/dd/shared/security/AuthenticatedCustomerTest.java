package com.clean.dd.shared.security;

import com.clean.dd.customer.CustomerIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticatedCustomerTest {

	private static Jwt.Builder token() {
		return Jwt.withTokenValue("token").header("alg", "none");
	}

	private static void assertUnauthorized(Throwable thrown) {
		assertThat(thrown).isInstanceOfSatisfying(ResponseStatusException.class,
				e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
	}

	@Test
	void idOf_returnsSubject() {
		assertThat(AuthenticatedCustomer.idOf(token().subject("sub-1").build())).isEqualTo("sub-1");
	}

	@Test
	void idOf_withoutSubject_throws401() {
		Jwt jwt = token().claim("email", "a@example.com").build();

		assertThatThrownBy(() -> AuthenticatedCustomer.idOf(jwt)).satisfies(AuthenticatedCustomerTest::assertUnauthorized);
	}

	@Test
	void idOf_withBlankSubjectOrNullToken_throws401() {
		Jwt blank = token().subject("  ").build();

		assertThatThrownBy(() -> AuthenticatedCustomer.idOf(blank)).satisfies(AuthenticatedCustomerTest::assertUnauthorized);
		assertThatThrownBy(() -> AuthenticatedCustomer.idOf(null)).satisfies(AuthenticatedCustomerTest::assertUnauthorized);
	}

	@Test
	void identityOf_readsSubjectContactBillingAndAddressClaims() {
		Jwt jwt = token()
				.subject("sub-1")
				.claim("email", "max@example.com")
				.claim("preferred_username", "max")
				.claim("company_name", "Muster GmbH")
				.claim("vat_number", "DE123456789")
				.claim("address", Map.of(
						"street_address", "Hauptstr. 1",
						"postal_code", "90402",
						"locality", "Nürnberg",
						"country", "DE"))
				.build();

		CustomerIdentity identity = AuthenticatedCustomer.identityOf(jwt);

		assertThat(identity).isEqualTo(new CustomerIdentity("sub-1", "max@example.com", "max", "Muster GmbH",
				"Hauptstr. 1", "90402", "Nürnberg", "DE", "DE123456789"));
	}

	@Test
	void identityOf_missingClaims_giveNull() {
		CustomerIdentity identity = AuthenticatedCustomer.identityOf(token().subject("sub-1").build());

		assertThat(identity).isEqualTo(new CustomerIdentity("sub-1", null, null));
	}

	@Test
	void identityOf_addressNotAnObject_givesNullAddressFields() {
		Jwt jwt = token().subject("sub-1").claim("address", "Hauptstr. 1, Nürnberg").build();

		CustomerIdentity identity = AuthenticatedCustomer.identityOf(jwt);

		assertThat(identity.street()).isNull();
		assertThat(identity.postalCode()).isNull();
		assertThat(identity.city()).isNull();
		assertThat(identity.country()).isNull();
	}

	@Test
	void identityOf_partialAddress_givesNullForMissingFields() {
		Jwt jwt = token().subject("sub-1").claim("address", Map.of("locality", "Fürth")).build();

		CustomerIdentity identity = AuthenticatedCustomer.identityOf(jwt);

		assertThat(identity.city()).isEqualTo("Fürth");
		assertThat(identity.street()).isNull();
		assertThat(identity.postalCode()).isNull();
		assertThat(identity.country()).isNull();
	}

	@Test
	void identityOf_withoutSubject_throws401() {
		Jwt jwt = token().claim("email", "a@example.com").build();

		assertThatThrownBy(() -> AuthenticatedCustomer.identityOf(jwt))
				.satisfies(AuthenticatedCustomerTest::assertUnauthorized);
	}
}
