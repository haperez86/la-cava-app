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
    /** Hash de relleno: un usuario inexistente tarda lo mismo que una contraseña errada. */
    private final String dummyHash;

    public AuthService(AppUserRepository users, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.dummyHash = passwordEncoder.encode("la-cava-dummy-password");
    }

    /**
     * Valida las credenciales. Usuario inexistente, contraseña errada o usuario inactivo responden
     * igual: 401 "Usuario o contraseña incorrectos" (docs/01).
     */
    @Transactional(readOnly = true)
    public LoginResult login(String username, String password) {
        AppUser user = users.findByUsername(UserService.normalizeUsername(username)).orElse(null);
        if (user == null) {
            passwordEncoder.matches(password, dummyHash);
            throw BusinessException.unauthorized(INVALID_CREDENTIALS);
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash()) || !user.isActive()) {
            throw BusinessException.unauthorized(INVALID_CREDENTIALS);
        }
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
