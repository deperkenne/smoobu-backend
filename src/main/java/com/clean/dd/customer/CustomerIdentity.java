package com.clean.dd.customer;

/**
 * Identité du client telle que lue dans le JWT Keycloak.
 *
 * @param id          claim "sub" (obligatoire, stable)
 * @param email       claim "email" (optionnel)
 * @param username    claim "preferred_username" (optionnel)
 * @param companyName claim personnalisé "company_name" (optionnel : un particulier n'en a pas)
 * @param street      claim OIDC "address.street_address" (optionnel)
 * @param postalCode  claim OIDC "address.postal_code" (optionnel)
 * @param city        claim OIDC "address.locality" (optionnel)
 * @param country     claim OIDC "address.country" (optionnel)
 * @param vatNumber   claim personnalisé "vat_number" (optionnel)
 */
public record CustomerIdentity(
		String id,
		String email,
		String username,
		String companyName,
		String street,
		String postalCode,
		String city,
		String country,
		String vatNumber
) {

	/** Identité sans informations de facturation. */
	public CustomerIdentity(String id, String email, String username) {
		this(id, email, username, null, null, null, null, null, null);
	}
}
