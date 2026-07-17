import { test, expect } from '@playwright/test'
import { loginViaUI } from './helpers'

/**
 * 流程 2：AI 症状自诊问答（RAG）。
 * 发送 mock 预设的症状（src/mock/ai.js 的 mockSendMessage 对 "咳嗽有痰怎么办" 有固定回复），
 * 断言用户消息气泡 + AI 回复气泡均出现，且回复含 mock 的关键词"呼吸道感染"。
 * 这验证的是"发送→收到非空回复"的行为闭环，而非接口 code。
 */
test('患者发送症状描述后收到 AI 回复', async ({ page }) => {
  await loginViaUI(page, 'patient', 'patient')

  // 进入 AI 问诊页（路由 name=AiConsult, path=/patient/ai-consult）
  await page.goto('/patient/ai-consult')
  await expect(page.getByText('AI智能问诊')).toBeVisible({ timeout: 15_000 })

  // 输入症状并发送（输入框 placeholder 固定）
  await page.getByPlaceholder('请输入您的症状描述...').fill('咳嗽有痰怎么办')
  await page.getByRole('button', { name: '发送' }).click()

  // 行为断言1：我的消息出现在对话区
  await expect(page.getByText('咳嗽有痰怎么办').first()).toBeVisible({ timeout: 15_000 })

  // 行为断言2：AI 回复出现，且含 mock 预设关键词（呼吸道感染），
  // 证明走通了"发送→后端(mock)→渲染回复"的完整链路，不是空兜底。
  await expect(page.getByText('呼吸道感染').first()).toBeVisible({ timeout: 20_000 })

  // 行为断言3：RAG 可追溯证据随 AI 回复一起展示，避免前端只渲染 answer 丢失 citations/vectorMatches。
  await expect(page.getByText('检索证据').first()).toBeVisible()
  await expect(page.getByText('急性支气管炎').first()).toBeVisible()
  await expect(page.getByText('向量匹配').first()).toBeVisible()
  await expect(page.getByText('symptom').first()).toBeVisible()
})
