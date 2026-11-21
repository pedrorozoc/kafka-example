package bistro.reservations.controller;

import bistro.reservations.service.GetReservationStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping("/api/v1/reservations")
public class GetReservationStatusController {
    private final GetReservationStatusService service;
    private final ReservationStatusMapper mapper;

    public GetReservationStatusController(GetReservationStatusService service, ReservationStatusMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping(path = "/{reservationCode}", produces = APPLICATION_JSON_VALUE)
    public ReservationStatusResponse getStatus(@PathVariable String reservationCode) {
        return mapper.toResponse(service.findByCode(reservationCode));
    }
}
