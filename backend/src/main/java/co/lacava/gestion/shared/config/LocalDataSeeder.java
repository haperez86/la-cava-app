package co.lacava.gestion.shared.config;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import co.lacava.gestion.users.application.UserService;
import co.lacava.gestion.users.domain.Role;

/**
 * Datos de ejemplo del perfil {@code local}: usuarios de la demo con contraseña {@code <usuario>123}. Idempotente.
 * Solo siembra si la base de datos está en esta máquina (P2): las claves son predecibles.
 */
@Component
@Profile("local")
class LocalDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalDataSeeder.class);
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");

    private final UserService users;
    private final String datasourceUrl;

    LocalDataSeeder(UserService users, @Value("${spring.datasource.url:}") String datasourceUrl) {
        this.users = users;
        this.datasourceUrl = datasourceUrl;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!isLocalDatabase(datasourceUrl)) {
            log.warn("No se cargan los datos de ejemplo: la base de datos no está en localhost (host: {}). "
                    + "Los usuarios de ejemplo tienen claves predecibles.", hostOf(datasourceUrl));
            return;
        }
        seedUser("admin", "Administrador Demo", Role.ADMIN);
        seedUser("camila", "Camila Torres", Role.CASHIER);
        seedUser("laura", "Laura Gómez", Role.WAITER);
        seedUser("andres", "Andrés Rojas", Role.WAITER);
        seedUser("jorge", "Jorge Ruiz", Role.KITCHEN);
    }

    /** {@code true} solo si la URL JDBC apunta a {@code localhost}, {@code 127.0.0.1} o {@code ::1}. */
    static boolean isLocalDatabase(String jdbcUrl) {
        String host = hostOf(jdbcUrl);
        return host != null && LOCAL_HOSTS.contains(host.toLowerCase(Locale.ROOT));
    }

    /** Host de una URL {@code jdbc:...}, o {@code null} si no se puede extraer (URL malformada o varios hosts). */
    static String hostOf(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:")) {
            return null;
        }
        try {
            return URI.create(jdbcUrl.substring("jdbc:".length())).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void seedUser(String username, String fullName, Role role) {
        if (users.findByUsername(username).isEmpty()) {
            users.create(username, fullName, username + "123", role);
        }
    }
}
