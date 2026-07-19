import { expect, type Page } from '@playwright/test'
import { loginViaUI, test } from './helpers'

async function gotoDepartmentManage(page: Page) {
  await loginViaUI(page, 'staff', 'admin')
  await page.goto('/admin/department')
  await expect(page.locator('.page-title')).toBeVisible({ timeout: 15_000 })
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

test('管理员科室管理页在桌面缩放等效宽度下由表格容器承接横向滚动', async ({ page }) => {
  await page.setViewportSize({ width: 1024, height: 768 })
  await gotoDepartmentManage(page)

  const tableShell = page.getByTestId('admin-department-table-shell')
  await expect(tableShell).toBeVisible()
  await expect(page.locator('.el-table').first()).toBeVisible()

  const shellMetrics = await tableShell.evaluate(element => {
    const rect = element.getBoundingClientRect()
    const style = window.getComputedStyle(element)
    return {
      width: rect.width,
      scrollWidth: element.scrollWidth,
      clientWidth: element.clientWidth,
      overflowX: style.overflowX,
      viewportWidth: document.documentElement.clientWidth
    }
  })

  expect(shellMetrics.width).toBeLessThanOrEqual(shellMetrics.viewportWidth)
  expect(['auto', 'scroll']).toContain(shellMetrics.overflowX)
  expect(shellMetrics.scrollWidth).toBeGreaterThanOrEqual(shellMetrics.clientWidth)
  await expectNoHorizontalOverflow(page)
})

test('管理员科室弹窗在桌面缩放等效宽度下不挤出视口', async ({ page }) => {
  await page.setViewportSize({ width: 1024, height: 768 })
  await gotoDepartmentManage(page)

  await page.getByRole('button', { name: '新增科室' }).click()
  await expect(page.getByRole('dialog', { name: '新增科室' })).toBeVisible()
  const dialogPanel = page.locator('.el-dialog').filter({ hasText: '新增科室' }).first()
  await expect(dialogPanel).toBeVisible()

  const dialogMetrics = await dialogPanel.evaluate(element => {
    const rect = element.getBoundingClientRect()
    return {
      left: rect.left,
      right: rect.right,
      width: rect.width,
      viewportWidth: document.documentElement.clientWidth
    }
  })

  expect(dialogMetrics.left).toBeGreaterThanOrEqual(16)
  expect(dialogMetrics.right).toBeLessThanOrEqual(dialogMetrics.viewportWidth - 16)
  expect(dialogMetrics.width).toBeLessThanOrEqual(dialogMetrics.viewportWidth - 32)
  await expectNoHorizontalOverflow(page)
})

test('管理员科室列表加载失败时可在桌面端页面内重试恢复', async ({ page }) => {
  await page.setViewportSize({ width: 1024, height: 768 })
  await loginViaUI(page, 'staff', 'admin')
  await page.evaluate(() => localStorage.setItem('mock_admin_department_list_fail_once', '1'))
  await page.goto('/admin/department')

  const errorAlert = page.getByRole('alert')
  await expect(errorAlert).toBeVisible({ timeout: 15_000 })
  await errorAlert.locator('.page-state__action').click()
  await expect(page.getByTestId('admin-department-table-shell')).toBeVisible()
  await expectNoHorizontalOverflow(page)
})
