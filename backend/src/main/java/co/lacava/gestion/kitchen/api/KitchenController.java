package co.lacava.gestion.kitchen.api;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.lacava.gestion.kitchen.application.KitchenBoardService;
import co.lacava.gestion.orders.application.OrderView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** LCV-HU-12: tablero de preparación. */
@RestController
@RequestMapping("/api/v1/kitchen")
@Tag(name = "Preparación")
class KitchenController {

    private final KitchenBoardService board;

    KitchenController(KitchenBoardService board) {
        this.board = board;
    }

    @GetMapping("/board")
    @PreAuthorize("hasAnyRole('ADMIN', 'KITCHEN')")
    @Operation(summary = "Pedidos PENDING, IN_PREPARATION y READY por estado y del más antiguo al más reciente, sin paginar")
    List<OrderView> board() {
        return board.board();
    }
}
