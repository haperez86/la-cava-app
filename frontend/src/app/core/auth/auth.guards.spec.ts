import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { ToastService } from '../notifications/toast.service';
import { authGuard, guestGuard, homeRedirect, roleGuard } from './auth.guards';
import { Role } from './role';
import { storeTestSession } from './testing';

@Component({ template: '' })
class Blank {}

function setup(): void {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([
        { path: 'login', component: Blank, canActivate: [guestGuard] },
        {
          path: '',
          canActivate: [authGuard],
          canActivateChild: [roleGuard],
          children: [
            { path: '', pathMatch: 'full', redirectTo: homeRedirect },
            { path: 'dashboard', component: Blank, data: { roles: ['ADMIN', 'CASHIER'] } },
            { path: 'mesas', component: Blank, data: { roles: ['ADMIN', 'CASHIER', 'WAITER'] } },
            { path: 'preparacion', component: Blank, data: { roles: ['ADMIN', 'KITCHEN'] } },
          ],
        },
        { path: '**', redirectTo: homeRedirect },
      ]),
    ],
  });
}

let harness: RouterTestingHarness;

async function go(url: string): Promise<string> {
  harness ??= await RouterTestingHarness.create();
  await harness.navigateByUrl(url);
  return TestBed.inject(Router).url;
}

describe('auth guards', () => {
  beforeEach(() => {
    localStorage.clear();
    harness = undefined as unknown as RouterTestingHarness;
  });
  afterEach(() => localStorage.clear());

  it('sends anonymous users to the login', async () => {
    setup();
    expect(await go('/dashboard')).toBe('/login');
    expect(await go('/')).toBe('/login');
  });

  it.each<[Role, string]>([
    ['ADMIN', '/dashboard'],
    ['CASHIER', '/dashboard'],
    ['WAITER', '/mesas'],
    ['KITCHEN', '/preparacion'],
  ])('CA1/CA3 %s: "/", unknown routes and /login lead to its home %s', async (role, home) => {
    storeTestSession(role);
    setup();
    expect(await go('/')).toBe(home);
    expect(await go('/no-existe')).toBe(home);
    expect(await go('/login')).toBe(home);
  });

  it.each<[Role, string, string]>([
    ['WAITER', '/dashboard', '/mesas'],
    ['WAITER', '/preparacion', '/mesas'],
    ['KITCHEN', '/dashboard', '/preparacion'],
    ['KITCHEN', '/mesas', '/preparacion'],
    ['CASHIER', '/preparacion', '/dashboard'],
  ])('CA3 %s opening %s is redirected to its own home %s', async (role, url, home) => {
    storeTestSession(role);
    setup();
    expect(await go(url)).toBe(home);
  });

  it('CA3 lets the admin open every route', async () => {
    storeTestSession('ADMIN');
    setup();
    expect(await go('/dashboard')).toBe('/dashboard');
    expect(await go('/mesas')).toBe('/mesas');
    expect(await go('/preparacion')).toBe('/preparacion');
  });

  it('returns to the login with a notice when the session expired', async () => {
    storeTestSession('ADMIN', new Date(Date.now() - 1000).toISOString());
    setup();
    expect(await go('/dashboard')).toBe('/login');
    expect(TestBed.inject(ToastService).toasts()[0]).toMatchObject({
      kind: 'error',
      message: 'Tu sesión expiró. Inicia sesión de nuevo.',
    });
  });
});
