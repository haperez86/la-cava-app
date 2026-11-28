import { DiningTable } from './tables.model';

/** Mesa de prueba libre (GET /api/v1/tables con los campos de estado de HU-09). */
export function freeTable(id: number, number: number, active = true): DiningTable {
  return { id, number, active, status: 'FREE', stage: null, openOrders: 0, total: 0, waiterName: null };
}

/** Mesa de prueba ocupada con su etapa más urgente, pedidos, total y mesero. */
export function occupiedTable(
  id: number,
  number: number,
  stage: DiningTable['stage'],
  openOrders = 1,
  total = 18000,
  waiterName = 'Mateo Ruiz',
): DiningTable {
  return { id, number, active: true, status: 'OCCUPIED', stage, openOrders, total, waiterName };
}
