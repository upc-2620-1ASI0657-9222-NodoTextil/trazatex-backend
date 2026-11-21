export const QR_RETRY_AFTER_MS = 5000;

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
