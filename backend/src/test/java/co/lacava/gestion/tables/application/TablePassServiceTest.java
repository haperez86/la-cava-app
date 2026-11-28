package co.lacava.gestion.tables.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import co.lacava.gestion.shared.security.QrProperties;
import co.lacava.gestion.support.MutableTestClock;
import co.lacava.gestion.tables.domain.DiningTable;

/** D37: comprobante de mesa firmado y de vida corta. */
class TablePassServiceTest {

    private static final String SECRET = "unit-test-secret-for-qr-tokens-0123456789";
    private static final Instant NOW = Instant.parse("2026-03-10T04:00:00Z");
    private static final long USER = 7;

    private final MutableTestClock clock = new MutableTestClock();
    private final TablePassService service = new TablePassService(new QrProperties(SECRET), clock);
    private DiningTable table;

    @BeforeEach
    void setUp() {
        clock.fixAt(NOW);
        table = table(3L, 3, NOW.minusSeconds(3600));
    }

    @Test
    @DisplayName("D37 issue da LCP.<mesa>.<usuario>.<exp epoch s>.<firma Base64URL>, vence a las 4 h y verify lo acepta")
    void issueAndVerify() {
        TablePassService.TablePass pass = service.issue(table, USER);

        Instant expires = NOW.plus(Duration.ofHours(4));
        assertThat(pass.expiresAt()).isEqualTo(expires);
        assertThat(pass.value()).matches("LCP\\.3\\.7\\." + expires.getEpochSecond() + "\\.[A-Za-z0-9_-]{43}");
        assertThat(pass.toString()).doesNotContain(pass.value());
        assertThat(service.verify(pass.value(), table, USER)).isTrue();
    }

    @Test
    @DisplayName("D37 verify falla con otra mesa, otro usuario, vencido, mesa inactiva o QR regenerado")
    void verifyRejects() {
        String pass = service.issue(table, USER).value();

        assertThat(service.verify(pass, table(4L, 4, table.getTokenRotatedAt()), USER)).as("otra mesa").isFalse();
        assertThat(service.verify(pass, table, 8)).as("otro usuario").isFalse();

        clock.fixAt(NOW.plus(Duration.ofHours(4)));
        assertThat(service.verify(pass, table, USER)).as("vencido justo a las 4 h").isFalse();
        clock.fixAt(NOW.plus(Duration.ofHours(4)).minusSeconds(1));
        assertThat(service.verify(pass, table, USER)).as("un segundo antes").isTrue();

        table.rotate("otro-token", "1234", NOW.plusSeconds(5));
        assertThat(service.verify(pass, table, USER)).as("QR regenerado").isFalse();

        DiningTable inactive = table(3L, 3, NOW.minusSeconds(3600));
        String forInactive = service.issue(inactive, USER).value();
        inactive.setActive(false);
        assertThat(service.verify(forInactive, inactive, USER)).as("mesa inactiva").isFalse();
    }

    @Test
    @DisplayName("D37 verify falla si el pass está alterado, mal formado, es nulo o se firmó con otro secreto")
    void verifyRejectsTampered() {
        String pass = service.issue(table, USER).value();
        String[] parts = pass.split("\\.");
        String otherExpiry = String.join(".", parts[0], parts[1], parts[2],
                Long.toString(Long.parseLong(parts[3]) + 60), parts[4]);
        String otherSecret = new TablePassService(new QrProperties(SECRET + "-otro"), clock).issue(table, USER)
                .value();

        for (String bad : new String[] { otherExpiry, otherSecret, pass + "x", "LCP.3.7", "", null,
                "LCP.99999999999999999999.7.1." + parts[4], pass.replace("LCP.", "LCX.") }) {
            assertThat(service.verify(bad, table, USER)).as(String.valueOf(bad)).isFalse();
        }
    }

    @Test
    @DisplayName("D37 la clave se deriva de QR_SECRET y no es la del QR: firmar con el secreto crudo no sirve")
    void derivedKeyDiffersFromQrKey() throws Exception {
        String pass = service.issue(table, USER).value();
        String[] parts = pass.split("\\.");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String message = parts[1] + ":" + parts[2] + ":" + parts[3] + ":" + table.getTokenRotatedAt().toEpochMilli();
        String rawSigned = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));

        assertThat(rawSigned).isNotEqualTo(parts[4]);
        assertThat(service.verify(String.join(".", parts[0], parts[1], parts[2], parts[3], rawSigned), table, USER))
                .isFalse();
    }

    private static DiningTable table(long id, int number, Instant rotatedAt) {
        DiningTable table = new DiningTable(number, "token-" + number, "4821", rotatedAt);
        ReflectionTestUtils.setField(table, "id", id);
        return table;
    }
}
