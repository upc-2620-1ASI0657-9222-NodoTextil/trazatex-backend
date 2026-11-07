import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { fakeIndexedDB } from '../testing/fake-indexeddb';
import { DraftStorage, OPERATION_TTL_MS, StaleOperationError } from './draft-storage.service';

describe('Solicitudes pendientes y claves de idempotencia (ADR-010)', () => {
  let idb: ReturnType<typeof fakeIndexedDB>;
  let storage: DraftStorage;
  const records = () => [...idb.data.entries()].filter(([key]) => key.startsWith('operation/'));
  const age = (hours: number) => { for (const [, value] of records()) value.createdAt = Date.now() - hours * 3600000; };
  beforeEach(() => {
    idb = fakeIndexedDB();
    vi.stubGlobal('indexedDB', idb);
    storage = TestBed.inject(DraftStorage);
  });
  afterEach(() => vi.unstubAllGlobals());

  it('la vigencia en el cliente es de 23 h, una menos que las claves del servidor', () => {
    expect(OPERATION_TTL_MS).toBe(23 * 3600000);
  });

  it('repite la misma Idempotency-Key mientras la solicitud sigue vigente', async () => {
    const first = await storage.requestId('u1', 'POST', '/transfers/t1/accept', {});
    age(22);
    const retry = await storage.requestId('u1', 'POST', '/transfers/t1/accept', {});
    expect(retry.id).toBe(first.id);
    expect(retry.fingerprint).toBe(first.fingerprint);
  });

  it('una solicitud sin formulario de más de 23 h se descarta, avisa y la siguiente usa una clave nueva', async () => {
    const first = await storage.requestId('u1', 'POST', '/transfers/t1/accept', {});
    age(24);
    await expect(storage.requestId('u1', 'POST', '/transfers/t1/accept', {})).rejects.toBeInstanceOf(StaleOperationError);
    await expect(storage.requestId('u1', 'POST', '/transfers/t1/accept', {})).resolves.toBeDefined();
    expect(records()).toHaveLength(1);
    const fresh = records()[0][1];
    expect(fresh.id).not.toBe(first.id);
    expect(Date.now() - fresh.createdAt).toBeLessThan(60000);
  });

  it('el aviso dice que la operación pudo haberse registrado y que hay que revisar antes de repetir', async () => {
    await storage.requestId('u1', 'POST', '/batches/b1/discard', {});
    age(30);
    const error = await storage.requestId('u1', 'POST', '/batches/b1/discard', {}).catch(e => e);
    expect(error.message).toContain('pudo haberse registrado');
    expect(error.message).toContain('Revisa');
  });

  it('el borde de 23 h: 22 h 59 min se reutiliza, 23 h 01 min caduca', async () => {
    await storage.requestId('u1', 'PATCH', '/machines/m1/status', { status: 'ACTIVE' });
    for (const [, value] of records()) value.createdAt = Date.now() - (23 * 3600000 - 60000);
    await expect(storage.requestId('u1', 'PATCH', '/machines/m1/status', { status: 'ACTIVE' })).resolves.toBeDefined();
    for (const [, value] of records()) value.createdAt = Date.now() - (23 * 3600000 + 60000);
    await expect(storage.requestId('u1', 'PATCH', '/machines/m1/status', { status: 'ACTIVE' })).rejects.toBeInstanceOf(StaleOperationError);
  });

  it('solo se descartan las solicitudes caducadas de esa acción: las de otras acciones siguen intactas', async () => {
    await storage.requestId('u1', 'POST', '/transfers/t1/accept', {});
    await storage.requestId('u1', 'POST', '/transfers/t2/accept', {});
    const [[staleKey, stale]] = records();
    stale.createdAt = Date.now() - 25 * 3600000;
    const survivors = records().filter(([key]) => key !== staleKey).map(([key]) => key);
    await expect(storage.requestId('u1', 'POST', stale.path, {})).rejects.toBeInstanceOf(StaleOperationError);
    expect(records().map(([key]) => key)).toEqual(survivors);
  });

  describe('con un formulario activo (contexto)', () => {
    const open = (context: string) => storage.activate('form/u1/batch', context, async () => undefined);

    it('una solicitud anterior con otros datos sigue bloqueando mientras está vigente', async () => {
      open('ctx-1');
      await storage.requestId('u1', 'POST', '/batches', { name: 'A' });
      age(2);
      await expect(storage.requestId('u1', 'POST', '/batches', { name: 'B' })).rejects.toThrow(/solicitud anterior con otros datos/);
    });

    it('si esa solicitud con otros datos caducó, ya no bloquea: avisa y luego deja registrar', async () => {
      open('ctx-1');
      await storage.requestId('u1', 'POST', '/batches', { name: 'A' });
      age(25);
      await expect(storage.requestId('u1', 'POST', '/batches', { name: 'B' })).rejects.toBeInstanceOf(StaleOperationError);
      const next = await storage.requestId('u1', 'POST', '/batches', { name: 'B' });
      expect(next.id).toBeTruthy();
      expect(records().every(([, value]) => Date.now() - value.createdAt < 60000)).toBe(true);
    });
  });
});

describe('Borradores al cerrar sesión o expirar (FE-M5)', () => {
  let idb: ReturnType<typeof fakeIndexedDB>;
  let storage: DraftStorage;
  const claves = () => [...idb.data.keys()].sort();
  beforeEach(() => {
    idb = fakeIndexedDB();
    vi.stubGlobal('indexedDB', idb);
    storage = TestBed.inject(DraftStorage);
  });
  afterEach(() => vi.unstubAllGlobals());

  it('borra los borradores de formulario y las confirmaciones, y conserva solo las claves de idempotencia', async () => {
    await storage.write('form/u1/lotes/nuevo', { foto: 'bytes' });
    await storage.write('form/u2/transferencias', { x: 1 });
    await storage.write('confirmed/ctx-1', { resultado: 'ok' });
    await storage.requestId('u1', 'POST', '/transfers/t1/accept', {});
    expect(claves()).toHaveLength(4);
    await storage.clearDrafts();
    expect(claves()).toHaveLength(1);
    expect(claves()[0]).toMatch(/^operation\//);
  });

  it('olvida los formularios activos y el aviso de recuperación', async () => {
    const flush = vi.fn(async () => undefined);
    storage.activate('form/u1/x', 'ctx', flush);
    storage.notice.set('Se recuperó el borrador de este formulario.');
    await storage.clearDrafts();
    expect(storage.notice()).toBe('');
    expect((storage as unknown as { forms: Map<string, unknown> }).forms.size).toBe(0);
  });

  it('sin nada guardado no falla', async () => {
    await expect(storage.clearDrafts()).resolves.toBeUndefined();
  });
});
