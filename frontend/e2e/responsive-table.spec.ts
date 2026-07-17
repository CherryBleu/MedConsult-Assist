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

  test('audit log switches to cards on mobile', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await page.route('**/api/v1/audit-logs**', async (route) => {
      await route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          message: 'success',
          data: {
            total: 1,
            items: [{
              id: 1,
              auditNo: 'AUD202607170001',
              resourceType: 'appointment',
              resourceId: 'APT202607170001',
              action: 'VIEW',
              operatorName: '系统管理员',
              operatorRole: 'HOSPITAL_ADMIN',
              result: 'SUCCESS',
              createdAt: '2026-07-17 09:30:00'
            }]
          }
        })
      })
    })
    await loginViaUI(page, 'staff', 'admin')

    await page.goto('/admin/audit-log')

    await expect(page.locator('[data-testid="responsive-audit-card"]').first()).toBeVisible()
    await expect(page.locator('.responsive-table__desktop')).toBeHidden()
    await expectNoHorizontalOverflow(page)
  })
})
