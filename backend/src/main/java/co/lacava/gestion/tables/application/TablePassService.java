package co.lacava.gestion.tables.application;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import co.lacava.gestion.shared.security.QrProperties;
import co.lacava.gestion.tables.domain.DiningTable;

/**
 * Comprobante de mesa (D37): prueba de vida corta de que un usuario resolvió la mesa (por QR o por número + PIN).
 * Formato {@code LCP.<tableId>.<userId>.<expira epoch s>.<firma>}, con firma Base64URL de HMAC-SHA256 sobre
 * {@code tableId:userId:expira:token_rotated_at (epoch ms)}. La clave se deriva de {@code QR_SECRET}
 * (HMAC(QR_SECRET, "table-pass-v1")), así es distinta de la que firma los QR. Vence a las 4 h y deja de servir si se
 * regenera el QR (cambia {@code token_rotated_at}) o se desactiva la mesa. Nunca se registra en logs.
 */
@Service
public class TablePassService {

    public static final Duration TTL = Duration.ofHours(4);
    static final String PREFIX = "LCP.";
    private static final String HMAC = "HmacSHA256";
    private static final String KEY_CONTEXT = "table-pass-v1";
    private static final Pattern PASS = Pattern
            .compile("^LCP\\.([1-9][0-9]{0,18})\\.([1-9][0-9]{0,18})\\.([1-9][0-9]{0,18})\\.([A-Za-z0-9_-]{43})$");
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private final SecretKeySpec key;
    private final Clock clock;

    public TablePassService(QrProperties properties, Clock clock) {
        byte[] secret = properties.secret().getBytes(StandardCharsets.UTF_8);
        this.key = new SecretKeySpec(hmac(new SecretKeySpec(secret, HMAC), KEY_CONTEXT), HMAC);
        this.clock = clock;
    }

    /** Emite el comprobante de {@code table} para {@code userId}, vigente {@link #TTL} desde ahora. */
    public TablePass issue(DiningTable table, long userId) {
        Instant expiresAt = clock.instant().plus(TTL);
        long exp = expiresAt.getEpochSecond();
        String signature = sign(table.getId(), userId, exp, table.getTokenRotatedAt());
        return new TablePass(PREFIX + table.getId() + "." + userId + "." + exp + "." + signature,
                Instant.ofEpochSecond(exp));
    }

    /**
     * {@code true} si {@code pass} está bien formado, es de esa mesa y de ese usuario, no ha vencido, la mesa sigue
     * activa y su QR no se ha regenerado desde la emisión. La firma se compara en tiempo constante.
     */
    public boolean verify(String pass, DiningTable table, long userId) {
        if (pass == null || table == null || table.getId() == null || !table.isActive()) {
            return false;
        }
        Matcher matcher = PASS.matcher(pass);
        if (!matcher.matches()) {
            return false;
        }
        long passTable;
        long passUser;
        long exp;
        try {
            passTable = Long.parseLong(matcher.group(1));
            passUser = Long.parseLong(matcher.group(2));
            exp = Long.parseLong(matcher.group(3));
        } catch (NumberFormatException e) {
            return false;
        }
        String expected = sign(passTable, passUser, exp, table.getTokenRotatedAt());
        boolean signatureOk = MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                matcher.group(4).getBytes(StandardCharsets.US_ASCII));
        boolean sameTable = passTable == table.getId();
        boolean sameUser = passUser == userId;
        boolean current = clock.instant().getEpochSecond() < exp;
        return signatureOk & sameTable & sameUser & current;
    }

    private String sign(long tableId, long userId, long exp, Instant tokenRotatedAt) {
        String message = tableId + ":" + userId + ":" + exp + ":" + tokenRotatedAt.toEpochMilli();
        return BASE64_URL.encodeToString(hmac(key, message));
    }

    private static byte[] hmac(SecretKeySpec key, String message) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(key);
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo firmar el comprobante de mesa.", e);
        }
    }

    /** Comprobante emitido y su vencimiento. {@code toString} no expone el valor. */
    public record TablePass(String value, Instant expiresAt) {

        @Override
        public String toString() {
            return "TablePass[expiresAt=" + expiresAt + "]";
        }
    }
}
