package com.clean.dd.shared.keycloak;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/** Crée les comptes des agents dans Keycloak via son API d'administration. */
@Component
@EnableConfigurationProperties(KeycloakAdminProperties.class)
public class KeycloakAdminClient {

	private static final String AGENT_ROLE = "AGENT";

	private RestClient http = RestClient.create();
	private final KeycloakAdminProperties props;

	public KeycloakAdminClient(KeycloakAdminProperties props) {
		this.props = props;
		this.http = RestClient.builder()          // builder statique, sans bean Spring
			.baseUrl(props.serverUrl())
			.build();
	}

	//public KeycloakAdminClient(KeycloakAdminProperties props) {
	//	this.props = props;
	//}

	/** Crée l'agent dans Keycloak et renvoie son id Keycloak (le "sub"). */
	public String createAgent(String firstName, String lastName, String email) {
		String token = adminToken();
		String userId = createUser(token, firstName, lastName, email);
		try {
			assignClientRole(token, userId, AGENT_ROLE);
			sendInvitation(token, userId);
			return userId;
		} catch (RuntimeException e) {
			deleteUser(token, userId); // pas de compte à moitié créé
			throw e;
		}
	}

	// (1) Token admin : le back-end se connecte avec son compte de service
	private String adminToken() {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "client_credentials");
		form.add("client_id", props.adminClientId());
		form.add("client_secret", props.adminClientSecret());
		Map<String, Object> res = http.post()
				.uri(props.serverUrl() + "/realms/{realm}/protocol/openid-connect/token", props.realm())
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.body(new ParameterizedTypeReference<Map<String, Object>>() {});
		return (String) res.get("access_token");
	}

	// (2) Création de l'utilisateur
	private String createUser(String token, String firstName, String lastName, String email) {
		Map<String, Object> user = Map.of(
				"username", email,
				"email", email,
				"firstName", firstName,
				"lastName", lastName,
				"enabled", true,
				"emailVerified", false);
		try {
			ResponseEntity<Void> res = authed(token).post()
					.uri(admin("/users"), props.realm())
					.contentType(MediaType.APPLICATION_JSON)
					.body(user)
					.retrieve()
					.toBodilessEntity();
			// Keycloak renvoie 201 + Location: .../users/{id}
			String path = res.getHeaders().getLocation().getPath();
			return path.substring(path.lastIndexOf('/') + 1);
		} catch (HttpClientErrorException.Conflict e) {
			throw new EmailAlreadyUsedException(email); // -> 409 dans @RestControllerAdvice
		}
	}

	// (3) Rôle du client rolesClientId (ex. order-management-app) attribué à l'utilisateur
	private void assignClientRole(String token, String userId, String roleName) {
		List<Map<String, Object>> clients = authed(token).get()
				.uri(admin("/clients?clientId={clientId}"), props.realm(), props.rolesClientId())
				.retrieve()
				.body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
		if (clients == null || clients.isEmpty()) {
			throw new IllegalStateException("Client Keycloak introuvable : " + props.rolesClientId());
		}
		String clientUuid = (String) clients.getFirst().get("id");
		Map<String, Object> role = authed(token).get()
				.uri(admin("/clients/{client}/roles/{role}"), props.realm(), clientUuid, roleName)
				.retrieve()
				.body(new ParameterizedTypeReference<Map<String, Object>>() {});
		authed(token).post()
				.uri(admin("/users/{user}/role-mappings/clients/{client}"), props.realm(), userId, clientUuid)
				.contentType(MediaType.APPLICATION_JSON)
				.body(List.of(role))
				.retrieve()
				.toBodilessEntity();
	}

	// (4) E-mail d'invitation : l'agent choisit son mot de passe et vérifie son e-mail
	private void sendInvitation(String token, String userId) {
		authed(token).put()
				.uri(admin("/users/{user}/execute-actions-email?client_id={client}&redirect_uri={redirect}"),
						props.realm(), userId, props.rolesClientId(), props.redirectUri())
				.contentType(MediaType.APPLICATION_JSON)
				.body(List.of("UPDATE_PASSWORD", "VERIFY_EMAIL"))
				.retrieve()
				.toBodilessEntity();
	}

	/** Désactive le compte Keycloak : l'utilisateur ne peut plus se connecter. */
	public void disableUser(String userId) {
		authed(adminToken()).put()
				.uri(admin("/users/{user}"), props.realm(), userId)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("enabled", false))
				.retrieve()
				.toBodilessEntity();
	}

	/** Supprime le compte Keycloak (annulation quand l'enregistrement en base échoue). */
	public void deleteUser(String userId) {
		deleteUser(adminToken(), userId);
	}

	private void deleteUser(String token, String userId) {
		authed(token).delete()
				.uri(admin("/users/{user}"), props.realm(), userId)
				.retrieve()
				.toBodilessEntity();
	}

	/** Client qui envoie le jeton admin sur chaque appel. */
	private RestClient authed(String token) {
		return http.mutate().defaultHeaders(h -> h.setBearerAuth(token)).build();
	}


	private String admin(String path) {
		return props.serverUrl() + "/admin/realms/{realm}" + path;
	}


}
