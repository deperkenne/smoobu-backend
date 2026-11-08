package com.clean.dd.owner;

import com.clean.dd.common.ConflictException;
import com.clean.dd.common.NotFoundException;
import com.clean.dd.customer.Customer;
import com.clean.dd.owner.dto.CreateRoomRequest;
import com.clean.dd.owner.dto.RoomResponse;
import com.clean.dd.shared.security.Caller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

	@Mock
	private PropertyRepository propertyRepository;
	@Mock
	private RoomRepository roomRepository;

	@InjectMocks
	private RoomService roomService;

	private static final Caller ADMIN = new Caller("admin-sub", true);
	private static final Caller OWNING_CUSTOMER = new Caller("sub-1", false);
	private static final Caller OTHER_CUSTOMER = new Caller("sub-2", false);

	private UUID propertyId;
	private Property property;

	@BeforeEach
	void setUp() {
		Owner owner = Owner.create(Customer.register("sub-1", "c1@example.com", "c1"), "Max Muster", null, null);
		ReflectionTestUtils.setField(owner, "id", UUID.randomUUID());
		propertyId = UUID.randomUUID();
		property = Property.create(owner, "Loft", "Loft Nürnberg", null, null, null);
		ReflectionTestUtils.setField(property, "id", propertyId);
	}

	private void saveReturnsWithId(UUID id) {
		when(roomRepository.save(any(Room.class))).thenAnswer(inv -> {
			Room saved = inv.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", id);
			return saved;
		});
	}

	@Test
	void add_createsRoomForExistingProperty() {
		UUID roomId = UUID.randomUUID();
		when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));
		when(roomRepository.existsByPropertyIdAndNumber(propertyId, "101")).thenReturn(false);
		saveReturnsWithId(roomId);

		RoomResponse response = roomService.add(ADMIN, propertyId, new CreateRoomRequest(" 101 ", new BigDecimal("45.00")));

		assertThat(response.id()).isEqualTo(roomId);
		assertThat(response.propertyId()).isEqualTo(propertyId);
		assertThat(response.number()).isEqualTo("101");
		assertThat(response.price()).isEqualByComparingTo("45.00");
	}

	@Test
	void add_throwsNotFoundWhenPropertyMissing() {
		when(propertyRepository.findById(propertyId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> roomService.add(ADMIN, propertyId, new CreateRoomRequest("101", BigDecimal.TEN)))
				.isInstanceOf(NotFoundException.class)
				.hasMessageContaining(propertyId.toString());
		verifyNoInteractions(roomRepository);
	}

	@Test
	void create_throwsConflictWhenNumberAlreadyUsedInProperty() {
		when(roomRepository.existsByPropertyIdAndNumber(propertyId, "2B")).thenReturn(true);

		assertThatThrownBy(() -> roomService.create(property, new CreateRoomRequest("2B", BigDecimal.TEN)))
				.isInstanceOf(ConflictException.class)
				.hasMessageContaining("2B");
		verify(roomRepository, never()).save(any());
	}

	@Test
	void listByProperty_returnsRooms() {
		when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));
		when(roomRepository.findByPropertyIdOrderByNumber(propertyId)).thenReturn(List.of(
				Room.create(property, "101", new BigDecimal("45.00")),
				Room.create(property, "2B", new BigDecimal("55.50"))));

		assertThat(roomService.listByProperty(ADMIN, propertyId))
				.extracting(RoomResponse::number, RoomResponse::propertyId)
				.containsExactly(
						tuple("101", propertyId),
						tuple("2B", propertyId));
	}

	@Test
	void listByProperty_throwsNotFoundWhenPropertyMissing() {
		when(propertyRepository.findById(propertyId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> roomService.listByProperty(ADMIN, propertyId))
				.isInstanceOf(NotFoundException.class)
				.hasMessageContaining(propertyId.toString());
		verifyNoInteractions(roomRepository);
	}

	@Test
	void add_asOwningCustomer_createsRoom() {
		when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));
		when(roomRepository.existsByPropertyIdAndNumber(propertyId, "101")).thenReturn(false);
		saveReturnsWithId(UUID.randomUUID());

		assertThat(roomService.add(OWNING_CUSTOMER, propertyId, new CreateRoomRequest("101", BigDecimal.TEN)).number())
				.isEqualTo("101");
	}

	@Test
	void add_asOtherCustomer_throwsNotFound() {
		when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));

		assertThatThrownBy(() -> roomService.add(OTHER_CUSTOMER, propertyId, new CreateRoomRequest("101", BigDecimal.TEN)))
				.isInstanceOf(NotFoundException.class)
				.hasMessageContaining(propertyId.toString());
		verifyNoInteractions(roomRepository);
	}

	@Test
	void listByProperty_asOtherCustomer_throwsNotFound() {
		when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));

		assertThatThrownBy(() -> roomService.listByProperty(OTHER_CUSTOMER, propertyId))
				.isInstanceOf(NotFoundException.class);
		verifyNoInteractions(roomRepository);
	}
}
