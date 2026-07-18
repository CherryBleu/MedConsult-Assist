import { expect } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { loginViaUI, test } from './helpers'

test.describe('主布局导航与键盘语义', () => {
  test('移动端使用抽屉导航并在路由跳转后关闭', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await loginViaUI(page, 'patient', 'patient')
    await expect(page).toHaveURL(/\/patient\/home/, { timeout: 15_000 })

    const skipLink = page.getByRole('link', { name: '跳到主要内容' })
    await expect(skipLink).toHaveAttribute('href', '#main-content')

    const menuButton = page.getByRole('button', { name: '打开导航菜单' })
    await expect(menuButton).toHaveAttribute('aria-expanded', 'false')
    await expect(page.getByTestId('desktop-navigation')).toHaveCount(0)

    await menuButton.press('Enter')
    const closeMenuButton = page.getByRole('button', { name: '关闭导航菜单' })
    await expect(closeMenuButton).toHaveAttribute('aria-expanded', 'true')
    const mobileNavigation = page.getByRole('navigation', { name: '移动端主导航' })
    await expect(mobileNavigation).toBeVisible()

    await mobileNavigation.getByRole('menuitem', { name: '我的病历' }).click()
    await expect(page).toHaveURL(/\/patient\/records/)
    await expect(page.getByRole('button', { name: '打开导航菜单' })).toHaveAttribute('aria-expanded', 'false')
  })

  test('桌面侧栏和通知操作可通过键盘激活', async ({ page }, testInfo) => {
    test.skip(testInfo.project.name === 'mobile-chrome', '桌面侧栏仅在桌面项目验证')
    await loginViaUI(page, 'staff', 'admin')
    await expect(page).toHaveURL(/\/admin\/user/, { timeout: 15_000 })

    const collapseButton = page.getByRole('button', { name: '收起侧边栏' })
    await expect(collapseButton).toHaveAttribute('aria-expanded', 'true')
    await collapseButton.press('Enter')
    await expect(page.getByRole('button', { name: '展开侧边栏' })).toHaveAttribute('aria-expanded', 'false')

    const noticeButton = page.getByRole('button', { name: /通知，\d+ 条未读/ })
    await noticeButton.press('Enter')
    await expect(page.getByRole('button', { name: /运营日报/ })).toBeVisible()
    await expect(page.getByRole('button', { name: '查看全部通知' })).toBeVisible()
  })

  test('患者不能通过地址栏进入管理员页面', async ({ page }) => {
    await loginViaUI(page, 'patient', 'patient')
    await expect(page).toHaveURL(/\/patient\/home/, { timeout: 15_000 })

    await page.goto('/admin/user')

    await expect(page).not.toHaveURL(/\/admin\/user/)
  })

  test('管理员主布局没有 serious 或 critical Axe 违规', async ({ page }, testInfo) => {
    if (testInfo.project.name === 'system-chrome') {
      await page.setViewportSize({ width: 1440, height: 900 })
    }
    await loginViaUI(page, 'staff', 'admin')
    await expect(page).toHaveURL(/\/admin\/user/, { timeout: 15_000 })
    await expect(page.locator('.el-message')).toHaveCount(0, { timeout: 5_000 })

    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze()
    const severeViolations = results.violations.filter(({ impact }) =>
      impact === 'serious' || impact === 'critical')

    expect(severeViolations).toEqual([])
  })
})
