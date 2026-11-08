package com.clean.dd.customer;

import com.clean.dd.customer.dto.CustomerResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Route publique (sans jeton) : tous les customers existants. */
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

	private final CustomerService customerService;

	public CustomerController(CustomerService customerService) {
		this.customerService = customerService;
	}

	@GetMapping
	public List<CustomerResponse> listAll() {
		return customerService.listAll();
	}
}
