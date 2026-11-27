package co.lacava.gestion.menu.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.lacava.gestion.menu.domain.Category;
import co.lacava.gestion.menu.infrastructure.CategoryRepository;

/** Categorías de la carta. Se crean al vuelo al guardar un producto (CA1). */
@Service
public class CategoryService {

    private final CategoryRepository categories;

    public CategoryService(CategoryRepository categories) {
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public List<CategoryView> list() {
        return categories.findAllByOrderByNameAsc().stream()
                .map(c -> new CategoryView(c.getId(), c.getName()))
                .toList();
    }

    /** Busca la categoría por nombre (recortado, sin distinguir mayúsculas) o la crea si no existe. */
    @Transactional
    public Category findOrCreate(String name) {
        String trimmed = name.trim();
        return categories.findByNameIgnoreCase(trimmed)
                .orElseGet(() -> categories.save(new Category(trimmed)));
    }

    public record CategoryView(Long id, String name) {
    }
}
