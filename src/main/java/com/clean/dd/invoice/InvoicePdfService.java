package com.clean.dd.invoice;

import com.clean.dd.invoice.dto.InvoiceResponse;
import com.clean.dd.shared.security.Caller;
import org.springframework.stereotype.Service;

@Service
public class InvoicePdfService {

	private final InvoiceService invoiceService;
	private final InvoicePdfRenderer renderer;

	public InvoicePdfService(InvoiceService invoiceService, InvoicePdfRenderer renderer) {
		this.invoiceService = invoiceService;
		this.renderer = renderer;
	}

	/** PDF d'une facture déjà établie ; mêmes droits que la lecture (404 si elle n'est pas au client). */
	public PdfFile render(Caller caller, Long id) {
		InvoiceResponse invoice = invoiceService.get(caller, id);
		return new PdfFile(invoice.invoice().invoiceNumber() + ".pdf", renderer.render(invoice));
	}
}
