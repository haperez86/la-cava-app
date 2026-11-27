package co.lacava.gestion.auth.application;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.lacava.gestion.shared.error.BusinessException;
import co.lacava.gestion.shared.error.Problems;
import co.lacava.gestion.users.application.UserService;
import co.lacava.gestion.users.domain.AppUser;
import co.lacava.gestion.users.infrastructure.AppUserRepository;

@Service
public class AuthService {

    public static final String INVALID_CREDENTIALS = "Usuario o contraseña incorrectos";

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final LoginAttemptService loginAttempts;
    /** Hash de relleno: un usuario inexistente tarda lo mismo que una contraseña errada. */
    private final String dummyHash;

    public AuthService(AppUserRepository users, PasswordEncoder passwordEncoder, TokenService tokenService,
            LoginAttemptService loginAttempts) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.loginAttempts = loginAttempts;
        this.dummyHash = passwordEncoder.encode("la-cava-dummy-password");
    }

    /**
     * Valida las credenciales. Usuario inexistente, contraseña errada, usuario inactivo o usuario bloqueado por
     * intentos fallidos (P1) responden igual: 401 "Usuario o contraseña incorrectos" (docs/01). Siempre se compara
     * la contraseña (contra el hash real o el de relleno) para no delatar el caso por el tiempo de respuesta.
     * El intento se reserva antes de comparar (D27), así una ráfaga concurrente no supera el máximo de intentos.
     */
    @Transactional(readOnly = true)
    public LoginResult login(String username, String password) {
        String normalized = UserService.normalizeUsername(username);
        LoginAttemptService.Permit permit = loginAttempts.tryAcquire(normalized);
        AppUser user = users.findByUsername(normalized).orElse(null);
        boolean matches = passwordEncoder.matches(password, user == null ? dummyHash : user.getPasswordHash());
        if (!permit.allowed()) {
            throw BusinessException.unauthorized(INVALID_CREDENTIALS);
        }
        if (user == null || !matches || !user.isActive()) {
            loginAttempts.recordFailure(permit);
            throw BusinessException.unauthorized(INVALID_CREDENTIALS);
        }
        loginAttempts.recordSuccess(normalized);
        return new LoginResult(tokenService.issue(user), user);
    }

    @Transactional(readOnly = true)
    public AppUser currentUser(long userId) {
        return users.findById(userId)
                .filter(AppUser::isActive)
                .orElseThrow(() -> BusinessException.unauthorized(Problems.SESSION_EXPIRED));
    }

    public record LoginResult(IssuedToken token, AppUser user) {
    }
}
