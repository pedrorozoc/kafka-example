package bistro.reservations;

public class ReservationNotFoundException extends RuntimeException {
    public ReservationNotFoundException() {
        super("No existe una reserva con ese código.");
    }
}
