import { defineConfig, devices } from '@playwright/test';

const backendUrl = process.env.E2E_BACKEND_URL || 'http://localhost:8080';
const frontendUrl = process.env.E2E_FRONTEND_URL || 'http://localhost:3000';

/**
 * RESIDENT-PORTAL "TENANT JOURNEY" E2E SUITE
 *
 * Runs the real React app (Vite, :3000) against the real Spring Boot API (:8080).
 * Both servers are started automatically; locally they are reused if already running.
 *
 *   npm run test:e2e          # headless
 *   npm run test:e2e:ui       # Playwright UI mode (great for demos / debugging)
 */
export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.e2e.ts', // keeps Vitest (*.test.ts) and Playwright separate
  timeout: 30_000,
  expect: { timeout: 7_000 },
  fullyParallel: false, // tests share one in-memory H2 database, so run them in order
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: frontendUrl,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'desktop-chromium', use: { ...devices['Desktop Chrome'] } },
    // Residents mostly use phones, so the maintenance flow is also checked on a mobile viewport.
    { name: 'mobile-chromium', use: { ...devices['Pixel 7'] }, grep: /@mobile/ },
  ],
  webServer: [
    {
      command: process.platform === 'win32' ? 'mvnw.cmd spring-boot:run' : './mvnw spring-boot:run',
      cwd: '../backend',
      url: `${backendUrl}/api/rooms`,
      reuseExistingServer: !process.env.CI,
      timeout: 180_000,
      env: {
        SERVER_PORT: new URL(backendUrl).port,
        ...(process.env.JAVA_HOME ? {} : { JAVA_HOME: 'C:\\Users\\wachi\\.jdks\\ms-17.0.20.1' }),
      },
    },
    {
      command: `npm run dev -- --port ${new URL(frontendUrl).port} --strictPort`,
      url: frontendUrl,
      env: { VITE_API_TARGET: backendUrl },
      reuseExistingServer: !process.env.CI,
      timeout: 60_000,
    },
  ],
});
