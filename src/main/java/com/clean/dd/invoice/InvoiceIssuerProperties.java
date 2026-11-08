package com.clean.dd.invoice;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Coordonnées de l'émetteur imprimées sur le PDF (clés app.invoice.issuer.*).
 * Une valeur absente n'est simplement pas imprimée.
 */
@ConfigurationProperties("app.invoice.issuer")
public record InvoiceIssuerProperties(
		@DefaultValue("D&D Clean") String name,
		String street,
		String postalCode,
		String city,
		String email,
		String phone,
		String taxNumber,
		String taxNote) {
}
