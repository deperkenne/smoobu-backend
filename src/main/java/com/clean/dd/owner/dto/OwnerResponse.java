package com.clean.dd.owner.dto;

import com.clean.dd.owner.Owner;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OwnerResponse(
		UUID id,
		String customerId,
		String name,
		String email,
		String phone,
		Instant createdAt,
		List<PropertyResponse> properties) {

	public static OwnerResponse from(Owner o, List<PropertyResponse> properties) {
		return new OwnerResponse(o.getId(), o.getCustomerId(), o.getName(), o.getEmail(),
				o.getPhone(), o.getCreatedAt(), properties);
	}
}
