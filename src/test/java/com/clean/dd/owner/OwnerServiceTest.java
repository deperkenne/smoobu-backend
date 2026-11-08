package com.clean.dd.owner;

import com.clean.dd.common.NotFoundException;
import com.clean.dd.customer.Customer;
import com.clean.dd.customer.CustomerIdentity;
import com.clean.dd.customer.CustomerService;
import com.clean.dd.owner.dto.CreateOwnerRequest;
import com.clean.dd.owner.dto.CreatePropertyRequest;
import com.clean.dd.owner.dto.OwnerResponse;
import com.clean.dd.owner.dto.PropertyResponse;
import com.clean.dd.shared.security.Caller;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OwnerServiceTest {

	@Mock
	private OwnerRepository ownerRepository;
	@Mock
	private PropertyRepository propertyRepository;
	@Mock
	private PropertyService propertyService;
	@Mock
	private CustomerService customerService;

	@InjectMocks
	private OwnerService ownerService;

	private static final CustomerIdentity IDENTITY = new CustomerIdentity("sub-1", "c1@example.com", "c1");
	private static final Customer CUSTOMER = Customer.register("sub-1", "c1@example.com", "c1");
	private static final Customer OTHER_CUSTOMER = Customer.register("sub-2", "c2@example.com", "c2");
	private static final Caller ADMIN = new Caller("admin-sub", true);
	private static final Caller CUSTOMER_CALLER = new Caller("sub-1", false);
	private static final Caller OTHER_CALLER = new Caller("sub-2", false);

	private static Owner owner(UUID id, String name) {
		return owner(CUSTOMER, id, name);
	}

	private static Owner owner(Customer customer, UUID id, String name) {
		Owner owner = Owner.create(customer, name, "mail@example.com", "0911");
		ReflectionTestUtils.setField(owner, "id", id);
		return owner;
	}

	private static PropertyResponse propertyResponse(Owner owner, String name) {
		return new PropertyResponse(UUID.randomUUID(), name, name, null, null, null,
				owner.getId(), owner.getCustomerId(), owner.getName(), null, null);
	}

	@Test
	void create_savesOwnerWithoutProperties() {
		UUID id = UUID.randomUUID();
		when(customerService.getOrCreate(IDENTITY)).thenReturn(CUSTOMER);
		when(ownerRepository.save(any(Owner.class))).thenAnswer(inv -> {
			Owner saved = inv.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", id);
			return saved;
		});

		OwnerResponse response = ownerService.create(IDENTITY,
				new CreateOwnerRequest("Max Muster", "max@example.com", "0911", null));

		assertThat(response.id()).isEqualTo(id);
		assertThat(response.customerId()).isEqualTo("sub-1");
		assertThat(response.name()).isEqualTo("Max Muster");
		assertThat(response.email()).isEqualTo("max@example.com");
		assertThat(response.properties()).isEmpty();
		verifyNoInteractions(propertyService);
		ArgumentCaptor<Owner> saved = ArgumentCaptor.forClass(Owner.class);
		verify(ownerRepository).save(saved.capture());
		assertThat(saved.getValue().getCustomer()).isSameAs(CUSTOMER);
	}

	@Test
	void create_createsEachPropertyForSavedOwner() {
		CreatePropertyRequest p1 = new CreatePropertyRequest("Loft", "Loft Nürnberg", null, null, null, null, null);
		CreatePropertyRequest p2 = new CreatePropertyRequest("Studio", "Studio Fürth", null, null, null, null, null);
		when(customerService.getOrCreate(IDENTITY)).thenReturn(CUSTOMER);
		when(ownerRepository.save(any(Owner.class))).thenAnswer(inv -> {
			Owner saved = inv.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
			return saved;
		});
		when(propertyService.create(any(Owner.class), eq(p1)))
				.thenAnswer(inv -> propertyResponse(inv.getArgument(0), "Loft"));
		when(propertyService.create(any(Owner.class), eq(p2)))
				.thenAnswer(inv -> propertyResponse(inv.getArgument(0), "Studio"));

		OwnerResponse response = ownerService.create(IDENTITY,
				new CreateOwnerRequest("Anna", null, null, List.of(p1, p2)));

		assertThat(response.properties()).extracting(PropertyResponse::name).containsExactly("Loft", "Studio");
		assertThat(response.properties()).allSatisfy(p -> assertThat(p.ownerId()).isEqualTo(response.id()));
	}

	@Test
	void create_resolvesCustomerBeforeSavingOwner_andSavedOwnerCarriesIt() {
		when(customerService.getOrCreate(IDENTITY)).thenReturn(CUSTOMER);
		when(ownerRepository.save(any(Owner.class))).thenAnswer(inv -> inv.getArgument(0));

		ownerService.create(IDENTITY, new CreateOwnerRequest("Max", null, null, null));

		InOrder order = inOrder(customerService, ownerRepository);
		order.verify(customerService).getOrCreate(IDENTITY);
		ArgumentCaptor<Owner> saved = ArgumentCaptor.forClass(Owner.class);
		order.verify(ownerRepository).save(saved.capture());
		assertThat(saved.getValue().getCustomer()).isSameAs(CUSTOMER);
		assertThat(saved.getValue().getCustomerId()).isEqualTo("sub-1");
	}

	@Test
	void list_groupsPropertiesByOwner() {
		Owner a = owner(UUID.randomUUID(), "A");
		Owner b = owner(OTHER_CUSTOMER, UUID.randomUUID(), "B");
		Property pa = Property.create(a, "Loft", "Loft", null, null, null);
		when(ownerRepository.findAll(any(Sort.class))).thenReturn(List.of(a, b));
		when(propertyRepository.findByOwnerIdInOrderByName(anyList())).thenReturn(List.of(pa));

		List<OwnerResponse> result = ownerService.list();

		assertThat(result).extracting(OwnerResponse::customerId).containsExactly("sub-1", "sub-2");
		assertThat(result.get(0).properties()).extracting(PropertyResponse::name).containsExactly("Loft");
		assertThat(result.get(1).properties()).isEmpty();
		verify(ownerRepository, never()).findByCustomer(any());
	}

	@Test
	void listMine_returnsOnlyOwnersOfTheCaller() {
		Owner a = owner(UUID.randomUUID(), "A");
		when(ownerRepository.findByCustomer("sub-1")).thenReturn(List.of(a));
		when(propertyRepository.findByOwnerIdInOrderByName(anyList())).thenReturn(List.of());

		List<OwnerResponse> result = ownerService.listMine(CUSTOMER_CALLER);

		assertThat(result).extracting(OwnerResponse::customerId).containsExactly("sub-1");
		verify(ownerRepository, never()).findAll(any(Sort.class));
	}

	@Test
	void get_returnsOwnerWithProperties() {
		UUID id = UUID.randomUUID();
		Owner o = owner(id, "A");
		when(ownerRepository.findById(id)).thenReturn(Optional.of(o));
		when(propertyRepository.findByOwnerIdOrderByName(id))
				.thenReturn(List.of(Property.create(o, "Loft", "Loft", "Str 1", "90402", "Nürnberg")));

		OwnerResponse response = ownerService.get(ADMIN, id);

		assertThat(response.id()).isEqualTo(id);
		assertThat(response.properties()).singleElement()
				.satisfies(p -> {
					assertThat(p.city()).isEqualTo("Nürnberg");
					assertThat(p.ownerCustomerId()).isEqualTo("sub-1");
				});
	}

	@Test
	void get_throwsNotFoundWhenOwnerMissing() {
		UUID id = UUID.randomUUID();
		when(ownerRepository.findById(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> ownerService.get(ADMIN, id))
				.isInstanceOf(NotFoundException.class)
				.hasMessageContaining(id.toString());
	}

	@Test
	void get_asOwningCustomer_returnsOwner() {
		UUID id = UUID.randomUUID();
		when(ownerRepository.findById(id)).thenReturn(Optional.of(owner(id, "A")));
		when(propertyRepository.findByOwnerIdOrderByName(id)).thenReturn(List.of());

		assertThat(ownerService.get(CUSTOMER_CALLER, id).id()).isEqualTo(id);
	}

	@Test
	void get_asOtherCustomer_throwsNotFound() {
		UUID id = UUID.randomUUID();
		when(ownerRepository.findById(id)).thenReturn(Optional.of(owner(OTHER_CUSTOMER, id, "A")));

		assertThatThrownBy(() -> ownerService.get(CUSTOMER_CALLER, id))
				.isInstanceOf(NotFoundException.class)
				.hasMessageContaining(id.toString());
		verify(propertyRepository, never()).findByOwnerIdOrderByName(any());
	}

	@Test
	void get_asCustomer_ownerWithoutAccount_throwsNotFound() {
		UUID id = UUID.randomUUID();
		Owner legacy = owner(id, "Legacy");
		ReflectionTestUtils.setField(legacy, "customer", null);
		when(ownerRepository.findById(id)).thenReturn(Optional.of(legacy));

		assertThatThrownBy(() -> ownerService.get(OTHER_CALLER, id))
				.isInstanceOf(NotFoundException.class);
	}
}
