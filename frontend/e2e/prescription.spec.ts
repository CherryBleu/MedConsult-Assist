import { test, expect } from '@playwright/test'
import { loginViaUI } from './helpers'

/**
 * 流程 5：医生写病历/处方（保存草稿）。
 * 填主诉（必填，否则前端拦截提示"请填写主诉"），点"保存草稿"，
 * 断言成功提示出现。验证"填写 → 校验 → 提交 → 成功反馈"的病历录入闭环。
 * mock（src/mock/record.js）创建病历固定返回成功。
 *
 * 注：RecordWrite 通过 route.query 接收 appointmentId/patientId，直接 goto 不带参数时
 * 走默认值（patientName='患者'），mock 下不影响草稿保存。
 */
test('医生填写主诉后保存病历草稿成功', async ({ page }) => {
  await loginViaUI(page, 'staff', 'doctor')

  // 进入病历书写页（路由 name=RecordWrite, path=/doctor/record/write）
  await page.goto('/doctor/record/write')
  await expect(page.getByPlaceholder('请输入主诉')).toBeVisible({ timeout: 15_000 })

  // 填主诉（前端校验：主诉为空时拦截）
  await page.getByPlaceholder('请输入主诉').fill('咳嗽伴胸闷3天')
  await page.getByPlaceholder('请输入初步诊断').fill('急性支气管炎')

  // 点"保存草稿"
  await page.getByRole('button', { name: '保存草稿' }).click()

  // 行为断言：成功提示出现（mock 固定返回，页面 ElMessage.success('草稿已保存')）
  await expect(page.getByText('草稿已保存').first()).toBeVisible({ timeout: 15_000 })
})
