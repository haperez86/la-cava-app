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
import { STREAM_TICKET_URL } from '../notifications/sse-client';
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
              { path: 'pedidos', component: Blank },
              { path: 'preparacion', component: Blank },
              { path: 'menu', component: Blank },
              { path: 'inventario', component: Blank },
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
    http.match(STREAM_TICKET_URL); // HU-13: el shell abre el canal SSE; se prueba en sse-client.spec.ts
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
    expect(root.querySelector('.brand app-brand-logo')).not.toBeNull();
    expect(root.querySelector('[data-testid="api-status"]')?.textContent).toContain('API conectada');
    expect(root.querySelector('[data-testid="home-health"]')?.textContent).toContain('operativas');
  });

  it('the sidebar shows the La Cava logo as an accessible inline SVG instead of the "C" mark', async () => {
    const harness = await open('ADMIN', '/dashboard');
    const root = await settled(harness, () => http.expectOne('/api/v1/health').flush({ status: 'UP', db: 'UP' }));
    const svg = root.querySelector('.sidebar app-brand-logo svg[role="img"]');
    expect(svg).not.toBeNull();
    expect(svg?.getAttribute('aria-label')).toBe('La Cava, copas y tablas');
    expect(svg?.querySelector('circle')).not.toBeNull();
    expect(root.querySelector('.brand-mark')).toBeNull();
  });

  it('LCV-HU-00 CA2 shows "API sin conexión" when /health fails', async () => {
    const harness = await open('ADMIN', '/dashboard');
    const root = await settled(harness, () =>
      http.expectOne('/api/v1/health').flush(null, { status: 503, statusText: 'Service Unavailable' }),
    );
    expect(root.querySelector('[data-testid="api-status"]')?.textContent).toContain('API sin conexión');
  });

  it.each<[Role, string, string[]]>([
    ['ADMIN', '/dashboard', ['Dashboard', 'Mesas', 'Pedidos', 'Comandas', 'Menú', 'Inventario']],
    ['CASHIER', '/dashboard', ['Dashboard', 'Mesas', 'Pedidos']],
    ['WAITER', '/mesas', ['Mis mesas']],
    ['KITCHEN', '/preparacion', ['Comandas']],
  ])('CA3 %s sees only its own options', async (role, url, expected) => {
    const harness = await open(role, url);
    const root = await settled(harness, () => http.expectOne('/api/v1/health').flush({ status: 'UP', db: 'UP' }));
    expect(labels(root, '.nav a')).toEqual(expected);
    expect(labels(root, '.mobile-nav a')).toEqual(expected);
  });

  it('every navigation item (sidebar and mobile bar) renders an inline SVG icon instead of a Unicode glyph', async () => {
    const harness = await open('ADMIN', '/dashboard');
    const root = await settled(harness, () => http.expectOne('/api/v1/health').flush({ status: 'UP', db: 'UP' }));
    for (const selector of ['.nav a', '.mobile-nav a']) {
      const links = Array.from(root.querySelectorAll(selector));
      expect(links.length).toBe(6);
      for (const a of links) {
        expect(a.querySelector('.ico svg path')).not.toBeNull();
        expect(a.querySelector('.ico')?.textContent?.trim()).toBe('');
      }
    }
  });

  it('LCV-HU-02 the Menú option leads ADMIN to /menu', async () => {
    const harness = await open('ADMIN', '/menu');
    const root = await settled(harness, () => http.expectOne('/api/v1/health').flush({ status: 'UP', db: 'UP' }));
    const link = Array.from(root.querySelectorAll<HTMLAnchorElement>('.nav a')).find((a) => a.textContent?.includes('Menú'));
    expect(link?.getAttribute('href')).toBe('/menu');
    expect(root.querySelector('.crumb')?.textContent?.trim()).toBe('Menú');
  });

  it('LCV-HU-04 the Inventario option leads ADMIN to /inventario with its own SVG icon', async () => {
    const harness = await open('ADMIN', '/inventario');
    const root = await settled(harness, () => http.expectOne('/api/v1/health').flush({ status: 'UP', db: 'UP' }));
    const link = Array.from(root.querySelectorAll<HTMLAnchorElement>('.nav a')).find((a) =>
      a.textContent?.includes('Inventario'),
    );
    expect(link?.getAttribute('href')).toBe('/inventario');
    expect(link?.querySelector('.ico svg path')).not.toBeNull();
    expect(root.querySelector('.crumb')?.textContent?.trim()).toBe('Inventario');
  });

  it.each<[Role, string]>([
    ['CASHIER', '/dashboard'],
    ['WAITER', '/mesas'],
    ['KITCHEN', '/preparacion'],
  ])('LCV-HU-04 %s does not see the Inventario option', async (role, url) => {
    const harness = await open(role, url);
    const root = await settled(harness, () => http.expectOne('/api/v1/health').flush({ status: 'UP', db: 'UP' }));
    expect(labels(root, '.nav a')).not.toContain('Inventario');
    expect(labels(root, '.mobile-nav a')).not.toContain('Inventario');
  });

  it.each<Role>(['ADMIN', 'CASHIER'])(
    'LCV-HU-10 the Pedidos option leads %s to /pedidos with its own SVG icon',
    async (role) => {
      const harness = await open(role, '/pedidos');
      const root = await settled(harness, () => http.expectOne('/api/v1/health').flush({ status: 'UP', db: 'UP' }));
      for (const selector of ['.nav a', '.mobile-nav a']) {
        const link = Array.from(root.querySelectorAll<HTMLAnchorElement>(selector)).find((a) =>
          a.textContent?.includes('Pedidos'),
        );
        expect(link?.getAttribute('href')).toBe('/pedidos');
        expect(link?.querySelector('.ico svg path')).not.toBeNull();
      }
      expect(root.querySelector('.crumb')?.textContent?.trim()).toBe('Pedidos');
    },
  );

  it.each<[Role, string]>([
    ['WAITER', '/mesas'],
    ['KITCHEN', '/preparacion'],
  ])('LCV-HU-10 %s does not see the Pedidos option', async (role, url) => {
    const harness = await open(role, url);
    const root = await settled(harness, () => http.expectOne('/api/v1/health').flush({ status: 'UP', db: 'UP' }));
    expect(labels(root, '.nav a')).not.toContain('Pedidos');
    expect(labels(root, '.mobile-nav a')).not.toContain('Pedidos');
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
