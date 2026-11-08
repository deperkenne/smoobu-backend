package com.clean.dd.invoice;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

	@EntityGraph(attributePaths = "owner")
	Optional<Invoice> findByOwnerIdAndBillingMonth(UUID ownerId, String billingMonth);

	@EntityGraph(attributePaths = "owner")
	List<Invoice> findByOwnerIdOrderByBillingMonthDesc(UUID ownerId);

	@EntityGraph(attributePaths = "owner")
	Optional<Invoice> findWithOwnerById(Long id);
}
