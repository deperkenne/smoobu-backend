package com.clean.dd;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/** Jetons JWT simulés pour MockMvc : aucun appel à Keycloak. */
public final class TestJwt {

	public static final String ADMIN_SUB = "admin-sub";
	/** Client par défaut qui crée les propriétaires dans les scénarios (POST /api/owners est réservé à CUSTOMER). */
	public static final String OWNER_SUB = "owner-sub";

	private TestJwt() {
	}

	public static RequestPostProcessor admin() {
		return token(ADMIN_SUB, "ROLE_ADMIN");
	}

	public static RequestPostProcessor customer(String sub) {
		return token(sub, "ROLE_CUSTOMER");
	}

	public static RequestPostProcessor agent(String sub) {
		return token(sub, "ROLE_AGENT");
	}

	private static RequestPostProcessor token(String sub, String role) {
		return jwt()
				.jwt(j -> j.subject(sub)
						.claim("email", sub + "@example.com")
						.claim("preferred_username", sub))
				.authorities(new SimpleGrantedAuthority(role));
	}
}
