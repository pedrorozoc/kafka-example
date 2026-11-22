package bistro.reservations;

import bistro.reservations.controller.CreateReservationController;
import bistro.reservations.controller.CreatedReservationMapper;
import bistro.reservations.service.CreateReservationService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CreateReservationController.class)
class CreateReservationValidationMvcTest {
    @Autowired
    MockMvc mvc;

    @MockitoBean
    CreateReservationService service;

    @MockitoBean
    CreatedReservationMapper mapper;

    @ParameterizedTest
    @CsvSource({
            "2030-06-12T19:00, 13, La cantidad de comensales no puede superar las 12 personas.",
            "2030-06-12T19:00, 0, La cantidad de comensales debe ser un entero positivo.",
            "2030-06-12T19:00, 1.5, 'Se requiere un cuerpo JSON válido con los tipos y formatos esperados.'",
            "2030-06-12T19:00:00, 4, 'Se requiere un cuerpo JSON válido con los tipos y formatos esperados.'"
    })
    void rejectsInvalidInputBeforeCallingCreationService(String time, String size, String detail) throws Exception {
        mvc.perform(post(ReservationTestDatabase.PATH)
                        .contentType(APPLICATION_JSON)
                        .content(ReservationTestDatabase.request(time, size)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Solicitud inválida"))
                .andExpect(jsonPath("$.detail").value(detail));

        verifyNoInteractions(service);
    }
}
