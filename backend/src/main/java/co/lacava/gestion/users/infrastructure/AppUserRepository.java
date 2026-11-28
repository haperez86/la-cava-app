package co.lacava.gestion.users.infrastructure;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import co.lacava.gestion.users.domain.AppUser;
import co.lacava.gestion.users.domain.Role;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);

    List<AppUser> findByRoleAndActiveTrueOrderByFullNameAscIdAsc(Role role);
}
