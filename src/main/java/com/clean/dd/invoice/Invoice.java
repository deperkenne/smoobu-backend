package com.clean.dd.invoice;

import com.clean.dd.owner.Owner;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Objects;

/** Facture mensuelle d'un propriétaire. Figée une fois générée : une seule par propriétaire et par mois. */
@Entity
@Table(name = "invoices",
		uniqueConstraints = @UniqueConstraint(name = "uk_invoice_owner_month", columnNames = {"owner_id", "billing_month"}))
public class Invoice {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false, updatable = false)
	private Owner owner;

	/** Ex. "F-2026-11-<id du owner>". */
	@Column(name = "invoice_number", nullable = false, unique = true, length = 50, updatable = false)
	private String invoiceNumber;

	/** Mois facturé, au format "2026-11". */
	@Column(name = "billing_month", nullable = false, length = 7, updatable = false)
	private String billingMonth;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal total;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Invoice() {
		// requis par JPA
	}

	public static Invoice create(Owner owner, YearMonth month, BigDecimal total) {
		Invoice invoice = new Invoice();
		invoice.owner = Objects.requireNonNull(owner, "owner est obligatoire");
		invoice.billingMonth = Objects.requireNonNull(month, "month est obligatoire").toString();
		invoice.invoiceNumber = "F-" + invoice.billingMonth + "-" + owner.getId();
		invoice.total = Objects.requireNonNull(total, "total est obligatoire");
		return invoice;
	}

	/** Nouveau total après recalcul des lignes (même numéro, même mois). */
	public void recalculate(BigDecimal total) {
		this.total = Objects.requireNonNull(total, "total est obligatoire");
	}

	public Long getId() { return id; }
	public Owner getOwner() { return owner; }
	public String getInvoiceNumber() { return invoiceNumber; }
	public String getBillingMonth() { return billingMonth; }
	public BigDecimal getTotal() { return total; }
	public Instant getCreatedAt() { return createdAt; }

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Invoice other)) return false;
		return id != null && Objects.equals(id, other.id);
	}

	@Override
	public int hashCode() {
		return getClass().hashCode();
	}
}
