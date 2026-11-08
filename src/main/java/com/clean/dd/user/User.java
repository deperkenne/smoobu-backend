package com.clean.dd.user;

import com.clean.dd.customer.Customer;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Membre du personnel (admin, secrétaire, agent). Le mot de passe vit uniquement dans Keycloak. */
@Entity
@Table(name = "users") // "user" est un mot réservé de PostgreSQL
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "customer_id")
	private Customer customer;

	/** Le "sub" du compte Keycloak de l'utilisateur. */
	@Column(name = "keycloak_id", nullable = false, unique = true, length = 64)
	private String keycloakId;

	/** Keycloak impose un e-mail unique par realm. */
	@Column(nullable = false, unique = true)
	private String email;

	@Column(name = "full_name", nullable = false)
	private String fullName;

	private String phone;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Role role;

	@Column(nullable = false)
	private boolean active = true;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected User() {
		// requis par JPA
	}

	public static User create(Customer customer, String keycloakId, String email, String fullName,
							  String phone, Role role) {
		User user = new User();
		user.customer = customer;
		user.keycloakId = Objects.requireNonNull(keycloakId, "keycloakId");
		user.email = Objects.requireNonNull(email, "email");
		user.fullName = Objects.requireNonNull(fullName, "fullName");
		user.phone = phone;
		user.role = Objects.requireNonNull(role, "role");
		return user;
	}

	public void deactivate() {
		this.active = false;
	}

	public UUID getId() { return id; }
	public Customer getCustomer() { return customer; }
	public String getKeycloakId() { return keycloakId; }
	public String getEmail() { return email; }
	public String getFullName() { return fullName; }
	public String getPhone() { return phone; }
	public Role getRole() { return role; }
	public boolean isActive() { return active; }
	public Instant getCreatedAt() { return createdAt; }

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof User other)) return false;
		return id != null && Objects.equals(id, other.id);
	}

	@Override
	public int hashCode() {
		return getClass().hashCode();
	}
}
