package co.lacava.gestion.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import co.lacava.gestion.inventory.application.InventoryService;
import co.lacava.gestion.menu.application.ProductData;
import co.lacava.gestion.menu.application.ProductService;
import co.lacava.gestion.menu.application.ProductView;
import co.lacava.gestion.users.application.UserService;
import co.lacava.gestion.users.domain.AppUser;

/** P2: los datos de ejemplo solo se siembran contra una base en localhost. */
class LocalDataSeederTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "jdbc:postgresql://localhost:5432/la_cava|true",
            "jdbc:postgresql://LOCALHOST/la_cava|true",
            "jdbc:postgresql://127.0.0.1:5432/la_cava|true",
            "jdbc:postgresql://[::1]:5432/la_cava|true",
            "jdbc:postgresql:la_cava|true",
            "jdbc:postgresql:///la_cava|true",
            "jdbc:postgresql:///la_cava?sslmode=disable|true",
            "jdbc:postgresql:la_cava?PGHOST=db.example.com|false",
            "jdbc:postgresql://localhost/la_cava?pghost=db.example.com|false",
            "jdbc:postgresql://db.example.com:5432/la_cava|false",
            "jdbc:postgresql://10.0.0.5:5432/la_cava|false",
            "jdbc:postgresql://localhost.example.com/la_cava|false",
            "jdbc:postgresql://localhost:5432,db.example.com:5432/la_cava|false",
            "jdbc:postgresql://exa mple/la_cava|false",
            "postgresql://localhost/la_cava|false",
            "no es una url|false" })
    @DisplayName("Solo localhost, 127.0.0.1, ::1 o una URL PostgreSQL sin host cuentan como base local")
    void detectsLocalDatabase(String url, boolean expected) {
        assertThat(LocalDataSeeder.isLocalDatabase(url)).isEqualTo(expected);
    }

    @Test
    @DisplayName("URL vacía o nula no es local")
    void emptyUrlIsNotLocal() {
        assertThat(LocalDataSeeder.isLocalDatabase(null)).isFalse();
        assertThat(LocalDataSeeder.isLocalDatabase("")).isFalse();
    }

    private final UserService users = mock(UserService.class);
    private final ProductService products = mock(ProductService.class);
    private final InventoryService inventory = mock(InventoryService.class);

    @Test
    @DisplayName("Con una base remota no siembra usuarios ni productos")
    void doesNotSeedRemoteDatabase() {
        new LocalDataSeeder(users, products, inventory, "jdbc:postgresql://db.example.com:5432/la_cava").run(null);

        verifyNoInteractions(users, products, inventory);
    }

    @Test
    @DisplayName("Con una base en localhost siembra los usuarios y los 11 productos de la demo con sus lotes")
    void seedsLocalDatabase() {
        AppUser admin = mock(AppUser.class);
        when(admin.getId()).thenReturn(1L);
        when(users.findByUsername(anyString())).thenReturn(Optional.empty());
        when(users.findByUsername("admin")).thenReturn(Optional.empty(), Optional.of(admin));
        when(products.findByName(anyString())).thenReturn(Optional.empty());
        when(inventory.today()).thenReturn(LocalDate.of(2026, 11, 27));
        when(products.create(any(), any(), anyLong())).thenAnswer(inv -> view(((ProductData) inv.getArgument(0)).name()));

        new LocalDataSeeder(users, products, inventory, "jdbc:postgresql://localhost:5432/la_cava").run(null);

        verify(users, times(5)).create(anyString(), anyString(), anyString(), any());
        verify(products, times(11)).create(any(), any(), anyLong());
        // 10 productos con un lote, "Postre de la casa" con dos (uno vencido) y "Tabla vegetariana" sin lotes.
        verify(inventory, times(11)).loadSampleLot(anyLong(), anyInt(), any(), any(), any(), anyLong());
        verify(inventory).loadSampleLot(anyLong(), anyInt(), eq(LocalDate.of(2026, 11, 26)), any(), any(), anyLong());
    }

    @Test
    @DisplayName("Es idempotente: un producto que ya existe no se crea de nuevo ni recibe lotes")
    void doesNotDuplicateProducts() {
        AppUser admin = mock(AppUser.class);
        when(admin.getId()).thenReturn(1L);
        when(users.findByUsername(anyString())).thenReturn(Optional.of(admin));
        when(products.findByName(anyString())).thenReturn(Optional.of(view("existente")));
        when(inventory.today()).thenReturn(LocalDate.of(2026, 11, 27));

        new LocalDataSeeder(users, products, inventory, "jdbc:postgresql://localhost:5432/la_cava").run(null);

        verify(users, never()).create(anyString(), anyString(), anyString(), any());
        verify(products, never()).create(any(), any(), anyLong());
        verify(inventory, never()).loadSampleLot(anyLong(), anyInt(), any(), any(), any(), anyLong());
    }

    private static ProductView view(String name) {
        return new ProductView(7L, name, 1L, "Tablas", BigDecimal.TEN, null, null, 0, true, null, null, 0);
    }
}
