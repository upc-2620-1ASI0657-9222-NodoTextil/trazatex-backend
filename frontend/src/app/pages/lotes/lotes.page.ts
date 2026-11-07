import { QrScannerComponent } from '../../shared/qr-scanner.component';
import { effect } from '@angular/core';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ESTADOS_CALIDAD, FASES, Lote, MATERIALES } from '../../core/models';
import { SessionService } from '../../core/session.service';
import { StoreService } from '../../core/store.service';
import { DENSITY, SubnavItem, paginar } from '../../shared/density';
import { SHARED } from '../../shared/ui';
import { DividirLoteDialog, NuevoLoteDialog, ProductoFinalDialog } from './lote-dialogs';

type Vista = 'todos' | 'disponibles' | 'operacion' | 'transferencia' | 'historicos' | 'final';

@Component({
  selector: 'app-lotes',
  imports: [QrScannerComponent, ...SHARED, ...DENSITY, FormsModule, NuevoLoteDialog, DividirLoteDialog, ProductoFinalDialog],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (scanning()) { <app-qr-scanner (closed)="scanning.set(false)" /> }
    <div class="page page--lotes">
      <ng-template appHeaderActions>
        <button class="btn btn--ghost" type="button" (click)="scanning.set(true)"><app-icon name="qr" [size]="16" /> Escanear QR</button>
          @if (session.esOperario()) {
            <button class="btn btn--primary" type="button" (click)="nuevo.set(true)"><app-icon name="plus" [size]="16" /> Registrar lote</button>
          } @else {
            <app-readonly />
          }
        </ng-template>

      <app-subnav panel="lotes" [items]="vistas()" [(active)]="vista" (activeChange)="page.set(0)" label="Vistas de lotes" />
      <div appTabPanel="lotes" [tabActive]="vista()">

      <div class="filters">
        <label class="search">
          <app-icon name="search" [size]="16" />
          <input class="input" placeholder="Buscar nombre, ID, material u origen" [ngModel]="q()" (ngModelChange)="q.set($event); page.set(0)" aria-label="Buscar lotes" />
        </label>
        <app-more-filters [activos]="masActivos()" (open)="masFiltros.set(true)" />
      </div>

      @if (filtrados().length) {
        <div class="rows">
          @for (l of pagina(); track l.id) {
            @let d = store.disponibilidad(l);
            <button type="button" class="row-card row-card--{{ d.tono }}" [class.is-sel]="sel()?.id === l.id" [class.is-dim]="store.esHistorico(l) && !l.productoFinal" (click)="selId.set(l.id)">
              <span class="rc-id">{{ l.id }}</span>
              <app-icon class="rc-figure" name="lot" [size]="42" />
              <span class="rc-state">@if (d.bloqueado && d.tono !== 'hist') {<app-icon name="lock" [size]="11" [stroke]="2" />}{{ l.productoFinal ? 'Producto final' : d.etiqueta }}</span>
              <span class="rc-title">{{ l.nombre }}</span>
              <span class="rc-meta"><span class="num">{{ l.cantidadKg | kg }}</span> / {{ l.calidad }}@if (session.esDueno()) { / {{ store.nombreEmpresa(l.empresaId) }}}</span>
            </button>
          }
        </div>
        <app-pager [total]="filtrados().length" [(page)]="page" [(size)]="size" [sizes]="[8]" />
      } @else if (store.loading()) {
        <app-skeleton />
      } @else {
        <div class="empty"><strong>Sin lotes en esta vista</strong><span>Cambia de vista o ajusta los filtros.</span></div>
      }
      </div>
    </div>

    
    @if (sel(); as l) {
      @let d = store.disponibilidad(l);
      @let propio = store.puedeVerPrivado(l.empresaId);
      <app-drawer [title]="l.nombre || l.id" [eyebrow]="l.material" [sub]="store.nombreEmpresa(l.empresaId)" (closed)="selId.set(null)">
        <app-lot-status [lote]="l" />
        @if (d.bloqueado && d.tono !== 'hist') {
          <div class="notice" [class.notice--fail]="d.tono === 'fail'" [class.notice--warn]="d.tono === 'warn'"><app-icon name="lock" /><span>{{ d.motivo }}</span></div>
        }
        @if (session.esOperario() && propio) {
          <section class="d-section">
            <h3>Acciones de producción</h3>
            <div class="d-actions">
              <app-action cls="btn--primary" icon="split" [reason]="d.bloqueado ? d.motivo : null" (run)="dividir.set(l)">Dividir</app-action>
              <app-action cls="btn--primary" icon="process" [reason]="d.bloqueado ? d.motivo : null" (run)="router.navigate(['/procesos'], { queryParams: { nuevo: l.id } })">Transformar</app-action>
              <app-action cls="btn--primary" icon="transfer" [reason]="d.bloqueado ? d.motivo : null" (run)="router.navigate(['/transferencias'], { queryParams: { nuevo: l.id } })">Transferir</app-action>
              @if (l.productoFinal) {
                <button type="button" class="btn btn--ghost btn--sm" (click)="final.set(l)">Datos comerciales</button>
              }
            </div>
          </section>
        }
        <section class="d-section">
          <h3>Evidencias / {{ l.evidencias.length }}</h3>
          <app-evidence ownerType="lote" [ownerId]="l.id" [items]="l.evidencias" [canAdd]="session.esOperario() && propio" (added)="store.agregarEvidencia('lote', l.id, $event)" />
        </section>
        <section class="d-section">
          <h3>Resumen</h3>
          <dl class="dl">
            <dt>Cantidad</dt>
            <dd>@if (store.puedeVer('cantidad', l.empresaId)) {<span class="num">{{ l.cantidadKg | kg }}</span>} @else {<app-priv />}</dd>
            <dt>Origen</dt>
            <dd>@if (store.puedeVer('origen', l.empresaId)) {{{ l.origen }}} @else {<app-priv />}</dd>
            <dt>Composición</dt>
            <dd>@if (store.puedeVer('composicion', l.empresaId)) {<app-composition [comp]="l.composicion" />} @else {<app-priv />}</dd>
            <dt>Registro</dt>
            <dd class="num">{{ l.fechaRegistro | fecha }}</dd>
          </dl>
        </section>
        <ng-container footer>
          <a class="btn btn--ghost btn--sm" [routerLink]="['/trazabilidad']" [queryParams]="{ lote: l.id }"><app-icon name="trace" [size]="14" /> Genealogía</a>
          <a class="btn btn--primary btn--sm" [routerLink]="['/lotes', l.id]">Abrir ficha completa <app-icon name="arrowRight" [size]="14" /></a>
        </ng-container>
      </app-drawer>
    }

    @if (masFiltros()) {
      <app-drawer title="Filtros de lotes" (closed)="masFiltros.set(false)">
        <label class="field">
          <span>Calidad</span>
          <select class="select" [ngModel]="fCalidad()" (ngModelChange)="fCalidad.set($event); page.set(0)">
            <option value="">Toda calidad</option>
            @for (c of estados; track c) { <option [value]="c">{{ c }}</option> }
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

        <label class="field">
          <span>Tipo de material</span>
          <select class="select" [ngModel]="fMaterial()" (ngModelChange)="fMaterial.set($event); page.set(0)">
            <option value="">Todo material</option>
            @for (m of materiales(); track m) {
              <option [value]="m">{{ m }}</option>
            }
          </select>
        </label>
        <label class="field">
          <span>Fase operativa</span>
          <select class="select" [ngModel]="fFase()" (ngModelChange)="fFase.set($event); page.set(0)">
            <option value="">Toda fase</option>
            @for (f of fases; track f) {
              <option [value]="f">{{ f }}</option>
            }
          </select>
        </label>
        <label class="field">
          <span>Registrado desde</span>
          <input class="input" type="date" [ngModel]="fDesde()" (ngModelChange)="fDesde.set($event); page.set(0)" />
        </label>
        <ng-container footer>
          <button class="btn btn--ghost btn--sm" type="button" (click)="limpiar()">Limpiar</button>
          <button class="btn btn--primary btn--sm" type="button" (click)="masFiltros.set(false)">Ver {{ filtrados().length }} lotes</button>
        </ng-container>
      </app-drawer>
    }

    @if (nuevo()) {
      <app-nuevo-lote (closed)="nuevo.set(false)" />
    }
    @if (dividir(); as l) {
      <app-dividir-lote [lote]="l" (closed)="dividir.set(null)" />
    }
    @if (final(); as l) {
      <app-producto-final [lote]="l" (closed)="final.set(null)" />
    }
  `,
})
export class LotesPage {
  readonly scanning = signal(false);
  constructor() { effect(() => { const id = this.selId(); const entity = id ? this.store.lote(id) : null; if (id && entity && entity.empresaId === this.session.empresaId()) this.store.requestEvidence('lote', id); }); }

  readonly store = inject(StoreService);
  readonly session = inject(SessionService);
  readonly router = inject(Router);

  readonly materiales = computed(() => [...new Set([...MATERIALES, ...this.store.misLotes().map(lote => lote.material)])].sort());
  readonly fases = FASES;
  readonly estados = ESTADOS_CALIDAD;

  readonly vista = signal<Vista>('todos');
  readonly q = signal('');
  readonly fEmpresa = signal('');
  readonly fMaterial = signal('');
  readonly fFase = signal('');
  readonly fCalidad = signal('');
  readonly fDesde = signal('');
  readonly page = signal(0);
  readonly size = signal(8);
  readonly nuevo = signal(false);
  readonly masFiltros = signal(false);
  readonly selId = signal<string | null>(null);
                                                                        
  readonly sel = computed(() => this.store.lote(this.selId()) ?? null);
  readonly dividir = signal<Lote | null>(null);
  readonly final = signal<Lote | null>(null);

  private enVista(l: Lote, v: Vista) {
    switch (v) {
      case 'disponibles':
        return l.fase === 'Disponible' && !l.productoFinal;
      case 'operacion':
        return l.fase === 'En transformación' || l.fase === 'En control de calidad';
      case 'transferencia':
        return l.fase === 'En transferencia';
      case 'historicos':
        return ['Procesado', 'Dividido', 'Descartado'].includes(l.fase);
      case 'final':
        return l.productoFinal;
      default:
        return true;
    }
  }

  private base = computed(() => {
    const q = this.q().trim().toLowerCase();
    return this.store
      .misLotes()
      .filter(
        (l) =>
          (!q || l.nombre?.toLowerCase().includes(q) || l.id.toLowerCase().includes(q) || l.material.toLowerCase().includes(q) || l.origen.toLowerCase().includes(q)) &&
          (!this.fEmpresa() || l.empresaId === this.fEmpresa()) &&
          (!this.fMaterial() || l.material === this.fMaterial()) &&
          (!this.fFase() || l.fase === this.fFase()) &&
          (!this.fCalidad() || l.calidad === this.fCalidad()) &&
          (!this.fDesde() || l.fechaRegistro.slice(0, 10) >= this.fDesde()),
      )
      .sort((a, b) => b.fechaRegistro.localeCompare(a.fechaRegistro));
  });

  readonly vistas = computed<SubnavItem[]>(() => {
    const b = this.base();
    const n = (v: Vista) => b.filter((l) => this.enVista(l, v)).length;
    return [
      { id: 'todos', label: 'Todos', count: b.length },
      { id: 'disponibles', label: 'Disponibles', count: n('disponibles') },
      { id: 'operacion', label: 'En operación', count: n('operacion') },
      { id: 'transferencia', label: 'En transferencia', count: n('transferencia') },
      { id: 'historicos', label: 'Históricos', count: n('historicos') },
      { id: 'final', label: 'Producto final', count: n('final') },
    ];
  });
  readonly filtrados = computed(() => this.base().filter((l) => this.enVista(l, this.vista())));
  readonly pagina = computed(() => paginar(this.filtrados(), this.page(), this.size()));
  readonly masActivos = computed(() => [this.fMaterial(), this.fFase(), this.fDesde()].filter(Boolean).length);

  limpiar() {
    this.fMaterial.set('');
    this.fFase.set('');
    this.fDesde.set('');
    this.page.set(0);
  }
}

