package bistro.reservations.controller;

import bistro.reservations.service.CreateReservationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping("/api/v1/reservations")
public class CreateReservationController {
    private final CreateReservationService service;
    private final CreatedReservationMapper mapper;

    public CreateReservationController(CreateReservationService service, CreatedReservationMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping(consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<CreatedReservationResponse> create(@Valid @RequestBody CreateReservationRequest request) {
        var reservation = service.create(request.customerName(), request.customerEmail(),
                request.reservationTime(), request.partySize());
        var location = URI.create("/api/v1/reservations/" + reservation.getReservationCode());
        return ResponseEntity.created(location).body(mapper.toResponse(reservation));
    }
}
