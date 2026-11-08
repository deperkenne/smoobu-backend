package com.clean.dd.owner;

import com.clean.dd.customer.Customer;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "owners")
public class Owner {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, length = 150)
	private String name;

	@Column(length = 255)
	private String email;

	@Column(length = 30)
	private String phone;

	/** Customer (compte Keycloak) qui a créé ce propriétaire, lu dans le token. Nullable en base pour les anciens propriétaires. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "customer_account_id", updatable = false)
	private Customer customer;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Owner() {
		// requis par JPA
	}

	private Owner(Customer customer, String name, String email, String phone) {
		this.customer = Objects.requireNonNull(customer, "customer est obligatoire");
		this.name = requireText(name, "name");
		this.email = email;
		this.phone = phone;
	}

	public static Owner create(Customer customer, String name, String email, String phone) {
		return new Owner(customer, name, email, phone);
	}

	public void updateContact(String name, String email, String phone) {
		this.name = requireText(name, "name");
		this.email = email;
		this.phone = phone;
	}


	private static String requireText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " est obligatoire");
		}
		return value.strip();
	}

	public UUID getId() { return id; }
	/** Id du customer (sub du token) ; ne charge pas le customer. */
	public String getCustomerId() { return customer == null ? null : customer.getId(); }
	public String getName() { return name; }
	public String getEmail() { return email; }
	public String getPhone() { return phone; }
	public Instant getCreatedAt() { return createdAt; }
	public Customer getCustomer() { return customer; }

	// equals/hashCode sur l'id uniquement (recommandation Hibernate pour les ids générés)
	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Owner other)) return false;
		return id != null && Objects.equals(id, other.id);
	}

	@Override
	public int hashCode() {
		return getClass().hashCode();
	}

}
