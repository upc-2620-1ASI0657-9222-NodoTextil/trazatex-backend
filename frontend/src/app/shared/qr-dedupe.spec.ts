import { QR_RETRY_AFTER_MS, RejectedQrMemory } from './qr-dedupe';

describe('Memoria de QR rechazados (FE-I1)', () => {
  it('la pausa por defecto es de 5 s', () => {
    expect(QR_RETRY_AFTER_MS).toBe(5000);
  });

  it('un valor nunca rechazado siempre se procesa', () => {
    const memory = new RejectedQrMemory();
    expect(memory.shouldProcess('https://a/lotes/TT-1', 0)).toBe(true);
  });

  it('el mismo valor rechazado se ignora durante la pausa', () => {
    const memory = new RejectedQrMemory();
    memory.reject('QR-MALO', 1000);
    expect(memory.shouldProcess('QR-MALO', 1000)).toBe(false);
    expect(memory.shouldProcess('QR-MALO', 1000 + 4999)).toBe(false);
  });

  it('pasada la pausa vuelve a aceptar el mismo valor', () => {
    const memory = new RejectedQrMemory();
    memory.reject('QR-MALO', 1000);
    expect(memory.shouldProcess('QR-MALO', 1000 + 5000)).toBe(true);
  });

  it('un valor distinto se procesa de inmediato', () => {
    const memory = new RejectedQrMemory();
    memory.reject('QR-MALO', 1000);
    expect(memory.shouldProcess('QR-OTRO', 1001)).toBe(true);
  });

  it('reset() vuelve a aceptar el mismo valor al instante', () => {
    const memory = new RejectedQrMemory();
    memory.reject('QR-MALO', 1000);
    memory.reset();
    expect(memory.shouldProcess('QR-MALO', 1001)).toBe(true);
  });

  it('un segundo rechazo reinicia la pausa', () => {
    const memory = new RejectedQrMemory();
    memory.reject('QR-MALO', 0);
    memory.reject('QR-MALO', 6000);
    expect(memory.shouldProcess('QR-MALO', 10000)).toBe(false);
    expect(memory.shouldProcess('QR-MALO', 11000)).toBe(true);
  });
});
