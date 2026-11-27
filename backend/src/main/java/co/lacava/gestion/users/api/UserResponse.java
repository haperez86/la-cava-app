package co.lacava.gestion.users.api;

import co.lacava.gestion.users.domain.AppUser;
import co.lacava.gestion.users.domain.Role;

public record UserResponse(Long id, String username, String fullName, Role role, boolean active) {

    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getFullName(), user.getRole(), user.isActive());
    }
}
