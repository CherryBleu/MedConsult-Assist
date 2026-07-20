import { expect, type Page } from '@playwright/test'
import { loginViaUI, test } from './helpers'

// 注：测试文件名保留 ai-summary-stream.spec.ts 以避免历史 mock fail-once 开关名变更。
// 但 2026-07-20 起 RecordSummary.vue 已改用一次性 POST /ai/summary/by-record/{recordNo}，
// 不再走 SSE 流。测试同步改为：生成 → loading → 结构化结果一次出现。

async function gotoRecordSummary(page: Page) {
  await loginViaUI(page, 'staff', 'doctor')
  await page.goto('/doctor/record-summary')
  await expect(page.getByRole('heading', { name: 'AI病历摘要生成' })).toBeVisible({ timeout: 15_000 })
}

async function selectMockRecord(page: Page) {
  await page.getByRole('combobox', { name: '选择病历' }).click()
  await page.locator('.el-select-dropdown__item').filter({ hasText: 'MR20260708001' }).click()
}

test('医生一次性生成病历摘要并看到结构化结果', async ({ page }) => {
  await gotoRecordSummary(page)
  await selectMockRecord(page)

  await page.getByRole('button', { name: '生成摘要' }).click()

  // 一次性接口：loading 期间显示"正在生成摘要"，随后直接出现结构化结果。
  // mock 是同步 Promise.resolve，loading 极短，因此断言以最终结果为准。
  await expect(page.getByRole('heading', { name: '生成结果' })).toBeVisible({ timeout: 15_000 })
  await expect(page.getByText('急性支气管炎').first()).toBeVisible()
})

test('病历摘要生成失败时保留所选病历并允许重试', async ({ page }) => {
  await gotoRecordSummary(page)
  await page.evaluate(() => localStorage.setItem('mock_summary_stream_fail_once', '1'))
  await selectMockRecord(page)

  await page.getByRole('button', { name: '生成摘要' }).click()

  const errorAlert = page.getByRole('alert').filter({ hasText: '病历摘要生成失败' })
  await expect(errorAlert).toBeVisible({ timeout: 15_000 })
  await expect(errorAlert).toContainText('AI 摘要服务暂时不可用')

  await errorAlert.getByRole('button', { name: '重试' }).click()
  await expect(page.getByRole('heading', { name: '生成结果' })).toBeVisible({ timeout: 15_000 })
  await expect(page.getByText('急性支气管炎').first()).toBeVisible()
})
