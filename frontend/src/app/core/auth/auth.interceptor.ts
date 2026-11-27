import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { problemMessage } from '../http/problem';
import { ToastService } from '../notifications/toast.service';
import { AuthService, LOGIN_URL, SESSION_EXPIRED_MESSAGE } from './auth.service';

export const FORBIDDEN_MESSAGE = 'No tienes permiso para realizar esta acción.';

/**
 * Agrega el JWT a las llamadas a /api. Un 401 (token vencido, inválido o usuario inactivo) cierra
 * la sesión y vuelve al login con aviso; un 403 solo muestra un toast de error y conserva la sesión.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const toast = inject(ToastService);

  const isLogin = req.url === LOGIN_URL;
  const token = auth.token();
  const request = req.url.startsWith('/api/') && !isLogin && token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(request).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && !isLogin) {
        if (error.status === 401 && auth.hasSession()) {
          auth.logout();
          toast.error(problemMessage(error, SESSION_EXPIRED_MESSAGE));
          void router.navigateByUrl('/login');
        } else if (error.status === 403) {
          toast.error(problemMessage(error, FORBIDDEN_MESSAGE));
        }
      }
      return throwError(() => error);
    }),
  );
};
