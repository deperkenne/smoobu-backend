package com.clean.dd.owner;

import com.clean.dd.owner.dto.AssignAgentRequest;
import com.clean.dd.owner.dto.CreatePropertyRequest;
import com.clean.dd.owner.dto.PropertyResponse;
import com.clean.dd.shared.security.Caller;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** CUSTOMER : logements de ses propres propriétaires ; ADMIN : tous. */
@RestController
@RequestMapping("/api")
public class PropertyController {

	private final PropertyService propertyService;

	public PropertyController(PropertyService propertyService) {
		this.propertyService = propertyService;
	}

	@PostMapping("/owners/{ownerId}/properties")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
	public PropertyResponse add(JwtAuthenticationToken authentication, @PathVariable UUID ownerId,
								@Valid @RequestBody CreatePropertyRequest request) {
		return propertyService.add(Caller.of(authentication), ownerId, request);
	}

	@GetMapping("/owners/{ownerId}/properties")
	@PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
	public List<PropertyResponse> listByOwner(JwtAuthenticationToken authentication, @PathVariable UUID ownerId) {
		return propertyService.listByOwner(Caller.of(authentication), ownerId);
	}

	/** Tous les logements du customer connecté ; le customer vient du jeton. */
	@GetMapping("/properties/mine")
	@PreAuthorize("hasRole('CUSTOMER')")
	public List<PropertyResponse> listMine(JwtAuthenticationToken authentication) {
		return propertyService.listMine(Caller.of(authentication));
	}

	/** Logements attribués à l'agent connecté ; l'agent vient du jeton. */
	@GetMapping("/properties/assigned")
	@PreAuthorize("hasRole('AGENT')")
	public List<PropertyResponse> listAssignedToMe(@AuthenticationPrincipal Jwt jwt) {
		return propertyService.listAssignedToMe(jwt);
	}

	@GetMapping("/properties/{id}")
	@PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
	public PropertyResponse get(JwtAuthenticationToken authentication, @PathVariable UUID id) {
		return propertyService.get(Caller.of(authentication), id);
	}

	/** Attribue un agent à un logement sans agent. */
	@PostMapping("/properties/{id}/assign")
	@PreAuthorize("hasRole('CUSTOMER')")
	public PropertyResponse assign(JwtAuthenticationToken authentication, @PathVariable UUID id,
								   @Valid @RequestBody AssignAgentRequest request) {
		return propertyService.assignAgent(Caller.of(authentication), id, request.agentId());
	}

	/** Déplace un logement déjà attribué vers un autre agent. */
	@PostMapping("/properties/{id}/move")
	@PreAuthorize("hasRole('CUSTOMER')")
	public PropertyResponse move(JwtAuthenticationToken authentication, @PathVariable UUID id,
								 @Valid @RequestBody AssignAgentRequest request) {
		return propertyService.moveToAgent(Caller.of(authentication), id, request.agentId());
	}
}
