package co.lacava.gestion.inventory.application;

import java.util.List;

import co.lacava.gestion.shared.error.FieldError;
import co.lacava.gestion.shared.error.InvalidFieldsException;

/**
 * El consumo pide más que la existencia vigente. Sigue siendo un 400 en {@code quantity} para las salidas manuales
 * (D33); los pedidos lo traducen a 422 con el producto y lo disponible (D38).
 */
public class InsufficientStockException extends InvalidFieldsException {

    private final long productId;
    private final int available;

    public InsufficientStockException(long productId, int available) {
        super(List.of(new FieldError("quantity", InventoryService.notEnough(available))));
        this.productId = productId;
        this.available = available;
    }

    public long getProductId() {
        return productId;
    }

    public int getAvailable() {
        return available;
    }
}
