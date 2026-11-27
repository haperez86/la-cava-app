package co.lacava.gestion.shared.error;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import co.lacava.gestion.support.IntegrationTest;

/** LCV-HU-00: manejo global de errores con ProblemDetail en español. */
class ProblemDetailIntegrationTest extends IntegrationTest {

    private static final String BASE = "/api/v1/test/errors";

    @Test
    @WithMockUser
    void beanValidationReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post(BASE + "/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\", \"quantity\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Datos inválidos"))
                .andExpect(jsonPath("$.detail").value(Problems.VALIDATION))
                .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("name", "quantity")))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')].message").value("El nombre es obligatorio."));
    }

    @Test
    @WithMockUser
    void malformedBodyReturns400() throws Exception {
        mockMvc.perform(post(BASE + "/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(Problems.UNREADABLE));
    }

    @Test
    @WithMockUser
    void notFoundReturns404WithBusinessMessage() throws Exception {
        mockMvc.perform(get(BASE + "/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("No encontrado"))
                .andExpect(jsonPath("$.detail").value("No existe el producto 99."));
    }

    @Test
    @WithMockUser
    void unknownRouteReturns404InSpanish() throws Exception {
        mockMvc.perform(get("/api/v1/no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(Problems.NOT_FOUND));
    }

    @Test
    @WithMockUser
    void conflictReturns409() throws Exception {
        mockMvc.perform(get(BASE + "/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflicto"))
                .andExpect(jsonPath("$.detail").value("Ya hay un turno de caja abierto."));
    }

    @Test
    @WithMockUser
    void businessRuleReturns422() throws Exception {
        mockMvc.perform(get(BASE + "/rule"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Regla de negocio"))
                .andExpect(jsonPath("$.detail").value("No hay existencias suficientes."));
    }

    @Test
    @WithMockUser
    void concurrentUpdateAndIntegrityViolationReturn409() throws Exception {
        mockMvc.perform(get(BASE + "/optimistic-lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(Problems.CONCURRENT_UPDATE));
        mockMvc.perform(get(BASE + "/integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(Problems.DATA_CONFLICT));
    }

    @Test
    @WithMockUser
    void unexpectedErrorReturns500WithoutTechnicalDetails() throws Exception {
        mockMvc.perform(get(BASE + "/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value(Problems.INTERNAL))
                .andExpect(content().string(not(containsString("fallo interno"))));
    }

    @Test
    void missingTokenReturns401ProblemDetail() throws Exception {
        mockMvc.perform(get(BASE + "/conflict"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("No autenticado"))
                .andExpect(jsonPath("$.detail").value(Problems.UNAUTHENTICATED));
    }
}
