package com.clean.dd.user;

import com.clean.dd.common.NotFoundException;
import com.clean.dd.customer.Customer;
import com.clean.dd.customer.CustomerService;
import com.clean.dd.shared.keycloak.KeycloakAdminClient;
import com.clean.dd.shared.security.AuthenticatedCustomer;
import com.clean.dd.user.dto.AgentResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Utilisateurs D&D Clean. Un customer ne voit et ne gère que ses propres agents (customer = jeton). */
@Service
public class UserService {

	private final UserRepository users;
	private final KeycloakAdminClient keycloak;
	private final CustomerService customerService;

	public UserService(UserRepository users, KeycloakAdminClient keycloak, CustomerService customerService) {
		this.users = users;
		this.keycloak = keycloak;
		this.customerService = customerService;
	}

	/** L'utilisateur D&D Clean qui correspond au token reçu. */
	@Transactional(readOnly = true)
	public User currentUser(Jwt jwt) {
		User user = users.findByKeycloakId(jwt.getSubject())
				.orElseThrow(() -> new AccessDeniedException("Compte inconnu dans D&D Clean"));
		if (!user.isActive()) {
			throw new AccessDeniedException("Compte désactivé");
		}
		return user;
	}

	/** L'entreprise (customer) de l'utilisateur connecté : lue dans le jeton, créée à la volée si besoin. */
	@Transactional
	public Customer currentCustomer(Jwt jwt) {
		return customerService.getOrCreate(AuthenticatedCustomer.identityOf(jwt));
	}

	@Transactional(readOnly = true)
	public List<AgentResponse> listAgents(Jwt jwt) {
		return users.findAllByCustomerIdAndRoleOrderByFullNameAsc(AuthenticatedCustomer.idOf(jwt), Role.AGENT).stream()
				.map(AgentResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public AgentResponse getAgent(UUID id, Jwt jwt) {
		return AgentResponse.from(findAgentOfMyCustomer(id, jwt));
	}

	/**
	 * Désactive l'agent dans Keycloak (il ne peut plus se connecter) puis en base.
	 * Pas de @Transactional : l'appel HTTP se fait hors transaction ; Keycloak d'abord, la base ensuite.
	 */
	public void deactivateAgent(UUID id, Jwt jwt) {
		User agent = findAgentOfMyCustomer(id, jwt);
		keycloak.disableUser(agent.getKeycloakId());
		agent.deactivate();
		users.save(agent);
	}

	private User findAgentOfMyCustomer(UUID id, Jwt jwt) {
		return users.findByIdAndCustomerId(id, AuthenticatedCustomer.idOf(jwt))
				.filter(u -> u.getRole() == Role.AGENT)
				.orElseThrow(() -> new NotFoundException("Agent introuvable"));
	}
}
