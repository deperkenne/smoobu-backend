package com.clean.dd.owner;

import com.clean.dd.common.ConflictException;
import com.clean.dd.common.NotFoundException;
import com.clean.dd.customer.Customer;
import com.clean.dd.owner.dto.CreatePropertyRequest;
import com.clean.dd.owner.dto.CreateRoomRequest;
import com.clean.dd.owner.dto.PropertyResponse;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropertyServiceTest {

	@Mock
	private OwnerRepository ownerRepository;
	@Mock
	private PropertyRepository propertyRepository;
	@Mock
	private RoomService roomService;

	@InjectMocks
	private PropertyService propertyService;

	private static final Caller ADMIN = new Caller("admin-sub", true);
	private static final Caller OWNING_CUSTOMER = new Caller("sub-1", false);
	private static final Caller OTHER_CUSTOMER = new Caller("sub-2", false);

	private UUID ownerId;
	private Owner owner;

	@BeforeEach
	void setUp() {
		ownerId = UUID.randomUUID();
		owner = Owner.create(Customer.register("sub-1", "c1@example.com", "c1"), "Max Muster", null, null);
		ReflectionTestUtils.setField(owner, "id", ownerId);
	}

	private static CreatePropertyRequest request(String emailLabel) {
		return request(emailLabel, null);
	}

	private static CreatePropertyRequest request(String emailLabel, List<CreateRoomRequest> rooms) {
		return new CreatePropertyRequest("Loft", emailLabel, "Hauptstr. 1", "90402", "Nürnberg", rooms, null);
	}

	private void saveReturnsWithId(UUID id) {
		when(propertyRepository.save(any(Property.class))).thenAnswer(inv -> {
			Property saved = inv.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", id);
			return saved;
		});
	}

	@Test
	void create_savesPropertyWithStrippedEmailLabel() {
		UUID propertyId = UUID.randomUUID();
		when(propertyRepository.existsByEmailLabel("Loft Nürnberg")).thenReturn(false);
		saveReturnsWithId(propertyId);

		PropertyResponse response = propertyService.create(owner, request("  Loft Nürnberg "));

		assertThat(response.id()).isEqualTo(propertyId);
		assertThat(response.emailLabel()).isEqualTo("Loft Nürnberg");
		assertThat(response.name()).isEqualTo("Loft");
		assertThat(response.street()).isEqualTo("Hauptstr. 1");
		assertThat(response.postalCode()).isEqualTo("90402");
		assertThat(response.city()).isEqualTo("Nürnberg");
		assertThat(response.ownerId()).isEqualTo(ownerId);
		assertThat(response.ownerCustomerId()).isEqualTo("sub-1");
		assertThat(response.ownerName()).isEqualTo("Max Muster");
		verifyNoInteractions(roomService);
	}

	@Test
	void create_createsEachRoomForSavedProperty() {
		CreateRoomRequest r1 = new CreateRoomRequest("101", new BigDecimal("45.00"));
		CreateRoomRequest r2 = new CreateRoomRequest("2B", new BigDecimal("55.50"));
		when(propertyRepository.existsByEmailLabel("Loft Nürnberg")).thenReturn(false);
		saveReturnsWithId(UUID.randomUUID());

		PropertyResponse response = propertyService.create(owner, request("Loft Nürnberg", List.of(r1, r2)));

		verify(roomService, times(2)).create(any(Property.class), any(CreateRoomRequest.class));
		verify(roomService).create(any(Property.class), eq(r1));
		verify(roomService).create(any(Property.class), eq(r2));
		assertThat(response.emailLabel()).isEqualTo("Loft Nürnberg");
	}

	@Test
	void create_throwsConflictWhenEmailLabelAlreadyExists() {
		when(propertyRepository.existsByEmailLabel("Loft Nürnberg")).thenReturn(true);

		assertThatThrownBy(() -> propertyService.create(owner, request("Loft Nürnberg")))
				.isInstanceOf(ConflictException.class)
				.hasMessageContaining("Loft Nürnberg");
		verify(propertyRepository, never()).save(any());
		verifyNoInteractions(roomService);
	}

	@Test
	void add_createsPropertyForExistingOwner() {
		when(ownerRepository.findById(ownerId)).thenReturn(Optional.of(owner));
		when(propertyRepository.existsByEmailLabel("Loft Nürnberg")).thenReturn(false);
		saveReturnsWithId(UUID.randomUUID());

		PropertyResponse response = propertyService.add(ADMIN, ownerId, request("Loft Nürnberg"));

		assertThat(response.ownerId()).isEqualTo(ownerId);
	}

	@Test
	void add_throwsNotFoundWhenOwnerMissing() {
		when(ownerRepository.findById(ownerId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> propertyService.add(ADMIN, ownerId, request("Loft")))
				.isInstanceOf(NotFoundException.class)
				.hasMessageContaining(ownerId.toString());
		verify(propertyRepository, never()).save(any());
	}

	@Test
	void listByOwner_returnsProperties() {
		when(ownerRepository.findById(ownerId)).thenReturn(Optional.of(owner));
		when(propertyRepository.findByOwnerIdOrderByName(ownerId)).thenReturn(List.of(
				Property.create(owner, "A", "A", null, null, null),
				Property.create(owner, "B", "B", null, null, null)));

		assertThat(propertyService.listByOwner(ADMIN, ownerId))
				.extracting(PropertyResponse::name).containsExactly("A", "B");
	}

	@Test
	void listByOwner_throwsNotFoundWhenOwnerMissing() {
		when(ownerRepository.findById(ownerId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> propertyService.listByOwner(ADMIN, ownerId))
				.isInstanceOf(NotFoundException.class);
		verify(propertyRepository, never()).findByOwnerIdOrderByName(any());
	}

	@Test
	void get_returnsProperty() {
		UUID id = UUID.randomUUID();
		Property property = Property.create(owner, "Loft", "Loft", null, null, null);
		ReflectionTestUtils.setField(property, "id", id);
		when(propertyRepository.findById(id)).thenReturn(Optional.of(property));

		assertThat(propertyService.get(ADMIN, id).id()).isEqualTo(id);
	}

	@Test
	void get_throwsNotFoundWhenPropertyMissing() {
		UUID id = UUID.randomUUID();
		when(propertyRepository.findById(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> propertyService.get(ADMIN, id))
				.isInstanceOf(NotFoundException.class)
				.hasMessageContaining(id.toString());
	}

	@Test
	void add_asOwningCustomer_createsProperty() {
		when(ownerRepository.findById(ownerId)).thenReturn(Optional.of(owner));
		when(propertyRepository.existsByEmailLabel("Loft Nürnberg")).thenReturn(false);
		saveReturnsWithId(UUID.randomUUID());

		assertThat(propertyService.add(OWNING_CUSTOMER, ownerId, request("Loft Nürnberg")).ownerId())
				.isEqualTo(ownerId);
	}

	@Test
	void add_asOtherCustomer_throwsNotFound() {
		when(ownerRepository.findById(ownerId)).thenReturn(Optional.of(owner));

		assertThatThrownBy(() -> propertyService.add(OTHER_CUSTOMER, ownerId, request("Loft")))
				.isInstanceOf(NotFoundException.class)
				.hasMessageContaining(ownerId.toString());
		verify(propertyRepository, never()).save(any());
	}

	@Test
	void listByOwner_asOtherCustomer_throwsNotFound() {
		when(ownerRepository.findById(ownerId)).thenReturn(Optional.of(owner));

		assertThatThrownBy(() -> propertyService.listByOwner(OTHER_CUSTOMER, ownerId))
				.isInstanceOf(NotFoundException.class);
		verify(propertyRepository, never()).findByOwnerIdOrderByName(any());
	}

	@Test
	void get_asOwningCustomer_returnsProperty() {
		UUID id = UUID.randomUUID();
		Property property = Property.create(owner, "Loft", "Loft", null, null, null);
		ReflectionTestUtils.setField(property, "id", id);
		when(propertyRepository.findById(id)).thenReturn(Optional.of(property));

		assertThat(propertyService.get(OWNING_CUSTOMER, id).id()).isEqualTo(id);
	}

	@Test
	void get_asOtherCustomer_throwsNotFound() {
		UUID id = UUID.randomUUID();
		when(propertyRepository.findById(id))
				.thenReturn(Optional.of(Property.create(owner, "Loft", "Loft", null, null, null)));

		assertThatThrownBy(() -> propertyService.get(OTHER_CUSTOMER, id))
				.isInstanceOf(NotFoundException.class)
				.hasMessageContaining(id.toString());
	}
}
