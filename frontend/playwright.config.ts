import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir: './e2e',
  projects: [
    { name: 'contracts', testMatch: 'production.spec.ts' },
    { name: 'browser', testIgnore: 'production.spec.ts', dependencies: ['contracts'] },
  ],
  fullyParallel: false,
  workers: 1,
  timeout: 60000,
  expect: { timeout: 15000 },
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    actionTimeout: 15000,
    baseURL: process.env['TRAZATEX_APP_URL'] ?? 'http://127.0.0.1:14200',
    // Ronda 5: solo el Chrome instalado en el equipo (no se descargan navegadores de Playwright).
    channel: 'chrome',
    headless: true,
    trace: 'retain-on-failure',
  },
});
