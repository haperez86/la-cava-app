package co.lacava.gestion.users.api;

import co.lacava.gestion.users.domain.AppUser;

/** Mesero activo para asignar pedidos. */
public record WaiterResponse(Long id, String fullName) {

    public static WaiterResponse from(AppUser user) {
        return new WaiterResponse(user.getId(), user.getFullName());
    }
}
