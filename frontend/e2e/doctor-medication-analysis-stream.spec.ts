import { expect } from '@playwright/test'
import { loginViaUI, test } from './helpers'

// 注：文件名保留 doctor-medication-analysis-stream.spec.ts 以避免 mock fail-once
// 开关名变更。2026-07-20 起 MedicationAnalysis.vue 已改用一次性 POST
// /api/v1/ai/medication-analysis（非流式），断言同步改为一次性结果。

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

test('doctor gets one-shot medication analysis result without streaming tokens', async ({ page }) => {
  await gotoMedicationAnalysis(page)
  await selectMedicationInputs(page)

  await page.getByRole('button', { name: '开始分析' }).click()

  // 一次性接口：mock 是同步 Promise.resolve，loading 极短；以最终结果为准
  const result = page.getByTestId('medication-analysis-result')
  await expect(result).toContainText('整体风险等级', { timeout: 15_000 })
  await expect(result).toContainText('阿莫西林')
})

test('medication analysis failure keeps context and allows retry', async ({ page }) => {
  await gotoMedicationAnalysis(page)
  await selectMedicationInputs(page)
  // mock_medication_analysis_fail_once 与旧 mock_medication_analysis_stream_fail_once
  // 在 mockMedicationAnalysis 中只保留前者；旧 _stream_ 开关已废弃。
  await page.evaluate(() => localStorage.setItem('mock_medication_analysis_fail_once', '1'))

  await page.getByRole('button', { name: '开始分析' }).click()

  const recoveryAlert = page.getByRole('alert').filter({ hasText: '用药分析失败' })
  await expect(recoveryAlert).toBeVisible({ timeout: 15_000 })
  await expect(page.getByText('已选择 1 个药品')).toBeVisible()

  await page.getByRole('button', { name: /重试/ }).first().click()
  await expect(page.getByTestId('medication-analysis-result')).toContainText('整体风险等级', { timeout: 15_000 })
  await expect(page.getByTestId('medication-analysis-result')).toContainText('阿莫西林')
})
