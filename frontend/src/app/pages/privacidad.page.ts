import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CAMPOS_COMPARTIDOS, CAMPOS_CONFIGURABLES, CAMPOS_PRIVADOS, CampoConfigurable, ETIQUETA_CAMPO, Visibilidad } from '../core/models';
import { SessionService } from '../core/session.service';
import { StoreService } from '../core/store.service';
import { DENSITY, SubnavItem } from '../shared/density';
import { SHARED, UiService } from '../shared/ui';

@Component({
  selector: 'app-privacidad',
  imports: [...SHARED, ...DENSITY, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page page--privacidad">
      

      <app-subnav panel="privacidad" [items]="vistas()" [(active)]="vista" label="Categorías de privacidad" />
      <div appTabPanel="privacidad" [tabActive]="vista()">

      @if (session.esDueno() && vista() === 'configurables') {
        <div class="filters">
          <select class="select" [ngModel]="empresaVista()" (ngModelChange)="empresaSel.set($event)" aria-label="Empresa">
            @for (e of store.empresas(); track e.id) {
              <option [value]="e.id">{{ e.razonSocial }}</option>
            }
          </select>
          <span class="small muted">{{ compartidosDe(empresaVista()) }} de {{ campos.length }} campos compartidos</span>
        </div>
      }

      <section class="panel-list">
        @switch (vista()) {
          @case ('configurables') {
            <ul class="toggles">
              @for (c of campos; track c) {
                @let v = store.visibilidad(empresaVista(), c);
                <li>
                  <span>{{ etiqueta[c] }}</span>
                  @if (session.esAdmin()) {
                    <div class="seg" role="group" [attr.aria-label]="etiqueta[c]">
                      <button type="button" [attr.aria-pressed]="v === 'Privado'" (click)="set(c, 'Privado')"><app-icon name="lock" [size]="13" /> Privado</button>
                      <button type="button" [attr.aria-pressed]="v === 'Compartido'" (click)="set(c, 'Compartido')"><app-icon name="eye" [size]="13" /> Compartido</button>
                    </div>
                  } @else {
                    <app-badge [text]="v" [tone]="v === 'Compartido' ? 'ok' : 'hist'" [icon]="v === 'Compartido' ? 'eye' : 'lock'" />
                  }
                </li>
              }
            </ul>
          }
          @case ('compartidos') {
            <p class="intro">Siempre visibles para todas las empresas de la cadena.</p>
            <ul class="toggles">
              @for (c of compartidos; track c) {
                <li><span>{{ c }}</span><app-badge text="Compartido" tone="ok" icon="eye" /></li>
              }
            </ul>
          }
          @case ('privados') {
            <p class="intro">Nunca se comparten entre empresas, sin importar la configuración.</p>
            <ul class="toggles">
              @for (c of privados; track c) {
                <li><span>{{ c }}</span><app-priv [always]="true" /></li>
              }
            </ul>
          }
        }
      </section>
      @if (session.esDueno()) {
        <p class="small muted" style="margin-top:.8rem">Vista de supervisión. Cada empresa administra su propia configuración.</p>
      }
      </div>
    </div>
  `,
  styles: `
    .panel-list { max-width: 57rem; background: var(--cream-50); border: 1px solid rgb(15 46 32 / .14); padding: .5rem 1.15rem .9rem; }
    .intro { padding: .6rem 0 .2rem; color: var(--muted); font-size: .88rem; }
    .toggles { list-style: none; margin: 0; padding: 0; }
    .toggles li { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: .5rem 1rem; padding: .65rem 0; border-bottom: 1px solid var(--hair); font-weight: 650; font-size: .92rem; }
    .toggles li:last-child { border-bottom: 0; }
    .toggles .seg button { min-height: 2rem; font-size: .78rem; display: inline-flex; align-items: center; gap: .3rem; }
  `,
})
export class PrivacidadPage {
  readonly store = inject(StoreService);
  readonly session = inject(SessionService);
  private readonly ui = inject(UiService);
  readonly campos = CAMPOS_CONFIGURABLES;
  readonly etiqueta = ETIQUETA_CAMPO;
  readonly compartidos = CAMPOS_COMPARTIDOS;
  readonly privados = CAMPOS_PRIVADOS;
  readonly vista = signal('configurables');
  readonly empresaSel = signal('');
  readonly empresaVista = computed(() => (this.session.esDueno() ? this.empresaSel() || this.store.empresas()[0].id : this.session.empresaId()!));
  compartidosDe = (empresaId: string) => this.campos.filter((c) => this.store.visibilidad(empresaId, c) === 'Compartido').length;
  readonly vistas = computed<SubnavItem[]>(() => [
    { id: 'configurables', label: 'Configurables', count: this.campos.length },
    { id: 'compartidos', label: 'Siempre compartidos', count: this.compartidos.length },
    { id: 'privados', label: 'Siempre privados', count: this.privados.length },
  ]);
  async set(c: CampoConfigurable, v: Visibilidad) {
    const result = await this.store.setVisibilidad(this.session.empresaId()!, c, v);
    if (!result.ok) return;
    this.ui.toast(`${ETIQUETA_CAMPO[c]}: ${v}`);
  }
}

