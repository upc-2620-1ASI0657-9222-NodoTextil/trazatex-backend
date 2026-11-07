import { effect } from '@angular/core';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Proceso, TIPOS_TRANSFORMACION } from '../../core/models';
import { SessionService } from '../../core/session.service';
import { StoreService } from '../../core/store.service';
import { DENSITY, SubnavItem, paginar } from '../../shared/density';
import { SHARED } from '../../shared/ui';
import { FinalizarProcesoDialog, NuevoProcesoDialog } from './proceso-dialogs';

type Vista = 'curso' | 'fin' | 'todos';

@Component({
  selector: 'app-procesos',
  imports: [...SHARED, ...DENSITY, FormsModule, NuevoProcesoDialog, FinalizarProcesoDialog],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page page--procesos">
      <ng-template appHeaderActions>
          @if (session.esOperario()) {
            <button class="btn btn--primary" type="button" (click)="nuevo.set(true)"><app-icon name="plus" [size]="16" /> Iniciar transformación</button>
          } @else {
            <app-readonly />
          }
        </ng-template>

      <app-subnav panel="procesos" [items]="vistas()" [(active)]="vista" (activeChange)="page.set(0)" label="Vistas de procesos" />
      <div appTabPanel="procesos" [tabActive]="vista()">

      <div class="filters">
        <label class="search">
          <app-icon name="search" [size]="16" />
          <input class="input" placeholder="Buscar proceso o lote" [ngModel]="q()" (ngModelChange)="q.set($event); page.set(0)" aria-label="Buscar procesos" />
        </label>
        <button class="btn btn--ghost" type="button" (click)="verFiltros.set(true)"><app-icon name="filter" [size]="16" /> Filtros</button>
      </div>

      @if (lista().length) {
        <div class="rows">
          @for (p of pagina(); track p.id) {
            <button type="button" class="row-card {{ p.fin ? 'row-card--ok' : 'row-card--warn' }}" [class.is-sel]="selId() === p.id" (click)="selId.set(p.id)">
              <span class="rc-id">{{ store.referencia(p.id) }}</span>
              <app-icon class="rc-figure" name="process" [size]="42" />
              <span class="rc-state">{{ p.fin ? 'Finalizado' : 'En curso' }}{{ store.esMezcla(p) ? ' / mezcla' : '' }}</span>
              <span class="rc-title">{{ store.nombreProceso(p) }}</span>
              <span class="rc-meta">{{ p.entradas.length }} entrada(s) / {{ p.salidasIds.length || '—' }} salida(s) / <span class="num">{{ p.inicio | fecha: false }}</span>@if (session.esDueno()) { / {{ store.nombreEmpresa(p.empresaId) }}}</span>
            </button>
          }
        </div>
        <app-pager [total]="lista().length" [(page)]="page" [(size)]="size" />
      } @else if (store.loading()) {
        <app-skeleton />
      } @else {
        <div class="empty"><strong>Sin procesos en esta vista</strong><span>Cambia de vista o ajusta la búsqueda.</span></div>
      }
      </div>
    </div>

    @if (verFiltros()) {
      <app-drawer title="Filtros de procesos" (closed)="verFiltros.set(false)">
        <label class="field"><span>Tipo de proceso</span>
          <select class="select" [ngModel]="fTipo()" (ngModelChange)="fTipo.set($event); page.set(0)">
            <option value="">Todos los tipos</option>
            @for (t of tipos; track t) { <option [value]="t">{{ t }}</option> }
          </select>
        </label>
        @if (session.esDueno()) {
          <label class="field"><span>Empresa</span>
            <select class="select" [ngModel]="fEmpresa()" (ngModelChange)="fEmpresa.set($event); page.set(0)">
              <option value="">Todas las empresas</option>
              @for (e of store.empresas(); track e.id) { <option [value]="e.id">{{ e.razonSocial }}</option> }
            </select>
          </label>
        }
        <div footer class="d-actions">
          <button class="btn btn--ghost" type="button" (click)="fTipo.set(''); fEmpresa.set(''); page.set(0)">Limpiar</button>
          <button class="btn btn--primary" type="button" (click)="verFiltros.set(false)">Ver resultados</button>
        </div>
      </app-drawer>
    }
    @if (sel(); as p) {
      @let propio = store.puedeVerPrivado(p.empresaId);
      <app-drawer [title]="store.nombreProceso(p)" [eyebrow]="store.referencia(p.id) + (store.esMezcla(p) ? ' / mezcla' : '')" [sub]="store.nombreEmpresa(p.empresaId)" (closed)="selId.set(null)">
        <section class="d-section">
          <h3>Evidencias / {{ p.evidencias.length }}</h3>
          <app-evidence ownerType="proceso" [ownerId]="p.id" [items]="p.evidencias" [canAdd]="session.esOperario() && propio" (added)="store.agregarEvidencia('proceso', p.id, $event)" />
        </section>
        <section class="d-section">
          <h3>Flujo de material</h3>
          <div class="flow">
            <div class="chips">@for (e of p.entradas; track e.loteId) {<a class="chip-id" [routerLink]="['/lotes', e.loteId]">{{ e.loteId }}</a>}</div>
            <app-icon name="arrowRight" [size]="16" />
            <div class="chips">@for (s of p.salidasIds; track s) {<a class="chip-id" [routerLink]="['/lotes', s]">{{ s }}</a>} @empty {<span class="small muted">Salidas al finalizar</span>}</div>
          </div>
        </section>
        <section class="d-section">
          <h3>Balance</h3>
          @if (p.fin) {
            @if (store.puedeVer('merma', p.empresaId) && store.puedeVer('cantidad', p.empresaId)) {
              <dl class="dl">
                <dt>Entrada</dt><dd class="num">{{ store.totalEntrada(p) | kg }}</dd>
                <dt>Salida</dt><dd class="num">{{ store.totalSalida(p) | kg }}</dd>
                <dt>Merma</dt><dd class="num">{{ store.merma(p) | kg }}</dd>
                <dt>Desperdicio</dt><dd class="num">{{ p.desperdicioKg | kg }}</dd>
              </dl>
            } @else {
              <p class="row"><app-priv /> <span class="small muted">Balance privado para la empresa responsable.</span></p>
            }
          } @else {
            <p class="small muted">En curso desde {{ p.inicio | fecha }}. El balance se calcula al finalizar.</p>
          }
        </section>
        <section class="d-section">
          <h3>Contexto</h3>
          <dl class="dl">
            <dt>Operario</dt><dd>@if (propio) {{{ store.nombreResponsable(p.operarioId) }}} @else {<app-priv [always]="true" />}</dd>
            <dt>Maquinaria</dt><dd>@if (store.puedeVer('maquinaria', p.empresaId)) {{{ store.nombreMaquinaria(p) }}} @else {<app-priv />}</dd>
            <dt>Duración</dt><dd class="num">{{ store.duracionH(p.inicio, p.fin) | horas }}</dd>
          </dl>
        </section>
        @if (session.esOperario() && propio && !p.fin) {
          <div class="d-actions"><button class="btn btn--primary" type="button" (click)="finalizar.set(p)"><app-icon name="check" [size]="16" /> Finalizar transformación</button></div>
        }
        <ng-container footer>
          <a class="btn btn--primary btn--sm" [routerLink]="['/procesos', p.id]">Abrir proceso completo <app-icon name="arrowRight" [size]="14" /></a>
        </ng-container>
      </app-drawer>
    }

    @if (nuevo()) {
      <app-nuevo-proceso [preselect]="preNuevo()" (closed)="nuevo.set(false)" />
    }
    @if (finalizar(); as p) {
      <app-finalizar-proceso [proceso]="p" (closed)="finalizar.set(null)" />
    }
  `,
})
export class ProcesosPage {
  constructor() { effect(() => { const id = this.selId(); const entity = id ? this.store.proceso(id) : null; if (id && entity && entity.empresaId === this.session.empresaId()) this.store.requestEvidence('proceso', id); }); }

  readonly store = inject(StoreService);
  readonly session = inject(SessionService);
  readonly router = inject(Router);

                                                                      
  readonly preNuevo = input<string | undefined>(undefined, { alias: 'nuevo' });

  readonly tipos = TIPOS_TRANSFORMACION;
  readonly vista = signal<Vista>('curso');
  readonly q = signal('');
  readonly verFiltros = signal(false);
  readonly fTipo = signal('');
  readonly fEmpresa = signal('');
  readonly page = signal(0);
  readonly size = signal(6);
  readonly nuevo = signal(false);
  readonly selId = signal<string | null>(null);
  readonly sel = computed(() => this.store.proceso(this.selId()) ?? null);
  readonly finalizar = signal<Proceso | null>(null);

  ngOnInit() {
    if (this.preNuevo() && this.session.esOperario()) this.nuevo.set(true);
    if (!this.base().some((p) => !p.fin)) this.vista.set('fin');
  }

  private base = computed(() => {
    const q = this.q().trim().toLowerCase();
    return this.store
      .misProcesos()
      .filter(
        (p) =>
          (!this.fEmpresa() || p.empresaId === this.fEmpresa()) &&
          (!this.fTipo() || p.tipo === this.fTipo()) &&
          (!q ||
            p.id.toLowerCase().includes(q) ||
             this.store.nombreProceso(p).toLowerCase().includes(q) ||
            p.entradas.some((e) => e.loteId.toLowerCase().includes(q)) ||
            p.salidasIds.some((s) => s.toLowerCase().includes(q))),
      )
      .sort((a, b) => b.inicio.localeCompare(a.inicio));
  });
  private en = (p: Proceso, v: Vista) => v === 'todos' || (v === 'curso' ? !p.fin : !!p.fin);
  readonly vistas = computed<SubnavItem[]>(() => [
    { id: 'curso', label: 'En curso', count: this.base().filter((p) => !p.fin).length },
    { id: 'fin', label: 'Finalizados', count: this.base().filter((p) => !!p.fin).length },
    { id: 'todos', label: 'Todos', count: this.base().length },
  ]);
  readonly lista = computed(() => this.base().filter((p) => this.en(p, this.vista())));
  readonly pagina = computed(() => paginar(this.lista(), this.page(), this.size()));
}

