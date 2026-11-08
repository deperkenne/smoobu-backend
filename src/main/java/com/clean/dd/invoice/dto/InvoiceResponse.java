package com.clean.dd.invoice.dto;

import java.util.List;

/** Facture complète : en-tête + lignes. */
public record InvoiceResponse(InvoiceSummaryResponse invoice, List<InvoiceLineResponse> lines) {
}
