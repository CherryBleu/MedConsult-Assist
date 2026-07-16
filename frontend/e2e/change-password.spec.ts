import { test, expect } from '@playwright/test'
import { loginViaUI } from './helpers'

/**
 * 流程 3：医生修改密码。
 * 填原密码/新密码/确认密码（满足 6 位 + 两次一致），提交后断言成功提示出现。
 * 验证的是"表单校验通过 → 提交 → 成功反馈"的行为闭环。
 * mock（src/mock/system.js mockChangePassword）固定返回"密码修改成功"。
 */
test('医生修改密码成功', async ({ page }) => {
  await loginViaUI(page, 'staff', 'doctor')

  // 进入个人信息页（路由 name=DoctorProfile, path=/doctor/profile）
  await page.goto('/doctor/profile')
  await expect(page.getByRole('button', { name: '修改密码' })).toBeVisible({ timeout: 15_000 })

  // 填写三段密码（用 placeholder 定位输入框）
  await page.getByPlaceholder('请输入原密码').fill('old123456')
  await page.getByPlaceholder('请输入新密码（至少6位）').fill('new123456')
  await page.getByPlaceholder('请再次输入新密码').fill('new123456')

  // 触发确认密码的 blur 校验后提交
  await page.getByRole('button', { name: '修改密码' }).click()

  // 行为断言：ElMessage 成功提示出现（mock 固定返回"密码修改成功"）
  await expect(page.getByText('密码修改成功').first()).toBeVisible({ timeout: 15_000 })
})
