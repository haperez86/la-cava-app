package co.lacava.gestion.inventory.api;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.lacava.gestion.inventory.application.InventoryService;
import co.lacava.gestion.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Inventario")
class InventoryController {

    private final InventoryService inventory;

    InventoryController(InventoryService inventory) {
        this.inventory = inventory;
    }

    @PostMapping("/inventory/lots")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Registra la entrada de un lote y su movimiento LOT_IN; sin temporada toma la del producto")
    LotResponse receiveLot(@Valid @RequestBody ReceiveLotRequest request, @AuthenticationPrincipal Jwt jwt) {
        return LotResponse.from(inventory.receiveLot(request.productId(), request.quantity(), request.expiresOn(),
                request.seasonName(), CurrentUser.id(jwt)));
    }

    @GetMapping("/products/{id}/lots")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lotes de un producto con su estado, ordenados por vencimiento")
    List<LotResponse> lotsOf(@PathVariable long id) {
        return inventory.lotsOf(id).stream().map(LotResponse::from).toList();
    }
}
