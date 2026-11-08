package com.clean.dd.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Resource server OAuth2 : chaque requête doit porter un JWT Keycloak valide.
 * Les rôles sont contrôlés endpoint par endpoint avec @PreAuthorize.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

	@Value("${app.security.keycloak.client-id}")
	private String keycloakClientId;

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.cors(cors -> cors.configurationSource(corsConfigurationSource()))
			.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(session ->
				session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
				.requestMatchers("/error").permitAll()
				// Routes publiques (sans jeton)
				.requestMatchers(HttpMethod.POST, "/api/reservations/simulate").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/reservations").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/customers").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/owners").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/owners/mine").hasRole("CUSTOMER")
				.requestMatchers(HttpMethod.DELETE, "/api/admin/reset").permitAll()
				.anyRequest().authenticated()
			)
			.oauth2ResourceServer(oauth2 -> oauth2
				.jwt(jwt -> jwt
					.jwtAuthenticationConverter(keycloakJwtAuthenticationConverter())
				)
			);

		return http.build();
	}

	@Bean
	public KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter() {
		return new KeycloakJwtAuthenticationConverter(keycloakClientId);
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(List.of(
			"http://localhost:3000",
			"http://localhost:5173",
			"http://localhost:5174"
		));
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of(
			"Authorization",
			"Content-Type",
			"X-Requested-With",
			"Accept"
		));
		config.setAllowCredentials(true);
		config.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}
}
