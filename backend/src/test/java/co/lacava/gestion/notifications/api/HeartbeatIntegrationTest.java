package co.lacava.gestion.notifications.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import co.lacava.gestion.support.IntegrationTest;
import co.lacava.gestion.users.application.UserService;
import co.lacava.gestion.users.domain.Role;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;

/** LCV-HU-13 CA2: el servidor envía {@code heartbeat} periódico (aquí cada 200 ms en lugar de 25 s). */
@TestPropertySource(properties = "app.sse.heartbeat-ms=200")
class HeartbeatIntegrationTest extends IntegrationTest {

    @Autowired
    private UserService users;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    @DisplayName("CA2 el stream recibe eventos heartbeat periódicos")
    void ca2StreamReceivesHeartbeats() throws Exception {
        users.create("laura", "Laura Gómez", "laura123", Role.WAITER);
        String login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", "laura", "password", "laura123"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String jwt = json.readTree(login).get("token").asString();
        String ticketResponse = mockMvc.perform(post("/api/v1/notifications/stream-ticket")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String ticket = json.readTree(ticketResponse).get("ticket").asString();

        MvcResult stream = mockMvc.perform(get("/api/v1/notifications/stream").param("ticket", ticket)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted()).andReturn();

        await().atMost(Duration.ofSeconds(5)).until(
                () -> stream.getResponse().getContentAsString().split("event:heartbeat", -1).length >= 3);
        assertThat(stream.getResponse().getContentAsString()).contains("event:heartbeat");
    }
}
