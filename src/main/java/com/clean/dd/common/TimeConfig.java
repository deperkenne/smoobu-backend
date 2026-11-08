package com.clean.dd.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/** Horloge de l'application, à l'heure de Nürnberg (remplaçable dans les tests). */
@Configuration
public class TimeConfig {

	@Bean
	public Clock clock() {
		return Clock.system(ZoneId.of("Europe/Berlin"));
	}
}
