import { spawn } from 'node:child_process';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const flag = '--no-experimental-webstorage';
const previous = process.env.NODE_OPTIONS ?? '';
const options = previous.split(/\s+/).includes(flag) ? previous : `${previous} ${flag}`.trim();

const child = spawn(
  process.execPath,
  [join(root, 'node_modules', '@angular', 'cli', 'bin', 'ng.js'), 'test', ...process.argv.slice(2)],
  { cwd: root, stdio: 'inherit', env: { ...process.env, NODE_OPTIONS: options } },
);
child.on('error', (error) => {
  console.error('No se pudo lanzar ng test:', error.message);
  process.exit(1);
});
child.on('exit', (code, signal) => {
  if (signal) process.kill(process.pid, signal);
  else process.exit(code ?? 1);
});
