import { StoreService } from '../../core/store.service';

                                                                                                     

export type NodeKind = 'lote' | 'proceso' | 'division' | 'control';

export interface GNode {
  id: string;
  kind: NodeKind;
  empresaId: string;
  depth: number;
  col: number;
  row: number;
  x: number;
  y: number;
  w: number;
  h: number;
}

export interface GEdge {
  id: string;
  from: string;
  to: string;
  kind: 'flow' | 'control';
  d: string;
  label?: { text: string; x: number; y: number };
}

export interface GCompany {
  empresaId: string;
  x: number;
  w: number;
}

export interface GLayout {
  nodes: GNode[];
  edges: GEdge[];
  companies: GCompany[];
  width: number;
  height: number;
  parents: Map<string, string[]>;
  children: Map<string, string[]>;
}

export interface Bloque {
  id: string;
  raices: string[];
  lotes: string[];
}

const SLOT_W = 228;
const SLOT_H = 106;
const COMPANY_GAP = 36;
const PAD_X = 18;
const HEAD_H = 54;
const PAD_BOTTOM = 22;
const SIZE: Record<NodeKind, { w: number; h: number }> = {
  lote: { w: 176, h: 80 },
  proceso: { w: 160, h: 50 },
  division: { w: 104, h: 36 },
  control: { w: 116, h: 38 },
};

                                                                           
export function bloques(store: StoreService): Bloque[] {
  const seen = new Set<string>();
  const out: Bloque[] = [];
  const lotes = [...store.lotes()].sort((a, b) => a.fechaRegistro.localeCompare(b.fechaRegistro));
  for (const l of lotes) {
    if (seen.has(l.id)) continue;
    const comp: string[] = [];
    const stack = [l.id];
    while (stack.length) {
      const cur = stack.pop()!;
      if (seen.has(cur) || !store.lote(cur)) continue;
      seen.add(cur);
      comp.push(cur);
      stack.push(...store.padres(cur), ...store.hijos(cur));
                                                                      
      store.procesosConsumidores(cur).forEach((p) => stack.push(...p.entradas.map((e) => e.loteId)));
    }
    const raices = comp.filter((id) => store.padres(id).length === 0).sort();
    out.push({ id: 'BG-' + raices.join('+'), raices, lotes: comp });
  }
  return out;
}

export function layout(store: StoreService, loteIds: string[], conControles: boolean): GLayout {
  const kinds = new Map<string, NodeKind>();
  const empresa = new Map<string, string>();
  const parents = new Map<string, string[]>();
  const children = new Map<string, string[]>();
  const edgesRaw: { from: string; to: string; kind: 'flow' | 'control' }[] = [];
  const link = (from: string, to: string, kind: 'flow' | 'control' = 'flow') => {
    edgesRaw.push({ from, to, kind });
    parents.set(to, [...(parents.get(to) ?? []), from]);
    children.set(from, [...(children.get(from) ?? []), to]);
  };

  const set = new Set(loteIds);
  for (const id of loteIds) {
    const l = store.lote(id)!;
    kinds.set(id, 'lote');
    empresa.set(id, l.empresaId);
  }
  for (const d of store.divisiones()) {
    if (!set.has(d.loteOrigenId)) continue;
    kinds.set(d.id, 'division');
    empresa.set(d.id, d.empresaId);
    link(d.loteOrigenId, d.id);
    d.lotesHijosIds.filter(h => set.has(h)).forEach((h) => link(d.id, h));
  }
  for (const p of store.procesos()) {
    if (!p.entradas.some((e) => set.has(e.loteId))) continue;
    kinds.set(p.id, 'proceso');
    empresa.set(p.id, p.empresaId);
    p.entradas.filter(e => set.has(e.loteId)).forEach((e) => link(e.loteId, p.id));
    p.salidasIds.filter(s => set.has(s)).forEach((s) => link(p.id, s));
  }
  if (conControles) {
    for (const c of store.controles()) {
      if (!set.has(c.loteId)) continue;
      kinds.set(c.id, 'control');
                                                                                         
      empresa.set(c.id, store.lote(c.loteId)!.empresaId);
      link(c.loteId, c.id, 'control');
    }
  }

                           
  const depth = new Map<string, number>();
  const ids = [...kinds.keys()];
  const visit = (id: string): number => {
    if (depth.has(id)) return depth.get(id)!;
    depth.set(id, 0);
    const ps = parents.get(id) ?? [];
    const d = ps.length ? Math.max(...ps.map(visit)) + 1 : 0;
    depth.set(id, d);
    return d;
  };
  ids.forEach(visit);

                                                          
  const minDepth = new Map<string, number>();
  ids.forEach((id) => {
    const e = empresa.get(id)!;
    minDepth.set(e, Math.min(minDepth.get(e) ?? Infinity, depth.get(id)!));
  });
  const companies = [...minDepth.keys()].sort((a, b) => minDepth.get(a)! - minDepth.get(b)! || a.localeCompare(b));

                                                                   
  const colOf = new Map<string, number>();
  const companyCols: { empresaId: string; first: number; last: number; idx: number }[] = [];
  let col = 0;
  companies.forEach((e, idx) => {
    const depths = [...new Set(ids.filter((id) => empresa.get(id) === e).map((id) => depth.get(id)!))].sort((a, b) => a - b);
    const first = col;
    depths.forEach((d) => {
      ids.filter((id) => empresa.get(id) === e && depth.get(id) === d).forEach((id) => colOf.set(id, col));
      col++;
    });
    companyCols.push({ empresaId: e, first, last: col - 1, idx });
  });
  const companyIdx = new Map(companyCols.map((c) => [c.empresaId, c.idx]));

                                                                               
  const rowOf = new Map<string, number>();
  const order = new Map(ids.map((id, i) => [id, i]));
  for (let c = 0; c < col; c++) {
    const inCol = ids.filter((id) => colOf.get(id) === c);
    const desired = (id: string) => {
      const ps = (parents.get(id) ?? []).filter((p) => rowOf.has(p));
      return ps.length ? ps.reduce((s, p) => s + rowOf.get(p)!, 0) / ps.length : order.get(id)! * 0.001;
    };
    inCol.sort((a, b) => desired(a) - desired(b) || (kinds.get(a) === 'control' ? 1 : 0) - (kinds.get(b) === 'control' ? 1 : 0) || a.localeCompare(b));
    let next = 0;
    for (const id of inCol) {
      const r = Math.max(next, Math.round(desired(id)));
      rowOf.set(id, r);
      next = r + 1;
    }
  }

                                                                                           
  for (let c = col - 1; c >= 0; c--) {
    const inCol = ids.filter((id) => colOf.get(id) === c).sort((a, b) => rowOf.get(a)! - rowOf.get(b)!);
    inCol.forEach((id, i) => {
      const ch = (children.get(id) ?? []).filter((k) => kinds.get(k) !== 'control');
      if (!ch.length) return;
      const target = Math.round(Math.min(...ch.map((k) => rowOf.get(k)!)));
      const prev = i > 0 ? rowOf.get(inCol[i - 1])! : -1;
      const nextRow = i < inCol.length - 1 ? rowOf.get(inCol[i + 1])! : Infinity;
      if (target > rowOf.get(id)! && target > prev && target < nextRow) rowOf.set(id, target);
    });
  }

  const rows = Math.max(0, ...[...rowOf.values()]) + 1;
  const nodes: GNode[] = ids.map((id) => {
    const k = kinds.get(id)!;
    const s = SIZE[k];
    const c = colOf.get(id)!;
    const ci = companyIdx.get(empresa.get(id)!)!;
    const slotX = PAD_X + c * SLOT_W + ci * COMPANY_GAP + 16;
    const slotY = HEAD_H + 14 + rowOf.get(id)! * SLOT_H;
    return {
      id,
      kind: k,
      empresaId: empresa.get(id)!,
      depth: depth.get(id)!,
      col: c,
      row: rowOf.get(id)!,
      x: slotX + (SIZE.lote.w - s.w) / 2,
      y: slotY + (SIZE.lote.h - s.h) / 2,
      w: s.w,
      h: s.h,
    };
  });
  const byId = new Map(nodes.map((n) => [n.id, n]));

  const edges: GEdge[] = edgesRaw.map((e) => {
    const a = byId.get(e.from)!;
    const b = byId.get(e.to)!;
    const x1 = a.x + a.w;
    const y1 = a.y + a.h / 2;
    const x2 = b.x;
    const y2 = b.y + b.h / 2;
    const span = b.col - a.col;
    const mx = span <= 1 ? Math.round((x1 + x2) / 2) : x2 - 26;
    const d = y1 === y2 ? `M${x1} ${y1}H${x2}` : `M${x1} ${y1}H${mx}V${y2}H${x2}`;
    let label: GEdge['label'];
    if (a.empresaId !== b.empresaId && kinds.get(b.id) === 'lote') {
      const t = store.transferenciasDe(b.id).find((t) => t.estado === 'Aceptada' && t.destinoId === b.empresaId);
      if (t) label = { text: t.id, x: mx, y: Math.min(y1, y2) + Math.abs(y2 - y1) / 2 };
    }
    return { id: `${e.from}>${e.to}`, from: e.from, to: e.to, kind: e.kind, d, label };
  });

  const companyBoxes: GCompany[] = companyCols.map((c) => {
    const x = PAD_X + c.first * SLOT_W + c.idx * COMPANY_GAP;
    const w = (c.last - c.first + 1) * SLOT_W - SLOT_W + SIZE.lote.w + 32;
    return { empresaId: c.empresaId, x, w };
  });
  const last = companyBoxes[companyBoxes.length - 1];
  return {
    nodes,
    edges,
    companies: companyBoxes,
    width: last ? last.x + last.w + PAD_X : 400,
    height: HEAD_H + 14 + rows * SLOT_H + PAD_BOTTOM,
    parents,
    children,
  };
}

export function relacionados(L: GLayout, id: string, dir: 'ambos' | 'atras' | 'adelante'): Set<string> {
  const out = new Set<string>([id]);
  const walk = (map: Map<string, string[]>) => {
    const stack = [id];
    while (stack.length) {
      const cur = stack.pop()!;
      for (const n of map.get(cur) ?? []) {
        if (!out.has(n)) {
          out.add(n);
          stack.push(n);
        }
      }
    }
  };
  if (dir !== 'adelante') walk(L.parents);
  if (dir !== 'atras') walk(L.children);
  return out;
}
