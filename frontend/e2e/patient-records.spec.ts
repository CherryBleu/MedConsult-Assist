import { expect } from '@playwright/test'
import { loginViaUI, test } from './helpers'

async function expectNoHorizontalOverflow(page) {
  const metrics = await page.evaluate(() => ({
    viewport: window.innerWidth,
    documentWidth: document.documentElement.scrollWidth,
    bodyWidth: document.body.scrollWidth
  }))

  expect(metrics.documentWidth, JSON.stringify(metrics)).toBeLessThanOrEqual(metrics.viewport)
  expect(metrics.bodyWidth, JSON.stringify(metrics)).toBeLessThanOrEqual(metrics.viewport)
}

test('移动端患者病历列表可通过键盘进入详情且不横向溢出', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await loginViaUI(page, 'patient', 'patient')

  await page.goto('/patient/records')
  await expect(page.getByRole('heading', { name: '我的病历' })).toBeVisible({ timeout: 15_000 })

  const firstRecord = page.getByRole('button', { name: /MR20260708001.*呼吸内科/ })
  await expect(firstRecord).toBeVisible()
  const box = await firstRecord.boundingBox()
  expect(box?.height).toBeGreaterThanOrEqual(44)
  await firstRecord.focus()
  await expect(firstRecord).toBeFocused()
  await expect(firstRecord).toHaveCSS('outline-style', 'solid')

  await expectNoHorizontalOverflow(page)
  await firstRecord.press('Enter')
  await expect(page).toHaveURL(/\/patient\/record\/1/, { timeout: 15_000 })
})

test('桌面端患者病历列表保持内容宽度稳定且不横向溢出', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await loginViaUI(page, 'patient', 'patient')

  await page.goto('/patient/records')
  await expect(page.getByRole('heading', { name: '我的病历' })).toBeVisible({ timeout: 15_000 })
  await expect(page.getByRole('button', { name: /MR20260708001.*呼吸内科/ })).toBeVisible()

  await expectNoHorizontalOverflow(page)
})

test('患者病历列表加载失败时提供可感知错误和重试入口', async ({ page }) => {
  await loginViaUI(page, 'patient', 'patient')
  await expect(page).toHaveURL(/\/patient\/home/, { timeout: 15_000 })
  await page.evaluate(() => localStorage.setItem('mock_patient_record_list_fail_once', '1'))

  await page.goto('/patient/records')

  await expect(page.getByRole('alert')).toContainText('病历列表加载失败')
  await page.getByRole('button', { name: '重试' }).click()

  await expect(page.getByRole('alert')).toHaveCount(0)
  await expect(page.getByRole('button', { name: /MR20260708001.*呼吸内科/ })).toBeVisible()
})
