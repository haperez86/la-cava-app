import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthService, LOGIN_URL } from '../../core/auth/auth.service';
import { Role } from '../../core/auth/role';
import { NETWORK_ERROR } from '../../core/http/problem';
import { Login } from './login';

const settle = () => new Promise((resolve) => setTimeout(resolve, 0));

describe('Login', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [Login],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  async function fillAndSubmit(root: HTMLElement, username: string, password: string): Promise<void> {
    const user = root.querySelector<HTMLInputElement>('#login-username')!;
    const pass = root.querySelector<HTMLInputElement>('#login-password')!;
    user.value = username;
    user.dispatchEvent(new Event('input'));
    pass.value = password;
    pass.dispatchEvent(new Event('input'));
    root.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    await settle();
  }

  const loginResponse = (role: Role) => ({
    token: 'jwt',
    expiresAt: new Date(Date.now() + 36_000_000).toISOString(),
    user: { id: 1, fullName: 'Usuario de prueba', role },
  });

  it.each<[Role, string]>([
    ['ADMIN', '/dashboard'],
    ['CASHIER', '/dashboard'],
    ['WAITER', '/mesas'],
    ['KITCHEN', '/preparacion'],
  ])('CA1 %s logs in with valid credentials and goes to %s', async (role, home) => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(Login);
    await fixture.whenStable();
    const root = fixture.nativeElement as HTMLElement;

    await fillAndSubmit(root, 'usuario', 'clave123');
    const req = http.expectOne(LOGIN_URL);
    expect(req.request.body).toEqual({ username: 'usuario', password: 'clave123' });
    req.flush(loginResponse(role));
    await settle();
    await fixture.whenStable();

    expect(TestBed.inject(AuthService).isSessionValid()).toBe(true);
    expect(navigate).toHaveBeenCalledWith(home);
  });

  it('CA2 shows "Usuario o contraseña incorrectos" on invalid credentials or inactive user (401)', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl');
    const fixture = TestBed.createComponent(Login);
    await fixture.whenStable();
    const root = fixture.nativeElement as HTMLElement;

    await fillAndSubmit(root, 'admin', 'mala');
    http.expectOne(LOGIN_URL).flush(
      { status: 401, detail: 'Usuario o contraseña incorrectos' },
      { status: 401, statusText: 'Unauthorized' },
    );
    await settle();
    await fixture.whenStable();

    expect(root.querySelector('[data-testid="login-error"]')?.textContent).toContain('Usuario o contraseña incorrectos');
    expect(TestBed.inject(AuthService).hasSession()).toBe(false);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('shows the La Cava logo as an accessible inline SVG above the "Iniciar sesión" heading', async () => {
    const fixture = TestBed.createComponent(Login);
    await fixture.whenStable();
    const root = fixture.nativeElement as HTMLElement;

    const svg = root.querySelector('.login-card .brand app-brand-logo svg[role="img"]');
    expect(svg).not.toBeNull();
    expect(svg?.getAttribute('aria-label')).toBe('La Cava, copas y tablas');
    expect(root.querySelector('.brand small')).toBeNull();
    expect(root.querySelector('h1')?.textContent?.trim()).toBe('Iniciar sesión');
  });

  it('does not call the API with empty fields and shows the field errors', async () => {
    const fixture = TestBed.createComponent(Login);
    await fixture.whenStable();
    const root = fixture.nativeElement as HTMLElement;

    root.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    await settle();
    await fixture.whenStable();

    http.expectNone(LOGIN_URL);
    expect(root.querySelectorAll('.field-error').length).toBe(2);
    expect(root.textContent).toContain('Ingresa tu usuario.');
  });

  it('reports a connection problem instead of invalid credentials when the server is unreachable', async () => {
    const fixture = TestBed.createComponent(Login);
    await fixture.whenStable();
    const root = fixture.nativeElement as HTMLElement;

    await fillAndSubmit(root, 'admin', 'admin123');
    http.expectOne(LOGIN_URL).error(new ProgressEvent('error'));
    await settle();
    await fixture.whenStable();

    expect(root.querySelector('[data-testid="login-error"]')?.textContent).toContain(NETWORK_ERROR);
  });
});
