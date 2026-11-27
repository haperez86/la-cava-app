package co.lacava.gestion.support;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import co.lacava.gestion.auth.application.LoginAttemptService;
import co.lacava.gestion.tables.application.PinAttemptService;

/**
 * Base de las pruebas de integración: contexto completo contra PostgreSQL local
 * ({@code la_cava_test}, D3) y tablas vacías antes de cada prueba .
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    private LoginAttemptService loginAttempts;

    @Autowired
    protected MutableTestClock clock;

    /** Cada prueba arranca con el reloj del sistema; la que necesite una fecha fija llama a {@code clock.fixAt}. */
    @BeforeEach
    void resetClock() {
        clock.useSystemClock();
    }

    @Autowired
    private PinAttemptService pinAttempts;

    /** Los intentos de login y de PIN de mesa viven en memoria del contexto compartido: se limpian entre pruebas. */
    @BeforeEach
    void clearLoginAttempts() {
        loginAttempts.clear();
        pinAttempts.clear();
    }

    @BeforeEach
    void truncateTables() {
        List<String> tables = jdbcTemplate.queryForList("""
                SELECT quote_ident(tablename) FROM pg_tables
                WHERE schemaname = current_schema() AND tablename <> 'flyway_schema_history'
                """, String.class);
        if (!tables.isEmpty()) {
            jdbcTemplate.execute("TRUNCATE TABLE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE");
        }
    }
}
