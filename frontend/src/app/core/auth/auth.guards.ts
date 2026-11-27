import { inject } from '@angular/core';
import { CanActivateChildFn, CanActivateFn, RedirectFunction, Router } from '@angular/router';
import { ToastService } from '../notifications/toast.service';
import { AuthService, SESSION_EXPIRED_MESSAGE } from './auth.service';
import { Role, homePathFor } from './role';

/** Exige una sesión vigente; si venció, avisa y lleva al login. */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  if (auth.isSessionValid()) {
    return true;
  }
  if (auth.hasSession()) {
    auth.logout();
    inject(ToastService).error(SESSION_EXPIRED_MESSAGE);
  }
  return inject(Router).createUrlTree(['/login']);
};

/** El login solo se muestra sin sesión vigente; con sesión lleva a la pantalla inicial del rol. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isSessionValid() ? inject(Router).createUrlTree([homePathFor(auth.role())]) : true;
};

/**
 * Rutas con `data.roles`: si el rol del usuario no está incluido lo redirige a SU pantalla inicial
 * (LCV-HU-01 CA3). Solo oculta pantallas; la autorización real la aplica el backend.
 */
export const roleGuard: CanActivateChildFn = (childRoute) => {
  const roles = childRoute.data['roles'] as readonly Role[] | undefined;
  const role = inject(AuthService).role();
  if (!roles || (role !== null && roles.includes(role))) {
    return true;
  }
  return inject(Router).createUrlTree([homePathFor(role)]);
};

/** Destino de `/` y de rutas desconocidas: pantalla inicial del rol, o /login sin sesión. */
export const homeRedirect: RedirectFunction = () => {
  const auth = inject(AuthService);
  return auth.isSessionValid() ? homePathFor(auth.role()) : '/login';
};
