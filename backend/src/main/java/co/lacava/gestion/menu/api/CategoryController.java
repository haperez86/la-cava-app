package co.lacava.gestion.menu.api;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.lacava.gestion.menu.application.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/categories")
@Tag(name = "Menú")
class CategoryController {

    private final CategoryService categoryService;

    CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CASHIER', 'WAITER', 'KITCHEN')")
    @Operation(summary = "Categorías ordenadas por nombre")
    List<CategoryResponse> list() {
        return categoryService.list().stream().map(c -> new CategoryResponse(c.id(), c.name())).toList();
    }

    record CategoryResponse(Long id, String name) {
    }
}
