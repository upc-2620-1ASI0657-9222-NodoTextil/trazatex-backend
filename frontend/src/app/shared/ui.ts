import {
  ChangeDetectionStrategy,
  Component,
  Directive,
  DestroyRef,
  TemplateRef,
  Injectable,
  Pipe,
  PipeTransform,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import qrcode from 'qrcode-generator';
import { Componente, EstadoCalidad, Evidencia, FaseOperativa, Lote } from '../core/models';
import { StoreService } from '../core/store.service';
import { SessionService } from '../core/session.service';
import { IconComponent } from './icon.component';
import { FormDraftDirective } from './form-draft.directive';
import { DialogFocusDirective } from './dialog-focus.directive';

                   
/** Tiempo mínimo que se ve un aviso informativo (los errores no se retiran solos). */
export const TOAST_MIN_MS = 4000;

@Injectable({ providedIn: 'root' })
export class UiService {
  readonly readOnlyCount = signal(0);
  readonly headerActions = signal<TemplateRef<unknown> | null>(null);
  readonly toasts = signal<{ id: number; text: string; error: boolean }[]>([]);
  readonly confirmation = signal<{ message: string; resolve: (accepted: boolean) => void } | null>(null);
  confirm(message: string): Promise<boolean> {
    this.answerConfirmation(false);
    return new Promise(resolve => this.confirmation.set({ message, resolve }));
  }
  answerConfirmation(accepted: boolean) {
    const pending = this.confirmation();
    this.confirmation.set(null);
    pending?.resolve(accepted);
  }
  private n = 0;
  toast(text: string, error = false) {
    const id = ++this.n;
    this.toasts.update((t) => [...t, { id, text, error }]);
    // Un error NO se retira solo: se queda hasta que la persona lo cierre (FE-I8), porque un temporizador no sabe si ya se leyó.
    // Un aviso informativo sí se retira, con tiempo de lectura proporcional al texto (~45 ms por carácter, mínimo 4 s).
    if (!error) setTimeout(() => this.dismissToast(id), Math.max(TOAST_MIN_MS, text.length * 45));
  }
  dismissToast(id: number) {
    this.toasts.update((t) => t.filter((x) => x.id !== id));
  }
}

                             
@Directive({ selector: 'ng-template[appHeaderActions]' })
export class HeaderActionsDirective {
  private readonly ui = inject(UiService);
  private readonly template = inject(TemplateRef<unknown>);
  constructor() { this.ui.headerActions.set(this.template); }
  ngOnDestroy() {
    if (this.ui.headerActions() === this.template) this.ui.headerActions.set(null);
  }
}

const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
const LIMA_DATE_TIME = new Intl.DateTimeFormat('es-PE', {
  timeZone: 'America/Lima', year: 'numeric', month: '2-digit', day: '2-digit',
  hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
});

@Pipe({ name: 'fecha' })
export class FechaPipe implements PipeTransform {
  transform(v?: string, hora = true): string {
    if (!v) return '—';
    let [d, t] = v.split('T');
    if (t && /(?:Z|[+-]\d{2}:\d{2})$/.test(v)) {
      const instant = new Date(v);
      if (Number.isNaN(instant.getTime())) return '—';
      const parts = Object.fromEntries(LIMA_DATE_TIME.formatToParts(instant).map(part => [part.type, part.value]));
      d = `${parts['year']}-${parts['month']}-${parts['day']}`;
      t = `${parts['hour']}:${parts['minute']}`;
    }
    const [y, m, dd] = d.split('-');
    const base = `${dd} ${MESES[+m - 1]} ${y}`;
    return hora && t ? `${base} / ${t.slice(0, 5)}` : base;
  }
}

@Pipe({ name: 'kg' })
export class KgPipe implements PipeTransform {
  transform(v?: number | null): string {
    if (v === undefined || v === null || Number.isNaN(v)) return '—';
    return `${v.toLocaleString('es-PE', { maximumFractionDigits: 1 })} kg`;
  }
}

@Pipe({ name: 'horas' })
export class HorasPipe implements PipeTransform {
  transform(h?: number): string {
    if (h === undefined) return '—';
    if (h < 24) return `${h.toFixed(1)} h`;
    const d = Math.floor(h / 24);
    return `${d} d ${Math.round(h - d * 24)} h`;
  }
}

                          
export const tonoFase = (f: FaseOperativa) =>
  f === 'Disponible' ? 'neutral' : f === 'Descartado' ? 'fail' : ['Procesado', 'Dividido'].includes(f) ? 'hist' : 'busy';
export const tonoCalidad = (c: EstadoCalidad) =>
  c === 'Conforme' ? 'ok' : c === 'Con falla' ? 'fail' : c === 'Por reevaluar' ? 'warn' : c === 'Posible falla derivada' ? 'derived' : 'neutral';

@Component({
  selector: 'app-badge',
  template: `<span class="badge badge--{{ tone() }}">@if (icon()) {<app-icon [name]="icon()!" [size]="11" [stroke]="2" />}{{ text() }}</span>`,
  imports: [IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BadgeComponent {
  readonly text = input.required<string>();
  readonly tone = input<string>('neutral');
  readonly icon = input<string>();
}

                                                                      
@Component({
  selector: 'app-lot-status',
  template: `<span class="badges">
    <app-badge [text]="lote().fase" [tone]="tf()" />
    <app-badge [text]="lote().calidad" [tone]="tc()" />
    @if (showAvail() && disp().bloqueado && disp().tono !== 'hist') {
      <span [attr.data-tip]="disp().motivo"><app-badge [text]="disp().etiqueta" tone="hist" icon="lock" /></span>
    }
    @if (lote().productoFinal) {
      <app-badge text="Producto final" tone="final" />
    }
  </span>`,
  imports: [BadgeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LotStatusComponent {
  private readonly store = inject(StoreService);
  readonly lote = input.required<Lote>();
  readonly showAvail = input(true);
  readonly tf = computed(() => tonoFase(this.lote().fase));
  readonly tc = computed(() => tonoCalidad(this.lote().calidad));
  readonly disp = computed(() => this.store.disponibilidad(this.lote()));
}

                         
@Component({
  selector: 'app-priv',
  template: `<span class="masked" [attr.data-tip]="why()"><app-icon name="lock" [size]="10" [stroke]="2" />Privado</span>`,
  imports: [IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PrivComponent {
  readonly always = input(false);
  readonly why = computed(() =>
    this.always() ? 'Dato siempre privado entre empresas.' : 'La empresa responsable mantiene este campo como Privado.',
  );
}

                     
@Component({
  selector: 'app-lot-link',
  template: `<a [routerLink]="['/lotes', store.codigoLote(id())]" [attr.title]="store.codigoLote(id())" [attr.aria-label]="'Lote ' + store.codigoLote(id())" (click)="$event.stopPropagation()">{{ store.nombreLote(id()) }}</a>`,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LotLinkComponent {
  readonly store = inject(StoreService);
  readonly id = input.required<string>();
}

                        
@Component({
  selector: 'app-composition',
  template: `<div class="comp" aria-hidden="true">
      @for (c of comp(); track c.nombre) {
        <i [style.flex]="c.porcentaje"></i>
      }
    </div>
    <div class="comp-legend">
      @for (c of comp(); track c.nombre) {
        <span><b class="num">{{ c.porcentaje }} %</b> {{ c.nombre }}</span>
      }
    </div>`,
  styles: `.comp-legend{display:flex;flex-wrap:wrap;gap:.2rem .9rem;margin-top:.35rem;font-weight:500;font-size:.84rem}`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CompositionComponent {
  readonly comp = input.required<Componente[]>();
}

                        
@Component({
  selector: 'app-qr-tag',
  template: `<figure class="qr-single qr-printable" [attr.aria-label]="'QR del lote ' + lote().id">
    <svg [attr.viewBox]="'0 0 ' + qr().n + ' ' + qr().n" shape-rendering="crispEdges" role="img" [attr.aria-label]="'Código QR del lote ' + lote().id">
      <path [attr.d]="qr().d" transform="translate(4 4)" fill="currentColor" />
    </svg>
    <figcaption class="qr-caption"><b>{{ lote().id }}</b><span>TrazaTex</span></figcaption>
  </figure>
  @if (actions()) {
    <div class="qr-actions">
      <button type="button" class="btn btn--ghost btn--sm" (click)="imprimir()">Imprimir etiqueta</button>
      <button type="button" class="btn btn--ghost btn--sm" (click)="descargar()">Descargar QR (SVG)</button>
    </div>
  }`,
  styles: `
    .qr-caption { display: grid; justify-items: center; gap: .1rem; margin-top: .6rem; text-align: center; }
    .qr-caption b { font-family: ui-monospace, monospace; font-size: 1.05rem; letter-spacing: .04em; }
    .qr-caption span { font-size: .72rem; letter-spacing: .12em; text-transform: uppercase; }
    .qr-actions { display: flex; gap: .5rem; justify-content: center; flex-wrap: wrap; margin-top: .75rem; }
    .qr-single { display: grid; place-items: center; width: min(100%, 19rem); margin: 0 auto; padding: 1.15rem; background: #fffdf7; color: #123723; }
    .qr-single svg { display: block; width: 100%; height: auto; aspect-ratio: 1; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QrTagComponent {
  readonly lote = input.required<Lote>();
  /** Muestra «Imprimir etiqueta» y «Descargar QR» (FE-M15). */
  readonly actions = input(false);
  readonly qr = computed(() => {
    const q = qrcode(0, 'M');
    q.addData(new URL('/lotes/' + encodeURIComponent(this.lote().id), window.location.origin).href);
    q.make();
    const n = q.getModuleCount();
    let d = '';
    for (let r = 0; r < n; r++) for (let c = 0; c < n; c++) if (q.isDark(r, c)) d += `M${c} ${r}h1v1h-1z`;
    return { n: n + 8, d };
  });

  /** El QR como archivo SVG independiente (fondo blanco, módulos oscuros), para imprimirlo desde cualquier programa. */
  svg(): string {
    const { n, d } = this.qr();
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${n} ${n}" width="${n * 8}" height="${n * 8}" shape-rendering="crispEdges">` +
      `<rect width="${n}" height="${n}" fill="#ffffff"/><path d="${d}" transform="translate(4 4)" fill="#123723"/></svg>`;
  }
  descargar() {
    const url = URL.createObjectURL(new Blob([this.svg()], { type: 'image/svg+xml' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = 'qr-' + this.lote().id.replace(/[^A-Za-z0-9_-]/g, '-') + '.svg';
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
  /** Imprime SOLO la etiqueta: `body.printing-qr` activa las reglas de `@media print` de styles.css y se quita al terminar. */
  imprimir() {
    const body = document.body;
    const terminar = () => { body.classList.remove('printing-qr'); window.removeEventListener('afterprint', terminar); };
    body.classList.add('printing-qr');
    window.addEventListener('afterprint', terminar);
    try { window.print(); } catch { terminar(); }
  }
}

                                      
@Component({
  selector: 'app-evidence',
  template: `
    @if (items().length) {
      <div class="evid">
        @for (e of items(); track e.id) {
          <figure>
            @if (e.url) { <img [src]="e.url" [alt]="e.nombre" loading="lazy" /> } @else { <p role="status">{{ e.status === "FAILED" ? "No se pudo subir la imagen." : "Imagen pendiente de subida." }}</p> }
            @if (pending()) {
              <label class="field"><span>Nombre descriptivo</span><input class="input" [ngModel]="e.nombre" (ngModelChange)="renamed.emit({id: e.id, name: $event})" maxlength="200" /></label>
              <button class="btn btn--ghost btn--sm" type="button" (click)="removed.emit(e.id)">Quitar fotografía</button>
            } @else { <figcaption>{{ e.nombre }}<br /><span class="label">{{ e.fecha | fecha }}</span></figcaption> }
            @if (canAdd() && ownerId()) { <button class="btn btn--danger btn--sm" type="button" [disabled]="deleting()" (click)="remove(e)">Eliminar evidencia</button> }
          </figure>
        }
      </div>
    } @else {
      <p class="muted small">Sin evidencias fotográficas.</p>
    }
    @for (draft of drafts(); track draft.id) {
      <figure class="evidence-draft">
        <img [src]="draft.url" [alt]="draft.name" width="160" height="120" style="object-fit:cover" />
        <label class="field"><span>Nombre descriptivo</span><input class="input" [ngModel]="draft.name" (ngModelChange)="renameDraft(draft.id, $event)" maxlength="200" /></label>
        <div class="actions">
          <button type="button" class="btn btn--ghost btn--sm" [disabled]="uploading()" (click)="dropDraft(draft.id)">Quitar fotografía</button>
          <button type="button" class="btn btn--primary btn--sm" [disabled]="uploading() || !draft.name.trim()" (click)="saveDraft(draft.id)">{{ uploading() ? 'Guardando…' : 'Guardar evidencia' }}</button>
        </div>
      </figure>
    }
    @if (canAdd()) {
      <label class="btn btn--ghost btn--sm upload" style="margin-top:.75rem">
        <app-icon name="camera" [size]="15" /> Adjuntar evidencia
        <input type="file" accept="image/jpeg,image/png,image/webp" (change)="pick($event)" />
      </label><p class="hint">JPEG, PNG o WebP · Hasta 10 MB por fotografía.</p>
    }
  `,
  imports: [FechaPipe, IconComponent, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EvidenceComponent {
  private readonly store = inject(StoreService);
  private readonly ui = inject(UiService);
  readonly ownerId = input('');
  readonly ownerType = input<'lote' | 'proceso' | 'control' | 'falla'>('lote');
  readonly pending = input(false);
  readonly removed = output<string>();
  readonly renamed = output<{id: string; name: string}>();
  readonly uploading = signal(false);
  readonly drafts = signal<{id: string; file: File; url: string; name: string}[]>([]);
  renameDraft(id: string, name: string) { this.drafts.update(items => items.map(item => item.id === id ? {...item, name} : item)); }
  dropDraft(id: string) {
    const draft = this.drafts().find(item => item.id === id);
    if (draft) URL.revokeObjectURL(draft.url);
    this.drafts.update(items => items.filter(item => item.id !== id));
  }
  async saveDraft(id: string) {
    const draft = this.drafts().find(item => item.id === id);
    if (!draft || !draft.name.trim() || this.uploading()) return;
    this.uploading.set(true);
    try {
      const file = new File([draft.file], draft.name.trim(), {type: draft.file.type});
      const result = await this.store.agregarEvidencia(this.ownerType(), this.ownerId(), file);
      if (result.ok) { this.dropDraft(id); this.ui.toast('Evidencia guardada.'); }
    } finally { this.uploading.set(false); }
  }
  ngOnDestroy() { this.drafts().forEach(draft => URL.revokeObjectURL(draft.url)); }
  readonly deleting = signal(false);
  async remove(evidence: Evidencia) {
    if (this.deleting() || !(await this.ui.confirm('¿Eliminar esta evidencia fotográfica? Esta acción no se puede deshacer.'))) return;
    this.deleting.set(true);
    try {
      const result = await this.store.eliminarEvidencia(this.ownerType(), this.ownerId(), evidence.id);
      if (result.ok) this.ui.toast('Evidencia eliminada.');
    } finally { this.deleting.set(false); }
  }

  readonly items = input.required<Evidencia[]>();
  readonly canAdd = input(false);
  readonly added = output<File>();
  pick(ev: Event) {
    const f = (ev.target as HTMLInputElement).files?.[0];
    if (f) {
      if (!['image/jpeg', 'image/png', 'image/webp'].includes(f.type) || f.size > 10 * 1024 * 1024) {
        this.ui.toast('Selecciona una imagen JPEG, PNG o WebP de hasta 10 MB.', true);
      } else if (this.ownerId()) {
        this.drafts.update(items => [...items, { id: crypto.randomUUID(), file: f, url: URL.createObjectURL(f), name: f.name }]);
      } else this.added.emit(f);
    }
    (ev.target as HTMLInputElement).value = '';
  }
}

                         
@Component({
  selector: 'app-dialog',
  template: `<div class="backdrop" (click)="closed.emit()"></div>
    <section class="dialog" [class.dialog--wide]="wide()" role="dialog" aria-modal="true" [attr.aria-label]="title()" appDialogFocus (dialogEscape)="closed.emit()">
      <header>
        <div>
          @if (eyebrow()) {
            <div class="eyebrow">{{ eyebrow() }}</div>
          }
          <h2>{{ title() }}</h2>
        </div>
        <button class="x" type="button" (click)="closed.emit()" aria-label="Cerrar"><app-icon name="x" /></button>
      </header>
      <div class="body"><ng-content /></div>
      <footer><ng-content select="[footer]" /></footer>
    </section>`,
  imports: [IconComponent, DialogFocusDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DialogComponent {
  readonly title = input.required<string>();
  readonly eyebrow = input<string>();
  readonly wide = input(false);
  readonly closed = output<void>();
}

                                                                                    
@Component({
  selector: 'app-action',
  template: `<button type="button" class="btn {{ cls() }}" [disabled]="!!reason()" [attr.aria-describedby]="reason() ? reasonId : null" (click)="run.emit()">
      @if (icon()) {<app-icon [name]="icon()!" [size]="16" />}
      <ng-content />
    </button>
    @if (reason()) { <small class="action-reason" [id]="reasonId">{{ reason() }}</small> }`,
  imports: [IconComponent],
  styles: `:host{display:inline-flex;flex-direction:column;align-items:flex-start;gap:.2rem;max-width:22rem}`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ActionComponent {
  private static next = 0;
  /** Id de la razón visible, referenciada por `aria-describedby` del botón. */
  protected readonly reasonId = 'action-reason-' + ++ActionComponent.next;
  /** Por qué el botón está deshabilitado. Se muestra SIEMPRE como texto junto al botón (no solo al pasar el ratón). */
  readonly reason = input<string | null | undefined>();
  readonly cls = input('btn--ghost');
  readonly icon = input<string>();
  readonly tipRight = input(false);
  readonly run = output<void>();
}

                                                        
@Component({
  selector: 'app-readonly',
  template: '',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReadonlyComponent {
  constructor() {
    const ui = inject(UiService);
    ui.readOnlyCount.update(count => count + 1);
    inject(DestroyRef).onDestroy(() => ui.readOnlyCount.update(count => count - 1));
  }
}

export const SHARED = [
  FormDraftDirective,
  HeaderActionsDirective,
  IconComponent,
  BadgeComponent,
  LotStatusComponent,
  PrivComponent,
  LotLinkComponent,
  CompositionComponent,
  QrTagComponent,
  EvidenceComponent,
  DialogComponent,
  ActionComponent,
  ReadonlyComponent,
  FechaPipe,
  KgPipe,
  HorasPipe,
  RouterLink,
] as const;

