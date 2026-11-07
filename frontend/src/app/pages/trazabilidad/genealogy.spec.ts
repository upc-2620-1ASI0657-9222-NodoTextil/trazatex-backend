import { StoreService } from '../../core/store.service';
import { bloques, layout } from './genealogy';
function example(complete: boolean): StoreService {
  const batches = (complete ? ['A', 'B', 'C'] : ['A']).map(id => ({ id, empresaId: id === 'C' ? 'Tejeduría' : 'Hilandería', fechaRegistro: '2026-10-24', cantidadKg: 50 }));
  const process = { id: 'P', empresaId: 'Hilandería', entradas: [{ loteId: 'A' }, { loteId: 'B' }], salidasIds: ['C'] };
  return {
    lotes: () => batches,
    lote: (id: string) => batches.find(batch => batch.id === id),
    procesos: () => [process], divisiones: () => [], controles: () => [], transferenciasDe: () => [],
    padres: (id: string) => id === 'C' ? ['A', 'B'] : [],
    hijos: (id: string) => ['A', 'B'].includes(id) ? ['C'] : [],
    procesosConsumidores: (id: string) => ['A', 'B'].includes(id) ? [process] : [],
  } as unknown as StoreService;
}
describe('Representación de genealogías reales', () => {
  it('mantiene una mezcla entre empresas con todos sus enlaces reales', () => {
    const store = example(true);
    const blocks = bloques(store);
    expect(blocks).toHaveLength(1);
    expect(new Set(blocks[0].lotes)).toEqual(new Set(['A', 'B', 'C']));
    const graph = layout(store, blocks[0].lotes, false);
    expect(new Set(graph.nodes.map(node => node.id))).toEqual(new Set(['A', 'B', 'C', 'P']));
    expect(graph.edges.map(edge => [edge.from, edge.to])).toEqual([['A', 'P'], ['B', 'P'], ['P', 'C']]);
    expect(graph.companies).toHaveLength(2);
  });
  it('no inventa lotes ni rompe el dibujo mientras faltan referencias por cargar', () => {
    const store = example(false);
    const blocks = bloques(store);
    expect(blocks[0].lotes).toEqual(['A']);
    const graph = layout(store, blocks[0].lotes, false);
    expect(graph.nodes.map(node => node.id)).toEqual(['A', 'P']);
    expect(graph.edges.map(edge => [edge.from, edge.to])).toEqual([['A', 'P']]);
    expect(graph.nodes.every(node => Number.isFinite(node.x) && Number.isFinite(node.y))).toBe(true);
  });
});
