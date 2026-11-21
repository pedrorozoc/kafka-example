package bistro.reservations.controller;

import bistro.reservations.model.ReservationStatus;

import java.util.UUID;

public record CreatedReservationResponse(UUID reservationCode, ReservationStatus status) {
}
