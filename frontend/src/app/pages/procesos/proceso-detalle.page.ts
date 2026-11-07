import { effect } from '@angular/core';
import { DENSITY } from '../../shared/density';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { SessionService } from '../../core/session.service';
import { StoreService } from '../../core/store.service';
import { SHARED, UiService } from '../../shared/ui';
import { FinalizarProcesoDialog } from './proceso-dialogs';

@Component({
  selector: 'app-proceso-detalle',
  imports: [...SHARED, ...DENSITY, FinalizarProcesoDialog],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (proceso(); as p) {
      @let propio = store.puedeVerPrivado(p.empresaId);
      <div class="page detail-page page--proceso-detalle">
        <ng-template appHeaderActions>
            <a class="btn btn--ghost" routerLink="/procesos"><app-icon name="arrowLeft" [size]="15" /> Procesos</a>
            <a class="btn btn--ghost" [routerLink]="['/trazabilidad']" [queryParams]="{ lote: p.entradas[0].loteId }"><app-icon name="trace" [size]="16" /> Ver en genealogía</a>
                  </ng-template>

        <div class="split">
          <div class="folds">
<app-fold n="01" title="Entradas" [summary]="p.entradas.length + ' lote(s)'" [open]="true">
              <div class="mini-cards">
                @for (e of p.entradas; track e.loteId) {
                  @if (store.lote(e.loteId); as l) {
                  <div class="mini mini--hist">
                    <div class="mini-top"><app-lot-link [id]="e.loteId" /><b class="num">@if (store.puedeVer('cantidad', p.empresaId)) {{{ e.cantidadKg | kg }}} @else {<app-priv />}</b></div>
                    <span class="small"><b>{{ l.material }}</b></span>
                    <span class="small muted">@if (store.puedeVer('composicion', l.empresaId)) { @for (c of l.composicion; track c.nombre) { {{ c.porcentaje }} % {{ c.nombre }}&nbsp; } } @else {<app-priv />}</span>
                  </div>
                  } @else {<p class="small" role="status">Consultando el lote relacionado…</p>}
                }
              </div>
            </app-fold>

            

            

            <app-fold n="02" title="Salidas" [summary]="p.salidasIds.length ? p.salidasIds.length + ' lote(s)' : 'Al finalizar'">
              @if (p.salidasIds.length) {
                <div class="mini-cards">
                  @for (s of p.salidasIds; track s) {
                    @if (store.lote(s); as l) {
                    <div class="mini mini--ok">
                      <div class="mini-top"><app-lot-link [id]="s" /><b class="num">@if (store.puedeVer('cantidad', l.empresaId)) {{{ l.cantidadKg | kg }}} @else {<app-priv />}</b></div>
                      <span class="small"><b>{{ l.material }}</b></span>
                      <span class="small muted">@if (store.puedeVer('composicion', l.empresaId)) { @for (c of l.composicion; track c.nombre) { {{ c.porcentaje }} % {{ c.nombre }}&nbsp; } } @else {<app-priv />}</span>
                      <app-lot-status [lote]="l" [showAvail]="false" />
                    </div>
                    } @else {<p class="small" role="status">Consultando el lote relacionado…</p>}
                  }
                </div>
              } @else {
                <p class="small muted">Las salidas se registran al finalizar la transformación.</p>
              }
            </app-fold>
          <app-fold n="03" title="Balance" [summary]="p.fin ? ((store.merma(p) ?? 0) + ' kg merma') : 'En curso'">
              @if (p.fin) {
                @if (verBalance()) {
                  <div class="bal">
                    <div class="bal-row">
                      <span class="label">Entrada total</span>
                      <div class="bar-in"><i style="width:100%"></i></div>
                      <b class="num">{{ entrada() | kg }}</b>
                    </div>
                    <div class="bal-row">
                      <span class="label">Distribución</span>
                      <div class="bar-split">
                        <i class="s" [style.flex]="salida()" [attr.data-tip]="'Salida ' + salida() + ' kg'"></i>
                        <i class="m" [style.flex]="merma()" [attr.data-tip]="'Merma ' + merma() + ' kg'"></i>
                        <i class="d" [style.flex]="desperdicio()" [attr.data-tip]="'Desperdicio ' + desperdicio() + ' kg'"></i>
                      </div>
                      <span></span>
                    </div>
                  </div>
                  <div class="eq">
                    <div><span class="label">Entrada total</span><b class="num">{{ entrada() | kg }}</b></div>
                    <span class="op">=</span>
                    <div><i class="sw s"></i><span class="label">Salida total</span><b class="num">{{ salida() | kg }}</b></div>
                    <span class="op">+</span>
                    <div><i class="sw m"></i><span class="label">Merma (calculada)</span><b class="num">{{ merma() | kg }}</b></div>
                    <span class="op">+</span>
                    <div><i class="sw d"></i><span class="label">Desperdicio</span><b class="num">{{ desperdicio() | kg }}</b></div>
                  </div>
                  <p class="small muted" style="margin-top:.6rem">
                    Rendimiento {{ ((salida() / entrada()) * 100).toFixed(1) }} %. Merma = Entrada − Salida − Desperdicio.
                  </p>
                } @else {
                  <p class="row"><app-priv /> <span class="small muted">Merma y desperdicio son privados para {{ store.nombreEmpresa(p.empresaId) }}.</span></p>
                }
              } @else {
                <div class="notice"><app-icon name="clock" /><span>Proceso en curso desde {{ p.inicio | fecha }}. El balance se calcula al finalizar.</span></div>
              }
            </app-fold>
            @if (session.esOperario() && propio && !p.fin) {
              <button class="btn btn--primary" type="button" (click)="finalizar.set(true)"><app-icon name="check" [size]="16" /> Finalizar transformación</button>
            } @else if (!session.esOperario()) {
              <app-readonly />
            }
</div>

          <div class="folds">
            <app-fold title="Maquinaria y contexto" [summary]="store.puedeVer('maquinaria', p.empresaId) ? (store.nombreMaquinaria(p) ?? '') : 'Privado'" [open]="true">
              <dl class="dl">
                <dt>Identificador</dt><dd class="id">{{ store.referencia(p.id) }}</dd>
                <dt>Empresa responsable</dt><dd>{{ store.nombreEmpresa(p.empresaId) }}</dd>
                <dt>Operario responsable</dt>
                <dd>@if (propio) {{{ store.nombreResponsable(p.operarioId) }}} @else {<app-priv [always]="true" />}</dd>
                <dt>Tipo</dt><dd>{{ p.tipo }}</dd>
                <dt>Maquinaria</dt>
                <dd>@if (store.puedeVer('maquinaria', p.empresaId)) {<span class="id">{{ p.maquinariaId }}</span> {{ store.nombreMaquinaria(p) }}} @else {<app-priv />}</dd>
                <dt>Inicio</dt><dd class="num">{{ p.inicio | fecha }}</dd>
                <dt>Fin</dt><dd class="num">{{ p.fin | fecha }}</dd>
                <dt>Duración</dt><dd class="num">{{ store.duracionH(p.inicio, p.fin) | horas }}</dd>
                @if (p.fin) {
                  <dt>Motivo del desperdicio</dt>
                  <dd>@if (store.puedeVer('motivoDesperdicio', p.empresaId)) {{{ p.motivoDesperdicio ?? '—' }}} @else {<app-priv />}</dd>
                }
              </dl>
            </app-fold>
            <app-fold title="Evidencias" [summary]="p.evidencias.length + ' foto(s)'" [open]="true">
              <app-evidence ownerType="proceso" [ownerId]="p.id" [items]="p.evidencias" [canAdd]="session.esOperario() && propio" (added)="addEv($event)" />
            </app-fold>
          </div>
        </div>
      </div>
      @if (finalizar()) {
        <app-finalizar-proceso [proceso]="p" (closed)="finalizar.set(false)" />
      }
    } @else {
      <div class="page"><div class="empty"><strong>Proceso no encontrado</strong><a routerLink="/procesos">Volver</a></div></div>
    }
  `,
  styles: `
    .bal { display: grid; gap: .65rem; }
    .bal-row { display: grid; grid-template-columns: minmax(6rem, 7.5rem) 1fr 5.5rem; gap: .75rem; align-items: center; }
    .bal-row b { text-align: right; }
    .bar-in, .bar-split { height: 1.1rem; display: flex; gap: 2px; }
    .bar-in i { background: var(--green-800); }
    .bar-split i { min-width: 3px; }
    .s, .sw.s { background: var(--green-600); }
    .m, .sw.m { background: var(--kraft-2); }
    .d, .sw.d { background: var(--brown-ink); }
    .eq { display: flex; flex-wrap: wrap; gap: .6rem 1rem; align-items: end; margin-top: 1rem; padding-top: .9rem; border-top: 1px solid var(--hair); }
    .eq > div { display: grid; grid-template-columns: auto 1fr; column-gap: .35rem; align-items: center; }
    .eq > div > .label { grid-column: 2; }
    .eq > div > b { grid-column: 1 / -1; font-size: 1.05rem; }
    .eq > div:first-child > .label { grid-column: 1 / -1; }
    .sw { width: .7rem; height: .7rem; grid-row: 1; }
    .op { font: 700 1.2rem var(--f-body); color: var(--brown-ink); padding-bottom: .1rem; }
    .eq > div { padding: .3rem 0; }
    @media (max-width: 560px) { .bal-row { grid-template-columns: 6rem 1fr; } .bal-row b { grid-column: 2; } }
  `,
})
export class ProcesoDetallePage {
  constructor() { effect(() => { const id = this.id(); const entity = id ? this.store.proceso(id) : null; if (entity?.entradas[0]) this.store.requestLineage(entity.entradas[0].loteId); if (id && entity && entity.empresaId === this.session.empresaId()) this.store.requestEvidence('proceso', id); }); }

  readonly store = inject(StoreService);
  readonly session = inject(SessionService);
  private readonly ui = inject(UiService);
  readonly id = input.required<string>();
  readonly proceso = computed(() => this.store.proceso(this.id()));
  readonly finalizar = signal(false);

  readonly entrada = computed(() => this.store.totalEntrada(this.proceso()!));
  readonly salida = computed(() => this.store.totalSalida(this.proceso()!));
  readonly merma = computed(() => this.store.merma(this.proceso()!) ?? 0);
  readonly desperdicio = computed(() => this.proceso()!.desperdicioKg ?? 0);
  readonly verBalance = computed(() => {
    const e = this.proceso()!.empresaId;
    return this.store.puedeVer('merma', e) && this.store.puedeVer('desperdicio', e) && this.store.puedeVer('cantidad', e);
  });

  async addEv(f: File) {
    const result = await this.store.agregarEvidencia('proceso', this.id(), f);
    if (result.ok) this.ui.toast('Evidencia registrada');
  }
}


