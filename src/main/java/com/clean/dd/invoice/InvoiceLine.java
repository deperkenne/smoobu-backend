package com.clean.dd.invoice;

import com.clean.dd.reservation.Reservation;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Ligne de facture : une réservation facturée. Les valeurs de la réservation sont copiées
 * pour que la facture ne change pas si la réservation change ensuite.
 */
@Entity
@Table(name = "invoice_lines",
		indexes = @Index(name = "idx_invoice_line_invoice", columnList = "invoice_id"))
public class InvoiceLine {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "invoice_id", nullable = false, updatable = false)
	private Invoice invoice;

	/** Unique : une réservation n'est facturée qu'une seule fois. */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "reservation_id", nullable = false, updatable = false, unique = true)
	private Reservation reservation;

	@Column(name = "booking_number", nullable = false, updatable = false)
	private String bookingNumber;

	@Column(name = "property_name", nullable = false, updatable = false)
	private String propertyName;

	@Column(name = "room_number", length = 20, updatable = false)
	private String roomNumber;

	@Column(name = "check_in", nullable = false, updatable = false)
	private LocalDate checkIn;

	@Column(name = "check_out", nullable = false, updatable = false)
	private LocalDate checkOut;

	@Column(name = "base_price", nullable = false, precision = 10, scale = 2, updatable = false)
	private BigDecimal basePrice;

	@Column(name = "sunday_surcharge", nullable = false, precision = 10, scale = 2, updatable = false)
	private BigDecimal sundaySurcharge;

	@Column(name = "line_total", nullable = false, precision = 10, scale = 2, updatable = false)
	private BigDecimal lineTotal;

	protected InvoiceLine() {
		// requis par JPA
	}

	public static InvoiceLine create(Invoice invoice, Reservation reservation, BigDecimal sundaySurcharge) {
		InvoiceLine line = new InvoiceLine();
		line.invoice = Objects.requireNonNull(invoice, "invoice est obligatoire");
		line.reservation = Objects.requireNonNull(reservation, "reservation est obligatoire");
		line.basePrice = Objects.requireNonNull(reservation.getPrice(), "la réservation n'a pas de prix");
		line.sundaySurcharge = Objects.requireNonNull(sundaySurcharge, "sundaySurcharge est obligatoire");
		line.lineTotal = line.basePrice.add(line.sundaySurcharge);
		line.bookingNumber = reservation.getBookingNumber();
		line.propertyName = reservation.getPropertyName();
		line.roomNumber = reservation.getRoomNumber();
		line.checkIn = reservation.getCheckIn();
		line.checkOut = reservation.getCheckOut();
		return line;
	}

	public Long getId() { return id; }
	public Invoice getInvoice() { return invoice; }
	public Reservation getReservation() { return reservation; }
	public String getBookingNumber() { return bookingNumber; }
	public String getPropertyName() { return propertyName; }
	public String getRoomNumber() { return roomNumber; }
	public LocalDate getCheckIn() { return checkIn; }
	public LocalDate getCheckOut() { return checkOut; }
	public BigDecimal getBasePrice() { return basePrice; }
	public BigDecimal getSundaySurcharge() { return sundaySurcharge; }
	public BigDecimal getLineTotal() { return lineTotal; }

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof InvoiceLine other)) return false;
		return id != null && Objects.equals(id, other.id);
	}

	@Override
	public int hashCode() {
		return getClass().hashCode();
	}
}
