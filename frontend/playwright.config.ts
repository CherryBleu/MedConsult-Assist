import { defineConfig, devices } from '@playwright/test'

/**
 * Playwright 端到端测试配置。
 *
 * 设计要点（区别于此前 .playwright-mcp/ 一次性日志，本配置产出可复现、进 git、能回归的 e2e）：
 * 1. webServer 自动起 vite dev，并通过环境变量强制走前端内置 mock（VITE_USE_MOCK=true），
 *    彻底解耦后端 7 个微服务 + Nacos/MySQL/Redis/RabbitMQ/Milvus——e2e 不再因后端环境抖动而 flaky。
 * 2. baseURL 固定 http://localhost:3000，与 vite.config.mjs 的 server.port 对齐。
 * 3. 只跑 Chromium 以保证本地与 CI 速度；如需多浏览器在 use.projects 扩展。
 * 4. retries: CI 上重试 2 次，本地 0 次（快速看到真实失败）。
 * 5. trace 在首条失败时保留，便于事后定位 selector 漂移。
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false, // 前端 mock 共享 localStorage/会话，串行更稳；规模小无需并行
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : 'list',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  use: {
    // 使用专用 e2e 端口，避免复用开发者已启动且 VITE_USE_MOCK=false 的 3000 端口服务。
    baseURL: 'http://127.0.0.1:3100',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    locale: 'zh-CN',
    actionTimeout: 15_000,
  },
  projects: [
    {
      name: 'system-chrome',
      // 本机 Playwright 浏览器包缺失时，不再触发 npx playwright install；
      // 直接使用已安装的 Chrome（如需 Edge，可运行 PW_BROWSER_CHANNEL=msedge npm run test:e2e）。
      use: { ...devices['Desktop Chrome'], channel: process.env.PW_BROWSER_CHANNEL || 'chrome' },
    },
  ],
  webServer: {
    // 强制走 mock：前端 api/*.js 的 USE_MOCK 分支据此生效，不依赖任何后端服务。
    // 端口固定到 3100，避免误复用日常开发的 3000 服务（其 .env.development 默认 VITE_USE_MOCK=false）。
    command: 'npm run dev -- --host 127.0.0.1 --port 3100 --strictPort',
    url: 'http://127.0.0.1:3100',
    timeout: 60_000,
    reuseExistingServer: false,
    env: {
      VITE_USE_MOCK: 'true',
      VITE_PORT: '3100',
    },
  },
})
