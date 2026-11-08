package com.clean.dd;

import com.clean.dd.customer.Customer;
import com.clean.dd.customer.CustomerRepository;
import com.clean.dd.owner.Owner;
import com.clean.dd.owner.OwnerRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static com.clean.dd.TestJwt.admin;
import static com.clean.dd.TestJwt.customer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Authentification (401), rôles (403) et cloisonnement des données par client (404) ; rollback à la fin. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityIntegrationTest {

	private static final String ALICE = "sub-alice";
	private static final String BOB = "sub-bob";
	private static final String UNKNOWN_ID = "00000000-0000-0000-0000-000000000000";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private CustomerRepository customerRepository;
	@Autowired
	private OwnerRepository ownerRepository;

	@Test
	void withoutToken_returns401() throws Exception {
		mockMvc.perform(get("/api/owners/mine")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/owners/{id}", UNKNOWN_ID)).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/owners").contentType(MediaType.APPLICATION_JSON)
						.content(ownerBody("SEC-401", "Sec 401")))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/properties/{id}", UNKNOWN_ID)).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/invoices/{id}", 1L)).andExpect(status().isUnauthorized());
	}

	@Test
	void tokenWithoutKnownRole_returns403() throws Exception {
		mockMvc.perform(get("/api/owners/mine").with(jwt().jwt(j -> j.subject(ALICE))))
				.andExpect(status().isForbidden());
	}

	@Test
	void customerCreatesOwner_customerIsProvisionedFromClaimsAndLinkedToOwner() throws Exception {
		RequestPostProcessor aliceWithBillingClaims = jwt()
				.jwt(j -> j.subject(ALICE)
						.claim("email", "alice@example.com")
						.claim("preferred_username", "alice")
						.claim("company_name", "Alice GmbH")
						.claim("vat_number", "DE999999999")
						.claim("address", Map.of(
								"street_address", "Königstr. 1",
								"postal_code", "90402",
								"locality", "Nürnberg",
								"country", "DE")))
				.authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
		assertThat(customerRepository.existsById(ALICE)).isFalse();

		String ownerId = createOwner(aliceWithBillingClaims, "SEC-JIT", "Sec JIT");

		Customer alice = customerRepository.findById(ALICE).orElseThrow();
		assertThat(alice.getEmail()).isEqualTo("alice@example.com");
		assertThat(alice.getUsername()).isEqualTo("alice");
		assertThat(alice.getCompanyName()).isEqualTo("Alice GmbH");
		assertThat(alice.getVatNumber()).isEqualTo("DE999999999");
		assertThat(alice.getStreet()).isEqualTo("Königstr. 1");
		assertThat(alice.getPostalCode()).isEqualTo("90402");
		assertThat(alice.getCity()).isEqualTo("Nürnberg");
		assertThat(alice.getCountry()).isEqualTo("DE");
		Owner owner = ownerRepository.findById(UUID.fromString(ownerId)).orElseThrow();
		assertThat(owner.getCustomer().getId()).isEqualTo(ALICE);

		// deuxième propriétaire du même client : pas de doublon, profil mis à jour avec le nouveau jeton
		createOwner(customer(ALICE), "SEC-JIT-2", "Sec JIT 2");
		assertThat(customerRepository.findAll()).filteredOn(c -> ALICE.equals(c.getId())).hasSize(1);
		assertThat(customerRepository.findById(ALICE).orElseThrow().getCompanyName()).isNull();
		assertThat(ownerRepository.findByCustomer(ALICE)).extracting(Owner::getName)
				.containsExactly("Owner SEC-JIT", "Owner SEC-JIT-2");
		assertThat(ownerRepository.findByCustomer(ALICE)).extracting(Owner::getCustomerId)
				.containsExactly(ALICE, ALICE);
	}

	@Test
	void customerSeesOnlyOwnersHeCreated() throws Exception {
		String ownerId = createOwner(customer(ALICE), "SEC-ALICE", "Sec Alice");
		String propertyId = propertyIdOf(ownerId);

		// Alice voit son propriétaire, ses logements, ses chambres et ses factures
		mockMvc.perform(get("/api/owners/mine").with(customer(ALICE)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").value(ownerId))
				.andExpect(jsonPath("$[0].properties", hasSize(1)));
		mockMvc.perform(get("/api/owners/{id}", ownerId).with(customer(ALICE))).andExpect(status().isOk());
		mockMvc.perform(get("/api/owners/{id}/properties", ownerId).with(customer(ALICE)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)));
		mockMvc.perform(get("/api/properties/{id}", propertyId).with(customer(ALICE))).andExpect(status().isOk());
		mockMvc.perform(get("/api/properties/{id}/rooms", propertyId).with(customer(ALICE)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)));
		mockMvc.perform(get("/api/owners/{id}/invoices", ownerId).with(customer(ALICE)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));

		// Bob ne voit rien de tout cela : 404 (pas 403) pour ne pas révéler l'existence des données
		mockMvc.perform(get("/api/owners/mine").with(customer(BOB)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
		mockMvc.perform(get("/api/owners/{id}", ownerId).with(customer(BOB))).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/owners/{id}/properties", ownerId).with(customer(BOB)))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/properties/{id}", propertyId).with(customer(BOB))).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/properties/{id}/rooms", propertyId).with(customer(BOB)))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/properties/{id}/reservations", propertyId).with(customer(BOB)))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/owners/{id}/invoices", ownerId).with(customer(BOB)))
				.andExpect(status().isNotFound());

		// Bob ne peut pas non plus écrire chez Alice
		mockMvc.perform(post("/api/owners/{id}/properties", ownerId).with(customer(BOB))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "Intrus", "emailLabel": "Sec Intrus"}
								"""))
				.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/properties/{id}/rooms", propertyId).with(customer(BOB))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"number": "999", "price": 10}
								"""))
				.andExpect(status().isNotFound());

		// ADMIN voit tout
		mockMvc.perform(get("/api/owners").with(admin()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", hasItem(ownerId)));
		mockMvc.perform(get("/api/owners/{id}", ownerId).with(admin())).andExpect(status().isOk());
		mockMvc.perform(get("/api/properties/{id}", propertyId).with(admin())).andExpect(status().isOk());
		mockMvc.perform(get("/api/properties/{id}/rooms", propertyId).with(admin()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)));
		mockMvc.perform(get("/api/owners/{id}/invoices", ownerId).with(admin())).andExpect(status().isOk());
	}

	@Test
	void customerCanAddPropertiesAndRoomsToHisOwner() throws Exception {
		String ownerId = createOwner(customer(ALICE), "SEC-ADD", "Sec Add");

		String json = mockMvc.perform(post("/api/owners/{id}/properties", ownerId).with(customer(ALICE))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "Studio", "emailLabel": "Sec Add Studio"}
								"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String studioId = JsonPath.read(json, "$.id");

		mockMvc.perform(post("/api/properties/{id}/rooms", studioId).with(customer(ALICE))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"number": "1", "price": 30}
								"""))
				.andExpect(status().isCreated());
		mockMvc.perform(get("/api/owners/{id}/properties", ownerId).with(customer(ALICE)))
				.andExpect(jsonPath("$", hasSize(2)));
	}

	@Test
	void customerSeesOnlyReservationsOfHisOwners() throws Exception {
		String aliceOwnerId = createOwner(customer(ALICE), "SEC-RA", "Sec Resa Alice");
		createOwner(customer(BOB), "SEC-RB", "Sec Resa Bob");
		String json = mockMvc.perform(post("/api/reservations/simulate").with(admin())
						.contentType(MediaType.APPLICATION_JSON)
						.content("[" + simulateBody("Sec Resa Alice", "SEC-RA-1") + ","
								+ simulateBody("Sec Resa Bob", "SEC-RB-1") + "]"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		Number aliceReservationId = JsonPath.read(json, "$[0].id");
		Number bobReservationId = JsonPath.read(json, "$[1].id");
		String alicePropertyId = propertyIdOf(aliceOwnerId);

		// GET /api/reservations est public : toutes les réservations, sans cloisonnement
		mockMvc.perform(get("/api/reservations"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].bookingReference", hasItem("SEC-RA-1")))
				.andExpect(jsonPath("$[*].bookingReference", hasItem("SEC-RB-1")));

		mockMvc.perform(get("/api/reservations/{id}", aliceReservationId.longValue()).with(customer(ALICE)))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/reservations/{id}", bobReservationId.longValue()).with(customer(ALICE)))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/properties/{id}/reservations", alicePropertyId).with(customer(ALICE)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)));

		// réservations annulées : même cloisonnement
		mockMvc.perform(post("/api/reservations/simulate").with(admin())
						.contentType(MediaType.APPLICATION_JSON)
						.content("[" + simulateBody("Sec Resa Alice", "SEC-RA-1", "CANCELLED") + ","
								+ simulateBody("Sec Resa Bob", "SEC-RB-1", "CANCELLED") + "]"))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/reservations/cancelled").with(customer(ALICE)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].bookingReference").value("SEC-RA-1"));
		mockMvc.perform(get("/api/reservations/cancelled").with(admin()))
				.andExpect(jsonPath("$[*].bookingReference", hasItem("SEC-RA-1")))
				.andExpect(jsonPath("$[*].bookingReference", hasItem("SEC-RB-1")));
	}

	@Test
	void customerSeesOnlyInvoicesOfHisOwners() throws Exception {
		String ownerId = createOwner(customer(ALICE), "SEC-INV", "Sec Invoice");
		mockMvc.perform(post("/api/reservations/simulate").with(admin())
						.contentType(MediaType.APPLICATION_JSON)
						.content("[" + simulateBody("Sec Invoice", "SEC-INV-1") + "]"))
				.andExpect(status().isOk());
		String json = mockMvc.perform(post("/api/owners/{id}/invoices", ownerId).with(admin()).param("month", "2026-11"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		Number invoiceId = JsonPath.read(json, "$.invoice.id");

		mockMvc.perform(get("/api/owners/{id}/invoices", ownerId).with(customer(ALICE)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", containsInAnyOrder(invoiceId.intValue())));
		mockMvc.perform(get("/api/invoices/{id}", invoiceId.longValue()).with(customer(ALICE)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lines", hasSize(1)));
		mockMvc.perform(get("/api/invoices/{id}", invoiceId.longValue()).with(customer(BOB)))
				.andExpect(status().isNotFound());
	}

	private String createOwner(RequestPostProcessor token, String label, String emailLabel) throws Exception {
		String json = mockMvc.perform(post("/api/owners").with(token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(ownerBody(label, emailLabel)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(json, "$.id");
	}

	private String propertyIdOf(String ownerId) throws Exception {
		String json = mockMvc.perform(get("/api/owners/{id}", ownerId).with(admin()))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(json, "$.properties[0].id");
	}

	private static String ownerBody(String label, String emailLabel) {
		return """
				{"name": "Owner %s",
				 "properties": [{"name": "Logement %s", "emailLabel": "%s",
				                 "rooms": [{"number": "101", "price": 45.00}, {"number": "2B", "price": 55.50}]}]}
				""".formatted(label, label, emailLabel);
	}

	private static String simulateBody(String emailLabel, String bookingReference) {
		return simulateBody(emailLabel, bookingReference, "CONFIRMED");
	}

	private static String simulateBody(String emailLabel, String bookingReference, String status) {
		return """
				{"emailLabel": "%s", "roomNumber": "101", "bookingReference": "%s",
				 "arrivalDate": "2026-11-10", "departureDate": "2026-11-12", "channel": "Airbnb", "status": "%s"}
				""".formatted(emailLabel, bookingReference, status);
	}
}
