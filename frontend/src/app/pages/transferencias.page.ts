import { formDraft } from '../shared/form-draft.directive';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Transferencia } from '../core/models';
import { SessionService } from '../core/session.service';
import { StoreService } from '../core/store.service';
import { DENSITY, SubnavItem, paginar } from '../shared/density';
import { SHARED, UiService } from '../shared/ui';

type Vista = 'todas' | 'entrantes' | 'salientes' | 'pendientes' | 'historial';

@Component({
  selector: 'app-transferencias',
  imports: [...SHARED, ...DENSITY, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page page--transferencias">
      <ng-template appHeaderActions>
          @if (session.esOperario()) {
            <button class="btn btn--primary" type="button" (click)="abrir()"><app-icon name="transfer" [size]="16" /> Iniciar transferencia</button>
          } @else if (session.esDueno()) {
            <app-readonly />
          }
        </ng-template>

      <app-subnav panel="transferencias" [items]="vistas()" [(active)]="vista" (activeChange)="page.set(0)" label="Vistas de transferencias" />
      <div appTabPanel="transferencias" [tabActive]="vista()">

      <div class="filters">
        <label class="search">
          <app-icon name="search" [size]="16" />
          <input class="input" placeholder="Buscar transferencia o lote" [ngModel]="q()" (ngModelChange)="q.set($event); page.set(0)" aria-label="Buscar transferencias" />
        </label>
        @if (session.esDueno()) { <button class="btn btn--ghost" type="button" (click)="verFiltros.set(true)"><app-icon name="filter" [size]="16" /> Filtros</button> }
      </div>

      @if (lista().length) {
        <div class="rows">
          @for (t of pagina(); track t.id) {
            <button type="button" class="row-card {{ t.estado === 'Aceptada' ? 'row-card--ok' : t.estado === 'Rechazada' ? 'row-card--fail' : 'row-card--warn' }}" [class.is-sel]="selId() === t.id" [attr.title]="store.refTransferencia(t.id)" (click)="selId.set(t.id)">
              <span class="rc-id" [attr.title]="store.refTransferencia(t.id)">{{ store.referencia(t.id) }}</span>
              <app-icon class="rc-figure" name="transfer" [size]="42" />
              <span class="rc-state">{{ t.estado === 'Pendiente' && t.destinoId === session.empresaId() ? (session.esAdmin() ? 'Espera tu decisión' : 'Espera decisión') : t.estado }}</span>
              <span class="rc-title">{{ corto(t.origenId) }} / {{ corto(t.destinoId) }}</span>
              <span class="rc-meta">{{ t.lotesIds.map(store.nombreEnTransferencia.bind(store)).join(', ') }} / <span class="num">{{ t.inicio | fecha: false }}</span></span>
            </button>
          }
        </div>
        <app-pager [total]="lista().length" [(page)]="page" [(size)]="size" />
      } @else if (store.loading()) {
        <app-skeleton />
      } @else {
        <div class="empty"><strong>Sin transferencias en esta vista</strong><span>Cambia de vista o ajusta la búsqueda.</span></div>
      }
      </div>
    </div>

    @if (verFiltros()) {
      <app-drawer title="Filtros de transferencias" (closed)="verFiltros.set(false)">
        <label class="field"><span>Empresa</span>
          <select class="select" [ngModel]="fEmpresa()" (ngModelChange)="fEmpresa.set($event); page.set(0)">
            <option value="">Todas las empresas</option>
            @for (e of store.empresas(); track e.id) { <option [value]="e.id">{{ e.razonSocial }}</option> }
          </select>
        </label>
        <div footer class="d-actions"><button class="btn btn--primary" type="button" (click)="verFiltros.set(false)">Aplicar</button></div>
      </app-drawer>
    }
    @if (sel(); as t) {
      @let decideAqui = t.estado === 'Pendiente' && t.destinoId === session.empresaId();
      <app-drawer [title]="corto(t.origenId) + ' / ' + corto(t.destinoId)" eyebrow="Transferencia" [sub]="t.estado" (closed)="selId.set(null)">
        <section class="d-section">
          <h3>Ruta</h3>
          <div class="route">
            <div><span class="label">Origen</span><b>{{ store.nombreEmpresa(t.origenId) }}</b></div>
            <app-icon name="arrowRight" [size]="18" />
            <div><span class="label">Destino</span><b>{{ store.nombreEmpresa(t.destinoId) }}</b></div>
          </div>
        </section>
        <section class="d-section">
          <h3>Lotes completos</h3>
          @if (!store.loadedTransferDetails().includes(t.id)) {<p class="small" role="status">Consultando los lotes…</p>}
          <div class="chips">@for (l of t.lotesIds; track l) {@if (store.lote(l)?.empresaId === session.empresaId()) {
  <a class="chip-id" [routerLink]="['/lotes', l]">{{ store.nombreEnTransferencia(l) }}</a>
} @else {
  <a class="chip-id" routerLink="/trazabilidad" [queryParams]="{ lote: l }">{{ store.nombreEnTransferencia(l) }}</a>
}
@if (store.transferSummaries()[l]; as summary) { <span>{{ summary.name }} / {{ summary.quantityKg | kg }} / {{ summary.materialType }}</span> }}</div>
        </section>
        <section class="d-section">
          <h3>Fechas</h3>
          <dl class="dl">
            <dt>Inicio</dt><dd class="num">{{ t.inicio | fecha }}</dd>
            @if (t.recepcion) {<dt>Recepción</dt><dd class="num">{{ t.recepcion | fecha }}</dd>}
            @if (t.rechazo) {<dt>Rechazo</dt><dd class="num">{{ t.rechazo | fecha }}</dd>}
            <dt>Duración</dt><dd class="num">{{ store.duracionH(t.inicio, t.recepcion ?? t.rechazo) | horas }}</dd>
          </dl>
        </section>
        @if (t.motivoRechazo) {
          <div class="notice notice--fail"><app-icon name="x" /><span><b>Motivo del rechazo.</b> {{ t.motivoRechazo }}</span></div>
        }
        @if (decideAqui) {
          <div footer class="d-actions">
            <app-action cls="btn--danger btn--sm" [reason]="decide()" (run)="rechazando.set(t); motivo = ''; error.set('')">Rechazar</app-action>
            <app-action cls="btn--primary btn--sm" icon="check" [tipRight]="true" [reason]="decide()" (run)="aceptar(t)">Aceptar</app-action>
          </div>
        }
      </app-drawer>
    }

    @if (nuevo()) {
      <app-dialog [appDraft]="draft" title="Iniciar transferencia" eyebrow="Transferencia" (closed)="nuevo.set(false)">
        <app-stepper [steps]="pasos" [current]="paso()" />
        <div class="step-body">
          @switch (paso()) {
            @case (0) {
              <ul class="pick">
                @for (l of candidatos(); track l.id) {
                  @let d = store.disponibilidad(l);
                  <li [class.off]="d.bloqueado">
                    <label class="check" [attr.data-tip]="d.bloqueado ? d.motivo : ''">
                      <input type="checkbox" [disabled]="d.bloqueado" [checked]="sel2().includes(l.id)" (change)="toggle(l.id)" />
                      <b class="id">{{ l.id }}</b> <span class="small">{{ l.material }}</span>
                      <span class="num muted" style="margin-left:auto">{{ l.cantidadKg | kg }}</span>
                      @if (d.bloqueado) {<app-icon name="lock" [size]="12" />}
                    </label>
                  </li>
                }
              </ul>
            }
            @case (1) {
              <label class="field">
                <span>Empresa destino</span>
                <select class="select" [(ngModel)]="destino">
                  <option value="" disabled>Selecciona…</option>
                  @for (e of destinos(); track e.id) {
                    <option [value]="e.id">{{ e.razonSocial }}</option>
                  }
                </select>
              </label>
            }
            @case (2) {
              <div class="review">
                <span class="label">Revisión</span>
                <dl class="dl">
                  <dt>Lotes</dt><dd><div class="chips">@for (s of sel2(); track s) {<span class="chip-id">{{ s }}</span>}</div></dd>
                  <dt>Destino</dt><dd>{{ store.nombreEmpresa(destino) }}</dd>
                  <dt>Estado inicial</dt><dd>Pendiente</dd>
                </dl>
              </div>
            }
          }
        </div>
        @if (error()) {<p class="err" role="alert" style="margin-top:.5rem">{{ error() }}</p>}
        <ng-container footer>
          @if (paso() > 0) {
            <button class="btn btn--ghost" type="button" (click)="paso.set(paso() - 1); error.set('')"><app-icon name="arrowLeft" [size]="14" /> Atrás</button>
          } @else {
            <button class="btn btn--ghost" type="button" (click)="nuevo.set(false)">Cancelar</button>
          }
          @if (paso() < 2) {
            <button class="btn btn--primary" type="button" (click)="siguiente()">Continuar <app-icon name="arrowRight" [size]="14" /></button>
          } @else {
            <button class="btn btn--primary" type="button" (click)="iniciar()">Enviar transferencia</button>
          }
        </ng-container>
      </app-dialog>
    }

    @if (rechazando(); as t) {
      <app-dialog [appDraft]="rejectionDraft" [title]="'Rechazar transferencia ' + store.referencia(t.id)" eyebrow="Transferencia entrante" (closed)="rechazando.set(null)">
        <p class="small" style="margin-bottom:.8rem">{{ t.lotesIds.map(store.nombreEnTransferencia.bind(store)).join(', ') }} volverá(n) a {{ store.nombreEmpresa(t.origenId) }} como Disponible. El motivo queda registrado.</p>
        <label class="field">
          <span>Motivo del rechazo (obligatorio)</span>
          <textarea class="textarea" [class.invalid]="error()" [(ngModel)]="motivo"></textarea>
        </label>
        @if (error()) {<p class="err" role="alert" style="margin-top:.4rem">{{ error() }}</p>}
        <ng-container footer>
          <button class="btn btn--ghost" type="button" (click)="rechazando.set(null)">Cancelar</button>
          <button class="btn btn--danger" type="button" (click)="rechazar(t)">Rechazar transferencia</button>
        </ng-container>
      </app-dialog>
    }
  `,
  styles: `
    .pick { list-style: none; margin: 0; padding: 0; max-height: 16rem; overflow-y: auto; border-top: 1px solid var(--hair-2); }
    .pick li { border-bottom: 1px dashed var(--hair); }
    .pick label { display: flex; gap: .55rem; align-items: center; padding: .55rem .25rem; width: 100%; }
    .pick li.off { opacity: .55; }
  `,
})
export class TransferenciasPage {
  readonly store = inject(StoreService);
  readonly session = inject(SessionService);
  private readonly ui = inject(UiService);

  readonly preNuevo = input<string | undefined>(undefined, { alias: 'nuevo' });
  readonly requestedTransfer = input<string | undefined>(undefined, { alias: 'transferencia' });
  constructor() {
    effect(() => {
      const id = this.requestedTransfer();
      if (id && this.store.transferencia(id)) this.selId.set(id);
    });
    effect(() => {
      const transfer = this.sel();
      if (transfer) this.store.requestTransferDetail(transfer.id);
    });
  }
  readonly pasos = ['Lotes', 'Destino', 'Confirmar'];
  readonly paso = signal(0);
  readonly vista = signal<Vista>('todas');
  readonly q = signal('');
  readonly verFiltros = signal(false);
  readonly fEmpresa = signal('');
  readonly page = signal(0);
  readonly size = signal(6);
  readonly nuevo = signal(false);
  readonly sel2 = signal<string[]>([]);
  readonly selId = signal<string | null>(null);
  readonly sel = computed(() => this.store.transferencia(this.selId()) ?? null);
  readonly rechazando = signal<Transferencia | null>(null);
  readonly error = signal('');
  destino = '';
  motivo = '';

  readonly decide = computed(() =>
    this.sel() && !this.store.loadedTransferDetails().includes(this.sel()!.id) ? 'Consultando los lotes de la transferencia.' : this.session.esAdmin() ? null : 'Solo el Administrador de la empresa destino puede aceptar o rechazar.',
  );
  readonly candidatos = computed(() =>
    this.store.lotes().filter((l) => l.empresaId === this.session.empresaId() && !this.store.esHistorico(l)),
  );
  readonly destinos = computed(() => this.store.empresas().filter((e) => e.estado === 'Activa' && e.id !== this.session.empresaId()));
  corto = (id: string) => this.store.nombreEmpresa(id).replace(/ S\.A\.C\.$/, '');

  ngOnInit() {
    if (this.store.transferencias().some((t) => t.estado === 'Pendiente' && t.destinoId === this.session.empresaId())) this.vista.set('pendientes');
    const p = this.preNuevo();
    if (p && this.session.esOperario()) {
      this.abrir();
      if (this.store.elegible(this.store.lote(p)!)) this.sel2.set([p]);
    }
  }

  private base = computed(() => {
    const q = this.q().trim().toLowerCase();
    return this.store
      .misTransferencias()
      .filter(
        (x) =>
          (!q || x.id.toLowerCase().includes(q) || this.store.nombreEmpresa(x.origenId).toLowerCase().includes(q) || this.store.nombreEmpresa(x.destinoId).toLowerCase().includes(q) || x.lotesIds.some((l) => l.toLowerCase().includes(q))) &&
          (!this.fEmpresa() || x.origenId === this.fEmpresa() || x.destinoId === this.fEmpresa()),
      )
      .sort((a, b) => b.inicio.localeCompare(a.inicio));
  });
  private en(t: Transferencia, v: Vista) {
    const me = this.session.empresaId();
    switch (v) {
      case 'entrantes':
        return t.destinoId === me;
      case 'salientes':
        return t.origenId === me;
      case 'pendientes':
        return t.estado === 'Pendiente';
      case 'historial':
        return t.estado !== 'Pendiente';
      default:
        return true;
    }
  }
  readonly vistas = computed<SubnavItem[]>(() => {
    const n = (v: Vista) => this.base().filter((t) => this.en(t, v)).length;
    const all: Vista[] = this.session.esDueno() ? ['todas', 'pendientes', 'historial'] : ['todas', 'entrantes', 'salientes', 'pendientes', 'historial'];
    const label: Record<Vista, string> = { todas: 'Todas', entrantes: 'Entrantes', salientes: 'Salientes', pendientes: 'Pendientes', historial: 'Historial' };
    return all.map((v) => ({ id: v, label: label[v], count: n(v) }));
  });
  readonly lista = computed(() => this.base().filter((t) => this.en(t, this.vista())));
  readonly pagina = computed(() => paginar(this.lista(), this.page(), this.size()));

  abrir() {
    this.sel2.set([]);
    this.destino = '';
    this.paso.set(0);
    this.error.set('');
    this.nuevo.set(true);
  }
  toggle(id: string) {
    this.sel2.update((s) => (s.includes(id) ? s.filter((x) => x !== id) : [...s, id]));
  }
  siguiente() {
    let e = '';
    if (this.paso() === 0 && !this.sel2().length) e = 'Selecciona al menos un lote completo.';
    if (this.paso() === 1 && !this.destino) e = 'Selecciona la empresa destino.';
    this.error.set(e);
    if (!e) this.paso.update((p) => p + 1);
  }
  readonly draft = formDraft('transfer/new', () => ({ lotes: this.sel2(), destino: this.destino, paso: this.paso() }), value => {
    this.sel2.set(value.lotes); this.destino = value.destino; this.paso.set(value.paso);
  });
  async iniciar() {
    const r = await this.store.iniciarTransferencia(this.sel2(), this.destino);
    if (!r.ok) return this.error.set(r.error);
    await this.draft.clear();
    this.ui.toast(`Transferencia ${this.store.referencia(r.id!)} enviada / Pendiente`);
    this.nuevo.set(false);
    this.vista.set('salientes');
    this.selId.set(r.id!);
  }
  /** Lotes de una transferencia por nombre (o código), nunca por UUID, respetando la privacidad del nombre. */
  nombres(t: Transferencia) { return t.lotesIds.map(l => this.store.nombreEnTransferencia(l)).join(', '); }
  async aceptar(t: Transferencia) {
    if (!(await this.ui.confirm('¿Aceptar la transferencia? Los lotes pasarán a estar bajo la responsabilidad de tu empresa.'))) return;
    const result = await this.store.aceptarTransferencia(t.id);
    if (!result.ok) return;
    this.ui.toast(`Transferencia ${this.store.referencia(t.id)} aceptada / ${this.nombres(t)} ahora bajo tu empresa`);
  }
  readonly rejectionDraft = formDraft(() => 'transfer/reject/' + this.rechazando()!.id, () => ({ motivo: this.motivo }), value => { this.motivo = value.motivo; });
  async rechazar(t: Transferencia) {
    const r = await this.store.rechazarTransferencia(t.id, this.motivo);
    if (!r.ok) return this.error.set(r.error);
    await this.rejectionDraft.clear();
    this.error.set('');
    this.rechazando.set(null);
    this.ui.toast(`Transferencia ${this.store.referencia(t.id)} rechazada / lotes devueltos al origen`);
  }
}



