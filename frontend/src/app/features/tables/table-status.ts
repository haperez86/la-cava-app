import { TableStage, TableStatus } from './tables.model';

/** Texto del estado de la mesa (LCV-HU-09 CA1). */
export const TABLE_STATUS_LABEL: Readonly<Record<TableStatus, string>> = {
  FREE: 'Disponible',
  OCCUPIED: 'Ocupada',
};

/** Clase de pill del skill lacava-design para el estado (vacía = verde "ok"). */
export const TABLE_STATUS_PILL: Readonly<Record<TableStatus, string>> = {
  FREE: '',
  OCCUPIED: 'busy',
};

/** Texto de la etapa más urgente de una mesa ocupada (CA1). */
export const TABLE_STAGE_LABEL: Readonly<Record<TableStage, string>> = {
  READY: 'Listo para recoger',
  IN_PREPARATION: 'En preparación',
  PENDING: 'Pendiente',
  SERVED: 'Servido',
};

/** Clase de pill de cada etapa; "Listo para recoger" lleva además el brillo de pedido listo (`glow`). */
export const TABLE_STAGE_PILL: Readonly<Record<TableStage, string>> = {
  READY: 'ready glow',
  IN_PREPARATION: 'busy',
  PENDING: 'busy',
  SERVED: '',
};

/** Orden de la etapa más urgente a la menos urgente (CA1). */
export const TABLE_STAGES: readonly TableStage[] = ['READY', 'IN_PREPARATION', 'PENDING', 'SERVED'];

export const NO_OPEN_ORDERS = 'Sin pedidos abiertos';

/** "1 pedido" / "3 pedidos". */
export function ordersLabel(count: number): string {
  return count === 1 ? '1 pedido' : `${count} pedidos`;
}
