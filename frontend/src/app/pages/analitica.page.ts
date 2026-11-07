import { ActivatedRoute } from '@angular/router';
import { FallasPage } from './fallas/fallas.page';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DecimalPipe } from '@angular/common';
import { ApiService, errorMessage } from '../core/api.service';
import { StoreService } from '../core/store.service';
import { SessionService } from '../core/session.service';
import { DENSITY } from '../shared/density';
import { SHARED } from '../shared/ui';

interface Summary {
  from: string; to: string; generatedAt: string;
  quality: {status: string; count: number; percentage: number}[];
  failureCauses: {cause: string; count: number}[];
  durations: {operation: string; count: number; averageHours: number}[];
  materials: {shrinkageKg: number; wasteKg: number; completedTransformations: number};
}
const names: Record<string,string> = { NOT_REVIEWED:'No revisado', CONFORMING:'Conforme', FAILED:'Con falla',
  PENDING_REEVALUATION:'Por reevaluar', POTENTIAL_DERIVED_FAILURE:'Posible falla derivada' };
/** El servicio rechaza más de 366 días (fechas inclusivas): se avisa antes de pedirlo. */
export const MAX_DAYS = 366;
export function daysBetween(from: string, to: string): number {
  return Math.round((Date.UTC(+to.slice(0,4), +to.slice(5,7)-1, +to.slice(8,10)) - Date.UTC(+from.slice(0,4), +from.slice(5,7)-1, +from.slice(8,10))) / 86400000) + 1;
}
function date(days = 0) {
  const d=new Date(); d.setDate(d.getDate()+days);
  return [d.getFullYear(),String(d.getMonth()+1).padStart(2,'0'),String(d.getDate()).padStart(2,'0')].join('-');
}

@Component({
  selector: 'app-analitica', imports: [...SHARED,...DENSITY,FormsModule,DecimalPipe,FallasPage], changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <!-- El título vive en la barra superior (regla de encabezados): aquí solo queda la frase de ayuda, sin .page-head ni segundo h2. -->
      <p class="page-lead">Indicadores calculados con los registros de Producción y Calidad de {{ store.nombreEmpresa(session.empresaId()) }}.</p>
      <form class="filters" (ngSubmit)="load()">
        <label class="field"><span>Desde</span><input class="input" type="date" name="from" [(ngModel)]="from" required /></label>
        <label class="field"><span>Hasta</span><input class="input" type="date" name="to" [(ngModel)]="to" [min]="from" required /></label>
        <button class="btn btn--primary" type="submit" [disabled]="loading()">Consultar</button>
        <button class="btn btn--ghost" type="button" (click)="period(0)" [disabled]="loading()">Hoy</button>
        <button class="btn btn--ghost" type="button" (click)="period(6)" [disabled]="loading()">7 días</button>
        <button class="btn btn--ghost" type="button" (click)="period(29)" [disabled]="loading()">30 días</button>
      </form>
      @if (error()) { <div class="notice notice--warn" role="alert">{{ error() }} <button class="btn btn--ghost btn--sm" type="button" (click)="load()">Reintentar</button></div> }
      @if (loading()) { <p role="status">Consultando indicadores…</p> }
      @if (data(); as d) {
        <p class="small muted">Periodo: {{ d.from }} al {{ d.to }} · America/Lima · Actualizado: {{ d.generatedAt | fecha }}</p>
        <app-subnav panel="analitica" [items]="tabs" [(active)]="tab" label="Secciones de analítica" />
        <div appTabPanel="analitica" [tabActive]="tab()">
        @switch (tab()) {
          @case ('calidad') {
            <div class="kpis">
              <div class="kpi"><span class="label">Lotes del periodo</span><span class="v">{{ totalLots() }}</span></div>
              <div class="kpi"><span class="label">Conformes</span><span class="v">{{ percentage('CONFORMING') }}<small>%</small></span></div>
              <div class="kpi"><span class="label">Con falla</span><span class="v">{{ percentage('FAILED') }}<small>%</small></span></div>
            </div>
            <section class="sheet"><div class="sheet-head"><h2 class="sheet-title">Lotes por estado de calidad</h2></div>
              <p class="small muted">Lotes registrados en el periodo y pertenecientes a tu empresa al cierre, con su último estado conocido hasta esa fecha.</p>
              @for (r of d.quality; track r.status) {
                <div class="metric-row"><span>{{ names[r.status] || r.status }}</span><progress [value]="r.count" [max]="totalLots() || 1" [attr.aria-label]="names[r.status] || r.status"></progress><b>{{ r.count }} / {{ r.percentage }} %</b></div>
              } @empty { <div class="empty"><strong>Sin lotes en este periodo</strong><span>Amplía las fechas para consultar los lotes registrados.</span></div> }
            </section>
          }
          @case ('fallas') {
            <div class="kpis"><div class="kpi"><span class="label">Fallas registradas</span><span class="v">{{ totalFailures() }}</span></div></div>
            <section class="sheet"><div class="sheet-head"><h2 class="sheet-title">Cantidad de fallas por causa</h2></div>
              @for (r of d.failureCauses; track r.cause) {
                <div class="metric-row"><span>{{ r.cause }}</span><progress [value]="r.count" [max]="totalFailures() || 1" [attr.aria-label]="r.cause"></progress><b>{{ r.count }}</b></div>
              } @empty { <div class="empty"><strong>Sin fallas registradas en este periodo</strong><span>Prueba con un rango de fechas más amplio.</span></div> }
            </section>
            <app-fallas [embedded]="true" />
          }
          @case ('tiempos') {
            <div class="kpis">
              @for (r of d.durations; track r.operation) {
                <div class="kpi"><span class="label">{{ r.operation }}</span><span class="v">{{ r.averageHours | number:'1.1-3' }}<small>h</small></span><span class="small muted">{{ r.count }} operación(es) finalizada(s)</span></div>
              } @empty { <div class="empty"><strong>Sin operaciones finalizadas en este periodo</strong><span>Los promedios se calculan al finalizar cada operación.</span></div> }
            </div>
            <p class="small muted">Duración desde el inicio hasta la finalización. Las transferencias son solo las aceptadas que tu empresa envió, desde que las inició hasta que se aceptaron; las rechazadas y las que recibiste no cuentan.</p>
          }
          @case ('perdidas') {
            <div class="kpis">
              <div class="kpi"><span class="label">Merma acumulada</span><span class="v">{{ d.materials.shrinkageKg | number:'1.0-3' }}<small>kg</small></span></div>
              <div class="kpi"><span class="label">Desperdicio acumulado</span><span class="v">{{ d.materials.wasteKg | number:'1.0-3' }}<small>kg</small></span></div>
              <div class="kpi"><span class="label">Transformaciones finalizadas</span><span class="v">{{ d.materials.completedTransformations }}</span></div>
            </div>
            <section class="sheet"><p>Merma = entrada total − salida total − desperdicio. Solo se acumulan transformaciones finalizadas dentro del periodo.</p>
              @if (!d.materials.completedTransformations) { <p class="small muted">No hay transformaciones finalizadas en estas fechas. Puedes ampliar el periodo.</p> }
            </section>
          }
        }
        </div>
        <p class="small muted" style="margin-top:1rem">Los indicadores se actualizan al recibir los eventos de las operaciones. Pulsa Consultar para actualizar.</p>
      }
    </div>
  `,
  styles: `
    .sheet-head .sheet-title { color: var(--cream); }
    .metric-row { display:grid; grid-template-columns:minmax(8rem,1fr) 1fr auto; gap:1rem; align-items:center; padding:.75rem 0; border-bottom:1px dashed var(--hair); }
    progress { width:100%; height:.7rem; accent-color:var(--green-800); }
    @media(max-width:600px) { .metric-row { grid-template-columns:1fr auto; } progress { grid-column:1 / -1; grid-row:2; } }
  `,
})
export class AnaliticaPage {
  readonly store=inject(StoreService); readonly session=inject(SessionService);
  private readonly api=inject(ApiService);
  readonly data=signal<Summary|null>(null); readonly loading=signal(false); readonly error=signal('');
  readonly names=names; readonly tab=signal(inject(ActivatedRoute).snapshot.queryParamMap.get('seccion') === 'fallas' ? 'fallas' : 'calidad');
  readonly tabs=[{id:'calidad',label:'Calidad'},{id:'fallas',label:'Fallas'},{id:'tiempos',label:'Tiempos'},{id:'perdidas',label:'Pérdidas'}];
  readonly totalLots=computed(() => this.data()?.quality.reduce((n,r) => n+r.count,0) ?? 0);
  readonly totalFailures=computed(() => this.data()?.failureCauses.reduce((n,r) => n+r.count,0) ?? 0);
  from=date(-29); to=date();
  constructor() { void this.load(); }
  percentage(status:string) { return this.data()?.quality.find(r => r.status===status)?.percentage ?? 0; }
  period(days:number) { this.from=date(-days); this.to=date(); void this.load(); }
  async load() {
    if(this.loading()) return;
    if(!this.from || !this.to || this.to<this.from) { this.error.set('Selecciona un periodo válido: Hasta debe ser igual o posterior a Desde.'); return; }
    if(daysBetween(this.from,this.to) > MAX_DAYS) { this.error.set('El periodo no puede superar '+MAX_DAYS+' días. Acorta las fechas.'); return; }
    this.loading.set(true); this.error.set('');
    try { this.data.set(await this.api.request<Summary>('GET','/analytics/summary?from='+this.from+'&to='+this.to)); }
    catch(error) { this.data.set(null); this.error.set(errorMessage(error)); }
    finally { this.loading.set(false); }
  }
}

