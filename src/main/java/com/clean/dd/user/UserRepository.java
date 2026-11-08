package com.clean.dd.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByKeycloakId(String keycloakId);

	Optional<User> findByEmailIgnoreCase(String email);

	boolean existsByEmailIgnoreCase(String email);

	// "CustomerId" = customer.id : Spring Data suit la relation tout seul
	List<User> findAllByCustomerIdAndRoleOrderByFullNameAsc(String customerId, Role role);

	List<User> findAllByRoleOrderByFullNameAsc(Role role);

	Optional<User> findByIdAndCustomerId(UUID id, String customerId);
}
