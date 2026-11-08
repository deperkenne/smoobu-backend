package com.clean.dd.invoice.dto;

import com.clean.dd.invoice.Invoice;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InvoiceSummaryResponse(
		Long id,
		String invoiceNumber,
		String billingMonth,
		BigDecimal total,
		Instant createdAt,
		UUID ownerId,
		String ownerCustomerId,
		String ownerName) {

	public static InvoiceSummaryResponse from(Invoice i) {
		return new InvoiceSummaryResponse(i.getId(), i.getInvoiceNumber(), i.getBillingMonth(), i.getTotal(),
				i.getCreatedAt(), i.getOwner().getId(), i.getOwner().getCustomerId(), i.getOwner().getName());
	}
}
