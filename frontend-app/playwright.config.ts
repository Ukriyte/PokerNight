import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 2 : 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  timeout: 45_000,
  use: {
    baseURL: 'http://127.0.0.1:18080',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'webkit-iphone-portrait', use: { ...devices['iPhone 15'] } },
    {
      name: 'webkit-iphone-landscape',
      use: { ...devices['iPhone 15 landscape'] },
      testMatch: /(mobile-resilience|visual)\.spec\.ts/,
    },
  ],
  webServer: {
    command: 'POKERNIGHT_E2E_PORT=18080 ../scripts/start-e2e-server.sh',
    url: 'http://127.0.0.1:18080/actuator/health',
    reuseExistingServer: !process.env.CI,
    timeout: 180_000,
  },
})
