import { effect } from '@angular/core';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { SessionService } from '../../core/session.service';
import { StoreService } from '../../core/store.service';
import { DENSITY } from '../../shared/density';
import { SHARED, UiService } from '../../shared/ui';
import { DividirLoteDialog, ProductoFinalDialog } from './lote-dialogs';

type Tab = 'genealogia' | 'procesos' | 'controles' | 'transferencias' | 'evidencias';

@Component({
  selector: 'app-lote-detalle',
  imports: [...SHARED, ...DENSITY, DividirLoteDialog, ProductoFinalDialog],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (lote(); as l) {
      @let d = store.disponibilidad(l);
      @let propio = store.puedeVerPrivado(l.empresaId);
      <div class="page detail-page page--lote-detalle">
        <ng-template appHeaderActions>
            <a class="btn btn--ghost" routerLink="/lotes"
              ><app-icon name="arrowLeft" [size]="15" /> Lotes</a
            >
            <a
              class="btn btn--ghost"
              [routerLink]="['/trazabilidad']"
              [queryParams]="{ lote: l.id }"
              ><app-icon name="trace" [size]="16" /> Ver en genealogía</a
            >
            @if (session.esOperario() && propio) {

              <app-action
                icon="split" cls="btn--primary"
                [reason]="d.bloqueado ? d.motivo : null"
                (run)="dividir.set(true)"
                >Dividir</app-action
              >
              <app-action
                icon="process" cls="btn--primary"
                [reason]="d.bloqueado ? d.motivo : null"
                (run)="router.navigate(['/procesos'], { queryParams: { nuevo: l.id } })"
                >Transformar</app-action
              >
              <app-action
                icon="transfer" cls="btn--primary"
                [reason]="d.bloqueado ? d.motivo : null"
                (run)="router.navigate(['/transferencias'], { queryParams: { nuevo: l.id } })"
                >Transferir</app-action
              >
              @if (l.productoFinal) {
                <button type="button" class="btn btn--ghost btn--sm" (click)="final.set(true)">
                  Datos comerciales
                </button>
              }
            }
            @if (!session.esOperario()) {
              <app-readonly />
            }
</ng-template>

        @if (d.bloqueado && d.tono !== 'hist') {
          <div
            class="notice"
            [class.notice--fail]="d.tono === 'fail'"
            [class.notice--warn]="d.tono === 'warn'"
            style="margin-bottom:1rem"
          >
            <app-icon name="lock" />
            <span
              ><b>{{ d.etiqueta }}.</b> {{ d.motivo }}
              @if (store.fallaAbiertaDe(l.id); as f) {
                Falla <a [routerLink]="['/fallas', f.id]" class="id">{{ f.id }}</a
                >.
              }
              @if (store.controlActivo(l.id); as c) {
                Control <a [routerLink]="['/calidad', c.id]" class="id" [attr.title]="c.id">{{ store.referencia(c.id) }}</a
                >.
              }
            </span>
          </div>
        }

        @if (!propio && !session.esDueno()) {
          <div class="notice" role="note" data-testid="lote-ajeno" style="margin-bottom:1rem">
            <app-icon name="eye" />
            <span>Pertenece a <b>{{ store.nombreEmpresa(l.empresaId) }}</b>; solo ves los datos que comparte.</span>
          </div>
        }

        <div class="split">
          <div class="folds">
<app-fold n="01" title="Estado" [summary]="l.fase + ' / ' + l.calidad" [open]="true">
              <dl class="dl">
                <dt>Fase operativa</dt>
                <dd><app-badge [text]="l.fase" [tone]="tf(l.fase)" /></dd>
                <dt>Estado de calidad</dt>
                <dd><app-badge [text]="l.calidad" [tone]="tc(l.calidad)" /></dd>
                @if (l.failureCause || l.calidad === 'Con falla') {
                  <dt>Causa de falla</dt><dd>@if (store.puedeVer('causaFalla', l.empresaId)) { {{ l.failureCause || 'Pendiente de registrar' }} } @else { <app-priv /> }</dd>
                }
                @if (l.qualityTests?.length) {
                  <dt>Últimas pruebas</dt><dd>
                    @for (test of l.qualityTests; track $index) {
                      <p class="small"><b>{{ test.criterionName }}</b>: esperado {{ test.expectedValue }} {{ test.unit }}, obtenido {{ test.obtainedValue }} {{ test.unit }} · {{ test.testResult === 'CONFORMING' ? 'Conforme' : 'Con falla' }}</p>
                    }
                  </dd>
                }
                <dt>Producto final</dt>
                <dd>{{ l.productoFinal ? 'Sí' : 'No' }}</dd>
                <dt>Disponibilidad</dt>
                <dd>
                  <app-badge
                    [text]="d.etiqueta"
                    [tone]="
                      d.tono === 'ok'
                        ? 'ok'
                        : d.tono === 'fail'
                          ? 'fail'
                          : d.tono === 'warn'
                            ? 'warn'
                            : 'hist'
                    "
                    [icon]="d.bloqueado ? 'lock' : undefined"
                  />
                  <br /><span class="small muted">{{ d.motivo }}</span>
                </dd>
              </dl>
            </app-fold>

            <app-fold
              n="02"
              title="Identificación"
              [summary]="l.id + ' / ' + store.nombreEmpresa(l.empresaId)"
            >
              <dl class="dl">
                <dt>Código</dt>
                <dd class="id">{{ l.id }}</dd>
                @if (l.nombre && store.esPropio(l)) {
                  <dt>Nombre</dt>
                  <dd>{{ l.nombre }}</dd>
                }
                <dt>QR</dt>
                <dd class="num">{{ l.qr }}</dd>
                <dt>Tipo de material</dt>
                <dd>{{ l.material }}</dd>
                <dt>Empresa responsable</dt>
                <dd>{{ store.nombreEmpresa(l.empresaId) }}</dd>
                <dt>Fecha de registro</dt>
                <dd class="num">{{ l.fechaRegistro | fecha }}</dd>
              </dl>
            </app-fold>

            <app-fold
              n="03"
              title="Material"
              [summary]="
                store.puedeVer('cantidad', l.empresaId) ? l.cantidadKg + ' kg' : 'Datos privados'
              "
            >
              <dl class="dl">
                <dt>Proveedor</dt>
                <dd>
                  @if (store.puedeVer('proveedor', l.empresaId)) {
                    {{ l.proveedor ?? 'Desconocido' }}
                  } @else {
                    <app-priv />
                  }
                </dd>
                <dt>Origen geográfico</dt>
                <dd>
                  @if (store.puedeVer('origen', l.empresaId)) {
                    {{ l.origen }}
                  } @else {
                    <app-priv />
                  }
                </dd>
                <dt>Composición</dt>
                <dd>
                  @if (store.puedeVer('composicion', l.empresaId)) {
                    <app-composition [comp]="l.composicion" />
                  } @else {
                    <app-priv />
                  }
                </dd>
                <dt>Cantidad</dt>
                <dd>
                  @if (store.puedeVer('cantidad', l.empresaId)) {
                    <span class="num">{{ l.cantidadKg | kg }}</span>
                  } @else {
                    <app-priv />
                  }
                </dd>
                <dt>Características de recepción</dt>
                <dd>
                  @if (store.puedeVer('caracteristicasRecepcion', l.empresaId)) {
                    {{ l.caracteristicasRecepcion }}
                  } @else {
                    <app-priv />
                  }
                </dd>
              </dl>
            </app-fold>

            

            <app-fold
              n="04"
              title="Genealogía"
              [summary]="
                store.ancestros(l.id).size +
                ' ancestros / ' +
                store.descendientes(l.id).size +
                ' descendientes'
              "
            >
              @if (origen(); as o) {
                <p class="small" style="margin-bottom:.6rem">
                  {{ o.texto }} <a [routerLink]="o.link" class="id">{{ store.referencia(o.id) }}</a>
                </p>
              }
              @if (relacionados().length) {
                <app-carousel>
                  @for (r of relacionados(); track r.id) {
                    @let rl = store.lote(r.id);
                    <a
                      class="slide"
                      [routerLink]="['/lotes', r.id]"
                      [style.--tone]="
                        rl?.calidad === 'Con falla'
                          ? 'var(--carmine)'
                          : rl && store.esHistorico(rl)
                            ? 'var(--brown)'
                            : 'var(--green-800)'
                      "
                    >
                      <span class="label" [style.color]="r.rel === 'Padre' ? 'var(--brown-ink)' : 'var(--green-800)'">{{ r.rel }}</span>
                      <b class="id">{{ r.id }}</b>
                      <!-- un padre o derivado puede no estar en el almacén (otra empresa o aún sin cargar): se muestra sin sus datos privados -->
                      @if (rl) {
                        <span class="small">{{ rl.material }}</span>
                        <span class="small muted">{{ rl.fase }} / {{ rl.calidad }}</span>
                      } @else {
                        <span class="small muted">Lote de otra empresa</span>
                      }
                    </a>
                  }
                </app-carousel>
              } @else {
                <p class="small muted">Lote sin padres ni derivados todavía.</p>
              }
            </app-fold>

            <app-fold n="05" title="Procesos" [summary]="conteo('procesos') + ' procesos'">
              <ul class="rel">
                @if (store.procesoOrigen(l.id); as p) {
                  <li>
                    <span class="label">Generado por</span>
                    <a class="id" [routerLink]="['/procesos', p.id]">{{ store.referencia(p.id) }}</a> {{ p.tipo }}
                  </li>
                }
                @for (p of store.procesosConsumidores(l.id); track p.id) {
                  <li>
                    <span class="label">Consumido en</span>
                    <a class="id" [routerLink]="['/procesos', p.id]">{{ store.referencia(p.id) }}</a> {{ p.tipo }}
                  </li>
                }
                @if (!conteo('procesos')) {
                  <li class="muted small">Sin procesos relacionados.</li>
                }
              </ul>
            </app-fold>

            <app-fold
              n="06"
              title="Transferencias"
              [summary]="conteo('transferencias') + ' registradas'"
            >
              <ul class="history">
                @for (t of store.transferenciasDe(l.id); track t.id) {
                  <li>
                    <span class="when">{{ t.inicio | fecha }}</span
                    ><br />
                    <b class="id">{{ store.referencia(t.id) }}</b> {{ store.nombreEmpresa(t.origenId) }} /
                    {{ store.nombreEmpresa(t.destinoId) }}
                    <app-badge
                      [text]="t.estado"
                      [tone]="
                        t.estado === 'Aceptada' ? 'ok' : t.estado === 'Rechazada' ? 'fail' : 'busy'
                      "
                    />
                    @if (t.motivoRechazo) {
                      <br /><span class="small muted">Motivo: {{ t.motivoRechazo }}</span>
                    }
                  </li>
                } @empty {
                  <li class="muted small">
                    Sin transferencias. El lote no ha cambiado de empresa.
                  </li>
                }
              </ul>
            </app-fold>

            <app-fold n="07" title="Evidencias" [summary]="l.evidencias.length + ' foto(s)'">
              <app-evidence
                ownerType="lote"
                [ownerId]="l.id"
                [items]="l.evidencias"
                [canAdd]="session.esOperario() && propio"
                (added)="addEv($event)"
              />
            </app-fold>
          </div>

          <aside class="aside-stack">
            <section class="qr-sheet">
              <app-qr-tag [lote]="l" [actions]="true" />
            </section>
            @if (l.productoFinal) {
              <app-fold title="Información comercial" summary="Siempre privado" [open]="true">
                @if (propio) {
                  @if (l.comercial; as c) {
                    <dl class="dl">
                      <dt>Distribuidor</dt>
                      <dd>{{ c.distribuidor || '—' }}</dd>
                      <dt>Precio de venta</dt>
                      <dd class="num">
                        {{ c.moneda }} {{ c.precioVenta?.toLocaleString('es-PE') ?? '—' }}
                      </dd>
                      <dt>Fecha de salida</dt>
                      <dd class="num">{{ c.fechaSalida | fecha: false }}</dd>
                      <dt>Referencia</dt>
                      <dd class="num">{{ c.referencia || '—' }}</dd>
                    </dl>
                  } @else {
                    <p class="small muted">Sin datos comerciales registrados.</p>
                  }
                } @else {
                  <app-priv [always]="true" />
                }
              </app-fold>
            }
          </aside>
        </div>
      </div>

      @if (dividir()) {
        <app-dividir-lote [lote]="l" (closed)="dividir.set(false)" />
      }
      @if (final()) {
        <app-producto-final [lote]="l" (closed)="final.set(false)" />
      }
    } @else {
      <div class="page">
        <div class="empty">
          <strong>Lote no encontrado</strong><a routerLink="/lotes">Volver a Lotes</a>
        </div>
      </div>
    }
  `,
  styles: `
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 0.55rem;
      justify-content: flex-end;
    }
    .split {
      align-items: start;
    }
    .folds {
      gap: 0.75rem;
    }
    .aside-stack {
      display: grid;
      gap: 0.8rem;
    }
    .rel {
      list-style: none;
      margin: 0.4rem 0 0;
      padding: 0;
      display: grid;
      gap: 0.55rem;
    }
    .rel li {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
      align-items: center;
      padding-bottom: 0.55rem;
      border-bottom: 1px solid rgb(15 46 32 / 0.08);
    }
    .rel li:last-child {
      border-bottom: 0;
      padding-bottom: 0;
    }
    .history {
      list-style: none;
      margin: 0.2rem 0 0;
      padding: 0;
      display: grid;
      gap: 0.6rem;
    }
    .history li {
      padding: 0.8rem 0.9rem;
      background: #fbf8ef;
      border: 1px solid rgb(15 46 32 / 0.1);
    }
    .slide {
      background: #fbf8ef;
      border: 1px solid rgb(15 46 32 / 0.12);
    }
    .slide .label {
      color: var(--green-700);
    }
    .qr-sheet {
      border: 1px solid rgb(15 46 32 / 0.14);
      display: flex;
      gap: 1rem;
      align-items: center;
      justify-content: center;
      flex-wrap: wrap;
      background: linear-gradient(rgb(12 39 25 / 0.04), rgb(12 39 25 / 0.04)), #f7f2e6;
      padding: 1.2rem 1rem;
    }
    .qr-sheet app-qr-tag {
      display: block;
      width: 100%;
      max-width: 19rem;
    }
    @media (max-width: 900px) {
      .actions {
        justify-content: flex-start;
      }
    }
  `,
})
export class LoteDetallePage {
  constructor() {
    effect(() => {
      const id = this.id();
      const entity = id ? this.store.lote(id) : null;
      if (id) this.store.requestLineage(id);
      if (id && entity && entity.empresaId === this.session.empresaId())
        this.store.requestEvidence('lote', id);
    });
  }

  readonly store = inject(StoreService);
  readonly session = inject(SessionService);
  readonly router = inject(Router);
  private readonly ui = inject(UiService);

  readonly id = input.required<string>();
  readonly lote = computed(() => this.store.lote(this.id()));
  readonly dividir = signal(false);
  readonly final = signal(false);

  readonly relacionados = computed(() => [
    ...this.store.padres(this.id()).map((id) => ({ id, rel: 'Padre' })),
    ...this.store.hijos(this.id()).map((id) => ({ id, rel: 'Derivado' })),
  ]);
  readonly origen = computed(() => {
    const id = this.id();
    const div = this.store.divisionOrigen(id);
    if (div)
      return { texto: 'Generado por la división', id: div.id, link: ['/lotes', div.loteOrigenId] };
    const p = this.store.procesoOrigen(id);
    if (p) return { texto: 'Generado por la transformación', id: p.id, link: ['/procesos', p.id] };
    return null;
  });

  tf = (f: string) =>
    f === 'Disponible'
      ? 'neutral'
      : f === 'Descartado'
        ? 'fail'
        : ['Procesado', 'Dividido'].includes(f)
          ? 'hist'
          : 'busy';
  tc = (c: string) =>
    c === 'Conforme'
      ? 'ok'
      : c === 'Con falla'
        ? 'fail'
        : c === 'Por reevaluar'
          ? 'warn'
          : c === 'Posible falla derivada'
            ? 'derived'
            : 'neutral';

  conteo(t: Tab) {
    const id = this.id();
    const l = this.lote();
    switch (t) {
      case 'genealogia':
        return this.store.padres(id).length + this.store.hijos(id).length;
      case 'procesos':
        return (this.store.procesoOrigen(id) ? 1 : 0) + this.store.procesosConsumidores(id).length;
      case 'controles':
        return this.store.controlesDe(id).length;
      case 'transferencias':
        return this.store.transferenciasDe(id).length;
      case 'evidencias':
        return l?.evidencias.length ?? 0;
    }
  }

  async addEv(f: File) {
    const result = await this.store.agregarEvidencia('lote', this.id(), f);
    if (result.ok) this.ui.toast('Evidencia registrada');
  }
}
