package com.clean.dd.reservation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Réservation d'un logement (ex. notification Smoobu / Booking.com).
 */


@Entity
@Table(name = "reservation")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nom du logement, ex. "Loft am Pegnitztal Nürnberg". */
    @NotBlank
    @Column(name = "property_name", nullable = false)
    private String propertyName;

    /** Anreise. */
    @NotNull
    @Column(name = "check_in", nullable = false)
    private LocalDate checkIn;

    /** Abreise. */
    @NotNull
    @Column(name = "check_out", nullable = false)
    private LocalDate checkOut;

    /** Gast. */
    @NotBlank
    @Column(name = "guest_name", nullable = false)
    private String guestName;

    /** Buchungsnummer, ex. "6041336634". */
    @Column(name = "booking_number", unique = true)
    private String bookingNumber;

    /** Gastnachricht (texte libre, ex. note de pré-paiement). */
    @Size(max = 2000)
    @Column(name = "guest_message", length = 2000)
    private String guestMessage;


    protected Reservation() {
        // requis par JPA
    }

    public Long getId() { return id; }

    public String getPropertyName() { return propertyName; }
    public void setPropertyName(String propertyName) { this.propertyName = propertyName; }

    public LocalDate getCheckIn() { return checkIn; }
    public void setCheckIn(LocalDate checkIn) { this.checkIn = checkIn; }

    public LocalDate getCheckOut() { return checkOut; }
    public void setCheckOut(LocalDate checkOut) { this.checkOut = checkOut; }

    public String getGuestName() { return guestName; }
    public void setGuestName(String guestName) { this.guestName = guestName; }

    public String getBookingNumber() { return bookingNumber; }
    public void setBookingNumber(String bookingNumber) { this.bookingNumber = bookingNumber; }

    public String getGuestMessage() { return guestMessage; }
    public void setGuestMessage(String guestMessage) { this.guestMessage = guestMessage; }

}
