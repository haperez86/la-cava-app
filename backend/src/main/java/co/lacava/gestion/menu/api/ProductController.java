package co.lacava.gestion.menu.api;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.lacava.gestion.menu.api.ProductRequests.ActiveRequest;
import co.lacava.gestion.menu.api.ProductRequests.CreateProductRequest;
import co.lacava.gestion.menu.api.ProductRequests.UpdateProductRequest;
import co.lacava.gestion.menu.application.ProductService;
import co.lacava.gestion.shared.api.PageResponse;
import co.lacava.gestion.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Menú")
class ProductController {

    private final ProductService productService;

    ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CASHIER', 'WAITER', 'KITCHEN')")
    @Operation(summary = "Productos paginados con existencia vigente; filtros q (nombre) y categoryId")
    PageResponse<ProductResponse> list(@RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return PageResponse.of(productService.search(q, categoryId, pageable), ProductResponse::from);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crea un producto (categoría al vuelo y lote inicial opcional)")
    ProductResponse create(@Valid @RequestBody CreateProductRequest request, @AuthenticationPrincipal Jwt jwt) {
        return ProductResponse.from(productService.create(request.data(), request.lot(), CurrentUser.id(jwt)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Edita un producto")
    ProductResponse update(@PathVariable long id, @Valid @RequestBody UpdateProductRequest request) {
        return ProductResponse.from(productService.update(id, request.data()));
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Retira el producto de la carta o lo vuelve a poner")
    ProductResponse setActive(@PathVariable long id, @Valid @RequestBody ActiveRequest request) {
        return ProductResponse.from(productService.setActive(id, request.active()));
    }
}
