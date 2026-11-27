package co.lacava.gestion.inventory.api;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.lacava.gestion.inventory.application.InventoryRow;
import co.lacava.gestion.inventory.application.InventoryService;
import co.lacava.gestion.inventory.application.InventorySummary;
import co.lacava.gestion.inventory.application.InventoryViewService;
import co.lacava.gestion.inventory.application.MovementView;
import co.lacava.gestion.shared.api.PageResponse;
import co.lacava.gestion.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Inventario")
class InventoryController {

    private final InventoryService inventory;
    private final InventoryViewService view;

    InventoryController(InventoryService inventory, InventoryViewService view) {
        this.inventory = inventory;
        this.view = view;
    }

    @GetMapping("/inventory")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Productos activos con vigente, mínimo, próximo vencimiento, unidades vencidas y estado; "
            + "filtros q, categoryId, season y state (AVAILABLE, LOW, OUT, EXPIRING, HAS_EXPIRED, OUT_OF_SEASON); "
            + "sort por name, available o minStock")
    PageResponse<InventoryRow> list(@RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId, @RequestParam(required = false) String state,
            @RequestParam(required = false) String season, @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(view.search(q, categoryId, state, season, pageable), row -> row);
    }

    @GetMapping("/inventory/summary")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Contadores: agotados, stock bajo, por vencer y unidades vencidas; temporadas para el filtro")
    InventorySummary summary() {
        return view.summary();
    }

    @GetMapping("/inventory/movements")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Movimientos del más reciente al más antiguo; filtros productId y fechas from/to "
            + "(YYYY-MM-DD, inclusivas, hora de Bogotá)")
    PageResponse<MovementView> movements(@RequestParam(required = false) Long productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(view.movements(productId, from, to, pageable), movement -> movement);
    }

    @PostMapping("/inventory/lots")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Registra la entrada de un lote y su movimiento LOT_IN; sin temporada toma la del producto")
    LotResponse receiveLot(@Valid @RequestBody ReceiveLotRequest request, @AuthenticationPrincipal Jwt jwt) {
        return LotResponse.from(inventory.receiveLot(request.productId(), request.quantity(), request.expiresOn(),
                request.seasonName(), CurrentUser.id(jwt)));
    }

    @PostMapping("/inventory/outputs")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Salida manual (merma, dañado, consumo interno, ajuste) consumida en FEFO; exige observación")
    OutputResponse registerOutput(@RequestBody RegisterOutputRequest request, @AuthenticationPrincipal Jwt jwt) {
        return OutputResponse.from(inventory.registerOutput(request.productId(), request.quantity(), request.type(),
                request.note(), CurrentUser.id(jwt)));
    }

    @PostMapping("/inventory/expired/write-off")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deja en 0 todos los lotes vencidos con un movimiento EXPIRED_WRITE_OFF por lote")
    WriteOffResponse writeOffExpired(@AuthenticationPrincipal Jwt jwt) {
        return WriteOffResponse.from(inventory.writeOffExpired(CurrentUser.id(jwt)));
    }

    @GetMapping("/products/{id}/lots")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lotes de un producto con su estado, ordenados por vencimiento")
    List<LotResponse> lotsOf(@PathVariable long id) {
        return inventory.lotsOf(id).stream().map(LotResponse::from).toList();
    }
}
