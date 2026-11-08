package com.clean.dd.customer;

import com.clean.dd.customer.dto.CustomerResponse;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CustomerService {

	private final CustomerRepository customerRepository;

	public CustomerService(CustomerRepository customerRepository) {
		this.customerRepository = customerRepository;
	}

	@Transactional(readOnly = true)
	public List<CustomerResponse> listAll() {
		return customerRepository.findAll(Sort.by("email")).stream()
				.map(CustomerResponse::from)
				.toList();
	}

	/**
	 * Just-In-Time provisioning : renvoie le client du token, en le créant s'il n'existe pas encore,
	 * puis met son profil à jour avec les claims du token (adresse, raison sociale, n° de TVA...).
	 * Rejoint la transaction de l'appelant : client et propriétaire sont validés ensemble.
	 */
	@Transactional
	public Customer getOrCreate(CustomerIdentity identity) {
		Customer customer = customerRepository.findById(identity.id())
			.orElseGet(() -> {
				customerRepository.insertIfAbsent(identity.id(), identity.email(), identity.username());
				return customerRepository.findById(identity.id()).orElseThrow();
			});
		customer.updateProfile(identity);
		return customer;
	}
}
