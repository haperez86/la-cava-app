package co.lacava.gestion.tables.infrastructure;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import co.lacava.gestion.tables.domain.DiningTable;

public interface DiningTableRepository extends JpaRepository<DiningTable, Long> {

    List<DiningTable> findAllByOrderByNumberAsc();

    List<DiningTable> findByActiveTrueOrderByNumberAsc();

    Optional<DiningTable> findByNumber(int number);

    boolean existsByNumber(int number);
}
