import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Home } from '../../features/home/home';
import { AuthService } from '../auth/auth.service';
import { Role } from '../auth/role';
import { storeTestSession } from '../auth/testing';
import { Shell } from './shell';

@Component({ template: '' })
class Blank {}

function labels(root: HTMLElement, selector: string): string[] {
  return Array.from(root.querySelectorAll(selector)).map((a) => (a.textContent ?? '').replace(/[^A-Za-zÀ-ÿ ]/g, '').trim());
}

describe('Shell', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          {
            path: '',
            component: Shell,
            children: [
              { path: 'dashboard', component: Home },
              { path: 'mesas', component: Blank },
              { path: 'preparacion', component: Blank },
            ],
          },
          { path: 'login', component: Blank },
        ]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  async function open(role: Role, url: string) {
    storeTestSession(role);
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    return harness;
  }

  async function settled(harness: RouterTestingHarness, health: () => void): Promise<HTMLElement> {
    health();
    harness.detectChanges();
    await harness.fixture.whenStable();
    return harness.fixture.nativeElement as HTMLElement;
  }

  it('LCV-HU-00 CA2 renders the brand and the API status after a successful /health call', async () => {
    const harness = await open('ADMIN', '/dashboard');
    const root = await settled(harness, () => http.expectOne('/api/v1/health').flush({ status: 'UP', db: 'UP' }));
    expect(root.querySelector('.brand h1')?.textContent).toContain('LA CAVA');
    expect(root.querySelector('[data-testid="api-status"]')?.textContent).toContain('API conectada');
    expect(root.querySelector('[data-testid="home-health"]')?.textContent).toContain('operativas');
  });

  it('LCV-HU-00 CA2 shows "API sin conexión" when /health fails', async () => {
    const harness = await open('ADMIN', '/dashboard');
    const root = await settled(harness, () =>
      http.expectOne('/api/v1/health').flush(null, { status: 503, statusText: 'Service Unavailable' }),
    );
    expect(root.querySelector('[data-testid="api-status"]')?.textContent).toContain('API sin conexión');
  });

  it.each<[Role, string, string[]]>([
    ['ADMIN', '/dashboard', ['Dashboard', 'Mesas', 'Comandas']],
    ['CASHIER', '/dashboard', ['Dashboard', 'Mesas']],
    ['WAITER', '/mesas', ['Mis mesas']],
    ['KITCHEN', '/preparacion', ['Comandas']],
  ])('CA3 %s sees only its own options', async (role, url, expected) => {
    const harness = await open(role, url);
    const root = await settled(harness, () => http.expectOne('/api/v1/health').flush({ status: 'UP', db: 'UP' }));
    expect(labels(root, '.nav a')).toEqual(expected);
    expect(labels(root, '.mobile-nav a')).toEqual(expected);
  });

  it('the top bar title uses the same per-role label as the menu (WAITER sees "Mis mesas")', async () => {
    const harness = await open('WAITER', '/mesas');
    const root = await settled(harness, () => http.expectOne('/api/v1/health').flush({ status: 'UP', db: 'UP' }));
    expect(root.querySelector('.crumb')?.textContent?.trim()).toBe('Mis mesas');
  });

  it('shows the user name, role and initials, and Salir closes the session', async () => {
    const harness = await open('WAITER', '/mesas');
    const root = await settled(harness, () => http.expectOne('/api/v1/health').flush({ status: 'UP', db: 'UP' }));
    expect(root.querySelector('[data-testid="user-name"]')?.textContent).toContain('Mateo Ruiz');
    expect(root.querySelector('[data-testid="user-role"]')?.textContent).toContain('Mesero');
    expect(root.querySelector('.avatar')?.textContent?.trim()).toBe('MR');

    root.querySelector<HTMLButtonElement>('[data-testid="logout"]')!.click();
    await harness.fixture.whenStable();
    expect(TestBed.inject(AuthService).hasSession()).toBe(false);
    expect(TestBed.inject(Router).url).toBe('/login');
  });
});
