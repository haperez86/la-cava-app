import { Component, computed, input, output } from '@angular/core';
import { InventoryStateFilter, InventorySummary } from './inventory.model';

/** Días de aviso de "por vencer" (D32: vence en ≤ 3 días). */
export const EXPIRY_WARN_DAYS = 3;

/** Tarjeta de contador y el filtro de estado que aplica al hacer clic. */
interface StatCard {
  readonly key: 'out' | 'low' | 'expiring' | 'expired';
  readonly label: string;
  readonly value: number;
  readonly filter: InventoryStateFilter;
  /** Color del número cuando es mayor que 0. */
  readonly tone: 'red' | 'amber';
}

/**
 * Contadores del inventario (LCV-HU-06 CA1), presentacional: agotados, stock bajo, por vencer y unidades vencidas.
 * Cada tarjeta emite su filtro de estado; la de vencidos ofrece "Dar de baja vencidos" cuando hay unidades.
 */
@Component({
  selector: 'app-inventory-stats',
  templateUrl: './inventory-stats.html',
  styleUrl: './inventory-stats.scss',
})
export class InventoryStats {
  readonly summary = input.required<InventorySummary>();
  /** Filtro de estado vigente, para marcar la tarjeta activa. */
  readonly activeFilter = input<InventoryStateFilter | null>(null);
  readonly writingOff = input(false);

  readonly filter = output<InventoryStateFilter>();
  readonly writeOff = output<void>();

  protected readonly cards = computed<readonly StatCard[]>(() => {
    const s = this.summary();
    return [
      { key: 'out', label: 'Agotados (no se ofrecen)', value: s.outOfStock, filter: 'OUT', tone: 'red' },
      { key: 'low', label: 'Stock bajo', value: s.lowStock, filter: 'LOW', tone: 'amber' },
      {
        key: 'expiring',
        label: `Por vencer (≤ ${EXPIRY_WARN_DAYS} días)`,
        value: s.expiring,
        filter: 'EXPIRING',
        tone: 'amber',
      },
      { key: 'expired', label: 'Unidades vencidas', value: s.expiredUnits, filter: 'HAS_EXPIRED', tone: 'red' },
    ];
  });
}
