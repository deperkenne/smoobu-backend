package com.clean.dd.user.dto;

import com.clean.dd.user.User;

import java.util.UUID;

public record AgentResponse(UUID id, String fullName, String email, String phone, boolean active) {

	public static AgentResponse from(User u) {
		return new AgentResponse(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.isActive());
	}
}
