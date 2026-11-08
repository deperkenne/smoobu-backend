package com.clean.dd.owner.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Agent à qui attribuer le logement ; il doit appartenir au même customer que le logement. */
public record AssignAgentRequest(@NotNull UUID agentId) {
}
