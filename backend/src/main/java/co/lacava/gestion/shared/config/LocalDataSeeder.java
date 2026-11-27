package co.lacava.gestion.shared.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import co.lacava.gestion.users.application.UserService;
import co.lacava.gestion.users.domain.Role;

/** Datos de ejemplo del perfil {@code local}: usuarios de la demo con contraseña {@code <usuario>123}. Idempotente. */
@Component
@Profile("local")
class LocalDataSeeder implements ApplicationRunner {

    private final UserService users;

    LocalDataSeeder(UserService users) {
        this.users = users;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedUser("admin", "Administrador Demo", Role.ADMIN);
        seedUser("camila", "Camila Torres", Role.CASHIER);
        seedUser("laura", "Laura Gómez", Role.WAITER);
        seedUser("andres", "Andrés Rojas", Role.WAITER);
        seedUser("jorge", "Jorge Ruiz", Role.KITCHEN);
    }

    private void seedUser(String username, String fullName, Role role) {
        if (users.findByUsername(username).isEmpty()) {
            users.create(username, fullName, username + "123", role);
        }
    }
}
