package com.clean.dd.shared.security;

import com.clean.dd.customer.Customer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CallerTest {

	private static final Customer CUSTOMER = Customer.register("sub-1", "c1@example.com", "c1");
	private static final Customer OTHER = Customer.register("sub-2", "c2@example.com", "c2");

	private static JwtAuthenticationToken authentication(String sub, String... roles) {
		Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "none").claim("scope", "openid");
		if (sub != null) {
			builder.subject(sub);
		}
		return new JwtAuthenticationToken(builder.build(),
				Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList());
	}

	@Test
	void of_admin() {
		assertThat(Caller.of(authentication("admin-sub", "ROLE_ADMIN", "ROLE_CUSTOMER")))
				.isEqualTo(new Caller("admin-sub", true));
	}

	@Test
	void of_customer() {
		assertThat(Caller.of(authentication("sub-1", "ROLE_CUSTOMER"))).isEqualTo(new Caller("sub-1", false));
	}

	@Test
	void of_withoutSubject_throws401() {
		JwtAuthenticationToken withoutSubject = authentication(null, "ROLE_ADMIN");

		assertThatThrownBy(() -> Caller.of(withoutSubject))
				.isInstanceOfSatisfying(ResponseStatusException.class,
						e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
	}

	@Test
	void canAccess_adminSeesEverything() {
		Caller admin = new Caller("admin-sub", true);

		assertThat(admin.canAccess(CUSTOMER)).isTrue();
		assertThat(admin.canAccess(OTHER)).isTrue();
		assertThat(admin.canAccess(null)).isTrue();
	}

	@Test
	void canAccess_customerSeesOnlyItself() {
		Caller caller = new Caller("sub-1", false);

		assertThat(caller.canAccess(CUSTOMER)).isTrue();
		assertThat(caller.canAccess(Customer.register("sub-1", null, null))).isTrue();
		assertThat(caller.canAccess(OTHER)).isFalse();
	}

	@Test
	void canAccess_customerIsRefusedWhenNoCustomerIsAttached() {
		assertThat(new Caller("sub-1", false).canAccess(null)).isFalse();
	}
}
