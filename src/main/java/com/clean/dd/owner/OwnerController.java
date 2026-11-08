package com.clean.dd.owner;

import com.clean.dd.owner.dto.CreateOwnerRequest;
import com.clean.dd.owner.dto.OwnerResponse;
import com.clean.dd.shared.security.AuthenticatedCustomer;
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

/** CUSTOMER : ses propres propriétaires ; ADMIN : tous. */
@RestController
@RequestMapping("/api/owners")
public class OwnerController {

	private final OwnerService ownerService;

	public OwnerController(OwnerService ownerService) {
		this.ownerService = ownerService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAnyRole('CUSTOMER')")
	public OwnerResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateOwnerRequest request) {
		return ownerService.create(AuthenticatedCustomer.identityOf(jwt), request);
	}

	/** Tous les propriétaires, sans jeton (route publique). */
	@GetMapping
	public List<OwnerResponse> list() {
		return ownerService.list();
	}

	/** Propriétaires du client connecté (menu déroulant du front). */
	@GetMapping("/mine")
	public List<OwnerResponse> listMine(JwtAuthenticationToken authentication) {
		return ownerService.listMine(Caller.of(authentication));
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
	public OwnerResponse get(JwtAuthenticationToken authentication, @PathVariable UUID id) {
		return ownerService.get(Caller.of(authentication), id);
	}
}
