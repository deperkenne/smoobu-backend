package com.clean.dd.owner;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OwnerRepository extends JpaRepository<Owner, UUID> {

	/** Propriétaires d'un customer (sub du token). */
	@Query("select o from Owner o where o.customer.id = :customerId order by o.name")
	List<Owner> findByCustomer(@Param("customerId") String customerId);
}
