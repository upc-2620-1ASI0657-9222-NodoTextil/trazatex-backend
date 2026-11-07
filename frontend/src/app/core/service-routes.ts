/**
 * Catálogo de TODAS las rutas `/api/**` que exponen los servicios (sin el prefijo `/api`), con la clase de plazo que
 * debe tener cada una según `timeouts.ts`. Solo lo usan las pruebas y `scripts/check-routes.mjs`; no entra en el bundle.
 *
 * - `npm test` comprueba que cada ruta cae en la clase indicada y que ninguna regla de `timeouts.ts` queda sin uso.
 * - `npm run check:routes` compara este catálogo con los controladores de `../backend` y falla si el backend tiene una
 *   ruta que no está aquí (es decir, una ruta nueva que nadie ha clasificado).
 *
 * Mantén una entrada por línea y con este formato exacto: el script la lee con una expresión regular.
 */
export type ClasePlazo = 'estandar' | 'subida' | 'alta-empresa' | 'pdf' | 'quality';

export interface RutaServicio {
  readonly method: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  readonly path: string;
  readonly clase: ClasePlazo;
  /** La petición lleva `FormData`. */
  readonly multipart?: true;
}

export const SERVICE_ROUTES: readonly RutaServicio[] = [
  // organization-access
  { method: 'POST', path: '/auth/login', clase: 'estandar' },
  { method: 'GET', path: '/admin-requests', clase: 'estandar' },
  { method: 'POST', path: '/admin-requests', clase: 'estandar' },
  { method: 'PATCH', path: '/admin-requests/{id}/decision', clase: 'estandar' },
  { method: 'GET', path: '/companies', clase: 'estandar' },
  { method: 'POST', path: '/companies', clase: 'alta-empresa' },
  { method: 'GET', path: '/companies/current/capacity', clase: 'estandar' },
  { method: 'PATCH', path: '/companies/{id}/status', clase: 'estandar' },
  { method: 'GET', path: '/invitations', clase: 'estandar' },
  { method: 'POST', path: '/invitations/operators', clase: 'estandar' },
  { method: 'POST', path: '/invitations/{id}/cancel', clase: 'estandar' },
  { method: 'GET', path: '/invitations/{token}', clase: 'estandar' },
  { method: 'POST', path: '/invitations/{token}/accept', clase: 'estandar' },
  { method: 'GET', path: '/license', clase: 'estandar' },
  { method: 'GET', path: '/privacy-settings', clase: 'estandar' },
  { method: 'PUT', path: '/privacy-settings', clase: 'estandar' },
  { method: 'GET', path: '/users', clase: 'estandar' },
  { method: 'PATCH', path: '/users/{id}/status', clase: 'estandar' },
  // production
  { method: 'GET', path: '/batches', clase: 'estandar' },
  { method: 'POST', path: '/batches', clase: 'estandar' },
  { method: 'GET', path: '/batches/qr/{qrCode}', clase: 'estandar' },
  { method: 'POST', path: '/batches/{id}/split', clase: 'estandar' },
  { method: 'GET', path: '/batches/{id}', clase: 'estandar' },
  { method: 'PUT', path: '/batches/{id}/commercial-data', clase: 'estandar' },
  { method: 'GET', path: '/batches/{id}/evidence', clase: 'estandar' },
  { method: 'POST', path: '/batches/{id}/evidence', clase: 'subida', multipart: true },
  { method: 'DELETE', path: '/batches/{id}/evidence/{evidenceId}', clase: 'estandar' },
  { method: 'GET', path: '/machines', clase: 'estandar' },
  { method: 'POST', path: '/machines', clase: 'estandar' },
  { method: 'GET', path: '/machines/{id}', clase: 'estandar' },
  { method: 'PATCH', path: '/machines/{id}/status', clase: 'estandar' },
  { method: 'GET', path: '/transfers', clase: 'estandar' },
  { method: 'POST', path: '/transfers', clase: 'estandar' },
  { method: 'GET', path: '/transfers/recipients', clase: 'estandar' },
  { method: 'GET', path: '/transfers/{id}', clase: 'estandar' },
  { method: 'POST', path: '/transfers/{id}/accept', clase: 'estandar' },
  { method: 'POST', path: '/transfers/{id}/reject', clase: 'estandar' },
  { method: 'GET', path: '/transformations', clase: 'estandar' },
  { method: 'POST', path: '/transformations', clase: 'estandar' },
  { method: 'GET', path: '/transformations/{id}', clase: 'estandar' },
  { method: 'POST', path: '/transformations/{id}/complete', clase: 'estandar' },
  { method: 'GET', path: '/transformations/{id}/evidence', clase: 'estandar' },
  { method: 'POST', path: '/transformations/{id}/evidence', clase: 'subida', multipart: true },
  { method: 'DELETE', path: '/transformations/{id}/evidence/{evidenceId}', clase: 'estandar' },
  // traceability
  { method: 'GET', path: '/traceability/lineages', clase: 'estandar' },
  { method: 'GET', path: '/traceability/batches/{batchId}/ancestors', clase: 'estandar' },
  { method: 'GET', path: '/traceability/batches/{batchId}/descendants', clase: 'estandar' },
  { method: 'GET', path: '/traceability/batches/{batchId}/lineage', clase: 'estandar' },
  // quality
  { method: 'GET', path: '/quality/batches', clase: 'estandar' },
  { method: 'POST', path: '/quality/batches/{batchId}/controls', clase: 'quality' },
  { method: 'GET', path: '/quality/controls', clase: 'estandar' },
  { method: 'GET', path: '/quality/controls/{controlId}', clase: 'estandar' },
  { method: 'POST', path: '/quality/controls/{controlId}/finish', clase: 'quality' },
  { method: 'GET', path: '/quality/controls/{controlId}/report', clase: 'pdf' },
  { method: 'POST', path: '/quality/controls/{controlId}/tests', clase: 'quality' },
  { method: 'GET', path: '/quality/controls/{id}/evidence', clase: 'estandar' },
  { method: 'DELETE', path: '/quality/controls/{id}/evidence/{evidenceId}', clase: 'quality' },
  { method: 'POST', path: '/quality/evidence', clase: 'subida', multipart: true },
  { method: 'GET', path: '/quality/failures', clase: 'estandar' },
  { method: 'GET', path: '/quality/failures/{failureId}', clase: 'estandar' },
  { method: 'PUT', path: '/quality/failures/{failureId}', clase: 'quality' },
  { method: 'POST', path: '/quality/failures/{failureId}/discard', clase: 'quality' },
  { method: 'POST', path: '/quality/failures/{failureId}/reevaluation', clase: 'quality' },
  { method: 'GET', path: '/quality/failures/{id}/evidence', clase: 'estandar' },
  { method: 'DELETE', path: '/quality/failures/{id}/evidence/{evidenceId}', clase: 'quality' },
  // notification
  { method: 'GET', path: '/notifications', clase: 'estandar' },
  { method: 'PUT', path: '/notifications/read-all', clase: 'estandar' },
  { method: 'GET', path: '/notifications/unread-count', clase: 'estandar' },
  // una inexistente o ajena responde 404 `NOTIFICATION_NOT_FOUND` (ver API_ERROR en api-models.ts)
  { method: 'PATCH', path: '/notifications/{id}/read', clase: 'estandar' },
  { method: 'PUT', path: '/notifications/{id}/read', clase: 'estandar' },
  // analytics
  { method: 'GET', path: '/analytics/summary', clase: 'estandar' },
];
