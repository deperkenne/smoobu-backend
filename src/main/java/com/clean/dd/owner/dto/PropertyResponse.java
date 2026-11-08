package com.clean.dd.owner.dto;

import com.clean.dd.owner.Property;

import java.util.UUID;

public record PropertyResponse(
		UUID id,
		String name,
		String emailLabel,
		String street,
		String postalCode,
		String city,
		UUID ownerId,
		String ownerCustomerId,
		String ownerName,
		UUID agentId,
		String agentName) {

	public static PropertyResponse from(Property p) {
		return new PropertyResponse(p.getId(), p.getName(), p.getEmailLabel(), p.getStreet(),
				p.getPostalCode(), p.getCity(), p.getOwner().getId(),
				p.getOwner().getCustomerId(), p.getOwner().getName(),
				p.getUser() == null ? null : p.getUser().getId(),
				p.getUser() == null ? null : p.getUser().getFullName());
	}
}
