package co.lacava.gestion.inventory.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import co.lacava.gestion.inventory.domain.StockMovement;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
}
