package com.clean.dd.owner;

import com.clean.dd.owner.dto.CreateRoomRequest;
import com.clean.dd.owner.dto.RoomResponse;
import com.clean.dd.shared.security.Caller;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** CUSTOMER : chambres des logements de ses propres propriétaires ; ADMIN : toutes. */
@RestController
@RequestMapping("/api/properties/{propertyId}/rooms")
public class RoomController {

	private final RoomService roomService;

	public RoomController(RoomService roomService) {
		this.roomService = roomService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
	public RoomResponse add(JwtAuthenticationToken authentication, @PathVariable UUID propertyId,
							@Valid @RequestBody CreateRoomRequest request) {
		return roomService.add(Caller.of(authentication), propertyId, request);
	}

	@GetMapping
	@PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
	public List<RoomResponse> list(JwtAuthenticationToken authentication, @PathVariable UUID propertyId) {
		return roomService.listByProperty(Caller.of(authentication), propertyId);
	}
}
