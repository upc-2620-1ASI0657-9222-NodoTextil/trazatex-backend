import { Directive, ElementRef, HostListener, inject, output } from '@angular/core';

@Directive({ selector: '[appDialogFocus]' })
export class DialogFocusDirective {
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private previousFocus: HTMLElement | null = null;
  readonly dialogEscape = output<void>();

  ngAfterViewInit() {
    this.previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    if (this.element.getAttribute('aria-modal') !== 'true') return;
    queueMicrotask(() => {
      if (this.element.isConnected) this.controls()[0]?.focus();
    });
  }

  ngOnDestroy() {
    if (this.previousFocus?.isConnected) this.previousFocus.focus();
  }

  @HostListener('document:keydown', ['$event'])
  keydown(event: KeyboardEvent) {
    if (Array.from(document.querySelectorAll('[role="dialog"]')).at(-1) !== this.element) return;
    if (event.key === 'Escape') {
      event.preventDefault();
      this.dialogEscape.emit();
      return;
    }
    if (event.key !== 'Tab' || this.element.getAttribute('aria-modal') !== 'true') return;
    const controls = this.controls();
    const first = controls[0], last = controls.at(-1);
    if (!first || !last) return;
    if (event.shiftKey && (document.activeElement === first || !this.element.contains(document.activeElement))) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && (document.activeElement === last || !this.element.contains(document.activeElement))) {
      event.preventDefault();
      first.focus();
    }
  }

  private controls() {
    return Array.from(this.element.querySelectorAll<HTMLElement>(
      'a[href],button,input:not([type="hidden"]),select,textarea,[tabindex]:not([tabindex="-1"])',
    )).filter(control => !control.matches(':disabled') && control.getClientRects().length > 0);
  }
}
