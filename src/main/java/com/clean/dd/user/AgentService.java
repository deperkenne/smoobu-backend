package com.clean.dd.user;

import com.clean.dd.customer.Customer;
import com.clean.dd.shared.keycloak.EmailAlreadyUsedException;
import com.clean.dd.shared.keycloak.KeycloakAdminClient;
import com.clean.dd.user.dto.AgentResponse;
import com.clean.dd.user.dto.CreateAgentRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Création des agents. Pas de @Transactional ici : l'appel HTTP à Keycloak ne doit pas tenir une
 * transaction ouverte. Chaque accès à la base a sa propre transaction (repository / UserService).
 */
@Service
public class AgentService {

	private final UserRepository users;
	private final UserService userService;
	private final KeycloakAdminClient keycloak;

	public AgentService(UserRepository users, UserService userService, KeycloakAdminClient keycloak) {
		this.users = users;
		this.userService = userService;
		this.keycloak = keycloak;
	}

	/** Crée le compte Keycloak de l'agent puis sa fiche, rattachée au client du token (jamais du formulaire). */
	public AgentResponse create(CreateAgentRequest req, Jwt jwt) {
		// L'entreprise vient du customer connecté, JAMAIS du formulaire
		Customer customer = userService.currentCustomer(jwt);
		String email = req.email().trim().toLowerCase(Locale.ROOT);
		if (users.existsByEmailIgnoreCase(email)) {
			throw new EmailAlreadyUsedException(email);
		}
		String keycloakId = keycloak.createAgent(req.firstName(), req.lastName(), email);
		try {
			User agent = User.create(customer, keycloakId, email,
					req.firstName().trim() + " " + req.lastName().trim(), req.phone(), Role.AGENT);
			return AgentResponse.from(users.saveAndFlush(agent));
		} catch (RuntimeException e) {
			keycloak.deleteUser(keycloakId); // la base a échoué : on annule aussi côté Keycloak
			throw e;
		}
	}
}
