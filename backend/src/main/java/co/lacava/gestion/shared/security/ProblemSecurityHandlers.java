package co.lacava.gestion.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

import co.lacava.gestion.shared.error.BusinessException;
import co.lacava.gestion.shared.error.Problems;

/**
 * Entry point (401) y access-denied handler (403) que delegan en {@code GlobalExceptionHandler}
 * para responder con {@code ProblemDetail} en español.
 */
@Component
class ProblemSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final HandlerExceptionResolver resolver;

    ProblemSecurityHandlers(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) {
        // Sin token → "Debes iniciar sesión"; token vencido, inválido o de un usuario inactivo → "Tu sesión expiró".
        String detail = authException instanceof InsufficientAuthenticationException
                ? Problems.UNAUTHENTICATED
                : Problems.SESSION_EXPIRED;
        resolver.resolveException(request, response, null, BusinessException.unauthorized(detail));
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) {
        resolver.resolveException(request, response, null, accessDeniedException);
    }
}
