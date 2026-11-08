package com.clean.dd;

import com.clean.dd.customer.CustomerRepository;
import com.clean.dd.reservation.ReservationRepository;
import com.clean.dd.user.Role;
import com.clean.dd.user.User;
import com.clean.dd.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static com.clean.dd.TestJwt.OWNER_SUB;
import static com.clean.dd.TestJwt.admin;
import static com.clean.dd.TestJwt.agent;
import static com.clean.dd.TestJwt.customer;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Scénario complet sur la base ddclean_test ; chaque test est annulé (rollback) à la fin. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OwnerPropertyReservationIntegrationTest {

	private static final String LOFT = "Loft am Pegnitztal Nürnberg";
	private static final String UNKNOWN_ID = "00000000-0000-0000-0000-000000000000";

	private static final String OWNER_WITH_TWO_PROPERTIES = """
			{
			  "name": "Max Muster",
			  "email": "max@example.com",
			  "phone": "0911 123456",
			  "properties": [
			    {"name": "Loft Pegnitztal", "emailLabel": "Loft am Pegnitztal Nürnberg",
			     "street": "Hauptstr. 1", "postalCode": "90402", "city": "Nürnberg",
			     "rooms": [{"number": "101", "price": 45.00}, {"number": "2B", "price": 55.50}]},
			    {"name": "Studio Fürth", "emailLabel": "Studio Fürth Zentrum"}
			  ]
			}
			""";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private CustomerRepository customerRepository;
	@Autowired
	private ReservationRepository reservationRepository;
	@Autowired
	private UserRepository userRepository;

	@Test
	void ownerPropertiesRoomsAndReservationsScenario() throws Exception {
		// 1) propriétaire avec 2 logements, le premier avec 2 chambres
		String ownerJson = mockMvc.perform(post("/api/owners").with(customer(OWNER_SUB))
						.contentType(MediaType.APPLICATION_JSON)
						.content(OWNER_WITH_TWO_PROPERTIES))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.customerId").value(OWNER_SUB))
				.andExpect(jsonPath("$.properties", hasSize(2)))
				.andReturn().getResponse().getContentAsString();
		String ownerId = JsonPath.read(ownerJson, "$.id");

		String propertiesJson = mockMvc.perform(get("/api/owners/{id}/properties", ownerId).with(admin()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andReturn().getResponse().getContentAsString();
		List<String> loftIds = JsonPath.read(propertiesJson, "$[?(@.emailLabel == '" + LOFT + "')].id");
		List<String> studioIds = JsonPath.read(propertiesJson, "$[?(@.emailLabel == 'Studio Fürth Zentrum')].id");
		assertThat(loftIds).hasSize(1);
		assertThat(studioIds).hasSize(1);
		String loftId = loftIds.getFirst();

		// chambres créées avec le logement, rattachées au premier logement uniquement
		String roomsJson = mockMvc.perform(get("/api/properties/{id}/rooms", loftId).with(admin()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[*].propertyId", everyItem(is(loftId))))
				.andExpect(jsonPath("$[0].number").value("101"))
				.andExpect(jsonPath("$[0].price").value(45.0))
				.andExpect(jsonPath("$[1].number").value("2B"))
				.andExpect(jsonPath("$[1].price").value(55.5))
				.andReturn().getResponse().getContentAsString();
		mockMvc.perform(get("/api/properties/{id}/rooms", studioIds.getFirst()).with(admin()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));

		// 2) 3 réservations sur les chambres du premier logement, envoyées en une seule liste
		String firstJson = simulate(
				simulateBody(LOFT, "101", "IT-BK-1", "2026-11-02", "2026-11-05", "confirmed"),
				simulateBody(LOFT, "2B", "IT-BK-2", "2026-11-10", "2026-11-12", "confirmed"),
				simulateBody(LOFT, "101", "IT-BK-3", "2026-11-20", "2026-11-25", "confirmed"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(3)))
				.andExpect(jsonPath("$[*].propertyId", everyItem(is(loftId))))
				.andExpect(jsonPath("$[0].roomNumber").value("101"))
				.andExpect(jsonPath("$[0].price").value(45.0))
				.andExpect(jsonPath("$[1].roomNumber").value("2B"))
				.andExpect(jsonPath("$[1].price").value(55.5))
				.andExpect(jsonPath("$[2].roomNumber").value("101"))
				.andExpect(jsonPath("$[2].price").value(45.0))
				.andReturn().getResponse().getContentAsString();
		Number firstId = JsonPath.read(firstJson, "$[0].id");

		// 3) même bookingReference : mise à jour (ici changement de chambre), même id, pas de doublon
		simulate(simulateBody(LOFT, "2B", "IT-BK-1", "2026-11-03", "2026-11-06", "modified"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").value(firstId.longValue()))
				.andExpect(jsonPath("$[0].arrivalDate").value("2026-11-03"))
				.andExpect(jsonPath("$[0].arrivalDay").value("Montag"))
				.andExpect(jsonPath("$[0].departureDate").value("2026-11-06"))
				.andExpect(jsonPath("$[0].status").value("modified"))
				.andExpect(jsonPath("$[0].roomNumber").value("2B"))
				.andExpect(jsonPath("$[0].price").value(55.5));

		mockMvc.perform(get("/api/reservations/{id}", firstId.longValue()).with(admin()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.bookingReference").value("IT-BK-1"))
				.andExpect(jsonPath("$.status").value("modified"));

		// 4) emailLabel inconnu : 404 avec message
		simulate(simulateBody("Logement inconnu", "101", "IT-BK-4", "2026-12-01", "2026-12-03", "confirmed"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.detail", containsString("Logement inconnu")));

		// 5) chambre inconnue dans un logement connu : 404 avec message
		simulate(simulateBody(LOFT, "999", "IT-BK-5", "2026-12-01", "2026-12-03", "confirmed"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.detail", containsString("Aucune chambre")))
				.andExpect(jsonPath("$.detail", containsString("999")));

		// 6) exactement 3 réservations sur le logement, bien rattachées
		mockMvc.perform(get("/api/properties/{id}/reservations", loftId).with(admin()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(3)))
				.andExpect(jsonPath("$[*].propertyId", everyItem(is(loftId))))
				.andExpect(jsonPath("$[*].propertyName", everyItem(is("Loft Pegnitztal"))))
				.andExpect(jsonPath("$[*].ownerCustomerId", everyItem(is(OWNER_SUB))))
				.andExpect(jsonPath("$[0].bookingReference").value("IT-BK-1"))
				.andExpect(jsonPath("$[0].roomNumber").value("2B"))
				.andExpect(jsonPath("$[1].bookingReference").value("IT-BK-2"))
				.andExpect(jsonPath("$[1].roomNumber").value("2B"))
				.andExpect(jsonPath("$[2].bookingReference").value("IT-BK-3"))
				.andExpect(jsonPath("$[2].roomNumber").value("101"))
				.andExpect(jsonPath("$[2].price").value(45.0));

		// GET /api/reservations : toutes les réservations, chacune une seule fois
		mockMvc.perform(get("/api/reservations").with(admin()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.bookingReference == 'IT-BK-1')]", hasSize(1)))
				.andExpect(jsonPath("$[?(@.bookingReference == 'IT-BK-1')].ownerCustomerId", everyItem(is(OWNER_SUB))))
				.andExpect(jsonPath("$[?(@.bookingReference == 'IT-BK-1')].roomNumber", everyItem(is("2B"))))
				.andExpect(jsonPath("$[?(@.propertyId == '" + loftId + "')]", hasSize(3)));
	}

	@Test
	void simulate_withoutRoomNumber_onSingleRoomProperty_storesUniqueAndRoomPrice() throws Exception {
		mockMvc.perform(post("/api/owners").with(customer(OWNER_SUB)).contentType(MediaType.APPLICATION_JSON).content("""
				{"name": "Single",
				 "properties": [{"name": "Appart", "emailLabel": "Appart Single", "rooms": [{"number": "1", "price": 70}]}]}
				"""))
				.andExpect(status().isCreated());

		String json = mockMvc.perform(post("/api/reservations/simulate").with(admin()).contentType(MediaType.APPLICATION_JSON).content("""
				[{"emailLabel": "Appart Single", "bookingReference": "IT-SGL-1", "arrivalDate": "2026-12-01",
				  "departureDate": "2026-12-03", "channel": "Airbnb", "status": "confirmed"}]
				"""))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		Number id = JsonPath.read(json, "$[0].id");

		mockMvc.perform(get("/api/reservations/{id}", id.longValue()).with(admin()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.roomNumber").value("unique"))
				.andExpect(jsonPath("$.price").value(70.0));
	}

	@Test
	void simulate_cancelled_setsStatusAndCancelledAt() throws Exception {
		mockMvc.perform(post("/api/owners").with(customer(OWNER_SUB)).contentType(MediaType.APPLICATION_JSON).content("""
				{"name": "Cancel",
				 "properties": [{"name": "Appart", "emailLabel": "Appart Cancel", "rooms": [{"number": "1", "price": 70}]}]}
				"""))
				.andExpect(status().isCreated());
		String confirmed = """
				[{"emailLabel": "Appart Cancel", "bookingReference": "IT-CXL-1", "arrivalDate": "2026-12-01",
				  "departureDate": "2026-12-03", "channel": "Airbnb", "status": "%s"}]
				""";
		String json = mockMvc.perform(post("/api/reservations/simulate").with(admin()).contentType(MediaType.APPLICATION_JSON)
						.content(confirmed.formatted("CONFIRMED")))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		Number id = JsonPath.read(json, "$[0].id");

		// annulation envoyée deux fois : même réservation, toujours CANCELLED avec une date d'annulation
		for (int i = 0; i < 2; i++) {
			mockMvc.perform(post("/api/reservations/simulate").with(admin()).contentType(MediaType.APPLICATION_JSON)
							.content(confirmed.formatted("CANCELLED")))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$[0].id").value(id.longValue()))
					.andExpect(jsonPath("$[0].status").value("CANCELLED"))
					.andExpect(jsonPath("$[0].cancelledAt").isNotEmpty());
		}

		mockMvc.perform(get("/api/reservations/{id}", id.longValue()).with(admin()))
				.andExpect(jsonPath("$.status").value("CANCELLED"));
		// annulation d'un Buchungsnummer inconnu : ignorée (absente de la réponse), rien n'est inséré
		mockMvc.perform(post("/api/reservations/simulate").with(admin()).contentType(MediaType.APPLICATION_JSON)
						.content(confirmed.replace("IT-CXL-1", "IT-CXL-UNKNOWN").formatted("CANCELLED")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
		mockMvc.perform(get("/api/reservations").with(admin()))
				.andExpect(jsonPath("$[?(@.bookingReference == 'IT-CXL-UNKNOWN')]", hasSize(0)));

		mockMvc.perform(get("/api/reservations/cancelled").with(admin()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.bookingReference == 'IT-CXL-1')]", hasSize(1)))
				.andExpect(jsonPath("$[?(@.bookingReference == 'IT-CXL-1')].id", everyItem(is(id.intValue()))));
	}

	@Test
	void reservationsMine_returnsOnlyReservationsOfTheConnectedCustomer() throws Exception {
		String owner = """
				{"name": "%s",
				 "properties": [{"name": "Appart", "emailLabel": "%s", "rooms": [{"number": "1", "price": 70}]}]}
				""";
		mockMvc.perform(post("/api/owners").with(customer("mine-sub-a")).contentType(MediaType.APPLICATION_JSON)
						.content(owner.formatted("Mine A", "Appart Mine A")))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/owners").with(customer("mine-sub-b")).contentType(MediaType.APPLICATION_JSON)
						.content(owner.formatted("Mine B", "Appart Mine B")))
				.andExpect(status().isCreated());
		String reservation = """
				[{"emailLabel": "%s", "bookingReference": "%s", "arrivalDate": "2026-12-01",
				  "departureDate": "2026-12-03", "channel": "Airbnb", "status": "CONFIRMED"}]
				""";
		for (String[] r : new String[][] {{"Appart Mine A", "IT-MINE-A"}, {"Appart Mine B", "IT-MINE-B"}}) {
			mockMvc.perform(post("/api/reservations/simulate").with(admin()).contentType(MediaType.APPLICATION_JSON)
							.content(reservation.formatted(r[0], r[1])))
					.andExpect(status().isOk());
		}

		mockMvc.perform(get("/api/reservations/mine").with(customer("mine-sub-a")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].bookingReference").value("IT-MINE-A"))
				.andExpect(jsonPath("$[0].ownerCustomerId").value("mine-sub-a"));
		mockMvc.perform(get("/api/reservations/mine").with(customer("mine-sub-without-owner")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
		mockMvc.perform(get("/api/reservations/mine"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void propertiesMine_returnsOnlyPropertiesOfTheConnectedCustomer() throws Exception {
		String owner = """
				{"name": "%s", "properties": [{"name": "%s", "emailLabel": "%s"}]}
				""";
		mockMvc.perform(post("/api/owners").with(customer("props-sub-a")).contentType(MediaType.APPLICATION_JSON)
						.content(owner.formatted("Props A", "Haus A", "Haus Props A")))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/owners").with(customer("props-sub-b")).contentType(MediaType.APPLICATION_JSON)
						.content(owner.formatted("Props B", "Haus B", "Haus Props B")))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/properties/mine").with(customer("props-sub-a")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].emailLabel").value("Haus Props A"))
				.andExpect(jsonPath("$[0].ownerName").value("Props A"))
				.andExpect(jsonPath("$[0].agentId").doesNotExist());
		mockMvc.perform(get("/api/properties/mine"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void propertiesAssigned_returnsOnlyPropertiesAssignedToTheConnectedAgent() throws Exception {
		String ownerJson = mockMvc.perform(post("/api/owners").with(customer("assign-sub")).contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "Assign", "properties": [
								  {"name": "Haus 1", "emailLabel": "Haus Assign 1"},
								  {"name": "Haus 2", "emailLabel": "Haus Assign 2"}]}
								"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String propertyId = JsonPath.read(ownerJson, "$.properties[0].id");
		User max = userRepository.save(User.create(customerRepository.findById("assign-sub").orElseThrow(),
				"agent-max-sub", "max@example.com", "Max Müller", null, Role.AGENT));

		mockMvc.perform(post("/api/properties/{id}/assign", propertyId).with(customer("assign-sub"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"agentId\": \"%s\"}".formatted(max.getId())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.agentName").value("Max Müller"));

		mockMvc.perform(get("/api/properties/assigned").with(agent("agent-max-sub")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").value(propertyId))
				.andExpect(jsonPath("$[0].agentId").value(max.getId().toString()));
		// compte Keycloak sans fiche agent : refusé
		mockMvc.perform(get("/api/properties/assigned").with(agent("unknown-agent-sub")))
				.andExpect(status().isForbidden());
		// un customer n'a pas accès à cette route
		mockMvc.perform(get("/api/properties/assigned").with(customer("assign-sub")))
				.andExpect(status().isForbidden());
	}

	@Test
	void rooms_addThenList_andDuplicateNumberReturns409() throws Exception {
		String ownerJson = mockMvc.perform(post("/api/owners").with(customer(OWNER_SUB)).contentType(MediaType.APPLICATION_JSON).content("""
						{"name": "R", "properties": [{"name": "Haus", "emailLabel": "Haus Rooms"}]}
						"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String propertyId = JsonPath.read(ownerJson, "$.properties[0].id");

		mockMvc.perform(post("/api/properties/{id}/rooms", propertyId).with(admin())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"number": " 101 ", "price": 45.00}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.propertyId").value(propertyId))
				.andExpect(jsonPath("$.number").value("101"))
				.andExpect(jsonPath("$.price").value(45.0));

		mockMvc.perform(get("/api/properties/{id}/rooms", propertyId).with(admin()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].number").value("101"));

		mockMvc.perform(post("/api/properties/{id}/rooms", propertyId).with(admin())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"number": "101", "price": 50}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.detail", containsString("101")));
	}

	@Test
	void createOwner_withTwoRoomsSameNumberInOneProperty_returns409() throws Exception {
		mockMvc.perform(post("/api/owners").with(customer(OWNER_SUB)).contentType(MediaType.APPLICATION_JSON).content("""
						{"name": "D", "properties": [
						  {"name": "Haus", "emailLabel": "Haus Dup",
						   "rooms": [{"number": "101", "price": 45.00}, {"number": " 101 ", "price": 50.00}]}
						]}
						"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.detail", containsString("101")));
	}

	@Test
	void rooms_onUnknownProperty_returns404() throws Exception {
		mockMvc.perform(get("/api/properties/{id}/rooms", UNKNOWN_ID).with(admin()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404));
	}

	@Test
	void rooms_withInvalidBody_returns400WithFieldErrors() throws Exception {
		mockMvc.perform(post("/api/properties/{id}/rooms", UNKNOWN_ID).with(admin())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"number": "", "price": -1}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.number").isNotEmpty())
				.andExpect(jsonPath("$.errors.price").isNotEmpty());
	}

	@Test
	void createOwner_withInvalidBody_returns400WithFieldErrors() throws Exception {
		mockMvc.perform(post("/api/owners").with(customer(OWNER_SUB))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "", "email": "pas-un-email"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.errors.name").isNotEmpty())
				.andExpect(jsonPath("$.errors.email").isNotEmpty());
	}

	@Test
	void createOwner_twiceWithSameToken_createsTwoOwnersForOneCustomer() throws Exception {
		String sub = "it-twice-sub";
		long customersBefore = customerRepository.count();

		for (String name : List.of("Premier", "Second")) {
			mockMvc.perform(post("/api/owners").with(customer(sub)).contentType(MediaType.APPLICATION_JSON)
							.content("""
									{"name": "%s"}
									""".formatted(name)))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.customerId").value(sub))
					.andExpect(jsonPath("$.properties", hasSize(0)));
		}

		assertThat(customerRepository.count()).isEqualTo(customersBefore + 1);
		assertThat(customerRepository.existsById(sub)).isTrue();
		mockMvc.perform(get("/api/owners/mine").with(customer(sub)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[*].customerId", everyItem(is(sub))))
				.andExpect(jsonPath("$[*].name", containsInAnyOrder("Premier", "Second")));
	}

	@Test
	void createOwner_ignoresCustomerIdSentInJson() throws Exception {
		mockMvc.perform(post("/api/owners").with(customer(OWNER_SUB)).contentType(MediaType.APPLICATION_JSON).content("""
						{"customerId": "PIRATE-0001", "name": "Max"}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.customerId").value(OWNER_SUB));
	}

	@Test
	void createOwner_withEmailLabelUsedByAnotherOwner_returns409() throws Exception {
		mockMvc.perform(post("/api/owners").with(customer(OWNER_SUB)).contentType(MediaType.APPLICATION_JSON).content("""
				{"name": "A", "properties": [{"name": "Studio", "emailLabel": "Studio Unique"}]}
				"""))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/owners").with(customer(OWNER_SUB)).contentType(MediaType.APPLICATION_JSON).content("""
				{"name": "B", "properties": [{"name": "Studio", "emailLabel": "Studio Unique"}]}
				"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail", containsString("Studio Unique")));
	}

	@Test
	void simulate_reservationCarriesCustomerOfPropertyOwner() throws Exception {
		mockMvc.perform(post("/api/owners").with(customer(OWNER_SUB)).contentType(MediaType.APPLICATION_JSON).content("""
				{"name": "Cust", "properties": [{"name": "Appart", "emailLabel": "Appart Customer",
				  "rooms": [{"number": "1", "price": 70}]}]}
				"""))
				.andExpect(status().isCreated());
		String json = simulate(simulateBody("Appart Customer", "1", "IT-CUS-1", "2026-12-01", "2026-12-03", "CONFIRMED"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		Number id = JsonPath.read(json, "$[0].id");

		mockMvc.perform(get("/api/reservations/{id}", id.longValue()).with(admin()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ownerCustomerId").value(OWNER_SUB));
		assertThat(reservationRepository.findById(id.longValue()).orElseThrow().getCustomerId())
				.isEqualTo(OWNER_SUB);
	}

	@Test
	void simulate_withEmptyList_returns400() throws Exception {
		simulate().andExpect(status().isBadRequest());
	}

	@Test
	void simulate_withInvalidItem_returns400() throws Exception {
		simulate("""
				{"emailLabel": "", "roomNumber": "", "bookingReference": "IT-BK-9", "arrivalDate": "2026-12-01",
				 "departureDate": "2026-12-03", "channel": "Airbnb", "status": "confirmed"}
				""").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
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
				 "arrivalDate": "%s", "arrivalDay": "Montag", "departureDate": "%s", "channel": "Booking.com", "status": "%s"}
				""".formatted(emailLabel, roomNumber, bookingReference, arrival, departure, status);
	}
}
