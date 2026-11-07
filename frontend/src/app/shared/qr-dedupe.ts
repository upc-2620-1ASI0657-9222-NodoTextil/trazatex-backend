/** Segundos que se ignora un QR que acaba de ser rechazado; pasado ese tiempo se vuelve a intentar con el mismo valor. */
export const QR_RETRY_AFTER_MS = 5000;

/**
 * La cámara entrega el mismo código decenas de veces por segundo. Sin esta memoria, un QR inválido (o ajeno, o un 429)
 * se reenviaba al servidor en bucle y gastaba el límite de 120 solicitudes/min. Solo se recuerda el último valor
 * rechazado: uno distinto se procesa de inmediato, y el mismo se procesa de nuevo pasada la pausa o tras `reset()`.
 */
export class RejectedQrMemory {
  private value?: string;
  private at = 0;
  constructor(private readonly pauseMs = QR_RETRY_AFTER_MS) {}
  shouldProcess(value: string, now = Date.now()): boolean {
    return !(value === this.value && now - this.at < this.pauseMs);
  }
  reject(value: string, now = Date.now()): void {
    this.value = value;
    this.at = now;
  }
  reset(): void {
    this.value = undefined;
  }
}
