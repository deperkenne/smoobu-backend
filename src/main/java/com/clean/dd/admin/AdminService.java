package com.clean.dd.admin;

import com.clean.dd.customer.CustomerRepository;
import com.clean.dd.invoice.InvoiceLineRepository;
import com.clean.dd.invoice.InvoiceRepository;
import com.clean.dd.owner.OwnerRepository;
import com.clean.dd.owner.PropertyRepository;
import com.clean.dd.owner.RoomRepository;
import com.clean.dd.reservation.ReservationRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Outil de développement : vide toutes les données. Jamais disponible en profil "prod". */
@Profile("!prod")
@Service
public class AdminService {

	private final InvoiceLineRepository invoiceLineRepository;
	private final InvoiceRepository invoiceRepository;
	private final ReservationRepository reservationRepository;
	private final RoomRepository roomRepository;
	private final PropertyRepository propertyRepository;
	private final OwnerRepository ownerRepository;
	private final CustomerRepository customerRepository;

	public AdminService(InvoiceLineRepository invoiceLineRepository, InvoiceRepository invoiceRepository,
						ReservationRepository reservationRepository, RoomRepository roomRepository,
						PropertyRepository propertyRepository, OwnerRepository ownerRepository,
						CustomerRepository customerRepository) {
		this.invoiceLineRepository = invoiceLineRepository;
		this.invoiceRepository = invoiceRepository;
		this.reservationRepository = reservationRepository;
		this.roomRepository = roomRepository;
		this.propertyRepository = propertyRepository;
		this.ownerRepository = ownerRepository;
		this.customerRepository = customerRepository;
	}

	/** Supprime enfants avant parents (clés étrangères) : lignes de facture, factures, réservations, chambres, logements, propriétaires, clients. */
	@Transactional
	public void reset() {
		invoiceLineRepository.deleteAllInBatch();
		invoiceRepository.deleteAllInBatch();
		reservationRepository.deleteAllInBatch();
		roomRepository.deleteAllInBatch();
		propertyRepository.deleteAllInBatch();
		ownerRepository.deleteAllInBatch();
		customerRepository.deleteAllInBatch();
	}
}
