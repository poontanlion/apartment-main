import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e', testMatch: 'smartsearch.e2e.ts', timeout: 30_000, workers: 1, reporter: 'list',
  outputDir: './test-results/smartsearch',
  use: { baseURL: 'http://127.0.0.1:4176', trace: 'retain-on-failure' },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npm run dev -- --host 127.0.0.1 --port 4176 --strictPort',
    url: 'http://127.0.0.1:4176', reuseExistingServer: false,
  },
});
