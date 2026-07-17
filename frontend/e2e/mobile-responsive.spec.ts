import { test, expect } from '@playwright/test'

async function expectNoHorizontalOverflow(page) {
  const metrics = await page.evaluate(() => ({
    viewport: window.innerWidth,
    documentWidth: document.documentElement.scrollWidth,
    bodyWidth: document.body.scrollWidth
  }))

  expect(metrics.documentWidth, JSON.stringify(metrics)).toBeLessThanOrEqual(metrics.viewport)
  expect(metrics.bodyWidth, JSON.stringify(metrics)).toBeLessThanOrEqual(metrics.viewport)
}

test.describe('移动端响应式基础', () => {
  test('390px 视口下登录入口和表单不横向溢出', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await page.goto('/login')

    await expect(page.getByRole('button', { name: '患者入口' })).toBeVisible()
    await expect(page.getByRole('button', { name: '工作人员入口' })).toBeVisible()
    await expectNoHorizontalOverflow(page)

    await page.getByRole('button', { name: '患者入口' }).click()
    await expect(page.getByPlaceholder('请输入患者账号')).toBeVisible()
    await expect(page.getByRole('button', { name: '返回选择' })).toBeVisible()
    await expectNoHorizontalOverflow(page)
  })

  test('390px 视口下注册页不横向溢出', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await page.goto('/register')

    await expect(page.getByRole('heading', { name: '用户注册' })).toBeVisible()
    await expect(page.getByRole('button', { name: '立即注册' })).toBeVisible()
    await expectNoHorizontalOverflow(page)
  })
})
