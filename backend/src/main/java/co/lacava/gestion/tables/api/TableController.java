package co.lacava.gestion.tables.api;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.lacava.gestion.shared.error.FieldError;
import co.lacava.gestion.shared.error.InvalidFieldsException;
import co.lacava.gestion.shared.security.CurrentUser;
import co.lacava.gestion.tables.api.TableRequests.ActiveRequest;
import co.lacava.gestion.tables.api.TableRequests.CreateTableRequest;
import co.lacava.gestion.tables.api.TableRequests.ResolveRequest;
import co.lacava.gestion.tables.application.QrSheetEntry;
import co.lacava.gestion.tables.application.TableAccount;
import co.lacava.gestion.tables.application.TableService;
import co.lacava.gestion.tables.application.TableView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** LCV-HU-07: mesas y códigos QR. El token y el PIN solo salen en {@code qr-sheet} (ADMIN). */
@RestController
@RequestMapping("/api/v1/tables")
@Tag(name = "Mesas")
class TableController {

    private final TableService tableService;

    TableController(TableService tableService) {
        this.tableService = tableService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CASHIER', 'WAITER')")
    @Operation(summary = "Mesas ordenadas por número (sin token ni PIN)")
    List<TableView> list() {
        return tableService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crea una mesa con token HMAC y PIN de 4 dígitos")
    TableView create(@Valid @RequestBody CreateTableRequest request) {
        return tableService.create(request.number());
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activa o desactiva una mesa")
    TableView setActive(@PathVariable long id, @Valid @RequestBody ActiveRequest request) {
        return tableService.setActive(id, request.active());
    }

    @PostMapping("/{id}/rotate-token")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Regenera token y PIN: invalida el QR anterior")
    TableView rotateToken(@PathVariable long id) {
        return tableService.rotateToken(id);
    }

    @GetMapping("/qr-sheet")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Hoja de QR imprimible: mesas activas con código y PIN, por número")
    List<QrSheetEntry> qrSheet() {
        return tableService.qrSheet();
    }

    @PostMapping("/resolve")
    @PreAuthorize("hasAnyRole('ADMIN', 'CASHIER', 'WAITER')")
    @Operation(summary = "Mesa y cuenta a partir del código del QR o de número + PIN; 404 si no es válido")
    TableAccount resolve(@RequestBody ResolveRequest request, @AuthenticationPrincipal Jwt jwt) {
        if (request.code() != null && !request.code().isBlank()) {
            return tableService.resolveByCode(request.code());
        }
        if (request.number() != null && request.pin() != null && !request.pin().isBlank()) {
            // D36: número + PIN se limita por usuario autenticado; el código del QR no.
            return tableService.resolveByPin(request.number(), request.pin(), CurrentUser.id(jwt),
                    jwt.getClaimAsString("username"));
        }
        throw new InvalidFieldsException(List.of(new FieldError("code", TableRequests.RESOLVE_REQUIRED)));
    }

    @GetMapping("/{id}/account")
    @PreAuthorize("hasAnyRole('ADMIN', 'CASHIER')")
    @Operation(summary = "Cuenta abierta de la mesa (el mesero solo vía resolve)")
    TableAccount account(@PathVariable long id) {
        return tableService.account(id);
    }
}
