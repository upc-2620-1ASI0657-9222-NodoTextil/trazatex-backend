// Solo para pruebas (lo importan los *.spec.ts). Sin dependencias; no entra en el bundle de la aplicación.
/* eslint-disable @typescript-eslint/no-explicit-any */
/** IndexedDB mínimo en memoria: lo justo para `get/put/delete/getAll/openCursor` de un solo almacén. */
export function fakeIndexedDB() {
  const data = new Map<string, any>();
  const transaction = () => {
    let pending = 0;
    const tx: any = { oncomplete: null, onabort: null, onerror: null };
    const done = () => { if (--pending === 0) setTimeout(() => tx.oncomplete?.(), 0); };
    const schedule = (request: any, run: () => unknown) => {
      pending++;
      setTimeout(() => { request.result = run(); request.onsuccess?.(); done(); }, 0);
      return request;
    };
    const cursor = () => {
      const keys = [...data.keys()].sort();
      let index = 0;
      const request: any = { result: null, onsuccess: null };
      const advance = () => schedule(request, () => {
        const key = keys[index++];
        return key === undefined ? null : { key, value: data.get(key), delete: () => data.delete(key), continue: advance };
      });
      advance();
      return request;
    };
    const objectStore = {
      get: (key: string) => schedule({ result: undefined }, () => data.get(key)),
      put: (value: unknown, key: string) => schedule({ result: undefined }, () => { data.set(key, value); return key; }),
      delete: (key: string) => schedule({ result: undefined }, () => { data.delete(key); }),
      getAll: () => schedule({ result: undefined }, () => [...data.values()]),
      openCursor: cursor,
    };
    tx.objectStore = () => objectStore;
    return tx;
  };
  const db = { createObjectStore: () => undefined, transaction };
  return {
    data,
    open: () => {
      const request: any = { result: db };
      setTimeout(() => { request.onupgradeneeded?.(); request.onsuccess?.(); }, 0);
      return request;
    },
  };
}
