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

async function gotoMedicationAnalysis(page) {
  await loginViaUI(page, 'staff', 'doctor')
  await page.goto('/doctor/medication-analysis')
  await expect(page.getByRole('heading', { name: 'AI用药安全分析' })).toBeVisible({ timeout: 15_000 })
}

async function selectMedicationInputs(page) {
  await page.locator('.medication-record-select .el-select__wrapper').click()
  await page.getByRole('option', { name: /MR20260708001/ }).click()
  await page.locator('.medication-drug-select .el-select__wrapper').click()
  await page.getByRole('option', { name: /阿莫西林胶囊/ }).click()
  await page.keyboard.press('Escape')
}

test('doctor sees streaming medication analysis progress before the final result', async ({ page }) => {
  await gotoMedicationAnalysis(page)
  await selectMedicationInputs(page)

  await page.getByRole('button', { name: '开始分析' }).click()

  const streamStatus = page.getByRole('status').filter({ hasText: '正在接收流式用药分析' })
  await expect(streamStatus).toBeVisible({ timeout: 10_000 })
  await expect(streamStatus).toContainText('阿莫西林', { timeout: 15_000 })

  const result = page.getByTestId('medication-analysis-result')
  await expect(result).toContainText('整体风险等级', { timeout: 15_000 })
  await expect(result).toContainText('阿莫西林')
})

test('stream failure keeps medication context and can retry on mobile without overflow', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await gotoMedicationAnalysis(page)
  await selectMedicationInputs(page)
  await page.evaluate(() => localStorage.setItem('mock_medication_analysis_stream_fail_once', '1'))

  await page.getByRole('button', { name: '开始分析' }).click()

  const recoveryAlert = page.getByRole('alert').filter({ hasText: '用药分析失败' })
  await expect(recoveryAlert).toBeVisible({ timeout: 15_000 })
  await expect(recoveryAlert).toContainText(/流式服务暂时不可用|重试/)
  await expect(page.getByText('已选择 1 个药品')).toBeVisible()

  await expectTouchTargetsAtLeast(page, '.medication-analysis-action:visible, .medication-page .el-select__wrapper:visible')
  await expectNoHorizontalOverflow(page)

  await recoveryAlert.getByRole('button', { name: /重试/ }).click()
  await expect(page.getByTestId('medication-analysis-result')).toContainText('整体风险等级', { timeout: 15_000 })
  await expect(page.getByTestId('medication-analysis-result')).toContainText('阿莫西林')
  await expectNoHorizontalOverflow(page)
})
