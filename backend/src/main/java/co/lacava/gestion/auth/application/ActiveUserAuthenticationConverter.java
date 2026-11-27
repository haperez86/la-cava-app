package co.lacava.gestion.auth.application;

import java.util.List;
import java.util.Optional;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import co.lacava.gestion.users.domain.AppUser;
import co.lacava.gestion.users.infrastructure.AppUserRepository;

/**
 * Convierte un JWT válido en la autenticación de la petición. En cada petición comprueba que el usuario
 * siga existiendo y activo, y toma el rol de la BD, no del token. Si no, la petición recibe 401.
 */
@Component
public class ActiveUserAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final AppUserRepository users;

    public ActiveUserAuthenticationConverter(AppUserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public AbstractAuthenticationToken convert(Jwt jwt) {
        AppUser user = parseId(jwt.getSubject())
                .flatMap(users::findById)
                .filter(AppUser::isActive)
                .orElseThrow(() -> new InvalidBearerTokenException("Usuario inexistente o inactivo"));
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        return new JwtAuthenticationToken(jwt, authorities, user.getUsername());
    }

    private static Optional<Long> parseId(String subject) {
        if (subject == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.valueOf(subject));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
