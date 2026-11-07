import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpEventType, HttpResponse } from '@angular/common/http';
import { Observable, filter, firstValueFrom, map, tap, TimeoutError, catchError, throwError, timeout } from 'rxjs';
import { environment } from './environment';
import { timeoutFor } from './timeouts';
import * as A from './api-models';

export interface ApiPage<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
export type ApiRole = 'OPERATOR' | 'COMPANY_ADMIN' | 'LICENSE_OWNER';
export interface ApiUser {
  id: string;
  companyId: string | null;
  email: string;
  firstName: string;
  lastName: string;
  position: string;
  role: ApiRole;
  status: 'ACTIVE' | 'INACTIVE';
  createdAt: string;
}
export interface LoginResult {
  token: string;
  tokenType: 'Bearer';
  expiresAt: string;
  user: ApiUser;
}
/** El `code` del `ApiError` del servidor (`{code, message, ...}`), o `null` si el error no vino de una respuesta con cuerpo. */
export function errorCode(error: unknown): string | null {
  return error instanceof HttpErrorResponse && typeof error.error?.code === 'string' ? error.error.code : null;
}
/**
 * Nombre de archivo de un `Content-Disposition` (`filename*=UTF-8''…` o `filename="…"`), sin rutas ni caracteres de control, o `fallback`
 * si no hay ninguno utilizable (FE-M8).
 */
export function filenameFromDisposition(header: string | null | undefined, fallback: string): string {
  const extended = /filename\*\s*=\s*(?:UTF-8|utf-8)''([^;]+)/.exec(header ?? '');
  const plain = /filename\s*=\s*(?:"([^"]*)"|([^;]+))/.exec(header ?? '');
  let name = extended?.[1] ?? plain?.[1] ?? plain?.[2] ?? '';
  try { name = extended ? decodeURIComponent(name) : name; } catch { name = ''; }
  name = name.trim().split(/[\\/]/).pop()!.replace(/[\u0000-\u001f\u007f<>:"|?*]/g, '').replace(/^\.+/, '');
  return name || fallback;
}
/** Qué decirle a la persona cuando el Gateway terminó su sesión (401), según el `code` (FE-M7). */
export function sessionEndMessage(error: unknown): string {
  switch (errorCode(error)) {
    case A.API_ERROR.TOKEN_EXPIRED: return 'Tu sesión venció. Inicia sesión de nuevo.';
    case A.API_ERROR.USER_INACTIVE: return 'Tu cuenta está inactiva. Consulta con el administrador de tu empresa.';
    default: return 'Tu sesión ya no es válida. Inicia sesión de nuevo.';
  }
}
export function errorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 401 && errorCode(error) === A.API_ERROR.USER_INACTIVE) return sessionEndMessage(error);
    if (error.status === 0) return 'No se pudo confirmar la respuesta del servidor. Conserva los datos y vuelve a intentarlo.';
    if (error.status === 401) return 'La sesión no es válida o las credenciales son incorrectas.';
    if (error.status === 403) return 'No tienes permiso para realizar esta acción.';
    if (error.status === 429) return 'Se alcanzó el límite de solicitudes. Espera unos segundos y vuelve a intentarlo.';
    return error.error?.message || 'No se pudo completar la operación.';
  }
  return error instanceof Error ? error.message : 'No se pudo completar la operación.';
}
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  /** Plazo según el tipo de operación (`timeouts.ts`). Al agotarse equivale a «sin respuesta» (status 0). */
  private readonly deadline = (ms: number) => <T>(source: import('rxjs').Observable<T>) => source.pipe(
    timeout({ first: ms }),
    catchError(error => throwError(() => error instanceof TimeoutError
      ? new HttpErrorResponse({ status: 0, statusText: 'Tiempo de espera agotado' }) : error)),
  );
  async download(path: string, filename: string): Promise<void> {
    let blob: Blob;
    try {
      const response = await firstValueFrom(this.http.get(environment.apiUrl + path, { responseType: 'blob', observe: 'response' }).pipe(this.deadline(timeoutFor('GET', path).ms)));
      blob = response.body!;
      filename = filenameFromDisposition(response.headers.get('Content-Disposition'), filename);
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.error instanceof Blob) {
        let message: unknown;
        try {
          message = JSON.parse(await error.error.text()).message;
        } catch { }
        if (typeof message === 'string') throw new Error(message);
      }
      throw error;
    }
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
  /**
   * `onProgress` (solo con `FormData`) recibe el porcentaje enviado (0–100). El plazo sigue siendo el de toda la operación: los eventos de
   * progreso no lo reinician, porque solo cuenta la respuesta final (FE-I2).
   */
  async request<T>(method: string, path: string, body?: unknown, idempotencyKey?: string, onProgress?: (percent: number) => void): Promise<T> {
    const requestId = idempotencyKey ?? (['GET', 'HEAD'].includes(method) ? undefined : crypto.randomUUID());
    const { ms } = timeoutFor(method, path, body);
    for (let attempt = 0; ; attempt++) {
      try {
        const headers: Record<string, string> = requestId ? { 'Idempotency-Key': requestId } : {};
        const call: Observable<T> = onProgress && body instanceof FormData
          ? this.http.request<T>(method, environment.apiUrl + path, { body, headers, observe: 'events', reportProgress: true }).pipe(
              tap(event => {
                if (event.type === HttpEventType.UploadProgress && event.total) onProgress(Math.min(100, Math.round((100 * event.loaded) / event.total)));
              }),
              filter((event): event is HttpResponse<T> => event.type === HttpEventType.Response),
              map(response => response.body as T),
            )
          : this.http.request<T>(method, environment.apiUrl + path, { body, headers });
        return await firstValueFrom(call.pipe(this.deadline(ms)));
      } catch (error) {
        if (
          !(error instanceof HttpErrorResponse) ||
          error.status !== 429 ||
          method !== 'GET' ||
          attempt >= 4
        )
          throw error;
        const seconds = Number(error.headers.get('Retry-After')) || 2 ** attempt;
        await new Promise((resolve) =>
          setTimeout(resolve, Math.min(60, Math.max(1, seconds)) * 1000),
        );
      }
    }
  }
  async all<T>(path: string): Promise<T[]> {
    const items: T[] = [];
    for (let page = 0; ; page++) {
      const result = await this.request<ApiPage<T>>(
        'GET',
        path + (path.includes('?') ? '&' : '?') + 'size=100&page=' + page,
      );
      items.push(...result.items);
      if (page + 1 >= result.totalPages) return items;
    }
  }
}
