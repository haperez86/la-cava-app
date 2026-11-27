package co.lacava.gestion.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import co.lacava.gestion.shared.error.BusinessException;
import co.lacava.gestion.users.domain.AppUser;
import co.lacava.gestion.users.domain.Role;
import co.lacava.gestion.users.infrastructure.AppUserRepository;

/** D27 en {@link AuthService}: el intento se reserva antes de comparar la contraseña. */
class AuthServiceLoginAttemptsTest {

    private static final String PASSWORD = "laura123";

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final TokenService tokens = mock(TokenService.class);
    private final LoginAttemptService attempts = new LoginAttemptService(
            Clock.fixed(Instant.parse("2026-03-10T12:00:00Z"), ZoneOffset.UTC), 5, Duration.ofMinutes(15));

    private AuthService authService(PasswordEncoder encoder) {
        AppUser laura = new AppUser("laura", "Laura Gómez", "hash:" + PASSWORD, Role.WAITER, Instant.EPOCH);
        when(users.findByUsername(any())).thenAnswer(inv -> Optional.of(laura));
        when(tokens.issue(any())).thenReturn(new IssuedToken("token", Instant.EPOCH));
        return new AuthService(users, encoder, tokens, attempts);
    }

    @Test
    @DisplayName("Una ráfaga concurrente con la clave correcta solo deja comparar 5 intentos como no bloqueados")
    void concurrentBurstGetsAtMostMaxAttempts() throws Exception {
        int threads = 20;
        // Cada matches() espera a que los 20 hilos estén comparando: todas las reservas ocurren antes de
        // que cualquier resultado (éxito o fallo) se registre.
        CountDownLatch comparing = new CountDownLatch(threads);
        AtomicInteger compared = new AtomicInteger();
        AuthService service = authService(new FakeEncoder(() -> {
            compared.incrementAndGet();
            comparing.countDown();
            try {
                comparing.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    try {
                        service.login("laura", PASSWORD);
                        return true;
                    } catch (BusinessException e) {
                        assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                        assertThat(e.getMessage()).isEqualTo(AuthService.INVALID_CREDENTIALS);
                        return false;
                    }
                }));
            }
            int successes = 0;
            for (Future<Boolean> result : results) {
                if (result.get(15, TimeUnit.SECONDS)) {
                    successes++;
                }
            }

            assertThat(compared).as("siempre se compara la contraseña, también bloqueado").hasValue(threads);
            assertThat(successes).as("solo 5 intentos se comparan como no bloqueados").isEqualTo(5);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Tras 4 fallos, el 5.º intento con la clave correcta entra y reinicia el contador")
    void fifthAttemptWithCorrectPasswordSucceeds() {
        AuthService service = authService(new FakeEncoder(() -> { }));
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> service.login("laura", "errada")).isInstanceOf(BusinessException.class);
        }

        assertThat(service.login("laura", PASSWORD).user().getUsername()).isEqualTo("laura");
        assertThat(attempts.isLocked("laura")).isFalse();
    }

    @Test
    @DisplayName("Tras 5 fallos, incluso la clave correcta responde 401 y se sigue comparando la contraseña")
    void lockedUserGetsSame401() {
        AtomicInteger compared = new AtomicInteger();
        AuthService service = authService(new FakeEncoder(compared::incrementAndGet));
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.login("laura", "errada")).isInstanceOf(BusinessException.class);
        }

        assertThatThrownBy(() -> service.login("laura", PASSWORD))
                .isInstanceOf(BusinessException.class)
                .hasMessage(AuthService.INVALID_CREDENTIALS);
        assertThat(compared).hasValue(6);
    }

    /** Codificador falso: {@code hash:<clave>}; ejecuta {@code onMatch} antes de comparar. */
    private record FakeEncoder(Runnable onMatch) implements PasswordEncoder {

        @Override
        public String encode(CharSequence rawPassword) {
            return "hash:" + rawPassword;
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            onMatch.run();
            return encode(rawPassword).equals(encodedPassword);
        }
    }
}
