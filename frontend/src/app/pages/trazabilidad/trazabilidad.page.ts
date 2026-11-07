import { errorMessage } from '../../core/api.service';
import { ChangeDetectionStrategy, Component, ElementRef, computed, effect, inject, input, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ESTADOS_CALIDAD, FASES, Lote } from '../../core/models';
import { SessionService } from '../../core/session.service';
import { StoreService } from '../../core/store.service';
import { SHARED, UiService } from '../../shared/ui';
import { GNode, bloques, layout, relacionados } from './genealogy';

type Etapa = 'Fibra' | 'Hilo' | 'Tela' | 'Prenda';
const etapa = (l: Lote): Etapa =>
  l.material.startsWith('Fibra') ? 'Fibra' : l.material.startsWith('Hilo') ? 'Hilo' : (/^(Tela|Tejido)\b/i.test(l.material)) ? 'Tela' : 'Prenda';

@Component({
  selector: 'app-trazabilidad',
  imports: [...SHARED, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './trazabilidad.page.html',
  styleUrl: './trazabilidad.page.css',
})
export class TrazabilidadPage {
  readonly store = inject(StoreService);
  readonly session = inject(SessionService);
  private readonly ui = inject(UiService);
  private readonly router = inject(Router);
  private readonly canvas = viewChild<ElementRef<HTMLElement>>('canvas');

  readonly lote = input<string>();
  readonly etapas: Etapa[] = ['Fibra', 'Hilo', 'Tela', 'Prenda'];
  readonly etapasVisibles = signal<Etapa[]>([...this.etapas]);
  readonly calidades = ESTADOS_CALIDAD;
  readonly fases = FASES;
  readonly calidadFiltro = signal('');
  readonly faseFiltro = signal('');
  readonly empresaFiltro = signal('');
  readonly empresasBloque = computed(() => {
    const ids = this.bloque()?.lotes ?? [];
    return [...new Set(ids.map((id) => this.store.lote(id)?.empresaId).filter((id): id is string => !!id))];
  });
  readonly query = signal('');
  readonly bloqueId = signal<string | null>(null);
  readonly selected = signal<string | null>(null);
  readonly dir = signal<'ambos' | 'atras' | 'adelante'>('ambos');
  readonly zoom = signal(0.86);

  readonly visibles = computed(() => {
    const mine = this.session.empresaId();
    if (this.session.esDueno()) return [];
    return bloques(this.store).filter((b) => b.lotes.some((id) => {
      const l = this.store.lote(id)!;
      return this.store.searchedLineageIds().includes(id) || l.empresaId === mine ||
        this.store.procesoOrigen(id)?.empresaId === mine ||
        this.store.transferenciasDe(id).some((t) => t.origenId === mine || t.destinoId === mine);
    }));
  });
  readonly bloque = computed(() => this.visibles().find((b) => b.id === this.bloqueId()) ?? this.visibles()[0]);
  readonly L = computed(() => this.bloque() ? layout(this.store, this.bloque()!.lotes, true) : null);
  readonly idxBloque = computed(() => Math.max(0, this.visibles().findIndex((b) => b.id === this.bloque()?.id)));
  readonly sugerencias = computed(() => [...new Set(this.visibles().flatMap((b) => b.lotes))].sort());
  readonly selNode = computed(() => this.L()?.nodes.find((n) => n.id === this.selected()) ?? null);
  readonly selLote = computed(() => this.selNode()?.kind === 'lote' ? this.store.lote(this.selected()) : undefined);
  readonly selProceso = computed(() => this.selNode()?.kind === 'proceso' ? this.store.proceso(this.selected()) : undefined);
  readonly selControl = computed(() => this.selNode()?.kind === 'control' ? this.store.control(this.selected()) : undefined);
  readonly selDivision = computed(() => this.selNode()?.kind === 'division'
    ? this.store.divisiones().find((d) => d.id === this.selected()) : undefined);
  readonly upstream = computed(() => {
    const graph = this.L(), selected = this.selected();
    return graph && selected ? relacionados(graph, selected, 'atras') : null;
  });
  readonly hl = computed(() => {
    const L = this.L();
    const s = this.selected();
    return L && s && L.nodes.some((n) => n.id === s) ? relacionados(L, s, this.dir()) : null;
  });

  constructor() {
    effect(() => {
      const blocks = this.visibles();
      if (!this.store.loading() && !this.bloqueId() && blocks.length) {
        const id = [...blocks].sort((a, b) => b.lotes.length - a.lotes.length)[0].id;
        this.bloqueId.set(id);
        queueMicrotask(() => void this.setBloque(id));
      }
    });
    effect(() => {
      const id = this.lote();
      if (id) queueMicrotask(async () => {
        try { await this.store.searchLineages(id); this.ir(id, false); } catch (error) { this.ui.toast(errorMessage(error), true); }
      });
    });
  }

  limpiarFiltros() {
    this.etapasVisibles.set([...this.etapas]);
    this.calidadFiltro.set('');
    this.faseFiltro.set('');
    this.empresaFiltro.set('');
    this.zoom.set(0.86);
    this.dir.set('ambos');
  }
  filtrarEtapa(e: Etapa) {
    this.etapasVisibles.update((current) => current.includes(e) ? current.filter((x) => x !== e) : [...current, e]);
  }
  moverBloque(d: number) {
    const b = this.visibles()[this.idxBloque() + d];
    if (b) this.setBloque(b.id);
  }
  async setBloque(id: string) {
    const block = this.visibles().find(b => b.id === id);
    const root = block?.lotes.filter(code => this.store.lote(code)?.empresaId === this.session.empresaId()).sort((a, b) => this.store.ancestros(a).size - this.store.ancestros(b).size || this.store.hijos(b).length - this.store.hijos(a).length)[0];
    if (root) { try { await this.store.loadLineage(root); id = this.visibles().find(b => b.lotes.includes(root))?.id ?? id; } catch (error) { this.ui.toast(errorMessage(error), true); } }
    this.bloqueId.set(id);
    this.selected.set(null);
    this.dir.set('ambos');
    this.empresaFiltro.set('');
    this.canvas()?.nativeElement.scrollTo({ left: 0, top: 0 });
  }
  async buscar() {
    const query = this.query().trim();
    if (!query) return;
    try {
      const ids = await this.store.searchLineages(query);
      if (ids.length) this.ir(ids[0]);
      else this.ui.toast('No se encontraron lotes con ese nombre o identificador.', true);
    } catch (error) { this.ui.toast(errorMessage(error), true); }
  }  ir(id: string, aviso = true) {
    const b = this.visibles().find((x) => x.lotes.includes(id));
    if (!b) {
      if (aviso) this.ui.toast('El lote no pertenece a una genealogía visible.', true);
      return;
    }
    this.bloqueId.set(b.id);
    this.selected.set(id);
    this.dir.set('ambos');
    this.query.set(id);
    const l = this.store.lote(id);
    if (l && !this.etapasVisibles().includes(etapa(l))) {
      this.etapasVisibles.update((old) => [...old, etapa(l)]);
    }
    setTimeout(() => {
      const c = this.canvas()?.nativeElement;
      const el = c?.querySelector<HTMLElement>(`[data-node="${id}"]`);
      if (!c || !el) return;
      const r = el.getBoundingClientRect();
      const outer = c.getBoundingClientRect();
      c.scrollTo({
        left: c.scrollLeft + r.left - outer.left - c.clientWidth / 2 + r.width / 2,
        top: c.scrollTop + r.top - outer.top - c.clientHeight / 2 + r.height / 2,
        behavior: 'smooth',
      });
    }, 40);
  }
  select(id: string) {
    this.selected.set(this.selected() === id ? null : id);
    this.dir.set('ambos');
  }
  trace(d: 'ambos' | 'atras' | 'adelante') {
    this.dir.set(d);
  }
  dim(n: GNode) {
    const hl = this.hl();
    const l = n.kind === 'lote' ? this.loteDe(n) : undefined;
    const filtrado = !!l && (
      !this.etapasVisibles().includes(etapa(l)) ||
      (!!this.calidadFiltro() && l.calidad !== this.calidadFiltro()) ||
      (!!this.faseFiltro() && l.fase !== this.faseFiltro()) ||
      (!!this.empresaFiltro() && l.empresaId !== this.empresaFiltro())
    );
    return filtrado || !!(hl && !hl.has(n.id));
  }
  edgeState(from: string, to: string) {
    const hl = this.hl();
    if (!hl) return '';
    if (!hl.has(from) || !hl.has(to)) return 'dim';
    return this.upstream()?.has(from) && this.upstream()?.has(to) ? 'hl ancestor' : 'hl descendant';
  }
  tonoQ(c: string) {
    return c === 'Conforme' ? 'ok' : c === 'Con falla' ? 'fail' : c === 'Posible falla derivada' ? 'derived' : c === 'Por reevaluar' ? 'warn' : 'none';
  }
  loteDe(n: GNode) { return this.store.lote(n.id)!; }
  procesoDe(n: GNode) { return this.store.proceso(n.id)!; }
  controlDe(n: GNode) { return this.store.control(n.id)!; }
  etapaDe(l: Lote) { return etapa(l); }
  resultado(id: string) {
    const c = this.store.control(id)!;
    return c.fin ? this.store.resultadoControl(c) : 'En curso';
  }
  verDetalle() {
    const n = this.selNode();
    if (!n || !this.store.puedeVerPrivado(n.empresaId)) return;
    if (n.kind === 'lote') this.router.navigate(['/lotes', n.id]);
    if (n.kind === 'proceso') this.router.navigate(['/procesos', n.id]);
    if (n.kind === 'control') this.router.navigate(['/calidad', n.id]);
  }
}



