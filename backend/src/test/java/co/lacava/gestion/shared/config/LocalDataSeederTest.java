package co.lacava.gestion.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import co.lacava.gestion.users.application.UserService;

/** P2: los datos de ejemplo solo se siembran contra una base en localhost. */
class LocalDataSeederTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "jdbc:postgresql://localhost:5432/la_cava|true",
            "jdbc:postgresql://LOCALHOST/la_cava|true",
            "jdbc:postgresql://127.0.0.1:5432/la_cava|true",
            "jdbc:postgresql://[::1]:5432/la_cava|true",
            "jdbc:postgresql://db.example.com:5432/la_cava|false",
            "jdbc:postgresql://10.0.0.5:5432/la_cava|false",
            "jdbc:postgresql://localhost.example.com/la_cava|false",
            "jdbc:postgresql://localhost:5432,db.example.com:5432/la_cava|false",
            "jdbc:postgresql://exa mple/la_cava|false",
            "postgresql://localhost/la_cava|false",
            "no es una url|false" })
    @DisplayName("Solo localhost, 127.0.0.1 y ::1 cuentan como base local")
    void detectsLocalDatabase(String url, boolean expected) {
        assertThat(LocalDataSeeder.isLocalDatabase(url)).isEqualTo(expected);
    }

    @Test
    @DisplayName("URL vacía o nula no es local")
    void emptyUrlIsNotLocal() {
        assertThat(LocalDataSeeder.isLocalDatabase(null)).isFalse();
        assertThat(LocalDataSeeder.isLocalDatabase("")).isFalse();
    }

    @Test
    @DisplayName("Con una base remota no siembra usuarios")
    void doesNotSeedRemoteDatabase() {
        UserService users = mock(UserService.class);

        new LocalDataSeeder(users, "jdbc:postgresql://db.example.com:5432/la_cava").run(null);

        verifyNoInteractions(users);
    }

    @Test
    @DisplayName("Con una base en localhost siembra los usuarios de la demo")
    void seedsLocalDatabase() {
        UserService users = mock(UserService.class);
        when(users.findByUsername(anyString())).thenReturn(Optional.empty());

        new LocalDataSeeder(users, "jdbc:postgresql://localhost:5432/la_cava").run(null);

        verify(users, times(5)).create(anyString(), anyString(), anyString(), any());
    }
}
