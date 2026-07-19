import { expect, type Page } from '@playwright/test'
import { loginViaUI, test } from './helpers'

async function gotoTriage(page: Page) {
  await loginViaUI(page, 'patient', 'patient')
  await page.goto('/patient/triage')
  await expect(page.getByRole('heading', { name: '智能分诊' })).toBeVisible({ timeout: 15_000 })
}

async function fillSymptoms(page: Page) {
  await page.locator('.triage-card textarea').fill('咳嗽、咳痰、低热3天')
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

test('患者智能分诊先展示流式进度再渲染推荐结果', async ({ page }) => {
  await gotoTriage(page)
  await fillSymptoms(page)

  await page.getByRole('button', { name: '开始分诊' }).click()

  const streamStatus = page.getByRole('status').filter({ hasText: '正在接收分诊建议' })
  await expect(streamStatus).toBeVisible({ timeout: 10_000 })
  await expect(streamStatus).toContainText('呼吸内科', { timeout: 15_000 })

  const result = page.getByTestId('triage-result')
  await expect(result).toContainText('风险等级', { timeout: 15_000 })
  await expect(result).toContainText('呼吸内科')
})

test('患者智能分诊流式失败后保留输入并提供页面内重试', async ({ page }) => {
  await gotoTriage(page)
  await page.evaluate(() => localStorage.setItem('mock_triage_stream_fail_once', '1'))
  await fillSymptoms(page)

  await page.getByRole('button', { name: '开始分诊' }).click()

  const recoveryAlert = page.getByRole('alert').filter({ hasText: '分诊请求失败' })
  await expect(recoveryAlert).toBeVisible({ timeout: 15_000 })
  await expect(recoveryAlert).toContainText('AI 分诊流服务暂时不可用')
  await expect(page.locator('.triage-card textarea')).toHaveValue('咳嗽、咳痰、低热3天')

  await recoveryAlert.getByRole('button', { name: '重试' }).click()
  await expect(recoveryAlert).toHaveCount(0)
  await expect(page.getByTestId('triage-result')).toContainText('呼吸内科', { timeout: 15_000 })
})

test('移动端患者智能分诊流式结果无横向溢出且关键操作满足触控尺寸', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await gotoTriage(page)
  await fillSymptoms(page)

  await page.getByRole('button', { name: '开始分诊' }).click()
  await expect(page.getByTestId('triage-result')).toContainText('呼吸内科', { timeout: 15_000 })

  await expectTouchTargetsAtLeast(page, '.triage-action:visible')
  await expectNoHorizontalOverflow(page)
})
