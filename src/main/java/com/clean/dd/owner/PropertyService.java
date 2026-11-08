package com.clean.dd.owner;

import com.clean.dd.common.ConflictException;
import com.clean.dd.common.NotFoundException;
import com.clean.dd.owner.dto.CreatePropertyRequest;
import com.clean.dd.owner.dto.PropertyResponse;
import com.clean.dd.shared.security.Caller;
import com.clean.dd.user.Role;
import com.clean.dd.user.User;
import com.clean.dd.user.UserRepository;
import com.clean.dd.user.UserService;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PropertyService {

	private final OwnerRepository ownerRepository;
	private final PropertyRepository propertyRepository;
	private final RoomService roomService;
	private final UserRepository userRepository;
	private final UserService userService;

	public PropertyService(OwnerRepository ownerRepository, PropertyRepository propertyRepository,
						   RoomService roomService, UserRepository userRepository, UserService userService) {
		this.ownerRepository = ownerRepository;
		this.propertyRepository = propertyRepository;
		this.roomService = roomService;
		this.userRepository = userRepository;
		this.userService = userService;
	}

	@Transactional
	public PropertyResponse add(Caller caller, UUID ownerId, CreatePropertyRequest request) {
		return create(findOwner(caller, ownerId), request);
	}

	/** Crée le logement et ses chambres. Appelé aussi par OwnerService : rejoint sa transaction. */
	@Transactional
	public PropertyResponse create(Owner owner, CreatePropertyRequest request) {
		String emailLabel = request.emailLabel().strip();
		if (propertyRepository.existsByEmailLabel(emailLabel)) {
			throw new ConflictException("Un logement avec l'emailLabel '" + emailLabel + "' existe déjà");
		}
		Property created = Property.create(owner, request.name(), emailLabel,
				request.street(), request.postalCode(), request.city());
		if (request.agentId() != null) {
			created.assignTo(findAgentOf(created, request.agentId()));
		}
		Property property = propertyRepository.save(created);
		request.rooms().forEach(room -> roomService.create(property, room));
		return PropertyResponse.from(property);
	}

	@Transactional(readOnly = true)
	public List<PropertyResponse> listByOwner(Caller caller, UUID ownerId) {
		findOwner(caller, ownerId);
		return propertyRepository.findByOwnerIdOrderByName(ownerId).stream()
				.map(PropertyResponse::from)
				.toList();
	}

	/** Tous les logements du customer connecté (customer du jeton), triés par propriétaire puis par nom. */
	@Transactional(readOnly = true)
	public List<PropertyResponse> listMine(Caller caller) {
		return propertyRepository.findByCustomer(caller.customerId()).stream()
				.map(PropertyResponse::from)
				.toList();
	}

	/**
	 * Logements attribués à l'agent connecté. L'agent est retrouvé par le "sub" du jeton ;
	 * compte inconnu ou désactivé → 403.
	 */
	@Transactional(readOnly = true)
	public List<PropertyResponse> listAssignedToMe(Jwt jwt) {
		User agent = userService.currentUser(jwt);
		return propertyRepository.findByUserIdOrderByName(agent.getId()).stream()
				.map(PropertyResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public PropertyResponse get(Caller caller, UUID id) {
		return propertyRepository.findById(id)
				.filter(p -> caller.canAccess(p.getOwner().getCustomer()))
				.map(PropertyResponse::from)
				.orElseThrow(() -> new NotFoundException("Logement introuvable : " + id));
	}

	/** Attribue un agent à un logement qui n'en a pas encore. */
	@Transactional
	public PropertyResponse assignAgent(Caller caller, UUID propertyId, UUID agentId) {
		Property property = findOwnProperty(caller, propertyId);
		if (property.getUser() != null) {
			throw new ConflictException("Le logement a déjà un agent : utilisez move pour le changer");
		}
		property.assignTo(findAgentOf(property, agentId));
		return PropertyResponse.from(property);
	}

	/** Déplace un logement déjà attribué vers un autre agent. */
	@Transactional
	public PropertyResponse moveToAgent(Caller caller, UUID propertyId, UUID agentId) {
		Property property = findOwnProperty(caller, propertyId);
		if (property.getUser() == null) {
			throw new ConflictException("Le logement n'a pas encore d'agent : utilisez assign");
		}
		property.assignTo(findAgentOf(property, agentId));
		return PropertyResponse.from(property);
	}

	/** Logement du customer connecté (pas de passe-droit ADMIN ici : le customer gère ses propres logements). */
	private Property findOwnProperty(Caller caller, UUID propertyId) {
		return propertyRepository.findById(propertyId)
				.filter(p -> caller.customerId().equals(p.getOwner().getCustomerId()))
				.orElseThrow(() -> new NotFoundException("Logement introuvable : " + propertyId));
	}

	/** Agent actif du même customer que le logement ; sinon 404 sans en dire plus. */
	private User findAgentOf(Property property, UUID agentId) {
		return userRepository.findByIdAndCustomerId(agentId, property.getOwner().getCustomerId())
				.filter(u -> u.getRole() == Role.AGENT && u.isActive())
				.orElseThrow(() -> new NotFoundException("Agent introuvable : " + agentId));
	}

	private Owner findOwner(Caller caller, UUID ownerId) {
		return ownerRepository.findById(ownerId)
				.filter(o -> caller.canAccess(o.getCustomer()))
				.orElseThrow(() -> new NotFoundException("Propriétaire introuvable : " + ownerId));
	}
}
