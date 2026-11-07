import { ChangeDetectionStrategy, Component, ElementRef, InjectionToken, inject, output, signal, viewChild } from '@angular/core';
import { Router } from '@angular/router';
import { BrowserQRCodeReader, IScannerControls } from '@zxing/browser';
import { StoreService } from '../core/store.service';
import { errorMessage } from '../core/api.service';
import { DialogComponent } from './ui';
import { RejectedQrMemory } from './qr-dedupe';

/** Lector de QR; se inyecta para poder sustituir la cámara en las pruebas. */
export type QrReader = Pick<BrowserQRCodeReader, 'decodeFromVideoDevice'>;
export const QR_READER = new InjectionToken<() => QrReader>('QR_READER', { providedIn: 'root', factory: () => () => new BrowserQRCodeReader() });

@Component({
  selector: 'app-qr-scanner',
  imports: [DialogComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<app-dialog title="Escanear QR del lote" (closed)="closed.emit()">
    <video #video autoplay muted playsinline style="width:100%;max-height:55vh;object-fit:contain" aria-label="Cámara para leer QR"></video>
    <p class="small" role="status">{{ busy() ? 'Comprobando acceso al lote.' : 'Enfoca el código QR de TrazaTex.' }}</p>
    @if (error()) { <p class="err" role="alert">{{ error() }}</p> }
    @if (canRetry()) { <button class="btn btn--ghost btn--sm" type="button" (click)="restart()">Volver a leer</button> }
    <button footer class="btn btn--ghost" type="button" (click)="closed.emit()">Cerrar</button>
  </app-dialog>`,
})
export class QrScannerComponent {
  readonly closed = output<void>();
  readonly error = signal('');
  readonly busy = signal(false);
  /** Hay un QR rechazado en pausa: se ofrece volver a leerlo sin esperar. */
  readonly canRetry = signal(false);
  private readonly rejected = new RejectedQrMemory();
  private readonly createReader = inject(QR_READER);
  private readonly video = viewChild<ElementRef<HTMLVideoElement>>('video');
  private readonly store = inject(StoreService);
  private readonly router = inject(Router);
  private controls?: IScannerControls;
  private destroyed = false;
  async ngAfterViewInit() {
    try {
      if (!navigator.mediaDevices?.getUserMedia) throw new Error('La cámara requiere un navegador compatible y una conexión segura o localhost.');
      const reader = this.createReader();
      this.controls = await reader.decodeFromVideoDevice(undefined, this.video()!.nativeElement, (result, _error, controls) => {
        this.controls = controls;
        if (!result || this.busy() || this.destroyed) return;
        const text = result.getText();
        if (this.rejected.shouldProcess(text)) void this.open(text);
      });
      if (this.destroyed) this.controls.stop();
    } catch (error) {
      const name = error instanceof Error ? error.name : '';
      this.error.set(name === 'NotAllowedError' ? 'Permiso de cámara denegado. Habilítalo en el navegador y vuelve a abrir el lector.' : name === 'NotFoundError' ? 'No se encontró una cámara disponible.' : errorMessage(error));
    }
  }
  private async open(value: string) {
    this.busy.set(true); this.error.set('');
    try {
      const code = await this.store.resolveLotQr(value);
      if (this.destroyed) return;
      this.controls?.stop(); this.closed.emit();
      await this.router.navigate(['/lotes', code]);
    } catch (error) {
      this.rejected.reject(value);
      this.canRetry.set(true);
      this.error.set(errorMessage(error));
    }
    finally { this.busy.set(false); }
  }
  /** Reinicia el lector: el mismo QR vuelve a aceptarse de inmediato. */
  restart() { this.rejected.reset(); this.canRetry.set(false); this.error.set(''); }
  ngOnDestroy() { this.destroyed = true; this.controls?.stop(); }
}
