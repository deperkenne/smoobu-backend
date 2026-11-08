package com.clean.dd.reservation;

import com.clean.dd.customer.Customer;
import com.clean.dd.owner.Property;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Réservation d'une chambre d'un logement (ex. notification Smoobu / Booking.com).
 */
@Entity
@Table(name = "reservation")
public class Reservation {

    /** Statut Smoobu d'une réservation annulée. */
    public static final String CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nom du logement, ex. "Loft am Pegnitztal Nürnberg". */
    @Column(name = "property_name", nullable = false)
    private String propertyName;

    /** Prix de la chambre copié au moment de la réservation ; vide si la chambre n'est pas connue. */
    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    /** Anreise. */
    @Column(name = "check_in", nullable = false)
    private LocalDate checkIn;

    /** Jour d'arrivée tel qu'écrit dans l'e-mail Smoobu, ex. "Montag". */
    @Column(name = "arrival_day", length = 20)
    private String arrivalDay;

    /** Abreise. */
    @Column(name = "check_out", nullable = false)
    private LocalDate checkOut;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    /** Customer propriétaire du logement, copié du owner. Nullable pour les logements sans customer. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    /** Numéro de la chambre réservée, ou "unique" si l'e-mail Smoobu n'en donne pas. */
    @Column(name = "room_number", length = 20)
    private String roomNumber;

    /** Gast (facultatif, non alimenté par la simulation Smoobu). */
    @Column(name = "guest_name")
    private String guestName;

    /** Buchungsnummer, ex. "6041336634". */
    @Column(name = "booking_number", nullable = false, unique = true)
    private String bookingNumber;

    /** Gastnachricht (texte libre, ex. note de pré-paiement). */
    @Column(name = "guest_message", length = 2000)
    private String guestMessage;

    /** Canal de réservation, ex. "Booking.com", "Airbnb". */
    @Column(length = 50)
    private String channel;

    @Column(name = "status_reservation")
    private String status;

    /** Date de la première annulation ; vide si la réservation n'est pas annulée. */
    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    protected Reservation() {
        // requis par JPA
    }

    public static Reservation create(Property property, String roomNumber, BigDecimal price, String bookingNumber,
                                     LocalDate checkIn, String arrivalDay, LocalDate checkOut,
                                     String channel, String status) {
        Reservation r = new Reservation();
        r.bookingNumber = requireText(bookingNumber, "bookingNumber");
        r.update(property, roomNumber, price, checkIn, arrivalDay, checkOut, channel, status);
        return r;
    }

    public void update(Property property, String roomNumber, BigDecimal price, LocalDate checkIn, String arrivalDay,
                       LocalDate checkOut, String channel, String status) {
        Objects.requireNonNull(checkIn, "checkIn est obligatoire");
        Objects.requireNonNull(checkOut, "checkOut est obligatoire");
        if (!checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("La date de départ doit être après la date d'arrivée");
        }
        this.property = Objects.requireNonNull(property, "property est obligatoire");
        this.propertyName = property.getName();
        this.customer = property.getOwner().getCustomer();
        this.roomNumber = requireText(roomNumber, "roomNumber");
        this.price = price;
        this.checkIn = checkIn;
        this.arrivalDay = arrivalDay;
        this.checkOut = checkOut;
        this.channel = channel;
        this.status = status;
        this.cancelledAt = null;
    }

    /** Annule la réservation : seul le statut change ; la date de la première annulation est conservée. */
    public void cancel() {
        this.status = CANCELLED;
        if (this.cancelledAt == null) {
            this.cancelledAt = Instant.now();
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " est obligatoire");
        }
        return value.strip();
    }

    public Long getId() { return id; }
    public Property getProperty() { return property; }
    /** Id du customer (sub du token) ; ne charge pas le customer. */
    public String getCustomerId() { return customer == null ? null : customer.getId(); }
    public String getRoomNumber() { return roomNumber; }
    public String getPropertyName() { return propertyName; }
    public BigDecimal getPrice() { return price; }
    public LocalDate getCheckIn() { return checkIn; }
    public String getArrivalDay() { return arrivalDay; }
    public LocalDate getCheckOut() { return checkOut; }
    public String getGuestName() { return guestName; }
    public String getBookingNumber() { return bookingNumber; }
    public String getGuestMessage() { return guestMessage; }
    public String getChannel() { return channel; }
    public String getStatus() { return status; }
    public Instant getCancelledAt() { return cancelledAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Reservation other)) return false;
        return id != null && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
