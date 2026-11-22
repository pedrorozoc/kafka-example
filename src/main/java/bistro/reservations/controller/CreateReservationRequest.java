package bistro.reservations.controller;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.time.LocalDateTime;

public record CreateReservationRequest(
        @NotBlank(message = "El nombre del cliente es obligatorio.")
        @JsonDeserialize(using = ReservationRequestJsonConfiguration.StrictString.class)
        String customerName,

        @NotBlank(message = "El correo electrónico es obligatorio.")
        @Email(message = "El correo electrónico debe tener un formato válido.")
        @JsonDeserialize(using = ReservationRequestJsonConfiguration.StrictString.class)
        String customerEmail,

        @NotNull(message = "La fecha y hora de la reserva son obligatorias.")
        @JsonDeserialize(using = ReservationRequestJsonConfiguration.ReservationTime.class)
        LocalDateTime reservationTime,

        @NotNull(message = "La cantidad de comensales es obligatoria.")
        @Positive(message = "La cantidad de comensales debe ser un entero positivo.")
        @Max(value = 12, message = "La cantidad de comensales no puede superar las 12 personas.")
        @JsonDeserialize(using = ReservationRequestJsonConfiguration.PartySize.class)
        Integer partySize
) {
}
