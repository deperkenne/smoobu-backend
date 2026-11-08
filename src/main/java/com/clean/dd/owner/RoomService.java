package com.clean.dd.owner;

import com.clean.dd.common.ConflictException;
import com.clean.dd.common.NotFoundException;
import com.clean.dd.owner.dto.CreateRoomRequest;
import com.clean.dd.owner.dto.RoomResponse;
import com.clean.dd.shared.security.Caller;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class RoomService {

	private final PropertyRepository propertyRepository;
	private final RoomRepository roomRepository;

	public RoomService(PropertyRepository propertyRepository, RoomRepository roomRepository) {
		this.propertyRepository = propertyRepository;
		this.roomRepository = roomRepository;
	}

	@Transactional
	public RoomResponse add(Caller caller, UUID propertyId, CreateRoomRequest request) {
		return create(findProperty(caller, propertyId), request);
	}

	/** Appelé aussi par PropertyService : rejoint sa transaction. */
	@Transactional
	public RoomResponse create(Property property, CreateRoomRequest request) {
		String number = request.number().strip();
		if (roomRepository.existsByPropertyIdAndNumber(property.getId(), number)) {
			throw new ConflictException("La chambre '" + number + "' existe déjà dans le logement " + property.getName());
		}
		return RoomResponse.from(roomRepository.save(Room.create(property, number, request.price())));
	}

	@Transactional(readOnly = true)
	public List<RoomResponse> listByProperty(Caller caller, UUID propertyId) {
		findProperty(caller, propertyId);
		return roomRepository.findByPropertyIdOrderByNumber(propertyId).stream()
				.map(RoomResponse::from)
				.toList();
	}

	private Property findProperty(Caller caller, UUID propertyId) {
		return propertyRepository.findById(propertyId)
				.filter(p -> caller.canAccess(p.getOwner().getCustomer()))
				.orElseThrow(() -> new NotFoundException("Logement introuvable : " + propertyId));
	}
}
