import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Home } from '../../features/home/home';
import { Shell } from './shell';

/** LCV-HU-00 · CA2: el frontend muestra el layout y consume /health. */
describe('Shell', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: '', component: Shell, children: [{ path: 'inicio', component: Home }] }]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('CA2 renders sidebar, bottom nav and the API status after a successful /health call', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/inicio');
    http.expectOne('/api/v1/health').flush({ status: 'UP', db: 'UP' });
    harness.detectChanges();
    await harness.fixture.whenStable();

    const root = harness.fixture.nativeElement as HTMLElement;
    expect(root.querySelector('.brand h1')?.textContent).toContain('LA CAVA');
    expect(root.querySelectorAll('.nav a').length).toBe(1);
    expect(root.querySelectorAll('.mobile-nav a').length).toBe(1);
    expect(root.querySelector('[data-testid="api-status"]')?.textContent).toContain('API conectada');
    expect(root.querySelector('[data-testid="home-health"]')?.textContent).toContain('operativas');
  });

  it('CA2 shows "API sin conexión" when /health fails', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/inicio');
    http.expectOne('/api/v1/health').flush(null, { status: 503, statusText: 'Service Unavailable' });
    harness.detectChanges();
    await harness.fixture.whenStable();

    const root = harness.fixture.nativeElement as HTMLElement;
    expect(root.querySelector('[data-testid="api-status"]')?.textContent).toContain('API sin conexión');
  });
});
