import { expect, type Page } from '@playwright/test'
import { loginViaUI, test } from './helpers'

async function gotoScheduleManage(page: Page) {
  await loginViaUI(page, 'staff', 'admin')
  await page.goto('/admin/schedule')
  await expect(page.locator('.page-title')).toBeVisible({ timeout: 15_000 })
}

async function expectNoHorizontalOverflow(page: Page) {
  const metrics = await page.evaluate(() => ({
    documentScrollWidth: document.documentElement.scrollWidth,
    bodyScrollWidth: document.body.scrollWidth,
    clientWidth: document.documentElement.clientWidth
  }))

  expect(metrics.documentScrollWidth).toBeLessThanOrEqual(metrics.clientWidth + 1)
  expect(metrics.bodyScrollWidth).toBeLessThanOrEqual(metrics.clientWidth + 1)
}

async function expectTouchTargetsAtLeast(page: Page, selector: string, minSize = 44) {
  const boxes = await page.locator(selector).evaluateAll(elements =>
    elements.map(element => {
      const rect = element.getBoundingClientRect()
      return { width: rect.width, height: rect.height }
    })
  )

  expect(boxes.length).toBeGreaterThan(0)
  for (const box of boxes) {
    expect(box.width).toBeGreaterThanOrEqual(minSize)
    expect(box.height).toBeGreaterThanOrEqual(minSize)
  }
}

test('管理员排班管理页在移动端切换为排班卡片', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await gotoScheduleManage(page)

  await expect(page.locator('[data-testid="responsive-admin-schedule-card"]').first()).toBeVisible()
  await expect(page.locator('.responsive-table__desktop')).toBeHidden()
  await expectTouchTargetsAtLeast(page, '.admin-schedule-action:visible')
  await expectNoHorizontalOverflow(page)
})

test('管理员排班列表加载失败时可在页面内重试恢复', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await loginViaUI(page, 'staff', 'admin')
  await page.evaluate(() => localStorage.setItem('mock_admin_schedule_list_fail_once', '1'))
  await page.goto('/admin/schedule')

  const errorAlert = page.getByRole('alert')
  await expect(errorAlert).toBeVisible({ timeout: 15_000 })
  await errorAlert.locator('.page-state__action').click()
  await expect(page.locator('[data-testid="responsive-admin-schedule-card"]').first()).toBeVisible()
  await expectNoHorizontalOverflow(page)
})
