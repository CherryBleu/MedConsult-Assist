import { expect, type Page } from '@playwright/test'
import { loginViaUI, test } from './helpers'

async function gotoDoctorManage(page: Page) {
  await loginViaUI(page, 'staff', 'admin')
  await page.goto('/admin/doctor')
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

async function expectDoctorTableScrollContainer(page: Page) {
  const tableShell = page.getByTestId('admin-doctor-table-shell')
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

async function expectDialogWithinViewport(page: Page) {
  await expect(page.getByRole('dialog', { name: /新增医生/ })).toBeVisible()

  const dialog = page.locator('.el-dialog').filter({ hasText: '新增医生' })
  await expect(dialog).toBeVisible()

  await expect
    .poll(async () => {
      return dialog.evaluate(element => {
        const rect = element.getBoundingClientRect()
        return (
          rect.left >= 15 &&
          rect.top >= 15 &&
          rect.right <= window.innerWidth - 15 &&
          rect.bottom <= window.innerHeight - 15
        )
      })
    })
    .toBe(true)

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

for (const viewport of [
  { width: 1024, height: 768 },
  { width: 1280, height: 720 }
]) {
  test(`管理员医生管理页在 ${viewport.width}px 桌面宽度由表格容器承接横向滚动`, async ({ page }) => {
    await page.setViewportSize(viewport)
    await gotoDoctorManage(page)

    await expect(page.locator('.responsive-table__desktop')).toBeVisible()
    await expect(page.locator('[data-testid="responsive-admin-doctor-card"]').first()).toBeHidden()
    await expectDoctorTableScrollContainer(page)
    await expectNoHorizontalOverflow(page)
  })
}

test('管理员医生管理新增弹窗在 1024px 桌面缩放下不越界并在内容过高时内部滚动', async ({ page }) => {
  await page.setViewportSize({ width: 1024, height: 620 })
  await gotoDoctorManage(page)

  await page.getByRole('button', { name: '新增医生' }).click()
  await expectDialogWithinViewport(page)
  await expectNoHorizontalOverflow(page)
})

test('管理员医生管理列表加载失败时通过 alert 暴露并可在桌面重试恢复', async ({ page }) => {
  await page.setViewportSize({ width: 1024, height: 768 })
  await loginViaUI(page, 'staff', 'admin')
  await page.evaluate(() => localStorage.setItem('mock_admin_doctor_list_fail_once', '1'))
  await page.goto('/admin/doctor')

  const errorAlert = page.getByRole('alert')
  await expect(errorAlert).toBeVisible({ timeout: 15_000 })
  await expect(errorAlert).toContainText('医生列表加载失败')
  await errorAlert.getByRole('button', { name: '重试' }).click()

  await expect(page.locator('.responsive-table__desktop')).toBeVisible()
  await expectDoctorTableScrollContainer(page)
  await expectNoHorizontalOverflow(page)
})
