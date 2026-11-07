import { Injectable, signal } from '@angular/core';

/** Vigencia de una solicitud pendiente en el cliente: 1 h menos que las claves de idempotencia del servidor (24 h). */
export const OPERATION_TTL_MS = 23 * 3600000;

/** Una solicitud pendiente caducó: pudo haberse registrado y hay que revisarlo antes de repetirla. */
export class StaleOperationError extends Error {
  constructor() {
    super('La operación anterior se envió hace más de 23 horas y pudo haberse registrado. Revisa el listado o el historial antes de repetirla; si la repites, se enviará como una operación nueva.');
    this.name = 'StaleOperationError';
  }
}

@Injectable({ providedIn: 'root' })
export class DraftStorage {
  private readonly forms = new Map<string, { context: string; flush: () => Promise<void> }>();
  activate(key: string, context: string, flush: () => Promise<void>) { this.forms.delete(key); this.forms.set(key, { context, flush }); }
  deactivate(key: string) { this.forms.delete(key); }
  readonly notice = signal('');
  private database?: Promise<IDBDatabase>;

  private open(): Promise<IDBDatabase> {
    return this.database ??= new Promise((resolve, reject) => {
      const request = indexedDB.open('trazatex-drafts', 1);
      request.onupgradeneeded = () => request.result.createObjectStore('drafts');
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(new Error('No se pudo abrir el almacenamiento de borradores.'));
    });
  }

  private async transaction<T>(mode: IDBTransactionMode, run: (store: IDBObjectStore) => IDBRequest<T>): Promise<T> {
    const db = await this.open();
    return new Promise<T>((resolve, reject) => {
      const tx = db.transaction('drafts', mode);
      const request = run(tx.objectStore('drafts'));
      tx.oncomplete = () => resolve(request.result);
      tx.onabort = () => reject(new Error('No se pudo conservar el borrador. Comprueba el almacenamiento del navegador.'));
      tx.onerror = () => reject(new Error('No se pudo conservar el borrador. Comprueba el almacenamiento del navegador.'));
    });
  }

  async read<T>(key: string): Promise<T | undefined> {
    return this.transaction('readonly', store => store.get(key)) as Promise<T | undefined>;
  }
  async write(key: string, value: unknown): Promise<void> {
    await this.transaction('readwrite', store => store.put(value, key));
  }
  async remove(key: string): Promise<void> {
    await this.transaction('readwrite', store => store.delete(key));
  }
  async recoverForm<T extends object>(key: string): Promise<T & { __operationId?: string } | undefined> {
    const value = await this.read<T & { __operationId?: string }>(key);
    if (!value?.__operationId) return value;
    const pending = await this.transaction('readonly', store => store.getAll());
    if (!pending.some(item => item?.id && item?.context === value.__operationId)) return value;
    return await this.read<T & { __operationId?: string }>('confirmed/' + value.__operationId) ?? value;
  }
  async clearForm(key: string, context: string): Promise<void> {
    const db = await this.open();
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction('drafts', 'readwrite'), store = tx.objectStore('drafts');
      const cursor = store.openCursor();
      cursor.onsuccess = () => {
        const row = cursor.result;
        if (!row) return;
        if (row.key === key || row.key === 'confirmed/' + context ||
            (String(row.key).startsWith('operation/') && row.value?.context === context)) row.delete();
        row.continue();
      };
      tx.oncomplete = () => resolve();
      tx.onabort = () => reject(new Error('No se pudo cerrar el borrador guardado.'));
      tx.onerror = () => reject(new Error('No se pudo cerrar el borrador guardado.'));
    });
  }
  /**
   * FE-M5: al cerrar sesión o expirar, borra los borradores de formularios (con sus fotos) y las confirmaciones guardadas, para que no
   * queden en el disco de un equipo compartido. Se CONSERVAN los registros `operation/*`: son solo una clave de idempotencia y una huella
   * (sin datos ni archivos) y evitan duplicar una operación cuyo resultado se desconoce si la persona vuelve a entrar y reintenta.
   */
  async clearDrafts(): Promise<void> {
    this.forms.clear();
    this.notice.set('');
    const db = await this.open();
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction('drafts', 'readwrite'), cursor = tx.objectStore('drafts').openCursor();
      cursor.onsuccess = () => {
        const row = cursor.result;
        if (!row) return;
        if (!String(row.key).startsWith('operation/')) row.delete();
        row.continue();
      };
      tx.oncomplete = () => resolve();
      tx.onabort = () => reject(new Error('No se pudieron borrar los borradores guardados.'));
      tx.onerror = () => reject(new Error('No se pudieron borrar los borradores guardados.'));
    });
  }
  /** Borra los registros `operation/*` caducados de esta huella o de este contexto. Devuelve si borró alguno. */
  private async purgeExpired(fingerprint: string, context?: string): Promise<boolean> {
    const db = await this.open();
    let purged = false;
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction('drafts', 'readwrite'), store = tx.objectStore('drafts');
      const cursor = store.openCursor();
      cursor.onsuccess = () => {
        const row = cursor.result;
        if (!row) return;
        const value = row.value as { createdAt?: number; context?: string } | undefined;
        if (String(row.key).startsWith('operation/') && typeof value?.createdAt === 'number' &&
            Date.now() - value.createdAt > OPERATION_TTL_MS &&
            (row.key === fingerprint || (context !== undefined && value.context === context))) {
          row.delete();
          purged = true;
        }
        row.continue();
      };
      tx.oncomplete = () => resolve();
      tx.onabort = () => reject(new Error('No se pudo revisar las solicitudes pendientes. Comprueba el almacenamiento del navegador.'));
      tx.onerror = () => reject(new Error('No se pudo revisar las solicitudes pendientes. Comprueba el almacenamiento del navegador.'));
    });
    return purged;
  }
  async requestId(userId: string, method: string, path: string, payload: unknown, multipart = false): Promise<{ fingerprint: string; id: string; context?: string }> {
    const active = [...this.forms.entries()].reverse().find(([key]) => key.startsWith('form/' + userId + '/'));
    await active?.[1].flush();
    const context = active?.[1].context;
    const bytes = new TextEncoder().encode(JSON.stringify([userId, context, method, path, payload]));
    const hash = [...new Uint8Array(await crypto.subtle.digest('SHA-256', bytes))].map(n => n.toString(16).padStart(2, '0')).join('');
    const fingerprint = 'operation/' + hash;
    // Las claves del servidor caducan a las 24 h (ADR-010): un registro de más de 23 h ya no se puede reutilizar.
    // Se descarta (el suyo y los de la misma pantalla que bloquearían esta acción) y se avisa; el siguiente intento
    // parte de cero con una clave nueva, una vez que el usuario ha podido revisar si la operación anterior se registró.
    if (await this.purgeExpired(fingerprint, context)) throw new StaleOperationError();
    const previous = await this.read<{ id: string; createdAt: number; context?: string }>(fingerprint);
    const entries = context ? await this.transaction('readonly', store => store.getAll()) : [];
    const pending = entries.filter(item => item?.id && item?.context === context);
    if (context && !previous && !multipart) {
      if (pending.some(item => item?.method === method && item?.path === path))
        throw new Error('Hay una solicitud anterior con otros datos. Recupera el borrador o consulta su resultado en el listado antes de registrar otra operación.');
    }
    if (active && pending.length === 0)
      await this.write('confirmed/' + context, await this.read(active[0]));
    const value = previous ?? { id: crypto.randomUUID(), createdAt: Date.now(), context, method, path };
    await this.write(fingerprint, value);
    return { fingerprint, id: value.id, context };
  }
}
