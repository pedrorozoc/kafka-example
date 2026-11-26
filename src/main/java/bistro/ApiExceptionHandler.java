package bistro;

import bistro.reservations.ReservationNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> invalidFields(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getAllErrors().getFirst().getDefaultMessage();
        return problem(HttpStatus.BAD_REQUEST, "Solicitud inválida", detail);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> unreadableBody() {
        return problem(HttpStatus.BAD_REQUEST, "Solicitud inválida",
                "Se requiere un cuerpo JSON válido con los tipos y formatos esperados.");
    }

    @ExceptionHandler(ReservationNotFoundException.class)
    ResponseEntity<ProblemDetail> reservationNotFound(ReservationNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Reserva no encontrada", exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> technicalFailure(Exception exception) {
        if (exception instanceof ErrorResponse error && error.getStatusCode().is4xxClientError()) {
            return problem(error.getStatusCode(), "Error en la solicitud", "La solicitud no se pudo procesar.");
        }
        LOGGER.error("Ocurrió un fallo técnico al procesar la solicitud.", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Error del sistema", "No se pudo completar la operación.");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatusCode status, String title, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(title);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }
}
