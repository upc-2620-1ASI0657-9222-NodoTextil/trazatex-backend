export type Stage = 0 | 1 | 2 | 3;
export interface Lot {
    code: string;
    name: string;
    stage: Stage;
    where: string;
    qty: string;
    parents: string[];
    available: boolean;
    slot: number;
}
export const STAGE_NAMES = ['Fibra', 'Hilo', 'Tela', 'Prenda'] as const;
export const LOTS: Lot[] = [
    { code: 'AL-0417', name: 'Algodón Pima', stage: 0, where: 'Ica', qty: '1 200 kg', parents: [], available: false, slot: 0 },
    { code: 'FA-0108', name: 'Fibra de alpaca', stage: 0, where: 'Puno', qty: '300 kg', parents: [], available: false, slot: 1 },
    { code: 'AL-0422', name: 'Algodón Tangüis', stage: 0, where: 'Cañete', qty: '900 kg', parents: [], available: true, slot: 2 },
    { code: 'HI-1182', name: 'Hilo peinado', stage: 1, where: 'Hilandería', qty: '340 kg', parents: ['AL-0417'], available: true, slot: 0 },
    { code: 'HI-1180', name: 'Hilo mezcla', stage: 1, where: 'Hilandería', qty: '820 kg', parents: ['AL-0417', 'FA-0108'], available: false, slot: 1 },
    { code: 'HI-1181', name: 'Hilo de algodón', stage: 1, where: 'Hilandería', qty: '760 kg', parents: ['AL-0422'], available: false, slot: 2 },
    { code: 'TE-2034', name: 'Tela jersey', stage: 2, where: 'Tejeduría', qty: '410 kg', parents: ['HI-1180'], available: false, slot: 0 },
    { code: 'TE-2035', name: 'Tela jersey', stage: 2, where: 'Tejeduría', qty: '380 kg', parents: ['HI-1180'], available: true, slot: 1 },
    { code: 'TE-2036', name: 'Tela piqué', stage: 2, where: 'Tejeduría', qty: '690 kg', parents: ['HI-1182', 'HI-1181'], available: false, slot: 2 },
    { code: 'PR-2291', name: 'Polos jersey', stage: 3, where: 'Confección', qty: '1 300 u', parents: ['TE-2034'], available: false, slot: 0 },
    { code: 'PR-2293', name: 'Polos manga larga', stage: 3, where: 'Confección', qty: '1 150 u', parents: ['TE-2035'], available: true, slot: 1 },
    { code: 'PR-2292', name: 'Polos piqué', stage: 3, where: 'Confección', qty: '2 100 u', parents: ['TE-2036'], available: true, slot: 2 },
];
export const byCode = new Map(LOTS.map((l) => [l.code, l]));
const childrenOf = new Map<string, string[]>();
for (const lot of LOTS) {
    for (const p of lot.parents) {
        const list = childrenOf.get(p) ?? [];
        list.push(lot.code);
        childrenOf.set(p, list);
    }
}
export const children = (code: string): string[] => childrenOf.get(code) ?? [];
export interface Edge {
    from: string;
    to: string;
}
export const EDGES: Edge[] = LOTS.flatMap((l) => l.parents.map((p) => ({ from: p, to: l.code })));
export function ancestors(code: string): Map<string, number> {
    const out = new Map<string, number>();
    const walk = (c: string, d: number) => {
        for (const p of byCode.get(c)?.parents ?? []) {
            if (!out.has(p) || (out.get(p) ?? 0) > d)
                out.set(p, d);
            walk(p, d + 1);
        }
    };
    walk(code, 1);
    return out;
}
export function descendants(code: string): Map<string, number> {
    const out = new Map<string, number>();
    const walk = (c: string, d: number) => {
        for (const k of children(c)) {
            if (!out.has(k) || (out.get(k) ?? 0) > d)
                out.set(k, d);
            walk(k, d + 1);
        }
    };
    walk(code, 1);
    return out;
}
export function family(code: string): Map<string, number> {
    const out = new Map<string, number>();
    const add = (c: string, d: number) => {
        if (c === code)
            return;
        if (!out.has(c) || (out.get(c) ?? 0) > d)
            out.set(c, d);
    };
    for (const [c, d] of descendants(code))
        add(c, d);
    for (const p of byCode.get(code)?.parents ?? []) {
        for (const sib of children(p)) {
            if (sib === code)
                continue;
            add(sib, 2);
            for (const [c, d] of descendants(sib))
                add(c, 2 + d);
        }
    }
    return out;
}
