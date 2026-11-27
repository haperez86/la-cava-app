import { Role } from '../auth/role';
import { IconName } from '../../shared/icons/icon';

export interface NavItem {
  readonly path: string;
  readonly label: string;
  readonly icon: IconName;
  readonly title: string;
  /** Roles que ven la opción (especificación §1). */
  readonly roles: readonly Role[];
  /** Etiqueta específica por rol (p. ej. "Mis mesas" para el mesero). */
  readonly labelByRole?: Partial<Record<Role, string>>;
}

export const NAV_ITEMS: readonly NavItem[] = [
  { path: 'dashboard', label: 'Dashboard', icon: 'home', title: 'Dashboard', roles: ['ADMIN', 'CASHIER'] },
  {
    path: 'mesas',
    label: 'Mesas',
    icon: 'tables',
    title: 'Mesas',
    roles: ['ADMIN', 'CASHIER', 'WAITER'],
    labelByRole: { WAITER: 'Mis mesas' },
  },
  { path: 'preparacion', label: 'Comandas', icon: 'kitchen', title: 'Comandas', roles: ['ADMIN', 'KITCHEN'] },
  { path: 'menu', label: 'Menú', icon: 'menu', title: 'Menú y productos', roles: ['ADMIN'] },
  { path: 'inventario', label: 'Inventario', icon: 'inventory', title: 'Inventario', roles: ['ADMIN'] },
];

export function navItemsFor(role: Role | null): readonly NavItem[] {
  return role ? NAV_ITEMS.filter((i) => i.roles.includes(role)) : [];
}

export function navLabel(item: NavItem, role: Role | null): string {
  return (role && item.labelByRole?.[role]) || item.label;
}
