package com.clean.dd.owner;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Chambre d'un logement : n'existe pas sans son logement (composition). */
@Entity
@Table(name = "rooms",
		uniqueConstraints = @UniqueConstraint(name = "uk_room_property_number", columnNames = {"property_id", "number"}))
public class Room {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "property_id", nullable = false, updatable = false)
	private Property property;

	/** Numéro ou code : « 101 », « 2B », « Mansarde ». */
	@Column(nullable = false, length = 20)
	private String number;

	/** Prix du ménage de la chambre, en euros. */
	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal price;

	protected Room() {
		// requis par JPA
	}

	public static Room create(Property property, String number, BigDecimal price) {
		Room room = new Room();
		room.property = Objects.requireNonNull(property, "property est obligatoire");
		if (number == null || number.isBlank()) {
			throw new IllegalArgumentException("number est obligatoire");
		}
		room.number = number.strip();
		room.changePrice(price);
		return room;
	}

	public void changePrice(BigDecimal price) {
		Objects.requireNonNull(price, "price est obligatoire");
		if (price.signum() < 0) {
			throw new IllegalArgumentException("price doit être positif ou nul");
		}
		this.price = price;
	}

	public UUID getId() { return id; }
	public Property getProperty() { return property; }
	public String getNumber() { return number; }
	public BigDecimal getPrice() { return price; }

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Room other)) return false;
		return id != null && Objects.equals(id, other.id);
	}

	@Override
	public int hashCode() {
		return getClass().hashCode();
	}
}
