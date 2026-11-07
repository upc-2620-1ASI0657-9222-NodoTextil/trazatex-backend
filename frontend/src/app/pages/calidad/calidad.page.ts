import { formDraft } from '../../shared/form-draft.directive';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ControlCalidad } from '../../core/models';
import { SessionService } from '../../core/session.service';
import { StoreService } from '../../core/store.service';
import { DENSITY, SubnavItem, paginar } from '../../shared/density';
import { SHARED, UiService } from '../../shared/ui';

type Vista = 'controles' | 'evaluaciones' | 'reevaluaciones' | 'pendientes';

export const PASOS_CONTROL = ['Lote', 'Pruebas', 'Revisar resultado', 'Finalizar'];

@Component({
  selector: 'app-calidad',
  imports: [...SHARED, ...DENSITY, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page page--calidad">
      <ng-template appHeaderActions>
        @if (session.esAdmin()) { <button class="btn btn--primary" type="button" (click)="abrirNuevo()"><app-icon name="plus" [size]="16" /> Iniciar control</button> }
        @else { <app-readonly /> }
      </ng-template>

      <app-subnav panel="calidad" [items]="vistas()" [(active)]="vista" (activeChange)="page.set(0)" label="Vistas de calidad" />
      <div appTabPanel="calidad" [tabActive]="vista()">

      @if (vista() === 'pendientes') {
        @if (pendientes().length) {
          <div class="rows">
            @for (l of paginaPend(); track l.id) {
              <button type="button" class="row-card {{ l.calidad === 'No revisado' ? 'row-card--busy' : 'row-card--warn' }}" (click)="abrirNuevo(l.id)">
                <span class="rc-id">{{ l.id }}</span>
                <span class="rc-state">{{ store.tipoControlPara(l) }}</span>
                <app-icon class="rc-figure" name="quality" [size]="42" /><span class="rc-title">{{ l.nombre }}</span>
                <span class="rc-meta">{{ l.calidad }} / <span class="num">{{ l.cantidadKg | kg }}</span> / iniciar control</span>
              </button>
            }
          </div>
          <app-pager [total]="pendientes().length" [(page)]="page" [(size)]="size" [sizes]="[8]" />
        } @else if (store.loading()) {
          <app-skeleton />
        } @else {
          <div class="empty"><strong>Sin lotes por controlar</strong><span>Todos los lotes disponibles ya fueron evaluados.</span></div>
        }
      } @else {
        <div class="filters">
          <label class="search">
            <app-icon name="search" [size]="16" />
            <input class="input" placeholder="Buscar control o lote" [ngModel]="q()" (ngModelChange)="q.set($event); page.set(0)" aria-label="Buscar controles" />
          </label>
          <select class="select" [ngModel]="fRes()" (ngModelChange)="fRes.set($event); page.set(0)" aria-label="Estado">
            <option value="">Todo estado</option>
            <option>En curso</option>
            <option>Conforme</option>
            <option>Con falla</option>
          </select>
          @if (session.esDueno()) {
            <select class="select" [ngModel]="fEmpresa()" (ngModelChange)="fEmpresa.set($event); page.set(0)" aria-label="Empresa">
              <option value="">Todas las empresas</option>
              @for (e of store.empresas(); track e.id) {
                <option [value]="e.id">{{ e.razonSocial }}</option>
              }
            </select>
          }
        </div>
        @if (lista().length) {
          <div class="rows">
            @for (c of pagina(); track c.id) {
              @let r = res(c);
              <button type="button" class="row-card {{ r === 'Conforme' ? 'row-card--ok' : r === 'Con falla' ? 'row-card--fail' : 'row-card--warn' }}" [class.is-sel]="selId() === c.id" [attr.title]="store.refControl(c)" (click)="selId.set(c.id)">
                <span class="rc-id" [attr.title]="store.refControl(c)">{{ store.referencia(c.id) }}</span>
                <span class="rc-state">{{ r }}</span>
                <app-icon class="rc-figure" name="quality" [size]="42" /><span class="rc-title">{{ store.tituloControl(c) }}</span>
                <span class="rc-meta">{{ c.pruebas.length }} prueba(s) / <span class="num">{{ c.inicio | fecha: false }}</span>@if (session.esDueno()) { / {{ store.nombreEmpresa(c.empresaId) }}}</span>
              </button>
            }
          </div>
          <app-pager [total]="lista().length" [(page)]="page" [(size)]="size" [sizes]="[8]" />
        } @else if (store.loading()) {
          <app-skeleton />
        } @else {
          <div class="empty"><strong>Sin controles en esta vista</strong><span>Ajusta la búsqueda o el estado.</span></div>
        }
      }
      </div>
    </div>

    @if (sel(); as c) {
      @let r = res(c);
      <app-drawer [title]="store.tituloControl(c)" [eyebrow]="store.referencia(c.id)" [sub]="'Lote ' + c.loteId + ' / ' + store.nombreEmpresa(c.empresaId)" (closed)="selId.set(null)">
        <app-badge [text]="r" [tone]="r === 'Conforme' ? 'ok' : r === 'Con falla' ? 'fail' : 'busy'" />
        <section class="d-section">
          <h3>Datos generales</h3>
          <dl class="dl">
            <dt>Lote</dt><dd><app-lot-link [id]="c.loteId" /> {{ store.lote(c.loteId)?.material }}</dd>
            <dt>Responsable</dt><dd>{{ store.nombreUsuario(c.administradorId) }}</dd>
            <dt>Inicio</dt><dd class="num">{{ c.inicio | fecha }}</dd>
            <dt>Fin</dt><dd class="num">{{ c.fin | fecha }}</dd>
          </dl>
        </section>
        <section class="d-section">
          <h3>Pruebas</h3>
          <ul class="tests">
            @for (p of c.pruebas; track p.id) {
              <li><span>{{ p.criterio }}</span><app-badge [text]="p.resultado" [tone]="p.resultado === 'Conforme' ? 'ok' : 'fail'" /></li>
            } @empty {
              <li class="muted small">Aún sin pruebas.</li>
            }
          </ul>
        </section>
        <ng-container footer>
          <a class="btn btn--primary btn--sm" [routerLink]="['/calidad', c.id]">{{ !c.fin && session.esAdmin() ? 'Continuar control' : 'Abrir control' }} <app-icon name="arrowRight" [size]="14" /></a>
        </ng-container>
      </app-drawer>
    }

    @if (nuevo()) {
      <app-dialog [appDraft]="draft" title="Iniciar control de calidad" eyebrow="Calidad" (closed)="nuevo.set(false)">
        <app-stepper [steps]="pasos" [current]="0" />
        <label class="field">
          <span>Lote a controlar</span>
          <select class="select" [(ngModel)]="loteSel">
            <option value="" disabled>Selecciona un lote…</option>
            @for (l of pendientes(); track l.id) {
              <option [value]="l.id">{{ l.nombre || l.material }} / {{ l.id }} / {{ l.calidad }}</option>
            }
          </select>
          <span class="hint">Lotes disponibles sin control activo. El tipo de control se determina por su estado e historial.</span>
        </label>
        @if (store.lote(loteSel); as l) {
          <div class="notice" style="margin-top:.8rem"><app-icon name="quality" /><span>Se iniciará una <b>{{ store.tipoControlPara(l) }}</b>. Mientras esté activa, el lote no podrá dividirse, transformarse ni transferirse.</span></div>
        }
        <ng-container footer>
          <button class="btn btn--ghost" type="button" (click)="nuevo.set(false)">Cancelar</button>
          @if (!loteSel) { <small class="action-reason" id="nuevo-control-razon">Selecciona un lote para continuar.</small> }
          <button class="btn btn--primary" type="button" [disabled]="!loteSel || store.saving()" [attr.aria-describedby]="loteSel ? null : 'nuevo-control-razon'" (click)="iniciar(loteSel)">Iniciar y registrar pruebas <app-icon name="arrowRight" [size]="14" /></button>
        </ng-container>
      </app-dialog>
    }
  `,
  styles: `
    .tests { list-style: none; margin: 0; padding: 0; }
    .tests li { display: flex; justify-content: space-between; gap: .5rem; align-items: center; padding: .45rem 0; border-bottom: 1px dashed var(--hair); font-weight: 600; font-size: .88rem; }
  `,
})
export class CalidadPage {
  readonly store = inject(StoreService);
  readonly session = inject(SessionService);
  readonly router = inject(Router);
  private readonly ui = inject(UiService);
  readonly pasos = PASOS_CONTROL;

  readonly vista = signal<Vista>('controles');
  readonly q = signal('');
  readonly fRes = signal('');
  readonly fEmpresa = signal('');
  readonly page = signal(0);
  readonly size = signal(8);
  readonly nuevo = signal(false);
  readonly selId = signal<string | null>(null);
  readonly sel = computed(() => this.store.control(this.selId()) ?? null);
  loteSel = '';

  readonly pendientes = computed(() => this.store.misLotes().filter((l) => this.store.puedeIniciarControl(l).ok));
  readonly paginaPend = computed(() => paginar(this.pendientes(), this.page(), this.size()));

  res = (c: ControlCalidad) => c.status === 'STARTING' ? 'Inicio pendiente' : c.status === 'CANCELLED' ? 'Cancelado' : c.finishPending ? 'Finalización pendiente' : c.fin ? (this.store.resultadoControl(c) ?? '—') : 'En curso';

  private base = computed(() => {
    const q = this.q().trim().toLowerCase();
    return this.store
      .misControles()
      .filter(
        (c) =>
          (!q || c.id.toLowerCase().includes(q) || c.loteId.toLowerCase().includes(q) || this.store.nombreLote(c.loteId).toLowerCase().includes(q)) &&
          (!this.fRes() || this.res(c) === this.fRes()) &&
          (!this.fEmpresa() || c.empresaId === this.fEmpresa()),
      )
      .sort((a, b) => b.inicio.localeCompare(a.inicio));
  });
  readonly vistas = computed<SubnavItem[]>(() => {
    const b = this.base();
    const items: SubnavItem[] = [
      { id: 'controles', label: 'Controles', count: b.length },
      { id: 'evaluaciones', label: 'Evaluaciones', count: b.filter((c) => c.tipo === 'Evaluación').length },
      { id: 'reevaluaciones', label: 'Reevaluaciones', count: b.filter((c) => c.tipo === 'Reevaluación').length },
    ];
    if (this.session.esAdmin()) items.push({ id: 'pendientes', label: 'Por controlar', count: this.pendientes().length });
    return items;
  });
  readonly lista = computed(() => {
    const v = this.vista();
    return this.base().filter((c) => v === 'controles' || (v === 'evaluaciones' ? c.tipo === 'Evaluación' : c.tipo === 'Reevaluación'));
  });
  readonly pagina = computed(() => paginar(this.lista(), this.page(), this.size()));

  abrirNuevo(loteId = '') {
    this.loteSel = loteId;
    this.nuevo.set(true);
  }
  readonly draft = formDraft('quality/start', () => ({ lote: this.loteSel }), value => { this.loteSel = value.lote; });
  async iniciar(loteId: string) {
    const r = await this.store.iniciarControl(loteId);
    if (!r.ok) return this.ui.toast(r.error, true);
    await this.draft.clear();
    this.ui.toast(this.store.control(r.id)?.status === 'STARTING' ? 'Inicio solicitado. Esperando confirmación de Producción.' : 'Control iniciado. Registra las pruebas.');
    this.nuevo.set(false);
    this.router.navigate(['/calidad', r.id]);
  }
}
