import { expect, type Page } from '@playwright/test'
import { loginViaUI, test } from './helpers'

async function gotoPharmacyAdminManage(page: Page) {
  await loginViaUI(page, 'staff', 'admin')
  await page.goto('/admin/pharmacy-admin')
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

async function expectPharmacyAdminTableScrollContainer(page: Page) {
  const tableShell = page.getByTestId('admin-pharmacy-admin-table-shell')
  await expect(tableShell).toBeVisible()

  const metrics = await tableShell.evaluate(element => {
    const styles = getComputedStyle(element)
    const table = element.querySelector('.el-table')
    const tableRect = table?.getBoundingClientRect()

    return {
      clientWidth: element.clientWidth,
      scrollWidth: element.scrollWidth,
      overflowX: styles.overflowX,
      tableWidth: tableRect?.width ?? 0
    }
  })

  expect(metrics.clientWidth).toBeGreaterThan(0)
  expect(['auto', 'scroll']).toContain(metrics.overflowX)
  expect(metrics.scrollWidth).toBeGreaterThan(metrics.clientWidth + 1)
  expect(metrics.tableWidth).toBeGreaterThan(metrics.clientWidth + 1)
}

async function expectDialogWithinViewport(page: Page, title: string) {
  await expect(page.getByRole('dialog', { name: title })).toBeVisible()
  const dialog = page.locator('.el-dialog').filter({ hasText: title }).first()
  await expect(dialog).toBeVisible()

  const metrics = await dialog.evaluate(element => {
    const rect = element.getBoundingClientRect()
    const body = element.querySelector('.el-dialog__body')
    const bodyStyles = body ? getComputedStyle(body) : null

    return {
      top: rect.top,
      right: rect.right,
      bottom: rect.bottom,
      left: rect.left,
      viewportWidth: window.innerWidth,
      viewportHeight: window.innerHeight,
      bodyClientHeight: body?.clientHeight ?? 0,
      bodyScrollHeight: body?.scrollHeight ?? 0,
      bodyOverflowY: bodyStyles?.overflowY ?? ''
    }
  })

  expect(metrics.left).toBeGreaterThanOrEqual(15)
  expect(metrics.top).toBeGreaterThanOrEqual(15)
  expect(metrics.right).toBeLessThanOrEqual(metrics.viewportWidth - 15)
  expect(metrics.bottom).toBeLessThanOrEqual(metrics.viewportHeight - 15)
  expect(['auto', 'scroll']).toContain(metrics.bodyOverflowY)
  expect(metrics.bodyClientHeight).toBeGreaterThan(0)
  expect(metrics.bodyScrollHeight).toBeGreaterThan(metrics.bodyClientHeight)
}

test('药房管理员管理页在 1280px 桌面宽度由表格容器承接横向滚动', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 720 })
  await gotoPharmacyAdminManage(page)

  await expectPharmacyAdminTableScrollContainer(page)
  await expectNoHorizontalOverflow(page)
})

test('药房管理员管理工具栏可键盘聚焦且焦点可见', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 720 })
  await gotoPharmacyAdminManage(page)

  const searchInput = page.getByPlaceholder('搜索账号、姓名或手机号')
  await searchInput.focus()
  await expect(searchInput).toBeFocused()

  await expect
    .poll(async () => searchInput.evaluate(element => {
      const input = element as HTMLElement
      const wrapper = input.closest('.pharmacy-admin-search') ?? input
      const styles = getComputedStyle(wrapper)
      return styles.outlineStyle !== 'none' || styles.boxShadow !== 'none'
    }))
    .toBe(true)

  await page.keyboard.press('Tab')
  const addButton = page.getByRole('button', { name: '新增药房管理员' })
  await expect(addButton).toBeFocused()
})

test('药房管理员列表加载失败时通过 alert 暴露并可重试恢复', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 720 })
  await loginViaUI(page, 'staff', 'admin')
  await expect(page).toHaveURL(/\/admin\//, { timeout: 15_000 })
  await page.evaluate(() => localStorage.setItem('mock_admin_pharmacy_admin_list_fail_once', '1'))
  await page.goto('/admin/pharmacy-admin')

  const errorAlert = page.getByRole('alert')
  await expect(errorAlert).toBeVisible({ timeout: 15_000 })
  await expect(errorAlert).toContainText('药房管理员列表加载失败')
  await errorAlert.getByRole('button', { name: '重试' }).click()

  await expectPharmacyAdminTableScrollContainer(page)
  await expectNoHorizontalOverflow(page)
})

test('药房管理员新增弹窗在桌面低高视口下不越界并可关闭', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 560 })
  await gotoPharmacyAdminManage(page)

  await page.getByRole('button', { name: '新增药房管理员' }).click()
  await expectDialogWithinViewport(page, '新增药房管理员')
  await page.getByRole('button', { name: '取消' }).click()
  await expect(page.getByRole('dialog', { name: '新增药房管理员' })).toBeHidden()
  await expectNoHorizontalOverflow(page)
})
