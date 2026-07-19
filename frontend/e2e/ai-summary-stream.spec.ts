import { expect, type Page } from '@playwright/test'
import { loginViaUI, test } from './helpers'

async function gotoRecordSummary(page: Page) {
  await loginViaUI(page, 'staff', 'doctor')
  await page.goto('/doctor/record-summary')
  await expect(page.getByRole('heading', { name: 'AI病历摘要生成' })).toBeVisible({ timeout: 15_000 })
}

async function selectMockRecord(page: Page) {
  await page.getByRole('combobox', { name: '选择病历' }).click()
  await page.locator('.el-select-dropdown__item').filter({ hasText: 'MR20260708001' }).click()
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

test('医生生成病历摘要时先看到流式内容再看到结构化结果', async ({ page }) => {
  await gotoRecordSummary(page)
  await selectMockRecord(page)

  await page.getByRole('button', { name: '生成摘要' }).click()

  const streamStatus = page.getByRole('status').filter({ hasText: '正在接收流式摘要' })
  await expect(streamStatus).toBeVisible({ timeout: 10_000 })
  await expect(streamStatus).toContainText('咳嗽、咳痰3天', { timeout: 15_000 })

  await expect(page.getByRole('heading', { name: '生成结果' })).toBeVisible({ timeout: 15_000 })
  await expect(page.getByText('急性支气管炎').first()).toBeVisible()
})

test('病历摘要流式生成失败时保留所选病历并允许重试', async ({ page }) => {
  await gotoRecordSummary(page)
  await page.evaluate(() => localStorage.setItem('mock_summary_stream_fail_once', '1'))
  await selectMockRecord(page)

  await page.getByRole('button', { name: '生成摘要' }).click()

  const errorAlert = page.getByRole('alert').filter({ hasText: '病历摘要生成失败' })
  await expect(errorAlert).toBeVisible({ timeout: 15_000 })
  await expect(errorAlert).toContainText('AI 摘要流服务暂时不可用')

  await errorAlert.getByRole('button', { name: '重试' }).click()
  await expect(page.getByRole('heading', { name: '生成结果' })).toBeVisible({ timeout: 15_000 })
  await expect(page.getByText('急性支气管炎').first()).toBeVisible()
})

test('移动端病历摘要流式生成无横向滚动且关键按钮满足触控尺寸', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await gotoRecordSummary(page)
  await selectMockRecord(page)

  await page.getByRole('button', { name: '生成摘要' }).click()
  await expect(page.getByText('急性支气管炎').first()).toBeVisible({ timeout: 15_000 })

  await expectTouchTargetsAtLeast(page, '.record-summary-action:visible')
  await expectNoHorizontalOverflow(page)
})
