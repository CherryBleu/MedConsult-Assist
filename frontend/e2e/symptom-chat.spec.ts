import { test, expect, type Page } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { loginViaUI } from './helpers'

async function gotoConsult(page: Page) {
  await loginViaUI(page, 'patient', 'patient')
  await page.goto('/patient/ai-consult')
  await expect(page.getByRole('heading', { name: 'AI智能问诊' })).toBeVisible({ timeout: 15_000 })
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

async function sendSymptom(page: Page, symptom: string) {
  await page.getByPlaceholder('请输入您的症状描述...').fill(symptom)
  await page.getByRole('button', { name: '发送' }).click()
}

/**
 * 流程 2：AI 症状自诊问答（RAG）。
 * 发送 mock 预设的症状（src/mock/ai.js 的 mockSendMessage 对 "咳嗽有痰怎么办" 有固定回复），
 * 断言用户消息气泡 + AI 回复气泡均出现，且回复含 mock 的关键词"呼吸道感染"。
 * 这验证的是"发送→收到非空回复"的行为闭环，而非接口 code。
 */
test('患者发送症状描述后收到 AI 回复', async ({ page }) => {
  await gotoConsult(page)

  // 输入症状并发送（输入框 placeholder 固定）
  await sendSymptom(page, '咳嗽有痰怎么办')

  // 行为断言1：我的消息出现在对话区
  await expect(page.getByText('咳嗽有痰怎么办').first()).toBeVisible({ timeout: 15_000 })

  // 行为断言2：AI 回复出现，且含 mock 预设关键词（呼吸道感染），
  // 证明走通了"发送→后端(mock)→渲染回复"的完整链路，不是空兜底。
  await expect(page.getByText('呼吸道感染').first()).toBeVisible({ timeout: 20_000 })

  // 行为断言3：RAG 可追溯证据随 AI 回复一起展示，避免前端只渲染 answer 丢失 citations/vectorMatches。
  await expect(page.getByText('检索证据').first()).toBeVisible()
  await expect(page.getByText('急性支气管炎').first()).toBeVisible()
  await expect(page.getByText('向量匹配').first()).toBeVisible()
  await expect(page.getByText('症状表现').first()).toBeVisible()
  await expect(page.getByText('日常预防').first()).toBeVisible()
  await expect(page.getByText('symptom')).toHaveCount(0)
  await expect(page.getByText('prevent')).toHaveCount(0)
  await expect(page.getByText('DISEASE_JSON')).toHaveCount(0)
})

test('高风险症状先展示急症安全摘要再展示 RAG 证据', async ({ page }) => {
  await gotoConsult(page)

  await sendSymptom(page, '胸痛大汗怎么办')

  const safetySummary = page.getByTestId('safety-summary').first()
  await expect(safetySummary).toBeVisible({ timeout: 20_000 })
  await expect(safetySummary).toHaveAttribute('role', 'alert')
  await expect(safetySummary).toContainText('建议尽快就医')
  await expect(safetySummary).toContainText('急诊科')

  const evidence = page.getByText('检索证据').first()
  await expect(evidence).toBeVisible()

  const safetyBox = await safetySummary.boundingBox()
  const evidenceBox = await evidence.boundingBox()
  expect(safetyBox?.y ?? 0).toBeLessThan(evidenceBox?.y ?? Number.MAX_SAFE_INTEGER)
})

test('桌面端输入区与用户消息列连续对齐', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 960 })
  await gotoConsult(page)

  await sendSymptom(page, '咳嗽有痰怎么办')
  await expect(page.getByText('呼吸道感染').first()).toBeVisible({ timeout: 20_000 })

  const composer = page.getByTestId('desktop-composer').first()
  const lastUserRow = page.locator('.message-item.user-message').last()
  const [composerBox, userRowBox] = await Promise.all([
    composer.boundingBox(),
    lastUserRow.boundingBox()
  ])

  expect(composerBox).not.toBeNull()
  expect(userRowBox).not.toBeNull()

  const composerLeft = composerBox?.x ?? 0
  const composerRight = (composerBox?.x ?? 0) + (composerBox?.width ?? 0)
  const userRowLeft = userRowBox?.x ?? 0
  const userRowRight = (userRowBox?.x ?? 0) + (userRowBox?.width ?? 0)

  expect(Math.abs(composerLeft - userRowLeft)).toBeLessThanOrEqual(16)
  expect(Math.abs(composerRight - userRowRight)).toBeLessThanOrEqual(16)
})

test('移动端问诊页无横向滚动且关键控件满足触控尺寸', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await gotoConsult(page)

  await sendSymptom(page, '咳嗽有痰怎么办')
  await expect(page.getByText('呼吸道感染').first()).toBeVisible({ timeout: 20_000 })

  await expectTouchTargetsAtLeast(page, '.ai-consult-action')
  await expectNoHorizontalOverflow(page)
})

test('发送失败时保留症状并提供可重试错误状态', async ({ page }) => {
  await gotoConsult(page)
  await page.evaluate(() => localStorage.setItem('mock_symptom_chat_fail_once', '1'))

  await sendSymptom(page, '咳嗽有痰怎么办')

  await expect(page.getByText('咳嗽有痰怎么办').first()).toBeVisible()
  const errorAlert = page.getByRole('alert').filter({ hasText: 'AI 问诊服务暂时不可用' })
  await expect(errorAlert).toBeVisible({ timeout: 20_000 })

  await errorAlert.getByRole('button', { name: '重试' }).click()
  await expect(page.getByText('呼吸道感染').first()).toBeVisible({ timeout: 20_000 })
})

test('键盘可聚焦并切换 RAG 证据 disclosure', async ({ page }) => {
  await gotoConsult(page)

  await sendSymptom(page, '咳嗽有痰怎么办')
  await expect(page.getByText('呼吸道感染').first()).toBeVisible({ timeout: 20_000 })

  const evidenceToggle = page.getByTestId('evidence-toggle').first()
  await evidenceToggle.focus()
  await expect(evidenceToggle).toBeFocused()
  await evidenceToggle.press('Enter')
  await expect(page.getByText('证据字段：症状表现').first()).toBeVisible()
  await expect(page.getByText('知识库疾病信息').first()).toBeVisible()
  await expect(page.getByText('vec_mock_001')).toHaveCount(0)
  await expect(page.getByText('cure_department')).toHaveCount(0)
})

test('患者 AI 问诊页没有 serious 或 critical Axe 违规', async ({ page }) => {
  await gotoConsult(page)
  await expect(page.locator('.el-message')).toHaveCount(0, { timeout: 5_000 })

  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze()
  const severeViolations = results.violations.filter(({ impact }) =>
    impact === 'serious' || impact === 'critical')

  expect(severeViolations).toEqual([])
})
