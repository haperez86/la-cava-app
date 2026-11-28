import { Component } from '@angular/core';
import { TABLE_STAGES, TABLE_STAGE_LABEL, TABLE_STATUS_LABEL, TABLE_STATUS_PILL } from './table-status';
import { TableStage, TableStatus } from './tables.model';

const STATUSES: readonly TableStatus[] = ['FREE', 'OCCUPIED'];

/** Colores de la leyenda: los de la tarjeta, sin el brillo animado de "Listo para recoger". */
const LEGEND_STAGE_PILL: Readonly<Record<TableStage, string>> = {
  READY: 'ready',
  IN_PREPARATION: 'busy',
  PENDING: 'busy',
  SERVED: '',
};

/** Leyenda de estados de las mesas (LCV-HU-09 CA2): estados y etapas con su color y siempre con texto. */
@Component({
  selector: 'app-table-legend',
  template: `
    <ul class="legend" aria-label="Leyenda de estados de las mesas" data-testid="table-legend">
      @for (item of items; track item.label) {
        <li class="pill {{ item.pill }}" data-testid="legend-item">
          @if (item.dot) {
            <span aria-hidden="true">●</span>
          }
          {{ item.label }}
        </li>
      }
    </ul>
  `,
  styles: `
    .legend {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 6px;
      margin: 0 0 16px;
      padding: 0;
      list-style: none;
    }
  `,
})
export class TableLegend {
  protected readonly items = [
    ...STATUSES.map((s) => ({ label: TABLE_STATUS_LABEL[s], pill: TABLE_STATUS_PILL[s], dot: true })),
    ...TABLE_STAGES.map((s) => ({ label: TABLE_STAGE_LABEL[s], pill: LEGEND_STAGE_PILL[s], dot: false })),
  ];
}
