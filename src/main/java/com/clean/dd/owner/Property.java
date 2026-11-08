package com.clean.dd.owner;

import com.clean.dd.user.User;
import jakarta.persistence.*;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "properties")
public class Property {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false, updatable = false)
	private Owner owner;

	@Column(nullable = false, length = 200)
	private String name;

	/**
	 * Nom exact du logement tel qu'il apparaît dans les e-mails Smoobu.
	 * Unique dans toute la base : c'est la seule clé de l'e-mail (à remplacer par l'id Smoobu via l'API).
	 */
	@Column(name = "email_label", nullable = false, unique = true, length = 200)
	private String emailLabel;

	@Column(length = 200)
	private String street;

	@Column(name = "postal_code", length = 10)
	private String postalCode;

	@Column(length = 100)
	private String city;

	/** Agent attribué au logement (0 ou 1) ; un agent peut avoir plusieurs logements. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id")
	private User user;


	protected Property() {
		// requis par JPA
	}

	private Property(Owner owner, String name, String emailLabel) {
		this.owner = Objects.requireNonNull(owner, "owner est obligatoire");
		this.name = requireText(name, "name");
		this.emailLabel = requireText(emailLabel, "emailLabel");
	}

	public static Property create(Owner owner, String name, String emailLabel,
								  String street, String postalCode, String city) {
		Property p = new Property(owner, name, emailLabel);
		p.updateAddress(street, postalCode, city);
		return p;
	}

	public void rename(String name) {
		this.name = requireText(name, "name");
	}

	public void updateAddress(String street, String postalCode, String city) {
		this.street = street;
		this.postalCode = postalCode;
		this.city = city;
	}

	public void assignTo(User user) {
		this.user = Objects.requireNonNull(user, "user");
	}

	public void unassign() {
		this.user = null;
	}


	private static String requireText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " est obligatoire");
		}
		return value.strip();
	}

	public UUID getId() { return id; }
	public Owner getOwner() { return owner; }
	public String getName() { return name; }
	public String getEmailLabel() { return emailLabel; }
	public String getStreet() { return street; }
	public String getPostalCode() { return postalCode; }
	public String getCity() { return city; }
	public User getUser() { return user; }

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Property other)) return false;
		return id != null && Objects.equals(id, other.id);
	}

	@Override
	public int hashCode() {
		return getClass().hashCode();
	}

}
