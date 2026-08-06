import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 2 : undefined,
  reporter: [["html", { open: "never" }], ["list"]],
  use: {
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [
    { name: "customer-chromium", use: { ...devices["Desktop Chrome"], baseURL: "http://127.0.0.1:3000" } },
    { name: "provider-mobile", use: { ...devices["Pixel 7"], baseURL: "http://127.0.0.1:3001" } },
    { name: "admin-chromium", use: { ...devices["Desktop Chrome"], baseURL: "http://127.0.0.1:3002" } },
  ],
  webServer: [
    { command: "npm run dev --workspace @localserve/customer-web", port: 3000, reuseExistingServer: !process.env.CI },
    { command: "npm run dev --workspace @localserve/provider-web", port: 3001, reuseExistingServer: !process.env.CI },
    { command: "npm run dev --workspace @localserve/admin-web", port: 3002, reuseExistingServer: !process.env.CI },
  ],
});
