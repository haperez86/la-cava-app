package co.lacava.gestion.tables.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import co.lacava.gestion.shared.security.QrProperties;

/** LCV-HU-07: formato del token HMAC, del código del QR y del PIN (D12, D35). */
class TableTokenServiceTest {

    private static final String SECRET = "unit-test-secret-for-qr-tokens-0123456789";

    private final TableTokenService service = new TableTokenService(new QrProperties(SECRET));

    @Test
    @DisplayName("CA1 el token es Base64URL sin relleno de HMAC-SHA256(secreto, \"número:nonce\") y cabe en 64 caracteres")
    void tokenIsBase64UrlHmac() throws Exception {
        byte[] nonce = new byte[TableTokenService.NONCE_BYTES];
        for (int i = 0; i < nonce.length; i++) {
            nonce[i] = (byte) i;
        }

        String token = service.token(7, nonce);

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String message = "7:" + Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
        String expected = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        assertThat(token).isEqualTo(expected).matches("[A-Za-z0-9_-]+").hasSizeLessThanOrEqualTo(64)
                .doesNotContain("=");
    }

    @Test
    @DisplayName("CA1 el mismo número y nonce dan el mismo token; otro número u otro secreto dan uno distinto")
    void tokenIsDeterministicForSameNonce() {
        byte[] nonce = new byte[TableTokenService.NONCE_BYTES];
        TableTokenService otherSecret = new TableTokenService(new QrProperties(SECRET + "-otro"));

        assertThat(service.token(3, nonce)).isEqualTo(service.token(3, nonce.clone()));
        assertThat(service.token(4, nonce)).isNotEqualTo(service.token(3, nonce));
        assertThat(otherSecret.token(3, nonce)).isNotEqualTo(service.token(3, nonce));
    }

    @Test
    @DisplayName("CA3 cada token nuevo de la misma mesa es distinto (nonce aleatorio al regenerar)")
    void newTokensDiffer() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            tokens.add(service.newToken(5));
        }
        assertThat(tokens).hasSize(50);
    }

    @Test
    @DisplayName("CA1 el PIN tiene 4 dígitos, con ceros a la izquierda")
    void pinHasFourDigits() {
        TableTokenService lowRandom = new TableTokenService(new QrProperties(SECRET), new FixedRandom(7));
        assertThat(lowRandom.newPin()).isEqualTo("0007");

        for (int i = 0; i < 200; i++) {
            assertThat(service.newPin()).matches("\\d{4}");
        }
    }

    @Test
    @DisplayName("CA2 el código del QR es LACAVA:MESA:<número>:<token> y se lee de vuelta")
    void codeRoundTrip() {
        String token = service.newToken(12);
        String code = service.code(12, token);

        assertThat(code).isEqualTo("LACAVA:MESA:12:" + token);
        assertThat(service.parse(code)).hasValueSatisfying(parsed -> {
            assertThat(parsed.number()).isEqualTo(12);
            assertThat(parsed.token()).isEqualTo(token);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "LACAVA:MESA:12", "LACAVA:MESA::abc", "LACAVA:MESA:0:abc", "LACAVA:MESA:-1:abc",
            "OTRO:MESA:1:abc", "LACAVA:MESA:1:abc:def", "LACAVA:MESA:x:abc", "LACAVA:MESA:1:a+b/c=",
            "LACAVA:MESA:99999999999:abc" })
    @DisplayName("Un código mal formado no se interpreta")
    void malformedCodes(String code) {
        assertThat(service.parse(code)).isEmpty();
    }

    @Test
    @DisplayName("La comparación de token/PIN solo acepta valores idénticos")
    void matches() {
        assertThat(service.matches("0427", "0427")).isTrue();
        assertThat(service.matches("0427", "0428")).isFalse();
        assertThat(service.matches("0427", "427")).isFalse();
        assertThat(service.matches("0427", null)).isFalse();
    }

    /** {@code SecureRandom} que siempre devuelve el mismo entero. */
    private static final class FixedRandom extends SecureRandom {

        private final int value;

        FixedRandom(int value) {
            this.value = value;
        }

        @Override
        public int nextInt(int bound) {
            return value;
        }
    }
}
