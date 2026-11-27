package co.lacava.gestion.users.application;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Locale;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.lacava.gestion.shared.error.BusinessException;
import co.lacava.gestion.users.domain.AppUser;
import co.lacava.gestion.users.domain.Role;
import co.lacava.gestion.users.infrastructure.AppUserRepository;

/** Usuarios del sistema. */
@Service
public class UserService {

    public static final int MAX_USERNAME = 40;
    public static final int MAX_FULL_NAME = 80;
    public static final int MIN_PASSWORD = 8;
    /** BCrypt solo usa los primeros 72 bytes: una contraseña más larga se rechaza en vez de truncarse en silencio. */
    public static final int MAX_PASSWORD = 72;

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UserService(AppUserRepository users, PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /** Normaliza el nombre de usuario: sin espacios y en minúsculas. */
    public static String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional(readOnly = true)
    public Optional<AppUser> findByUsername(String username) {
        return users.findByUsername(normalizeUsername(username));
    }

    /** Crea un usuario activo con la contraseña cifrada con BCrypt. 409 si el usuario ya existe. */
    @Transactional
    public AppUser create(String username, String fullName, String rawPassword, Role role) {
        String normalized = normalizeUsername(username);
        if (normalized.isEmpty() || normalized.length() > MAX_USERNAME || !normalized.matches("[a-z0-9._-]+")) {
            throw BusinessException.rule("El usuario solo admite letras sin tilde, números, punto, guion y guion bajo, "
                    + "sin espacios (máximo " + MAX_USERNAME + ").");
        }
        String name = fullName == null ? "" : fullName.trim();
        if (name.isEmpty() || name.length() > MAX_FULL_NAME) {
            throw BusinessException.rule("El nombre completo es obligatorio (máximo " + MAX_FULL_NAME + " caracteres).");
        }
        if (role == null) {
            throw BusinessException.rule("Selecciona el perfil.");
        }
        checkPassword(rawPassword);
        if (users.existsByUsername(normalized)) {
            throw BusinessException.conflict("Ya existe un usuario con el nombre \"" + normalized + "\".");
        }
        return users.save(new AppUser(normalized, name, passwordEncoder.encode(rawPassword), role, clock.instant()));
    }

    private static void checkPassword(String raw) {
        if (raw == null || raw.length() < MIN_PASSWORD || raw.length() > MAX_PASSWORD) {
            throw BusinessException.rule("La contraseña debe tener entre " + MIN_PASSWORD + " y " + MAX_PASSWORD
                    + " caracteres.");
        }
        if (raw.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD) {
            throw BusinessException.rule("La contraseña es demasiado larga: con tildes o ñ, usa una más corta.");
        }
    }
}
