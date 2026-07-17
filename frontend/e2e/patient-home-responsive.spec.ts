import { expect } from '@playwright/test'
import { loginViaUI, test } from './helpers'

test.describe('患者首页响应式与无障碍入口', () => {
  test('常用功能以按钮语义暴露并可跳转', async ({ page }) => {
    await loginViaUI(page, 'patient', 'patient')
    await expect(page.getByRole('heading', { name: /您好/ })).toBeVisible({ timeout: 15_000 })

    await page.getByRole('button', { name: '预约挂号' }).click()
    await expect(page).toHaveURL(/\/patient\/appointment$/, { timeout: 15_000 })
  })

  test('移动端患者首页没有水平滚动', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await loginViaUI(page, 'patient', 'patient')
    await page.goto('/patient/home')
    await expect(page.getByRole('heading', { name: /您好/ })).toBeVisible({ timeout: 15_000 })

    const metrics = await page.evaluate(() => ({
      documentScrollWidth: document.documentElement.scrollWidth,
      bodyScrollWidth: document.body.scrollWidth,
      clientWidth: document.documentElement.clientWidth
    }))

    expect(metrics.documentScrollWidth).toBeLessThanOrEqual(metrics.clientWidth + 1)
    expect(metrics.bodyScrollWidth).toBeLessThanOrEqual(metrics.clientWidth + 1)
  })
})
