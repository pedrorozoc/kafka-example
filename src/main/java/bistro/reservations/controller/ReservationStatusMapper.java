package bistro.reservations.controller;

import bistro.reservations.model.Reservation;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ReservationStatusMapper {
    ReservationStatusResponse toResponse(Reservation reservation);
}
