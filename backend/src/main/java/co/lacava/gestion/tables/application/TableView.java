package co.lacava.gestion.tables.application;

/** Mesa sin token ni PIN (lo que ven todos los roles). Los campos de estado llegan con HU-09 (D35). */
public record TableView(Long id, int number, boolean active) {
}
