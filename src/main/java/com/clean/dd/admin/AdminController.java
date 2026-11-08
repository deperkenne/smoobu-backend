package com.clean.dd.admin;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Outil de développement, public (sans jeton) : jamais disponible en profil "prod". */
@Profile("!prod")
@RestController
@RequestMapping("/api/admin")
public class AdminController {

	private final AdminService adminService;

	public AdminController(AdminService adminService) {
		this.adminService = adminService;
	}

	/** Vide toute la base : factures, réservations, chambres, logements, propriétaires. */
	@DeleteMapping("/reset")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void reset() {
		adminService.reset();
	}
}
