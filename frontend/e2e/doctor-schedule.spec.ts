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

async function expectTouchTargetsAtLeast(page, selector: string, minSize = 44) {
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

test('doctor schedule date tabs are keyboard-accessible and mobile-safe', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await loginViaUI(page, 'staff', 'doctor')

  await page.goto('/doctor/schedule')
  await expect(page.getByRole('heading', { name: '我的排班' })).toBeVisible({ timeout: 15_000 })

  const todayTab = page.getByRole('button', { name: /今天/ })
  await expect(todayTab).toBeVisible()
  await expect(todayTab).toHaveAttribute('aria-pressed', 'true')
  await todayTab.focus()
  await expect(todayTab).toBeFocused()
  await expect(todayTab).toHaveCSS('outline-style', 'solid')

  const secondTab = page.locator('.date-tab').nth(1)
  await secondTab.focus()
  await page.keyboard.press('Enter')
  await expect(secondTab).toHaveAttribute('aria-pressed', 'true')

  await expect(page.locator('[data-testid="doctor-schedule-card"]').first()).toBeVisible()
  await expectTouchTargetsAtLeast(page, '.date-tab:visible')
  await expectNoHorizontalOverflow(page)
})

test('doctor schedule exposes alert and retry after first load failure', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await loginViaUI(page, 'staff', 'doctor')
  await page.evaluate(() => localStorage.setItem('mock_doctor_schedule_fail_once', '1'))

  await page.goto('/doctor/schedule')

  await expect(page.getByRole('alert')).toContainText(/排班加载失败|加载失败|重试/)
  await page.getByRole('button', { name: /重试/ }).click()

  await expect(page.getByRole('alert')).toHaveCount(0)
  await expect(page.locator('[data-testid="doctor-schedule-card"]').first()).toBeVisible()
  await expectNoHorizontalOverflow(page)
})
