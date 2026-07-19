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

async function selectMedicationInputs(page) {
  await page.locator('.medication-record-select .el-select__wrapper').click()
  await page.getByRole('option', { name: /MR20260708001/ }).click()
  await page.locator('.medication-drug-select .el-select__wrapper').click()
  await page.getByRole('option', { name: /阿莫西林胶囊/ }).click()
  await page.keyboard.press('Escape')
}

test('doctor medication analysis keeps form and results usable on mobile', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await loginViaUI(page, 'staff', 'doctor')

  await page.goto('/doctor/medication-analysis')
  await expect(page.getByRole('heading', { name: 'AI用药安全分析' })).toBeVisible({ timeout: 15_000 })
  await selectMedicationInputs(page)

  await expectTouchTargetsAtLeast(page, '.medication-analysis-action:visible, .medication-page .el-select__wrapper:visible')
  await page.getByRole('button', { name: '开始分析' }).click()

  await expect(page.getByTestId('medication-analysis-result')).toContainText('整体风险等级')
  await expect(page.getByTestId('medication-analysis-result')).toContainText('阿莫西林')
  await expectNoHorizontalOverflow(page)
})

test('doctor medication analysis exposes alert and retry after analysis failure', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await loginViaUI(page, 'staff', 'doctor')

  await page.goto('/doctor/medication-analysis')
  await selectMedicationInputs(page)
  await page.evaluate(() => localStorage.setItem('mock_medication_analysis_fail_once', '1'))
  await page.getByRole('button', { name: '开始分析' }).click()

  const recoveryAlert = page.locator('.page-state__panel--error[role="alert"]')
  await expect(recoveryAlert).toContainText(/用药分析请求失败|用药分析失败|重试/)
  await recoveryAlert.getByRole('button', { name: /重试/ }).click()
  await expect(recoveryAlert).toHaveCount(0)
  await expect(page.getByTestId('medication-analysis-result')).toBeVisible()
  await expectNoHorizontalOverflow(page)
})
