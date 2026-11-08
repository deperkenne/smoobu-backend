package com.clean.dd.customer.dto;

import com.clean.dd.customer.Customer;

public record CustomerResponse(
		String id,
		String email,
		String username,
		String companyName,
		String street,
		String postalCode,
		String city,
		String country,
		String vatNumber) {

	public static CustomerResponse from(Customer c) {
		return new CustomerResponse(c.getId(), c.getEmail(), c.getUsername(), c.getCompanyName(),
				c.getStreet(), c.getPostalCode(), c.getCity(), c.getCountry(), c.getVatNumber());
	}
}
