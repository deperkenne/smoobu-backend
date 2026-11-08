package com.clean.dd.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakJwtAuthenticationConverterTest {

	private static final String CLIENT_ID = "ddclean-app";

	private final KeycloakJwtAuthenticationConverter converter = new KeycloakJwtAuthenticationConverter(CLIENT_ID);

	private static Jwt.Builder token() {
		return Jwt.withTokenValue("token").header("alg", "none").subject("sub-1");
	}

	private static List<String> authorities(AbstractAuthenticationToken authentication) {
		return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
	}

	@Test
	void convert_mapsRealmAndClientRolesToUppercaseRolePrefix() {
		Jwt jwt = token()
				.claim("realm_access", Map.of("roles", List.of("customer", "offline_access")))
				.claim("resource_access", Map.of(CLIENT_ID, Map.of("roles", List.of("Admin"))))
				.build();

		AbstractAuthenticationToken authentication = converter.convert(jwt);

		assertThat(authentication).isInstanceOf(JwtAuthenticationToken.class);
		assertThat(authorities(authentication))
				.containsExactlyInAnyOrder("ROLE_CUSTOMER", "ROLE_OFFLINE_ACCESS", "ROLE_ADMIN");
	}

	@Test
	void convert_ignoresRolesOfAnotherClient() {
		Jwt jwt = token()
				.claim("resource_access", Map.of(
						"other-client", Map.of("roles", List.of("admin")),
						CLIENT_ID, Map.of("roles", List.of("customer"))))
				.build();

		assertThat(authorities(converter.convert(jwt))).containsExactly("ROLE_CUSTOMER");
	}

	@Test
	void convert_withoutRoleClaims_hasNoAuthority() {
		assertThat(converter.convert(token().build()).getAuthorities()).isEmpty();
	}

	@Test
	void convert_deduplicatesRoleGivenInRealmAndClient() {
		Jwt jwt = token()
				.claim("realm_access", Map.of("roles", List.of("admin")))
				.claim("resource_access", Map.of(CLIENT_ID, Map.of("roles", List.of("ADMIN"))))
				.build();

		assertThat(authorities(converter.convert(jwt))).containsExactly("ROLE_ADMIN");
	}

	@Test
	void convert_withBlankClientId_ignoresClientRoles() {
		Jwt jwt = token()
				.claim("realm_access", Map.of("roles", List.of("customer")))
				.claim("resource_access", Map.of(" ", Map.of("roles", List.of("admin"))))
				.build();

		assertThat(authorities(new KeycloakJwtAuthenticationConverter(" ").convert(jwt)))
				.containsExactly("ROLE_CUSTOMER");
	}

	@Test
	void convert_principalNameIsPreferredUsernameOrSubject() {
		assertThat(converter.convert(token().claim("preferred_username", "max").build()).getName())
				.isEqualTo("max");
		assertThat(converter.convert(token().build()).getName()).isEqualTo("sub-1");
	}
}
