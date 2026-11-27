import { Routes } from '@angular/router';
import { authGuard, guestGuard, homeRedirect, roleGuard } from './core/auth/auth.guards';
import { Shell } from './core/layout/shell';

export const routes: Routes = [
  {
    path: 'login',
    title: 'Iniciar sesión · La Cava',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login').then((m) => m.Login),
  },
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    canActivateChild: [roleGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: homeRedirect },
      {
        path: 'dashboard',
        title: 'Dashboard · La Cava',
        data: { roles: ['ADMIN', 'CASHIER'] },
        loadComponent: () => import('./features/home/home').then((m) => m.Home),
      },
      {
        path: 'mesas',
        title: 'Mesas · La Cava',
        data: { roles: ['ADMIN', 'CASHIER', 'WAITER'] },
        loadComponent: () => import('./features/tables/tables-page').then((m) => m.TablesPage),
      },
      {
        path: 'preparacion',
        title: 'Comandas · La Cava',
        data: { roles: ['ADMIN', 'KITCHEN'] },
        loadComponent: () => import('./features/kitchen/kitchen-page').then((m) => m.KitchenPage),
      },
      {
        path: 'menu',
        title: 'Menú y productos · La Cava',
        data: { roles: ['ADMIN'] },
        loadComponent: () => import('./features/menu/menu-page').then((m) => m.MenuPage),
      },
      {
        path: 'inventario',
        title: 'Inventario · La Cava',
        data: { roles: ['ADMIN'] },
        loadComponent: () => import('./features/inventory/inventory-page').then((m) => m.InventoryPage),
      },
    ],
  },
  { path: '**', redirectTo: homeRedirect },
];
