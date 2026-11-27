package co.lacava.gestion.shared.api;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Estado del servicio y de la base de datos. Público. */
@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Sistema")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    @SecurityRequirements
    @Operation(summary = "Estado del servicio", description = "200 si la base de datos responde; 503 si no.")
    public ResponseEntity<HealthResponse> health() {
        boolean dbUp = databaseUp();
        HealthResponse body = dbUp
                ? new HealthResponse(HealthResponse.UP, HealthResponse.UP)
                : new HealthResponse(HealthResponse.DOWN, HealthResponse.DOWN);
        return ResponseEntity.status(dbUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    private boolean databaseUp() {
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return one != null && one == 1;
        } catch (DataAccessException ex) {
            return false;
        }
    }

    public record HealthResponse(String status, String db) {
        static final String UP = "UP";
        static final String DOWN = "DOWN";
    }
}
