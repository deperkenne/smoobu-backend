package com.clean.dd.user;

import com.clean.dd.user.dto.AgentResponse;
import com.clean.dd.user.dto.CreateAgentRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Le customer connecté gère ses agents ; le customer vient toujours du jeton. */
@RestController
@RequestMapping("/api/agents")
@PreAuthorize("hasRole('CUSTOMER')")
public class AgentController {

	private final AgentService agentService;
	private final UserService userService;

	public AgentController(AgentService agentService, UserService userService) {
		this.agentService = agentService;
		this.userService = userService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public AgentResponse create(@Valid @RequestBody CreateAgentRequest req, @AuthenticationPrincipal Jwt jwt) {
		return agentService.create(req, jwt);
	}

	@GetMapping
	public List<AgentResponse> list(@AuthenticationPrincipal Jwt jwt) {
		return userService.listAgents(jwt);
	}

	@GetMapping("/{id}")
	public AgentResponse get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
		return userService.getAgent(id, jwt);
	}

	/** Désactive l'agent : il ne peut plus se connecter, sa fiche est gardée (active = false). */
	@PatchMapping("/{id}/deactivate")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deactivate(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
		userService.deactivateAgent(id, jwt);
	}
}
