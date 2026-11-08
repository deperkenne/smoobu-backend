package com.clean.dd.owner;

import com.clean.dd.common.NotFoundException;
import com.clean.dd.customer.Customer;
import com.clean.dd.customer.CustomerIdentity;
import com.clean.dd.customer.CustomerService;
import com.clean.dd.owner.dto.CreateOwnerRequest;
import com.clean.dd.owner.dto.OwnerResponse;
import com.clean.dd.owner.dto.PropertyResponse;
import com.clean.dd.shared.security.Caller;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OwnerService {

	private final OwnerRepository ownerRepository;
	private final PropertyRepository propertyRepository;
	private final PropertyService propertyService;
	private final CustomerService customerService;

	public OwnerService(OwnerRepository ownerRepository, PropertyRepository propertyRepository,
						PropertyService propertyService, CustomerService customerService) {
		this.ownerRepository = ownerRepository;
		this.propertyRepository = propertyRepository;
		this.propertyService = propertyService;
		this.customerService = customerService;
	}

	/**
	 * Crée le propriétaire et ses logements dans une seule transaction, rattaché au client du token
	 * (créé à la volée s'il n'existe pas encore).
	 */
	@Transactional
	public OwnerResponse create(CustomerIdentity identity, CreateOwnerRequest request) {
		Customer customer = customerService.getOrCreate(identity);
		Owner owner = ownerRepository.save(
				Owner.create(customer, request.name(), request.email(), request.phone()));
		List<PropertyResponse> properties = request.properties().stream()
				.map(p -> propertyService.create(owner, p))
				.toList();
		return OwnerResponse.from(owner, properties);
	}

	@Transactional(readOnly = true)
	public List<OwnerResponse> list() {
		return withProperties(ownerRepository.findAll(Sort.by("createdAt")));
	}

	/** Propriétaires du client connecté (customer du token). */
	@Transactional(readOnly = true)
	public List<OwnerResponse> listMine(Caller caller) {
		return withProperties(ownerRepository.findByCustomer(caller.customerId()));
	}

	private List<OwnerResponse> withProperties(List<Owner> owners) {
		Map<UUID, List<PropertyResponse>> propertiesByOwner = propertyRepository
				.findByOwnerIdInOrderByName(owners.stream().map(Owner::getId).toList()).stream()
				.map(PropertyResponse::from)
				.collect(Collectors.groupingBy(PropertyResponse::ownerId));
		return owners.stream()
				.map(o -> OwnerResponse.from(o, propertiesByOwner.getOrDefault(o.getId(), List.of())))
				.toList();
	}

	@Transactional(readOnly = true)
	public OwnerResponse get(Caller caller, UUID id) {
		Owner owner = ownerRepository.findById(id)
				.filter(o -> caller.canAccess(o.getCustomer()))
				.orElseThrow(() -> new NotFoundException("Propriétaire introuvable : " + id));
		List<PropertyResponse> properties = propertyRepository.findByOwnerIdOrderByName(id).stream()
				.map(PropertyResponse::from)
				.toList();
		return OwnerResponse.from(owner, properties);
	}
}
