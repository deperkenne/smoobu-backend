package com.clean.dd.shared.security;

import com.clean.dd.customer.Customer;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Qui appelle l'API : l'id Keycloak du client ("sub") et s'il est ADMIN (D&D Clean).
 * Un ADMIN voit tout ; un CUSTOMER ne voit que les données de ses propres propriétaires.
 */
public record Caller(String customerId, boolean admin) {

	static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

	public static Caller of(JwtAuthenticationToken authentication) {
		boolean admin = authentication.getAuthorities().stream()
				.anyMatch(a -> ADMIN_AUTHORITY.equals(a.getAuthority()));
		return new Caller(AuthenticatedCustomer.idOf(authentication.getToken()), admin);
	}

	/** Vrai si l'appelant peut voir les données rattachées à ce client (null = créé avant la sécurité). */
	public boolean canAccess(Customer customer) {
		return admin || (customer != null && customerId.equals(customer.getId()));
	}
}
