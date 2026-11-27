package co.lacava.gestion.tables.application;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import co.lacava.gestion.shared.security.QrProperties;

/**
 * Tokens y PIN de las mesas (D12, D35). Token = Base64URL sin relleno de HMAC-SHA256(QR_SECRET, "número:nonce"),
 * con un nonce aleatorio de 16 bytes que cambia al regenerar; código del QR = {@code LACAVA:MESA:<número>:<token>}.
 * Ni el token ni el PIN se registran en logs.
 */
@Service
public class TableTokenService {

    static final String CODE_PREFIX = "LACAVA:MESA:";
    static final int NONCE_BYTES = 16;
    private static final String HMAC = "HmacSHA256";
    private static final Pattern CODE = Pattern.compile("^LACAVA:MESA:([1-9][0-9]{0,8}):([A-Za-z0-9_-]{1,64})$");
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private final SecretKeySpec key;
    private final SecureRandom random;

    @Autowired
    public TableTokenService(QrProperties properties) {
        this(properties, new SecureRandom());
    }

    TableTokenService(QrProperties properties, SecureRandom random) {
        this.key = new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), HMAC);
        this.random = random;
    }

    /** Token nuevo para la mesa, con un nonce aleatorio de 16 bytes. */
    public String newToken(int number) {
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        return token(number, nonce);
    }

    /** Token determinista: HMAC-SHA256 de {@code "<número>:<nonce en Base64URL>"}. */
    String token(int number, byte[] nonce) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(key);
            String message = number + ":" + BASE64_URL.encodeToString(nonce);
            return BASE64_URL.encodeToString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo calcular el token de la mesa.", e);
        }
    }

    /** PIN de 4 dígitos (0000–9999, con ceros a la izquierda). */
    public String newPin() {
        return String.format("%04d", random.nextInt(10_000));
    }

    /** Contenido del QR: {@code LACAVA:MESA:<número>:<token>}. */
    public String code(int number, String token) {
        return CODE_PREFIX + number + ":" + token;
    }

    /** Número y token de un código bien formado; vacío si el formato no es válido. */
    public Optional<ParsedCode> parse(String code) {
        if (code == null) {
            return Optional.empty();
        }
        Matcher matcher = CODE.matcher(code.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(new ParsedCode(Integer.parseInt(matcher.group(1)), matcher.group(2)));
    }

    /** Comparación en tiempo constante (token o PIN). */
    public boolean matches(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    /** Partes de un código de QR. {@code toString} no expone el token. */
    public record ParsedCode(int number, String token) {

        @Override
        public String toString() {
            return "ParsedCode[number=" + number + "]";
        }
    }
}
