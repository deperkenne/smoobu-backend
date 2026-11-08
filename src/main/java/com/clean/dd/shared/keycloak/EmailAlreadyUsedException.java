package com.clean.dd.shared.keycloak;

import com.clean.dd.common.ConflictException;

/** E-mail déjà utilisé par un compte Keycloak (→ 409 via le handler des ConflictException). */
public class EmailAlreadyUsedException extends ConflictException {

	public EmailAlreadyUsedException(String email) {
		super("Un compte existe déjà avec l'e-mail " + email);
	}
}
