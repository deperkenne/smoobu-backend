package com.clean.dd.owner;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PropertyRepository extends JpaRepository<Property, UUID> {

	/** Logements d'un propriétaire, avec propriétaire et agent chargés en une seule requête (fetch join). */
	@Query("""
			select p from Property p
			join fetch p.owner o
			left join fetch p.user
			where o.id = :ownerId
			order by p.name
			""")
	List<Property> findByOwnerIdOrderByName(@Param("ownerId") UUID ownerId);

	/** Logements de plusieurs propriétaires en une seule requête (fetch join) : pas de N+1 au chargement des owners. */
	@Query("""
			select p from Property p
			join fetch p.owner o
			left join fetch p.user
			where o.id in :ownerIds
			order by p.name
			""")
	List<Property> findByOwnerIdInOrderByName(@Param("ownerIds") Collection<UUID> ownerIds);

	/** Tous les logements du customer (via leur propriétaire), avec propriétaire et agent en une seule requête. */
	@Query("""
			select p from Property p
			join fetch p.owner o
			left join fetch p.user
			where o.customer.id = :customerId
			order by o.name, p.name
			""")
	List<Property> findByCustomer(@Param("customerId") String customerId);

	/** Logements attribués à un user (agent), avec propriétaire et agent en une seule requête. */
	@Query("""
			select p from Property p
			join fetch p.owner
			join fetch p.user u
			where u.id = :userId
			order by p.name
			""")
	List<Property> findByUserIdOrderByName(@Param("userId") UUID userId);

	boolean existsByEmailLabel(String emailLabel);

	Optional<Property> findByEmailLabel(String emailLabel);
}
