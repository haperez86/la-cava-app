export interface NavItem {
  readonly path: string;
  readonly label: string;
  readonly icon: string;
  readonly title: string;
}

/** Menú principal. Solo Dashboard hasta HU-01; luego se filtra por rol. */
export const NAV_ITEMS: readonly NavItem[] = [
  { path: 'inicio', label: 'Dashboard', icon: '⌂', title: 'Dashboard' },
];
