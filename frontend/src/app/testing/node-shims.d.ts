// Solo lo que usan los specs que leen el código fuente (sin añadir @types/node al proyecto). Solo entra en tsconfig.spec.json.
declare module 'node:fs' {
  export function readdirSync(path: string): string[];
  export function existsSync(path: string): boolean;
  export function readFileSync(path: string, encoding: 'utf8'): string;
  export function statSync(path: string): { isDirectory(): boolean };
}
declare module 'node:path' {
  export function join(...parts: string[]): string;
}
declare const process: { cwd(): string };
declare module 'node:crypto' {
  export function createHash(algorithm: 'sha256'): { update(data: string): { digest(encoding: 'base64'): string } };
}
