package com.clean.dd.invoice;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.YearMonth;

import static com.clean.dd.TestJwt.OWNER_SUB;
import static com.clean.dd.TestJwt.admin;
import static com.clean.dd.TestJwt.customer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Facturation mensuelle sur la base ddclean_test ; chaque test est annulé (rollback) à la fin. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InvoiceIntegrationTest {

	private static final String LOFT = "Loft Facture Nürnberg";
	private static final String OTHER = "Studio Autre Fürth";
	private static final String UNKNOWN_ID = "00000000-0000-0000-0000-000000000000";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void generateListAndGetInvoiceScenario() throws Exception {
		String ownerId = createOwner("INV-0001", LOFT);
		createOwner("INV-0002", OTHER);

		simulate(
				// retenues : novembre, CONFIRMED (casse ignorée) ; 2026-11-08 est un dimanche
				simulateBody(LOFT, "101", "INV-BK-SUN", "2026-11-08", "2026-11-10", "confirmed"),
				simulateBody(LOFT, "2B", "INV-BK-TUE", "2026-11-10", "2026-11-12", "CONFIRMED"),
				// exclues : décembre, autre statut, autre propriétaire
				simulateBody(LOFT, "101", "INV-BK-DEC", "2026-12-01", "2026-12-03", "CONFIRMED"),
				simulateBody(LOFT, "101", "INV-BK-PEN", "2026-11-15", "2026-11-17", "PENDING"),
				simulateBody(OTHER, "1", "INV-BK-OTH", "2026-11-20", "2026-11-22", "CONFIRMED"))
				.andExpect(status().isOk());

		String json = generate(ownerId, "2026-11")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.invoice.id").isNotEmpty())
				.andExpect(jsonPath("$.invoice.invoiceNumber").value("F-2026-11-" + ownerId))
				.andExpect(jsonPath("$.invoice.billingMonth").value("2026-11"))
				.andExpect(jsonPath("$.invoice.total").value(110.5))
				.andExpect(jsonPath("$.invoice.ownerId").value(ownerId))
				.andExpect(jsonPath("$.invoice.ownerCustomerId").value(OWNER_SUB))
				.andExpect(jsonPath("$.lines", hasSize(2)))
				.andExpect(jsonPath("$.lines[0].bookingReference").value("INV-BK-SUN"))
				.andExpect(jsonPath("$.lines[0].roomNumber").value("101"))
				.andExpect(jsonPath("$.lines[0].arrivalDate").value("2026-11-08"))
				.andExpect(jsonPath("$.lines[0].basePrice").value(45.0))
				.andExpect(jsonPath("$.lines[0].sundaySurcharge").value(10.0))
				.andExpect(jsonPath("$.lines[0].lineTotal").value(55.0))
				.andExpect(jsonPath("$.lines[1].bookingReference").value("INV-BK-TUE"))
				.andExpect(jsonPath("$.lines[1].roomNumber").value("2B"))
				.andExpect(jsonPath("$.lines[1].basePrice").value(55.5))
				.andExpect(jsonPath("$.lines[1].sundaySurcharge").value(0.0))
				.andExpect(jsonPath("$.lines[1].lineTotal").value(55.5))
				.andReturn().getResponse().getContentAsString();
		Number invoiceId = JsonPath.read(json, "$.invoice.id");

		// déjà générée pour ce mois : la facture existante est renvoyée, rien n'est recréé
		generate(ownerId, "2026-11")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.invoice.id").value(invoiceId.longValue()))
				.andExpect(jsonPath("$.invoice.total").value(110.5))
				.andExpect(jsonPath("$.lines", hasSize(2)));

		mockMvc.perform(get("/api/owners/{id}/invoices", ownerId).with(admin()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").value(invoiceId.longValue()))
				.andExpect(jsonPath("$[0].invoiceNumber").value("F-2026-11-" + ownerId))
				.andExpect(jsonPath("$[0].total").value(110.5));

		mockMvc.perform(get("/api/invoices/{id}", invoiceId.longValue()).with(admin()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.invoice.invoiceNumber").value("F-2026-11-" + ownerId))
				.andExpect(jsonPath("$.lines", hasSize(2)))
				.andExpect(jsonPath("$.lines[0].bookingReference").value("INV-BK-SUN"))
				.andExpect(jsonPath("$.lines[0].lineTotal").value(55.0))
				.andExpect(jsonPath("$.lines[1].bookingReference").value("INV-BK-TUE"));
	}

	@Test
	void invoiceNumber_containsOwnerId() throws Exception {
		String ownerId = createOwner("INV-NUM", LOFT);
		simulate(simulateBody(LOFT, "101", "INV-BK-NUM", "2026-11-10", "2026-11-12", "CONFIRMED"))
				.andExpect(status().isOk());

		generate(ownerId, "2026-11")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.invoice.invoiceNumber", containsString(ownerId)))
				.andExpect(jsonPath("$.invoice.invoiceNumber").value("F-2026-11-" + ownerId));
	}

	@Test
	void generate_includesFirstAndLastDayOfMonthOnly() throws Exception {
		String ownerId = createOwner("INV-0005", LOFT);
		simulate(
				// 2026-10-31 : veille du mois, exclue
				simulateBody(LOFT, "101", "INV-BK-OCT31", "2026-10-31", "2026-11-02", "CONFIRMED"),
				// 2026-11-01 : premier jour, et un dimanche
				simulateBody(LOFT, "101", "INV-BK-NOV01", "2026-11-01", "2026-11-03", "CONFIRMED"),
				// 2026-11-30 : dernier jour
				simulateBody(LOFT, "101", "INV-BK-NOV30", "2026-11-30", "2026-12-02", "CONFIRMED"))
				.andExpect(status().isOk());

		generate(ownerId, "2026-11")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lines", hasSize(2)))
				.andExpect(jsonPath("$.lines[0].bookingReference").value("INV-BK-NOV01"))
				.andExpect(jsonPath("$.lines[0].sundaySurcharge").value(10.0))
				.andExpect(jsonPath("$.lines[1].bookingReference").value("INV-BK-NOV30"))
				.andExpect(jsonPath("$.lines[1].sundaySurcharge").value(0.0));
	}

	@Test
	void generate_neverInvoicesTheSameReservationTwice() throws Exception {
		String ownerId = createOwner("INV-0006", LOFT);
		simulate(simulateBody(LOFT, "101", "INV-BK-MOVE", "2026-11-10", "2026-11-12", "CONFIRMED"))
				.andExpect(status().isOk());
		generate(ownerId, "2026-11")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lines", hasSize(1)));

		// Smoobu déplace la réservation déjà facturée en décembre : elle n'est pas refacturée
		simulate(simulateBody(LOFT, "101", "INV-BK-MOVE", "2026-12-05", "2026-12-07", "CONFIRMED"))
				.andExpect(status().isOk());
		generate(ownerId, "2026-12")
				.andExpect(status().isNotFound());

		// la facture de novembre reste figée
		mockMvc.perform(get("/api/owners/{id}/invoices", ownerId).with(admin()))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].billingMonth").value("2026-11"));
	}

	@Test
	void generate_monthWithoutConfirmedReservation_returns404() throws Exception {
		String ownerId = createOwner("INV-0003", LOFT);
		simulate(simulateBody(LOFT, "101", "INV-BK-NOV", "2026-11-08", "2026-11-10", "CONFIRMED"))
				.andExpect(status().isOk());

		generate(ownerId, "2026-10")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail", containsString("2026-10")));
	}

	@Test
	void generate_withUnknownOwner_returns404() throws Exception {
		generate(UNKNOWN_ID, "2026-11").andExpect(status().isNotFound());
	}

	@Test
	void generate_withInvalidMonth_returns400() throws Exception {
		String ownerId = createOwner("INV-0004", LOFT);

		generate(ownerId, "2026-13").andExpect(status().isBadRequest());
		generate(ownerId, "novembre").andExpect(status().isBadRequest());
	}

	@Test
	void generate_withoutMonth_usesCurrentMonth() throws Exception {
		String ownerId = createOwner("INV-0007", LOFT);

		// mois en cours par défaut : aucune réservation pour ce propriétaire, donc 404 (et non 400)
		mockMvc.perform(post("/api/owners/{id}/invoices", ownerId).with(admin()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail", containsString(YearMonth.now().toString())));
	}

	@Test
	void pdf_returnsPdfAttachmentForOwningCustomer_and404ForOtherCustomer() throws Exception {
		String ownerId = createOwner("INV-PDF", LOFT);
		simulate(simulateBody(LOFT, "101", "INV-BK-PDF", "2026-11-10", "2026-11-12", "CONFIRMED"))
				.andExpect(status().isOk());
		String json = mockMvc.perform(post("/api/owners/{id}/invoices", ownerId).with(customer(OWNER_SUB))
						.param("month", "2026-11"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		Number invoiceId = JsonPath.read(json, "$.invoice.id");

		byte[] pdf = mockMvc.perform(get("/api/invoices/{id}/pdf", invoiceId.longValue()).with(customer(OWNER_SUB)))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_PDF))
				.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
						allOf(startsWith("attachment"), containsString("F-2026-11-" + ownerId + ".pdf"))))
				.andReturn().getResponse().getContentAsByteArray();
		assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");

		mockMvc.perform(get("/api/invoices/{id}/pdf", invoiceId.longValue()).with(customer("sub-other")))
				.andExpect(status().isNotFound());
	}

	@Test
	void list_withUnknownOwner_returns404() throws Exception {
		mockMvc.perform(get("/api/owners/{id}/invoices", UNKNOWN_ID).with(admin())).andExpect(status().isNotFound());
	}

	@Test
	void get_withUnknownId_returns404() throws Exception {
		mockMvc.perform(get("/api/invoices/{id}", 999_999L).with(admin())).andExpect(status().isNotFound());
	}

	@Test
	void adminReset_withExistingInvoices_returns204AndDeletesThem() throws Exception {
		String ownerId = createOwner("INV-0005", LOFT);
		simulate(simulateBody(LOFT, "101", "INV-BK-RST", "2026-11-08", "2026-11-10", "CONFIRMED"))
				.andExpect(status().isOk());
		String json = generate(ownerId, "2026-11").andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		Number invoiceId = JsonPath.read(json, "$.invoice.id");

		mockMvc.perform(delete("/api/admin/reset").with(admin())).andExpect(status().isNoContent());

		mockMvc.perform(get("/api/invoices/{id}", invoiceId.longValue()).with(admin())).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/owners").with(admin())).andExpect(jsonPath("$", hasSize(0)));
	}

	private String createOwner(String label, String emailLabel) throws Exception {
		String json = mockMvc.perform(post("/api/owners").with(customer(OWNER_SUB)).contentType(MediaType.APPLICATION_JSON).content("""
						{"name": "Owner %s",
						 "properties": [{"name": "Logement %s", "emailLabel": "%s",
						                 "rooms": [{"number": "101", "price": 45.00}, {"number": "2B", "price": 55.50},
						                           {"number": "1", "price": 30.00}]}]}
						""".formatted(label, label, emailLabel)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(json, "$.id");
	}

	private ResultActions generate(String ownerId, String month) throws Exception {
		return mockMvc.perform(post("/api/owners/{id}/invoices", ownerId).with(admin()).param("month", month));
	}

	private ResultActions simulate(String... reservations) throws Exception {
		return mockMvc.perform(post("/api/reservations/simulate").with(admin())
				.contentType(MediaType.APPLICATION_JSON)
				.content("[" + String.join(",", reservations) + "]"));
	}

	private static String simulateBody(String emailLabel, String roomNumber, String bookingReference,
									   String arrival, String departure, String status) {
		return """
				{"emailLabel": "%s", "roomNumber": "%s", "bookingReference": "%s",
				 "arrivalDate": "%s", "departureDate": "%s", "channel": "Booking.com", "status": "%s"}
				""".formatted(emailLabel, roomNumber, bookingReference, arrival, departure, status);
	}
}
