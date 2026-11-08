package com.clean.dd.invoice;

import com.clean.dd.invoice.dto.InvoiceResponse;
import com.clean.dd.invoice.dto.InvoiceSummaryResponse;
import com.clean.dd.shared.security.Caller;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/** CUSTOMER : factures de ses propres propriétaires ; ADMIN : toutes. */
@RestController
@RequestMapping("/api")
public class InvoiceController {

	private final InvoiceService invoiceService;
	private final InvoicePdfService invoicePdfService;

	public InvoiceController(InvoiceService invoiceService, InvoicePdfService invoicePdfService) {
		this.invoiceService = invoiceService;
		this.invoicePdfService = invoicePdfService;
	}

	/** Facture officielle en PDF, à télécharger. */
	@GetMapping("/invoices/{id}/pdf")
	@PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
	public ResponseEntity<byte[]> pdf(JwtAuthenticationToken authentication, @PathVariable Long id) {
		PdfFile pdf = invoicePdfService.render(Caller.of(authentication), id);
		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(pdf.fileName()).build().toString())
				.body(pdf.content());
	}

	/**
	 * Établit la facture du mois (ex. month=2026-11, mois en cours par défaut) d'un propriétaire du client,
	 * ou la renvoie si elle existe déjà.
	 */
	@PostMapping("/owners/{ownerId}/invoices")
	@PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
	public InvoiceResponse generate(JwtAuthenticationToken authentication, @PathVariable UUID ownerId,
									@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
		return invoiceService.generate(Caller.of(authentication), ownerId, month);
	}

	@GetMapping("/owners/{ownerId}/invoices")
	@PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
	public List<InvoiceSummaryResponse> listByOwner(JwtAuthenticationToken authentication, @PathVariable UUID ownerId) {
		return invoiceService.listByOwner(Caller.of(authentication), ownerId);
	}

	@GetMapping("/invoices/{id}")
	@PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
	public InvoiceResponse get(JwtAuthenticationToken authentication, @PathVariable Long id) {
		return invoiceService.get(Caller.of(authentication), id);
	}
}
