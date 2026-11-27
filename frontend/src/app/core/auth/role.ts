export type Role = 'ADMIN' | 'CASHIER' | 'WAITER' | 'KITCHEN';

export const ROLE_LABELS: Record<Role, string> = {
  ADMIN: 'Administrador',
  CASHIER: 'Cajero',
  WAITER: 'Mesero',
  KITCHEN: 'Preparación',
};

/** Pantalla inicial de cada rol (LCV-HU-01 CA1). */
export const HOME_PATHS: Record<Role, string> = {
  ADMIN: '/dashboard',
  CASHIER: '/dashboard',
  WAITER: '/mesas',
  KITCHEN: '/preparacion',
};

export function homePathFor(role: Role | null): string {
  return role ? HOME_PATHS[role] : '/login';
}
