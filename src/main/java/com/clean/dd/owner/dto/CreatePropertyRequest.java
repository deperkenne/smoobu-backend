package com.clean.dd.owner.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Logement, avec ou sans chambres (rooms facultatif) et avec ou sans agent (agentId facultatif). */
public record CreatePropertyRequest(
		@NotBlank @Size(max = 200) String name,
		@NotBlank @Size(max = 200) String emailLabel,
		@Size(max = 200) String street,
		@Size(max = 10) String postalCode,
		@Size(max = 100) String city,
		List<@Valid CreateRoomRequest> rooms,
		UUID agentId) {

	public CreatePropertyRequest {
		rooms = rooms == null ? List.of() : List.copyOf(rooms);
	}
}
