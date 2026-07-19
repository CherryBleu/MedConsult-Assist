import { expect, type Page } from '@playwright/test'
import { loginViaUI, test } from './helpers'

async function gotoPrescriptions(page: Page) {
  await loginViaUI(page, 'patient', 'patient')
  await page.goto('/patient/prescriptions')
  await expect(page.getByRole('heading', { name: '我的处方' })).toBeVisible({ timeout: 15_000 })
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

test('患者处方页移动端可查看详情并完成缴费', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await gotoPrescriptions(page)

  await expect(page.getByTestId('responsive-patient-prescription-card').first()).toContainText('RX202607190001')
  await expectTouchTargetsAtLeast(page, '.patient-prescription-action:visible')
  await expectNoHorizontalOverflow(page)

  const detailButton = page.getByRole('button', { name: '查看详情' }).first()
  await detailButton.focus()
  await expect(detailButton).toBeFocused()
  await detailButton.press('Enter')

  const drawer = page.getByRole('dialog').filter({ hasText: '处方详情' })
  await expect(drawer.getByText('阿莫西林胶囊')).toBeVisible({ timeout: 15_000 })
  await drawer.getByRole('button', { name: '立即缴费' }).click()

  await expect(page.getByText('处方缴费').first()).toBeVisible()
  await page.getByRole('button', { name: '确认缴费' }).click()
  await expect(page.getByText('缴费成功').first()).toBeVisible({ timeout: 15_000 })
  await expect(drawer.getByText('已支付').first()).toBeVisible()
})

test('患者处方列表失败时展示可重试错误状态', async ({ page }) => {
  await loginViaUI(page, 'patient', 'patient')
  await page.evaluate(() => localStorage.setItem('mock_patient_prescription_list_fail_once', '1'))
  await page.goto('/patient/prescriptions')

  const errorAlert = page.getByRole('alert').filter({ hasText: '处方列表加载失败' })
  await expect(errorAlert).toBeVisible({ timeout: 15_000 })
  await errorAlert.getByRole('button', { name: '重试' }).click()

  await expect(page.getByText('RX202607190001').first()).toBeVisible({ timeout: 15_000 })
})

test('患者处方缴费失败时保留对话框并允许重试', async ({ page }) => {
  await gotoPrescriptions(page)

  await page.getByRole('button', { name: '立即缴费' }).first().click()
  await expect(page.getByText('处方缴费').first()).toBeVisible()
  await page.evaluate(() => localStorage.setItem('mock_patient_prescription_pay_fail_once', '1'))
  await page.getByRole('button', { name: '确认缴费' }).click()

  const payAlert = page.getByRole('alert').filter({ hasText: '缴费失败' })
  await expect(payAlert).toBeVisible({ timeout: 15_000 })

  await page.getByRole('button', { name: '确认缴费' }).click()
  await expect(page.getByText('缴费成功').first()).toBeVisible({ timeout: 15_000 })
})
