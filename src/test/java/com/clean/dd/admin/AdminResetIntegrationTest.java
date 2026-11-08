package com.clean.dd.admin;

import com.clean.dd.customer.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static com.clean.dd.TestJwt.OWNER_SUB;
import static com.clean.dd.TestJwt.admin;
import static com.clean.dd.TestJwt.customer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminResetIntegrationTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private CustomerRepository customerRepository;

	@Test
	void reset_deletesOwnersPropertiesRoomsReservationsAndCustomers() throws Exception {
		mockMvc.perform(post("/api/owners").with(customer(OWNER_SUB)).contentType(MediaType.APPLICATION_JSON).content("""
						{"name": "Reset",
						 "properties": [{"name": "Loft", "emailLabel": "Loft Reset",
						                 "rooms": [{"number": "1", "price": 40}]}]}
						"""))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/reservations/simulate").with(admin()).contentType(MediaType.APPLICATION_JSON).content("""
						[{"emailLabel": "Loft Reset", "roomNumber": "1", "bookingReference": "RST-BK-1",
						  "arrivalDate": "2026-12-01", "departureDate": "2026-12-03",
						  "channel": "Airbnb", "status": "confirmed"}]
						"""))
				.andExpect(status().isOk());

		assertThat(customerRepository.existsById(OWNER_SUB)).isTrue();

		mockMvc.perform(delete("/api/admin/reset").with(admin())).andExpect(status().isNoContent());

		assertThat(customerRepository.count()).isZero();

		mockMvc.perform(get("/api/owners").with(admin())).andExpect(jsonPath("$", hasSize(0)));
		mockMvc.perform(get("/api/reservations").with(admin())).andExpect(jsonPath("$", hasSize(0)));
	}
}
