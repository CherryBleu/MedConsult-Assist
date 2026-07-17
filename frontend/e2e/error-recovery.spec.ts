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

test.describe('list error recovery', () => {
  test('patient appointments expose retry path after first load failure', async ({ page }) => {
    await loginViaUI(page, 'patient', 'patient')
    await page.evaluate(() => localStorage.setItem('mock_appointment_list_fail_once', '1'))

    await page.goto('/patient/appointment')

    await expect(page.getByRole('alert')).toContainText(/加载失败|请求失败/)
    await expect(page.getByRole('button', { name: /重试/ })).toBeVisible()

    await page.getByRole('button', { name: /重试/ }).click()

    await expect(page.getByRole('alert')).toHaveCount(0)
    await expect(page.locator('.appointment-item').first()).toBeVisible()

    await page.setViewportSize({ width: 390, height: 844 })
    await expectNoHorizontalOverflow(page)
  })

  test('admin patient list exposes retry path after first load failure', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await loginViaUI(page, 'staff', 'admin')
    await page.evaluate(() => localStorage.setItem('mock_patient_list_fail_once', '1'))

    await page.goto('/admin/patient')

    await expect(page.getByRole('alert')).toContainText(/加载失败|患者列表加载失败/)
    await expect(page.getByRole('button', { name: /重试/ })).toBeVisible()

    await page.getByRole('button', { name: /重试/ }).click()

    await expect(page.getByRole('alert')).toHaveCount(0)
    await expect(page.locator('[data-testid="responsive-patient-card"]').first()).toBeVisible()
    await expectNoHorizontalOverflow(page)
  })

  test('doctor reception list exposes retry path after first load failure', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await loginViaUI(page, 'staff', 'doctor')
    await page.evaluate(() => localStorage.setItem('mock_reception_list_fail_once', '1'))

    await page.goto('/doctor/reception')

    await expect(page.getByRole('alert')).toContainText(/加载失败|接诊列表加载失败/)
    await expect(page.getByRole('button', { name: /重试/ })).toBeVisible()

    await page.getByRole('button', { name: /重试/ }).click()

    await expect(page.getByRole('alert')).toHaveCount(0)
    await expect(page.locator('[data-testid="responsive-reception-card"]').first()).toBeVisible()
    await expectNoHorizontalOverflow(page)
  })

  test('stock warning list exposes retry path after first load failure', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await loginViaUI(page, 'staff', 'pharmacy')
    await page.evaluate(() => localStorage.setItem('mock_stock_warning_fail_once', '1'))

    await page.goto('/pharmacy/stock-warning')

    await expect(page.getByRole('alert')).toContainText(/加载失败|库存预警加载失败/)
    await expect(page.getByRole('button', { name: /重试/ })).toBeVisible()

    await page.getByRole('button', { name: /重试/ }).click()

    await expect(page.getByRole('alert')).toHaveCount(0)
    await expect(page.locator('[data-testid="responsive-stock-warning-card"]').first()).toBeVisible()
    await expectNoHorizontalOverflow(page)
  })

  test('admin AI call log exposes retry path after first load failure', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await loginViaUI(page, 'staff', 'admin')
    await page.evaluate(() => localStorage.setItem('mock_ai_call_log_fail_once', '1'))

    await page.goto('/admin/ai-call-log')

    await expect(page.getByRole('alert')).toContainText(/鍔犺浇澶辫触|AI/)
    const retryButton = page.getByRole('alert').getByRole('button')
    await expect(retryButton).toBeVisible()

    await retryButton.click()

    await expect(page.getByRole('alert')).toHaveCount(0)
    await expect(page.locator('[data-testid="responsive-ai-call-log-card"]').first()).toBeVisible()
    await expectNoHorizontalOverflow(page)
  })

  test('admin AI feedback exposes retry path after first load failure', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await loginViaUI(page, 'staff', 'admin')
    await page.evaluate(() => localStorage.setItem('mock_ai_feedback_fail_once', '1'))

    await page.goto('/admin/ai-feedback')

    await expect(page.getByRole('alert')).toContainText(/鍔犺浇澶辫触|AI/)
    const retryButton = page.getByRole('alert').getByRole('button')
    await expect(retryButton).toBeVisible()

    await retryButton.click()

    await expect(page.getByRole('alert')).toHaveCount(0)
    await expect(page.locator('[data-testid="responsive-ai-feedback-card"]').first()).toBeVisible()
    await expectNoHorizontalOverflow(page)
  })
})
