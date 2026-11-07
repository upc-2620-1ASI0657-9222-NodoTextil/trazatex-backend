import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { StoreService } from '../core/store.service';
import { QR_READER, QrScannerComponent } from './qr-scanner.component';

/** Lector falso: guarda el callback que la cámara real llamaría una vez por fotograma. */
class FakeReader {
  callback?: (result: { getText(): string } | undefined, error: unknown, controls: { stop(): void }) => void;
  readonly controls = { stop: vi.fn() };
  async decodeFromVideoDevice(_device: unknown, _video: unknown, callback: FakeReader['callback']) {
    this.callback = callback;
    return this.controls;
  }
  /** La cámara ve el mismo código en `frames` fotogramas seguidos. */
  see(text: string, frames = 1) {
    for (let i = 0; i < frames; i++) this.callback?.({ getText: () => text }, undefined, this.controls);
  }
}

describe('Lector de QR: sin repetir el mismo código inválido (FE-I1)', () => {
  let reader: FakeReader;
  let resolve: ReturnType<typeof vi.fn>;
  let fixture: ComponentFixture<QrScannerComponent>;
  let router: Router;
  const settle = () => new Promise(done => setTimeout(done, 0));

  beforeEach(async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-11-04T10:00:00Z'));
    Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: { getUserMedia: vi.fn() } });
    reader = new FakeReader();
    resolve = vi.fn(async () => { throw new Error('El código QR no corresponde a un lote de TrazaTex.'); });
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: QR_READER, useValue: () => reader },
        { provide: StoreService, useValue: { resolveLotQr: resolve } },
      ],
    });
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(QrScannerComponent);
    fixture.detectChanges();
    await settle();
  });
  afterEach(() => {
    fixture.destroy();
    vi.useRealTimers();
  });
  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const retryButton = () => [...(fixture.nativeElement as HTMLElement).querySelectorAll('button')].find(b => b.textContent?.includes('Volver a leer'));

  it('un QR inválido visto en 200 fotogramas se envía una sola vez', async () => {
    reader.see('https://invalid.test/not-a-lot');
    await settle();
    expect(resolve).toHaveBeenCalledTimes(1);
    reader.see('https://invalid.test/not-a-lot', 200);
    await settle();
    expect(resolve).toHaveBeenCalledTimes(1);
  });

  it('mantiene visible el error mientras el mismo QR sigue frente a la cámara', async () => {
    reader.see('QR-MALO-1234');
    await settle();
    fixture.detectChanges();
    expect(text()).toContain('no corresponde');
    reader.see('QR-MALO-1234', 50);
    await settle();
    fixture.detectChanges();
    expect(text()).toContain('no corresponde');
  });

  it('un QR distinto se procesa enseguida', async () => {
    reader.see('QR-MALO-1234');
    await settle();
    reader.see('QR-OTRO-5678');
    await settle();
    expect(resolve).toHaveBeenCalledTimes(2);
    expect(resolve).toHaveBeenLastCalledWith('QR-OTRO-5678');
  });

  it('el mismo valor vuelve a aceptarse pasados 5 s', async () => {
    reader.see('QR-MALO-1234');
    await settle();
    vi.setSystemTime(new Date('2026-11-04T10:00:04.900Z'));
    reader.see('QR-MALO-1234', 10);
    await settle();
    expect(resolve).toHaveBeenCalledTimes(1);
    vi.setSystemTime(new Date('2026-11-04T10:00:05.100Z'));
    reader.see('QR-MALO-1234');
    await settle();
    expect(resolve).toHaveBeenCalledTimes(2);
  });

  it('«Volver a leer» reinicia el lector y acepta el mismo valor al instante', async () => {
    expect(retryButton()).toBeUndefined();
    reader.see('QR-MALO-1234');
    await settle();
    fixture.detectChanges();
    retryButton()!.click();
    fixture.detectChanges();
    expect(text()).not.toContain('no corresponde');
    expect(retryButton()).toBeUndefined();
    reader.see('QR-MALO-1234');
    await settle();
    expect(resolve).toHaveBeenCalledTimes(2);
  });

  it('un QR válido abre el lote, detiene la cámara y cierra el lector', async () => {
    resolve.mockResolvedValueOnce('TT-FOR-0002');
    const closed = vi.fn();
    fixture.componentInstance.closed.subscribe(closed);
    reader.see('http://localhost/lotes/TT-FOR-0002');
    await settle();
    expect(router.navigate).toHaveBeenCalledWith(['/lotes', 'TT-FOR-0002']);
    expect(closed).toHaveBeenCalledTimes(1);
    expect(reader.controls.stop).toHaveBeenCalled();
  });

  it('también deja de repetir un fallo de red o de límite (no solo los QR mal formados)', async () => {
    resolve.mockRejectedValue(Object.assign(new Error('Se alcanzó el límite de solicitudes.'), { status: 429 }));
    reader.see('QR-VALIDO-123', 100);
    await settle();
    expect(resolve).toHaveBeenCalledTimes(1);
  });
});
