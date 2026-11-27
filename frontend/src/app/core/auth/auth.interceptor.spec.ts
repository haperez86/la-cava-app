import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { ToastService } from '../notifications/toast.service';
import { authInterceptor } from './auth.interceptor';
import { AuthService, LOGIN_URL } from './auth.service';
import { storeTestSession } from './testing';

describe('authInterceptor', () => {
  let http: HttpClient;
  let controller: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    storeTestSession('ADMIN');
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    controller.verify();
    localStorage.clear();
  });

  it('adds the bearer token to API calls but not to the login call', () => {
    http.get('/api/v1/users').subscribe();
    expect(controller.expectOne('/api/v1/users').request.headers.get('Authorization')).toBe('Bearer token-admin');

    http.post(LOGIN_URL, {}).subscribe({ error: () => undefined });
    expect(controller.expectOne(LOGIN_URL).request.headers.has('Authorization')).toBe(false);
  });

  it('on 401 logs out, shows a notice and returns to the login', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const toast = TestBed.inject(ToastService);

    http.get('/api/v1/auth/me').subscribe({ error: () => undefined });
    controller.expectOne('/api/v1/auth/me').flush(
      { status: 401, detail: 'Tu sesión expiró o no es válida. Inicia sesión de nuevo.' },
      { status: 401, statusText: 'Unauthorized' },
    );

    expect(TestBed.inject(AuthService).hasSession()).toBe(false);
    expect(navigate).toHaveBeenCalledWith('/login');
    expect(toast.toasts()[0]).toMatchObject({ kind: 'error' });
  });

  it('CA4 on 403 shows an error toast and keeps the session', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const toast = TestBed.inject(ToastService);

    http.get('/api/v1/users').subscribe({ error: () => undefined });
    controller.expectOne('/api/v1/users').flush(
      { status: 403, detail: 'No tienes permiso para esta acción.' },
      { status: 403, statusText: 'Forbidden' },
    );

    expect(TestBed.inject(AuthService).hasSession()).toBe(true);
    expect(navigate).not.toHaveBeenCalled();
    expect(toast.toasts()[0]).toMatchObject({ kind: 'error', message: 'No tienes permiso para esta acción.' });
  });

  it('does not log out on a 401 from the login itself', () => {
    http.post(LOGIN_URL, {}).subscribe({ error: () => undefined });
    controller.expectOne(LOGIN_URL).flush({ detail: 'Usuario o contraseña incorrectos' }, { status: 401, statusText: 'Unauthorized' });

    expect(TestBed.inject(AuthService).hasSession()).toBe(true);
  });
});
