import { defineConfig, devices } from '@playwright/test';

const backendUrl = process.env.E2E_BACKEND_URL || 'http://127.0.0.1:9087';
const frontendUrl = process.env.E2E_FRONTEND_URL || 'http://127.0.0.1:4176';

// Separate from mock tests and from the legacy suite's persistent database server.
export default defineConfig({
  testDir: './e2e', testMatch: 'maintenance-predictions.live.ts', workers: 1,
  timeout: 120_000, expect: { timeout: 10_000 }, reporter: 'list',
  outputDir: 'test-results/live',
  use: { baseURL: frontendUrl, trace: 'retain-on-failure' },
  projects: [{ name: 'live-chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [{
    command: `${process.platform === 'win32' ? 'mvnw.cmd' : './mvnw'} spring-boot:test-run "-Dspring-boot.run.main-class=com.victory.apartment.PredictionLiveApplication" "-Dspring-boot.run.arguments=--server.port=${new URL(backendUrl).port}"`,
    cwd: '../backend', url: `${backendUrl}/actuator/health/readiness`, timeout: 180_000, reuseExistingServer: false,
  }, {
    command: `npm run dev -- --host 127.0.0.1 --port ${new URL(frontendUrl).port} --strictPort`,
    url: frontendUrl, env: { VITE_API_TARGET: backendUrl }, reuseExistingServer: false,
  }],
});
