import { test, expect } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { composeProject, isolatedProject } from './paths';

test.use({ trace: 'off' });
test.beforeEach(() => test.skip(!isolatedProject(), 'Agota el límite de login: solo en la instalación aislada.'));

function containerOf(service: string): string {
  const id = execFileSync('docker', ['ps', '-q', '--filter', 'label=com.docker.compose.project=' + composeProject(),
    '--filter', 'label=com.docker.compose.service=' + service], { encoding: 'utf8' }).trim();
  if (!id || id.includes('\n')) throw new Error('No hay exactamente un contenedor «' + service + '» en ' + composeProject());
  return id;
}
function loginFromContainer(id: string, email: string, forwardedFor?: string): number {
  const header = forwardedFor ? "--header='X-Forwarded-For: " + forwardedFor + "' " : '';
  const out = execFileSync('docker', ['exec', id, 'sh', '-c',
    "wget -S -O /dev/null --header='Content-Type: application/json' " + header +
    "--post-data='{\"email\":\"" + email + "\",\"password\":\"Incorrecta-9!\"}' http://web/api/auth/login 2>&1 | grep -m1 'HTTP/'"], { encoding: 'utf8' });
  const match = /HTTP\/\d(?:\.\d)?\s+(\d{3})/.exec(out);
  if (!match) throw new Error('Respuesta inesperada de wget: ' + out.trim().slice(0, 80));
  return Number(match[1]);
}

test('M8: dos clientes con IP distinta no comparten el cubo de login, y un X-Forwarded-For inventado no cambia el cubo', async ({ request }) => {
  test.setTimeout(180000);
  const fake = () => ({ email: 'sin-cuenta-' + crypto.randomUUID() + '@example.test', password: 'Incorrecta-9!' });
  let limitedAt = 0;
  for (let attempt = 1; attempt <= 80; attempt++) {
    const response = await request.post('/api/auth/login', { data: fake() });
    if (response.status() === 429) { limitedAt = attempt; expect((await response.json()).code).toBe('TOO_MANY_REQUESTS'); break; }
    expect(response.status()).toBe(401);
  }
  expect(limitedAt, 'el límite de 60/min debe aparecer en el intento 61 (margen por el minuto en curso)').toBeGreaterThan(0);
  expect(limitedAt).toBeLessThanOrEqual(65);
  const landing = containerOf('landing');
  expect(loginFromContainer(landing, 'sin-cuenta-' + crypto.randomUUID() + '@example.test')).toBe(401);
  const burst = async (headers?: Record<string, string>) => { const codes: number[] = []; for (let i = 0; i < 6; i++) codes.push((await request.post('/api/auth/login', { data: fake(), headers })).status()); return codes; };
  const spoofed = await burst({ 'X-Forwarded-For': '203.0.113.' + (1 + Math.floor(Math.random() * 200)) });
  expect(spoofed, 'el X-Forwarded-For del cliente se ignora').toContain(429);
  expect(loginFromContainer(landing, 'sin-cuenta-' + crypto.randomUUID() + '@example.test', '198.51.100.7')).toBe(401);
  expect(await burst(), 'el anfitrión sigue limitado').toContain(429);
});

test('M8: el gateway no publica ningún puerto en el anfitrión; la única entrada es el Nginx de web', async () => {
  const published = (service: string) => execFileSync('docker', ['port', containerOf(service)], { encoding: 'utf8' }).trim();
  expect(published('gateway'), 'el gateway no debe publicar puertos').toBe('');
  expect(published('web')).toMatch(/80\/tcp -> 127\.0\.0\.1:\d+/);
});
