package co.lacava.gestion.shared.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import co.lacava.gestion.support.IntegrationTest;

/** LCV-HU-00: /health responde 200 sin token y con la BD arriba; el contrato queda en OpenAPI. */
class HealthControllerIntegrationTest extends IntegrationTest {

    @Test
    void healthIsPublicAndReportsDatabaseUp() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.db").value("UP"));
    }

    @Test
    void openApiDocumentIncludesHealth() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/health'].get").exists());
    }
}
