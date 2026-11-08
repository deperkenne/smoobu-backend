package com.clean.dd.shared.security;

import com.clean.dd.customer.CustomerIdentity;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

/**
 * Extrait l'identifiant du client connecté depuis le JWT Keycloak.
 * On utilise le claim "sub" : stable et unique, contrairement à preferred_username ou email.
 */
public final class AuthenticatedCustomer {

	static final String ADDRESS_CLAIM = "address";
	static final String COMPANY_NAME_CLAIM = "company_name";
	static final String VAT_NUMBER_CLAIM = "vat_number";

	private AuthenticatedCustomer() {
	}

	public static String idOf(Jwt jwt) {
		if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A valid bearer token is required");
		}
		return jwt.getSubject();
	}

	/**
	 * Identité complète pour créer / mettre à jour le client :
	 * sub, email, preferred_username, claim OIDC standard "address" (objet JSON),
	 * et les claims personnalisés "company_name" et "vat_number" (mappers à créer dans Keycloak).
	 * Tout claim absent donne null.
	 */
	public static CustomerIdentity identityOf(Jwt jwt) {
		Map<String, Object> address = addressOf(jwt);
		return new CustomerIdentity(
				idOf(jwt),
				jwt.getClaimAsString("email"),
				jwt.getClaimAsString("preferred_username"),
				jwt.getClaimAsString(COMPANY_NAME_CLAIM),
				stringOf(address, "street_address"),
				stringOf(address, "postal_code"),
				stringOf(address, "locality"),
				stringOf(address, "country"),
				jwt.getClaimAsString(VAT_NUMBER_CLAIM));
	}

	private static Map<String, Object> addressOf(Jwt jwt) {
		Object address = jwt.getClaims().get(ADDRESS_CLAIM);
		if (address instanceof Map<?, ?> map) {
			Map<String, Object> result = new HashMap<>();
			map.forEach((key, value) -> result.put(String.valueOf(key), value));
			return result;
		}
		return Map.of();
	}

	private static String stringOf(Map<String, Object> claims, String key) {
		Object value = claims.get(key);
		return value == null ? null : value.toString();
	}
}
