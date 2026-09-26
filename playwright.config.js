// End to end: the built page in a real browser, asserting through the
// DOM the numbers the JVM and Jolt suites assert. `npm run e2e` builds
// the release and runs this; `npx playwright test` reruns over the
// last build. With ORRERY_URL set, the tests drive a page served
// elsewhere (the shadow watcher on http://localhost:8379, say) and no
// server is started here.
import { defineConfig } from '@playwright/test';

const port = 8391;
const external = process.env.ORRERY_URL;

export default defineConfig({
  testDir: './test/e2e',
  fullyParallel: true,
  retries: 0,
  reporter: 'list',
  timeout: 90_000,
  expect: { timeout: 10_000 },
  use: {
    baseURL: external || `http://localhost:${port}`,
    trace: 'retain-on-failure',
  },
  webServer: external ? undefined : {
    command: `python3 -m http.server ${port} --directory public`,
    url: `http://localhost:${port}/`,
    stderr: 'ignore',   // python's request log; a port in use is reported before this runs
  },
  projects: [{ name: 'chromium', use: { browserName: 'chromium' } }],
});
