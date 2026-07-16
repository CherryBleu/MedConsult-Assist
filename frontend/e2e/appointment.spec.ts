import { test, expect } from '@playwright/test'
import { loginViaUI } from './helpers'

/**
 * 流程 4：患者预约挂号（科室选择 → 进入医生列表）。
 * 选科室是挂号流程的第一步且最稳定；验证"选科室 → 跳转医生列表"的导航行为。
 * 不测完整 4 步预约（涉及排班/号源/档案校验等多重依赖，e2e 应聚焦核心闭环）。
 * mock（src/mock/department.js）含"心血管内科"等 6 个科室。
 */
test('患者选择科室后进入医生列表', async ({ page }) => {
  await loginViaUI(page, 'patient', 'patient')

  // 进入科室列表页（路由 name=DepartmentList）
  await page.goto('/patient/appointment/department')
  await expect(page.getByRole('heading', { name: '选择科室' })).toBeVisible({ timeout: 15_000 })

  // mock 科室含"心血管内科"，点击其卡片进入医生列表
  await page.getByText('心血管内科').first().click()

  // 行为断言：URL 跳转到医生列表（/patient/appointment/doctor）
  await expect(page).toHaveURL(/\/patient\/appointment\/doctor/, { timeout: 15_000 })
})
