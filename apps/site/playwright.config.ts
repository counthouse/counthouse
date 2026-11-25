import { defineConfig, devices } from "@playwright/test";

const url = "http://127.0.0.1:4719";

// Tests read the built site: run `npm run build` first.
export default defineConfig({
  testDir: "tests",
  forbidOnly: !!process.env.CI,
  reporter: process.env.CI ? [["list"], ["html", { open: "never" }]] : "dot",
  use: { baseURL: url, trace: "retain-on-failure" },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: {
    command: "node scripts/serve.mts",
    url,
    // A server already on the port would be tested instead of this build: fail instead.
    reuseExistingServer: false,
  },
});
