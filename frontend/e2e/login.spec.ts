import { test, expect } from '@playwright/test'
import { loginViaUI } from './helpers'

/**
 * 流程 1：患者登录。
 * 断言落到行为（URL 跳转到患者端首页 + 侧边栏出现患者专属菜单），
 * 而非"登录接口返回 200"这种脆弱状态码断言。
 */
test.describe('登录流程', () => {
  test('患者从患者入口登录后进入患者首页', async ({ page }) => {
    await loginViaUI(page, 'patient', 'patient')

    // 行为断言1：URL 跳转到患者端（/patient 或 /home 重定向后含 patient）
    await expect(page).toHaveURL(/\/(patient|home)/, { timeout: 15_000 })

    // 行为断言2：页面出现患者身份相关内容（用户名或患者专属菜单项）
    // 患者首页 PatientHome 有"预约挂号"等入口
    await expect(page.getByText('测试患者').first()).toBeVisible({ timeout: 15_000 })
  })

  test('工作人员从工作人员入口登录后进入医生工作台', async ({ page }) => {
    await loginViaUI(page, 'staff', 'doctor')
    await expect(page).toHaveURL(/\/(doctor|home)/, { timeout: 15_000 })
    await expect(page.getByText('张医生').first()).toBeVisible({ timeout: 15_000 })
  })

  test('患者账号不能从工作人员入口登录', async ({ page }) => {
    await loginViaUI(page, 'staff', 'patient')

    await expect(page).toHaveURL(/\/staff-login/, { timeout: 15_000 })
    await expect(page.getByPlaceholder('请输入工号/账号')).toHaveValue('patient')
    await expect.poll(() => page.evaluate(() => sessionStorage.getItem('hospital_token'))).toBeNull()
  })
})
