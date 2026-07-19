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

test('doctor record list keeps cards and actions usable on mobile', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await loginViaUI(page, 'staff', 'doctor')

  await page.goto('/doctor/records')
  await expect(page.getByRole('heading', { name: '病历管理' })).toBeVisible({ timeout: 15_000 })

  const firstCard = page.locator('[data-testid="doctor-record-card"]').first()
  await expect(firstCard).toBeVisible()
  await expect(firstCard).toContainText('MR20260708001')
  await expectTouchTargetsAtLeast(page, '.doctor-record-action:visible')
  await expectNoHorizontalOverflow(page)

  const detailButton = firstCard.getByRole('button', { name: /查看 MR20260708001/ })
  await detailButton.focus()
  await expect(detailButton).toBeFocused()
  await detailButton.press('Enter')
  await expect(page).toHaveURL(/\/doctor\/record\/1/, { timeout: 15_000 })
})

test('doctor record list exposes alert and retry after first load failure', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await loginViaUI(page, 'staff', 'doctor')
  await page.evaluate(() => localStorage.setItem('mock_doctor_record_list_fail_once', '1'))

  await page.goto('/doctor/records')

  await expect(page.getByRole('alert')).toContainText(/病历列表加载失败|加载失败|重试/)
  await page.getByRole('button', { name: /重试/ }).click()

  await expect(page.getByRole('alert')).toHaveCount(0)
  await expect(page.locator('[data-testid="doctor-record-card"]').first()).toBeVisible()
  await expectNoHorizontalOverflow(page)
})
