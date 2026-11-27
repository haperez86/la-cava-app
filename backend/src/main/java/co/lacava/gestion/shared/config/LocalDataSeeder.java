package co.lacava.gestion.shared.config;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import co.lacava.gestion.inventory.application.InventoryService;
import co.lacava.gestion.menu.application.ProductData;
import co.lacava.gestion.menu.application.ProductService;
import co.lacava.gestion.menu.application.ProductView;
import co.lacava.gestion.users.application.UserService;
import co.lacava.gestion.users.domain.AppUser;
import co.lacava.gestion.users.domain.Role;

/**
 * Datos de ejemplo del perfil {@code local}: usuarios de la demo con contraseña {@code <usuario>123} y productos de la
 * carta con lotes de fechas relativas al {@code Clock} (incluido uno vencido). Idempotente: un producto que ya existe
 * (por nombre) no se vuelve a crear ni recibe lotes.
 * Solo siembra si la base de datos está en esta máquina (P2): las claves son predecibles.
 */
@Component
@Profile("local")
class LocalDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalDataSeeder.class);
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");
    private static final String POSTGRES_PREFIX = "jdbc:postgresql:";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Bogota");

    private final UserService users;
    private final ProductService products;
    private final InventoryService inventory;
    private final String datasourceUrl;

    LocalDataSeeder(UserService users, ProductService products, InventoryService inventory,
            @Value("${spring.datasource.url:}") String datasourceUrl) {
        this.users = users;
        this.products = products;
        this.inventory = inventory;
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
        seedProducts();
    }

    /** Productos de la demo (demo/index.html y docs/02, "Datos de ejemplo"). Lotes: {cantidad, días desde hoy}. */
    private void seedProducts() {
        long adminId = users.findByUsername("admin").map(AppUser::getId).orElseThrow();
        LocalDate today = inventory.today();
        seedProduct("Tabla La Cava", "Tablas", 58000, "🧀", "Selección de quesos y charcutería", 4, null, null,
                List.of(new int[] { 9, 3 }), adminId);
        seedProduct("Tabla para dos", "Tablas", 42000, "🥖", "Para compartir", 4, null, null,
                List.of(new int[] { 7, 4 }), adminId);
        seedProduct("Copa de vino tinto", "Vinos", 18000, "🍷", "Copa de la casa", 10, null, null,
                List.of(new int[] { 36, 240 }), adminId);
        seedProduct("Vino botella", "Vinos", 95000, "🍾", "Selección de la casa", 3, null, null,
                List.of(new int[] { 8, 400 }), adminId);
        seedProduct("Cóctel de frutos rojos", "Cócteles", 26000, "🍹", "Preparación de la casa", 5, null, null,
                List.of(new int[] { 14, 2 }), adminId);
        seedProduct("Limonada natural", "Bebidas", 12000, "🍋", "Natural y refrescante", 5, null, null,
                List.of(new int[] { 12, 1 }), adminId);
        seedProduct("Agua", "Bebidas", 6000, "💧", "Agua personal", 10, null, null,
                List.of(new int[] { 30, 180 }), adminId);
        seedProduct("Postre de la casa", "Postres", 16000, "🍰", "Porción individual", 4, null, null,
                List.of(new int[] { 2, -1 }, new int[] { 3, 1 }), adminId);
        seedProduct("Tabla vegetariana", "Tablas", 45000, "🥗", "Quesos, frutas y frutos secos", 3, null, null,
                List.of(), adminId);
        seedProduct("Vino caliente navideño", "Vinos", 22000, "🎄", "Especial de temporada", 5, "Navidad",
                LocalDate.of(today.getYear(), 12, 31), List.of(new int[] { 15, 20 }), adminId);
        seedProduct("Sangría de verano", "Cócteles", 24000, "🍊", "Especial de temporada", 5, "Verano",
                today.minusDays(10), List.of(new int[] { 6, 30 }), adminId);
    }

    private void seedProduct(String name, String category, int price, String icon, String description, int minStock,
            String seasonName, LocalDate seasonUntil, List<int[]> lots, long adminId) {
        if (products.findByName(name).isPresent()) {
            return;
        }
        ProductView product = products.create(new ProductData(name, category, BigDecimal.valueOf(price), description,
                icon, minStock, seasonName, seasonUntil), null, adminId);
        LocalDate today = inventory.today();
        for (int[] lot : lots) {
            inventory.loadSampleLot(product.id(), lot[0], today.plusDays(lot[1]), null,
                    today.minusDays(2).atStartOfDay(BUSINESS_ZONE).toInstant(), adminId);
        }
    }

    /** {@code true} solo si la URL JDBC apunta a {@code localhost}, {@code 127.0.0.1} o {@code ::1}. */
    static boolean isLocalDatabase(String jdbcUrl) {
        String host = hostOf(jdbcUrl);
        return host != null && LOCAL_HOSTS.contains(host.toLowerCase(Locale.ROOT));
    }

    /**
     * Host de una URL {@code jdbc:...}, o {@code null} si no se puede extraer (URL malformada, varios hosts o host
     * fijado por la propiedad {@code PGHOST}). Las URL PostgreSQL sin host ({@code jdbc:postgresql:la_cava},
     * {@code jdbc:postgresql:///la_cava}) las resuelve el driver a {@code localhost}.
     */
    static String hostOf(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:")) {
            return null;
        }
        if (jdbcUrl.toLowerCase(Locale.ROOT).contains("pghost=")) {
            return null;
        }
        if (jdbcUrl.startsWith(POSTGRES_PREFIX)) {
            String rest = jdbcUrl.substring(POSTGRES_PREFIX.length());
            if (!rest.startsWith("//") || rest.startsWith("///")) {
                return "localhost";
            }
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
