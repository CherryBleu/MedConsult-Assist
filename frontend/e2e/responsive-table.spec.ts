import { expect } from '@playwright/test'
import { loginViaUI, test } from './helpers'

async function expectNoHorizontalOverflow(page) {
  const metrics = await page.evaluate(() => ({
    documentScrollWidth: document.documentElement.scrollWidth,
    bodyScrollWidth: document.body.scrollWidth,
    clientWidth: document.documentElement.clientWidth
  }))

  expect(metrics.documentScrollWidth).toBeLessThanOrEqual(metrics.clientWidth + 1)
  expect(metrics.bodyScrollWidth).toBeLessThanOrEqual(metrics.clientWidth + 1)
}

test.describe('responsive table', () => {
  test('admin user list switches to cards on mobile', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await loginViaUI(page, 'staff', 'admin')

    await page.goto('/admin/user')

    await expect(page.locator('[data-testid="responsive-user-card"]').first()).toBeVisible()
    await expect(page.locator('.responsive-table__desktop')).toBeHidden()
    await expectNoHorizontalOverflow(page)
  })
})
