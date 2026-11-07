import { SessionService } from '../core/session.service';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { StoreService } from '../core/store.service';
import { SHARED } from '../shared/ui';

@Component({
  selector: 'app-licencia',
  imports: [...SHARED],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page page--licencia">
      

      <div class="lic">
        <section class="tag">
          <span class="hole"></span>
          <span class="label">Licencia</span>
          <b class="kind">Perpetua</b>
          <dl class="dl">
            <dt>Titular</dt><dd>{{ session.usuario().nombres }} {{ session.usuario().apellidos }}</dd>
            <dt>Límite de empresas</dt><dd class="num">{{ L().limiteEmpresas }}</dd>
            <dt>Usuarios por empresa</dt><dd class="num">{{ L().limiteUsuariosPorEmpresa }}</dd>
          </dl>
        </section>

        <div class="stack">
          <div class="kpis" style="margin:0">
            <div class="kpi"><span class="label">Empresas usadas</span><span class="v">{{ empresas().length }}<small>/ {{ L().limiteEmpresas }}</small></span><b class="capacity-percent">{{ ((empresas().length / (L().limiteEmpresas || 1)) * 100).toFixed(0) }} %</b></div>
            <div class="kpi"><span class="label">Empresas activas</span><span class="v">{{ activas() }}</span></div>
            <div class="kpi"><span class="label">Cupos ocupados</span><span class="v">{{ totalUsuarios() }}</span></div>
            <div class="kpi"><span class="label">Cupos de empresa libres</span><span class="v">{{ L().limiteEmpresas - empresas().length }}</span></div>
          </div>
          <section class="sheet">
            <div class="sheet-head"><h2 class="sheet-title">Uso de empresas</h2><strong class="capacity-percent">{{ ((empresas().length / (L().limiteEmpresas || 1)) * 100).toFixed(0) }} % ocupado</strong></div>
            <div class="meter license-usage" role="meter" [attr.aria-valuenow]="empresas().length" [attr.aria-valuemax]="L().limiteEmpresas" aria-valuemin="0" aria-label="Empresas usadas"><i [style.width.%]="(empresas().length / (L().limiteEmpresas || 1)) * 100"></i></div>
            <div class="slots">
              @for (s of slots(); track $index) {
                <span class="slot" [class.used]="s" [class.off]="s?.estado === 'Inactiva'" [attr.data-tip]="s ? s.razonSocial + ' / ' + s.estado : 'Cupo disponible'">{{ s ? s.razonSocial.split(' ')[0] : 'Libre' }}</span>
              }
            </div>
          </section>
          <section class="sheet">
            <div class="sheet-head"><h2 class="sheet-title">Usuarios por empresa</h2></div>
            <div class="cards cards--tight">
              @for (e of empresas(); track e.id) {
                @let n = usedSlots(e.id);
                <article class="card {{ e.estado === 'Activa' ? (n / (L().limiteUsuariosPorEmpresa || 1) >= 0.8 ? 'card--warn' : 'card--ok') : 'card--hist' }}">
                  <header class="card-head"><span class="label">{{ e.estado }}</span><b class="num">{{ ((n / (L().limiteUsuariosPorEmpresa || 1)) * 100).toFixed(0) }} %</b></header>
                  <div class="card-body">
                    <h3 class="card-title">{{ e.razonSocial }}</h3>
                    <div class="meter"><i [style.width.%]="(n / (L().limiteUsuariosPorEmpresa || 1)) * 100" [class.warn]="n / (L().limiteUsuariosPorEmpresa || 1) >= 0.8"></i></div>
                    <p class="card-sub"><b class="num">{{ n }}</b> de <b class="num">{{ L().limiteUsuariosPorEmpresa }}</b> cupos ocupados</p>
                  </div>
                </article>
              }
            </div>
          </section>
        </div>
      </div>
    </div>
  `,
  styles: `
    .lic { display: grid; grid-template-columns: 19rem minmax(0, 1fr); gap: 1.5rem; align-items: start; }
    @media (max-width: 900px) { .lic { grid-template-columns: 1fr; } }
    .tag { position: relative; padding: 2.4rem 1.4rem 1.4rem; background: linear-gradient(rgb(11 32 22 / .78), rgb(11 32 22 / .78)), url('/tex/marron.webp') center / 320px, var(--green-900); background-blend-mode: normal; color: var(--cream);
      border: 1px solid var(--green-900); }
    .tag .hole { position: absolute; top: .9rem; left: 50%; translate: -50% 0; width: .7rem; height: .7rem; border-radius: 50%; background: var(--canvas); box-shadow: inset 0 0 0 2px var(--kraft); }
    .tag .label { color: var(--kraft); }
    .kind { display: block; font-family: var(--f-display); font-size: 3rem; font-weight: 400; line-height: 1; margin: .2rem 0 1rem; }
    .tag .dl > dt { color: #e1dfce !important; }
    .tag .dl > dd { color: #fff9ec !important; }
    .tag .dl > dt, .tag .dl > dd { background: transparent; border: 0; border-bottom: 1px solid rgb(239 231 210 / .2); }
    .capacity-percent { color: var(--green-900); font-size: .95rem; font-weight: 800; font-variant-numeric: tabular-nums; }
    .license-usage { margin: .2rem 0 1rem; height: .55rem; }
    .kpi .capacity-percent { margin-top: .25rem; }
    .slots { display: grid; grid-template-columns: repeat(auto-fill, minmax(8rem, 1fr)); gap: .5rem; }
    .slot { padding: .8rem .6rem; text-align: center; font-weight: 650; font-size: .84rem; border: 1px solid var(--brown); color: var(--muted); }
    .slot.used { border: 1px solid var(--green-800); background: var(--green-800); color: var(--cream); }
    .slot.used.off { background: var(--canvas-2); color: var(--brown-ink); border-color: var(--brown); }
    
    @media (max-width: 520px) { .slots { overflow-x: clip; } }
  `,
})
export class LicenciaPage {
  readonly store = inject(StoreService); readonly session = inject(SessionService);
  readonly L = this.store.licencia;
  readonly empresas = this.store.empresas;
  readonly activas = computed(() => this.empresas().filter((e) => e.estado === 'Activa').length);
  readonly totalUsuarios = computed(() => this.store.licenseUsage()?.companies.reduce((sum, c) => sum + c.usedSlots, 0) ?? 0);
  usedSlots(id: string) { return this.store.licenseUsage()?.companies.find(c => c.companyId === id)?.usedSlots ?? 0; }
  readonly slots = computed(() => Array.from({ length: this.L().limiteEmpresas }, (_, i) => this.empresas()[i] ?? null));
}


