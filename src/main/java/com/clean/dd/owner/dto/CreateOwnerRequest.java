package com.clean.dd.owner.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Propriétaire, avec ou sans logements (properties facultatif). Le customer vient du token, pas du JSON. */
public record CreateOwnerRequest(
		@NotBlank @Size(max = 150) String name,
		@Email @Size(max = 255) String email,
		@Size(max = 30) String phone,
		List<@Valid CreatePropertyRequest> properties) {

	public CreateOwnerRequest {
		properties = properties == null ? List.of() : List.copyOf(properties);
	}
}
