package com.clean.dd.customer;

import jakarta.persistence.*;

import java.util.Objects;

/**
 * Client local (compte Keycloak), créé automatiquement (Just-In-Time) à partir du JWT
 * la première fois qu'il crée un propriétaire. Le lien est porté par Owner (pas de @OneToMany ici).
 */
@Entity
@Table(name = "customers")
public class Customer {

	/** Claim "sub" de Keycloak (ex. "f47ac10b-58cc..."). */
	@Id
	@Column(length = 64)
	private String id;

	private String email;

	private String username;

	// Informations de facturation, copiées des claims Keycloak (toutes optionnelles).
	@Column(name = "company_name")
	private String companyName;

	private String street;

	@Column(name = "postal_code")
	private String postalCode;

	private String city;

	private String country;

	@Column(name = "vat_number")
	private String vatNumber;

	protected Customer() {
		// requis par JPA
	}

	public static Customer register(String id, String email, String username) {
		if (id == null || id.isBlank()) {
			throw new IllegalArgumentException("customer id must not be blank");
		}
		Customer customer = new Customer();
		customer.id = id;
		customer.email = email;
		customer.username = username;
		return customer;
	}

	/**
	 * Aligne le profil sur le token Keycloak (source de vérité). L'id ne change jamais.
	 * Hibernate n'écrit en base que si une valeur a réellement changé.
	 */
	public void updateProfile(CustomerIdentity identity) {
		if (!id.equals(identity.id())) {
			throw new IllegalArgumentException("Identity " + identity.id() + " does not belong to customer " + id);
		}
		this.email = identity.email();
		this.username = identity.username();
		this.companyName = identity.companyName();
		this.street = identity.street();
		this.postalCode = identity.postalCode();
		this.city = identity.city();
		this.country = identity.country();
		this.vatNumber = identity.vatNumber();
	}

	public String getId() { return id; }
	public String getEmail() { return email; }
	public String getUsername() { return username; }
	public String getCompanyName() { return companyName; }
	public String getStreet() { return street; }
	public String getPostalCode() { return postalCode; }
	public String getCity() { return city; }
	public String getCountry() { return country; }
	public String getVatNumber() { return vatNumber; }

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Customer other)) return false;
		return id != null && Objects.equals(id, other.id);
	}

	@Override
	public int hashCode() {
		return getClass().hashCode();
	}
}
