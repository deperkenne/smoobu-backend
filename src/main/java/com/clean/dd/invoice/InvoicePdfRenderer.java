package com.clean.dd.invoice;

import com.clean.dd.invoice.dto.InvoiceLineResponse;
import com.clean.dd.invoice.dto.InvoiceResponse;
import com.clean.dd.invoice.dto.InvoiceSummaryResponse;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.stream.Stream;

/** Met en page une facture en PDF (A4, en allemand : clients à Nürnberg). Aucune règle de calcul ici. */
@Component
@EnableConfigurationProperties(InvoiceIssuerProperties.class)
public class InvoicePdfRenderer {

	private static final Locale LOCALE = Locale.GERMANY;
	private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
	private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", LOCALE);

	private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
	private static final Font BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
	private static final Font NORMAL = FontFactory.getFont(FontFactory.HELVETICA, 10);
	private static final Font SMALL = FontFactory.getFont(FontFactory.HELVETICA, 8);

	private static final String[] HEADERS = {"Buchungsnr.", "Unterkunft", "Zimmer", "Anreise", "Abreise",
			"Preis", "Sonntagszuschlag", "Betrag"};

	private final InvoiceIssuerProperties issuer;

	public InvoicePdfRenderer(InvoiceIssuerProperties issuer) {
		this.issuer = issuer;
	}

	public byte[] render(InvoiceResponse invoice) {
		InvoiceSummaryResponse summary = invoice.invoice();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Document document = new Document(PageSize.A4, 40, 40, 40, 40);
		PdfWriter.getInstance(document, out);
		document.open();

		document.add(new Paragraph(issuer.name(), BOLD));
		String contact = joined(" · ", issuer.street(), joined(" ", issuer.postalCode(), issuer.city()),
				issuer.email(), issuer.phone());
		if (contact != null) {
			document.add(new Paragraph(contact, SMALL));
		}

		Paragraph title = new Paragraph("Rechnung", TITLE);
		title.setSpacingBefore(24);
		document.add(title);
		document.add(new Paragraph("Rechnungsnummer: " + summary.invoiceNumber(), NORMAL));
		document.add(new Paragraph("Rechnungsdatum: " + DATE.format(summary.createdAt().atZone(ZONE)), NORMAL));
		document.add(new Paragraph("Leistungszeitraum: " + MONTH.format(YearMonth.parse(summary.billingMonth())), NORMAL));

		Paragraph recipient = new Paragraph("Rechnungsempfänger: " + summary.ownerName(), NORMAL);
		recipient.setSpacingBefore(12);
		document.add(recipient);

		PdfPTable table = new PdfPTable(new float[]{2.2f, 3f, 1.4f, 1.6f, 1.6f, 1.5f, 1.9f, 1.5f});
		table.setWidthPercentage(100);
		table.setSpacingBefore(18);
		table.setHeaderRows(1);
		for (String header : HEADERS) {
			table.addCell(cell(header, BOLD, Element.ALIGN_LEFT));
		}
		for (InvoiceLineResponse line : invoice.lines()) {
			table.addCell(cell(line.bookingReference(), NORMAL, Element.ALIGN_LEFT));
			table.addCell(cell(line.propertyName(), NORMAL, Element.ALIGN_LEFT));
			table.addCell(cell(line.roomNumber(), NORMAL, Element.ALIGN_LEFT));
			table.addCell(cell(DATE.format(line.arrivalDate()), NORMAL, Element.ALIGN_LEFT));
			table.addCell(cell(DATE.format(line.departureDate()), NORMAL, Element.ALIGN_LEFT));
			table.addCell(cell(money(line.basePrice()), NORMAL, Element.ALIGN_RIGHT));
			table.addCell(cell(money(line.sundaySurcharge()), NORMAL, Element.ALIGN_RIGHT));
			table.addCell(cell(money(line.lineTotal()), NORMAL, Element.ALIGN_RIGHT));
		}
		document.add(table);

		Paragraph total = new Paragraph("Gesamtbetrag: " + money(summary.total()), BOLD);
		total.setAlignment(Element.ALIGN_RIGHT);
		total.setSpacingBefore(10);
		document.add(total);

		String legal = joined("\n", issuer.taxNumber() == null ? null : "Steuernummer: " + issuer.taxNumber(),
				issuer.taxNote());
		if (legal != null) {
			Paragraph legalText = new Paragraph(legal, SMALL);
			legalText.setSpacingBefore(24);
			document.add(legalText);
		}

		document.close();
		return out.toByteArray();
	}

	private static PdfPCell cell(String text, Font font, int alignment) {
		PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, font));
		cell.setHorizontalAlignment(alignment);
		cell.setPadding(4);
		return cell;
	}

	private static String money(BigDecimal amount) {
		return NumberFormat.getCurrencyInstance(LOCALE).format(amount);
	}

	/** Assemble les valeurs renseignées ; null si aucune. */
	private static String joined(String separator, String... parts) {
		String text = String.join(separator, Stream.of(parts).filter(p -> p != null && !p.isBlank()).toList());
		return text.isEmpty() ? null : text;
	}
}
