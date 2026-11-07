import { inject } from '@angular/core';
import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { SessionService } from './session.service';
import { sessionEndMessage } from './api.service';
import { environment } from './environment';
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  if (!request.url.startsWith(environment.apiUrl + '/')) return next(request);
  const session = inject(SessionService);
  const token = session.token();
  const authenticated = token
    ? request.clone({ setHeaders: { Authorization: 'Bearer ' + token } })
    : request;
  return next(authenticated).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && !request.url.endsWith('/auth/login')) session.expire(sessionEndMessage(error));
      return throwError(() => error);
    }),
  );
};
