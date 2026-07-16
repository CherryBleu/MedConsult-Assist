import { test as base, expect, type Page } from '@playwright/test'

/**
 * e2e 公共辅助。
 *
 * 测试全部走前端内置 mock（playwright.config.ts 的 webServer 注入 VITE_USE_MOCK=true），
 * 因此登录不校验真实密码，mock 账号见 src/mock/user.js：
 *   patient / doctor / admin / pharmacy（任意密码即可）
 */

/**
 * 通过 UI 完成登录（走真实的 Login.vue 交互，而非直接写 localStorage）。
 * 这样登录流程本身也被测试覆盖。entry 决定走患者入口还是工作人员入口。
 *
 * @param entry 'patient' | 'staff'
 * @param account mock 账号（patient/doctor/admin/pharmacy）
 */
export async function loginViaUI(page: Page, entry: 'patient' | 'staff', account: string) {
  await page.goto('/login')
  // 第一步：选择入口卡片。Login.vue 已把入口卡片语义化为 button，
  // 因此这里用 role+name 定位，既稳定也能覆盖可访问性回归。
  const entryName = entry === 'patient' ? '患者入口' : '工作人员入口'
  await page.getByRole('button', { name: entryName }).click()
  // 第二步：填写账号密码并提交
  await page.getByPlaceholder(entry === 'patient' ? '请输入患者账号' : '请输入工号/账号').fill(account)
  await page.getByPlaceholder('请输入登录密码').fill('123456')
  await page.getByRole('button', { name: '登 录' }).click()
}

/** 断言已登录并落在某路由（等待 URL 包含指定片段，验证行为而非状态码）。 */
export async function expectOnPath(page: Page, pathFragment: string) {
  await expect(page).toHaveURL(new RegExp(pathFragment), { timeout: 15_000 })
}

// 导出带名字的 test，供各 spec 引用，保持 reporter 输出可读
export const test = base
