package com.clean.dd.shared.keycloak;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Accès du back-end à l'API d'administration de Keycloak (compte de service). */
@ConfigurationProperties(prefix = "keycloak.admin")
public record KeycloakAdminProperties(
		String serverUrl,
		String realm,
		String adminClientId,
		String adminClientSecret,
		String rolesClientId,
		String redirectUri) {
}
