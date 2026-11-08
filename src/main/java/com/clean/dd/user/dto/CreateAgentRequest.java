package com.clean.dd.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Pas de customerId ni de rôle : le client ne doit pas pouvoir les choisir. */
public record CreateAgentRequest(
		@NotBlank String firstName,
		@NotBlank String lastName,
		@NotBlank @Email String email,
		String phone) {
}
