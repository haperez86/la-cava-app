package co.lacava.gestion.tables.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;

import co.lacava.gestion.shared.error.BusinessException;
import co.lacava.gestion.shared.security.QrProperties;
import co.lacava.gestion.tables.domain.DiningTable;
import co.lacava.gestion.tables.infrastructure.DiningTableRepository;

/** D36: el resolve por número + PIN reserva el intento antes de comparar, como el login (D27). */
@ExtendWith(OutputCaptureExtension.class)
class TableServicePinAttemptsTest {

    private static final Instant NOW = Instant.parse("2026-03-10T12:00:00Z");
    private static final String PIN = "4821";
    private static final long USER_ID = 7;
    private static final String SECRET = "unit-test-secret-for-qr-tokens-0123456789";

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final DiningTableRepository tables = mock(DiningTableRepository.class);

    TableServicePinAttemptsTest() {
        DiningTable table = new DiningTable(3, "token", PIN, NOW);
        ReflectionTestUtils.setField(table, "id", 3L);
        when(tables.findByNumber(3)).thenReturn(Optional.of(table));
    }

    private TableService service(TableTokenService tokens) {
        return new TableService(tables, tokens, clock, new PinAttemptService(clock, 5, Duration.ofMinutes(15)),
                new TablePassService(new QrProperties(SECRET), clock), tableId -> TableAccount.Account.empty());
    }

    @Test
    @DisplayName("D36 una ráfaga de 20 resolve con PIN correcto del mismo mesero: como máximo 5 llegan a comparar sin bloqueo")
    void concurrentBurstWithCorrectPinGetsAtMostFive() throws Exception {
        int threads = 20;
        CountDownLatch compared = new CountDownLatch(threads);
        CountDownLatch release = new CountDownLatch(1);
        // Cada comparación espera a que las 20 peticiones hayan reservado su intento: ningún acierto reinicia el
        // contador antes de tiempo, así se prueba que la reserva es atómica y previa a la comparación.
        TableTokenService blockingTokens = new TableTokenService(new QrProperties(SECRET)) {
            @Override
            public boolean matches(String expected, String actual) {
                compared.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return super.matches(expected, actual);
            }
        };
        TableService service = service(blockingTokens);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    try {
                        service.resolveByPin(3, PIN, USER_ID, "laura");
                        return true;
                    } catch (BusinessException e) {
                        return false;
                    }
                }));
            }
            assertThat(compared.await(5, TimeUnit.SECONDS)).isTrue();
            release.countDown();

            long opened = 0;
            for (Future<Boolean> result : results) {
                if (result.get(5, TimeUnit.SECONDS)) {
                    opened++;
                }
            }
            assertThat(opened).isEqualTo(5);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("D36 al iniciar el bloqueo se registra un aviso con usuario y mesa, nunca el PIN")
    void logsWarningWithUserAndTableButNoPin(CapturedOutput output) {
        TableService service = service(new TableTokenService(new QrProperties(SECRET)));
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> service.resolveByPin(3, "9135", USER_ID, "laura"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(TableService.INVALID_CODE);
        }
        assertThat(output).doesNotContain("Apertura de mesa por PIN bloqueada");

        assertThatThrownBy(() -> service.resolveByPin(3, "9135", USER_ID, "laura"))
                .hasMessage(TableService.INVALID_CODE);

        assertThat(output).contains("WARN").contains("Apertura de mesa por PIN bloqueada")
                .contains("'laura'").contains("id 7").contains("mesa 3")
                .doesNotContain("9135").doesNotContain(PIN);
        assertThatThrownBy(() -> service.resolveByPin(3, PIN, USER_ID, "laura"))
                .as("bloqueado: el PIN correcto también da el 404 genérico")
                .hasMessage(TableService.INVALID_CODE);
    }
}
