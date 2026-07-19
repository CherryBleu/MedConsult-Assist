import { expect, type Locator, type Page } from '@playwright/test'
import { loginViaUI, test } from './helpers'

async function expectNoHorizontalOverflow(page: Page) {
  const metrics = await page.evaluate(() => ({
    documentScrollWidth: document.documentElement.scrollWidth,
    bodyScrollWidth: document.body.scrollWidth,
    clientWidth: document.documentElement.clientWidth
  }))

  expect(metrics.documentScrollWidth).toBeLessThanOrEqual(metrics.clientWidth + 1)
  expect(metrics.bodyScrollWidth).toBeLessThanOrEqual(metrics.clientWidth + 1)
}

async function expectVisibleFocus(locator: Locator) {
  const focusStyle = await locator.evaluate((element) => {
    const style = window.getComputedStyle(element)
    return {
      outlineStyle: style.outlineStyle,
      outlineWidth: style.outlineWidth,
      boxShadow: style.boxShadow
    }
  })

  const hasOutline = focusStyle.outlineStyle !== 'none' && focusStyle.outlineWidth !== '0px'
  const hasShadow = focusStyle.boxShadow !== 'none'
  expect(hasOutline || hasShadow).toBeTruthy()
}

async function expectContainedScrollableTable(page: Page, testId: string, minWidth: number) {
  const shell = page.getByTestId(testId)
  await expect(shell).toBeVisible()

  const metrics = await shell.evaluate((element) => {
    const shellStyle = window.getComputedStyle(element)
    const table = element.querySelector('.el-table')
    const tableStyle = table ? window.getComputedStyle(table) : null

    return {
      clientWidth: Math.round(element.clientWidth),
      scrollWidth: Math.round(element.scrollWidth),
      overflowX: shellStyle.overflowX,
      tableMinWidth: tableStyle?.minWidth || '0px',
      viewportWidth: window.innerWidth
    }
  })

  expect(metrics.overflowX).toMatch(/auto|scroll/)
  expect(parseFloat(metrics.tableMinWidth)).toBeGreaterThanOrEqual(minWidth)
  expect(metrics.scrollWidth).toBeGreaterThan(metrics.clientWidth)
  expect(metrics.clientWidth).toBeLessThanOrEqual(metrics.viewportWidth)
  await expectNoHorizontalOverflow(page)
}

test.describe('admin drug desktop UX', () => {
  test.beforeEach(async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 })
    await loginViaUI(page, 'staff', 'admin')
  })

  test('drug list keeps table scroll contained and action buttons keyboard visible', async ({ page }) => {
    await page.goto('/admin/drug')
    await expect(page.getByRole('heading', { name: '药品管理' })).toBeVisible({ timeout: 15_000 })

    await expectContainedScrollableTable(page, 'admin-drug-table-scroll', 1120)

    const addButton = page.getByRole('button', { name: '新增药品' })
    await addButton.focus()
    await expect(addButton).toBeFocused()
    await expectVisibleFocus(addButton)

    const editButton = page.getByRole('button', { name: '编辑阿莫西林胶囊' })
    await editButton.focus()
    await expect(editButton).toBeFocused()
    await expectVisibleFocus(editButton)
  })

  test('drug list exposes retryable load and save errors without losing dialog input', async ({ page }) => {
    await page.evaluate(() => localStorage.setItem('mock_admin_drug_list_fail_once', '1'))
    await page.goto('/admin/drug')

    await expect(page.getByRole('alert')).toContainText(/药品列表加载失败|加载失败|重试/)
    await page.getByRole('button', { name: /重试/ }).click()
    await expect(page.getByRole('alert')).toHaveCount(0)
    await expectContainedScrollableTable(page, 'admin-drug-table-scroll', 1120)

    await page.getByRole('button', { name: '新增药品' }).click()
    const dialog = page.getByRole('dialog', { name: '新增药品' })
    await expect(dialog).toBeVisible()

    const dialogMetrics = await dialog.evaluate((element) => {
      const rect = element.getBoundingClientRect()
      const body = element.querySelector('[data-testid="admin-drug-dialog-body"]') as HTMLElement | null
      const bodyStyle = body ? window.getComputedStyle(body) : null

      return {
        top: rect.top,
        bottom: rect.bottom,
        viewportHeight: window.innerHeight,
        bodyOverflowY: bodyStyle?.overflowY || '',
        bodyMaxHeight: bodyStyle?.maxHeight || '0px'
      }
    })
    expect(dialogMetrics.top).toBeGreaterThanOrEqual(0)
    expect(dialogMetrics.bottom).toBeLessThanOrEqual(dialogMetrics.viewportHeight)
    expect(dialogMetrics.bodyOverflowY).toMatch(/auto|scroll/)
    expect(parseFloat(dialogMetrics.bodyMaxHeight)).toBeGreaterThan(0)

    await page.evaluate(() => localStorage.setItem('mock_drug_save_fail_once', '1'))
    await dialog.getByPlaceholder('请输入药品名称').fill('桌面端测试药品')
    await dialog.getByPlaceholder('如：0.5g*24粒').fill('10mg*10片')
    await dialog.getByPlaceholder('请输入生产厂家').fill('测试药厂')
    await dialog.locator('.category-select .el-select__wrapper').click()
    await page.getByRole('option', { name: '其他' }).click()
    await dialog.getByRole('spinbutton').fill('12.50')
    await dialog.getByRole('button', { name: '确定' }).click()

    await expect(dialog.getByRole('alert')).toContainText(/药品保存失败|保存失败|重试/)
    await expect(dialog.getByPlaceholder('请输入药品名称')).toHaveValue('桌面端测试药品')
    await expect(dialog).toBeVisible()
    await expectNoHorizontalOverflow(page)
  })

  test('stock flow keeps filters responsive and failures retryable on desktop', async ({ page }) => {
    await page.goto('/admin/stock')
    await expect(page.getByRole('heading', { name: '库存流水' })).toBeVisible({ timeout: 15_000 })
    await expectContainedScrollableTable(page, 'admin-stock-flow-table-scroll', 1180)

    const searchButton = page.getByRole('button', { name: '搜索' })
    await page.evaluate(() => localStorage.setItem('mock_stock_flow_delay_once', '1'))
    await searchButton.click()
    await expect(searchButton).toBeDisabled()
    await expect(page.getByRole('status')).toContainText(/正在加载库存流水/)
    await expect(searchButton).toBeEnabled()

    await page.evaluate(() => localStorage.setItem('mock_stock_flow_fail_once', '1'))
    await searchButton.click()
    await expect(page.getByRole('alert')).toContainText(/库存流水加载失败|加载失败|重试/)
    await page.getByRole('button', { name: /重试/ }).click()
    await expect(page.getByRole('alert')).toHaveCount(0)
    await expectContainedScrollableTable(page, 'admin-stock-flow-table-scroll', 1180)
  })

  test('stock warning supports dense filtering, refresh feedback and retry recovery', async ({ page }) => {
    await page.goto('/admin/stock-warning')
    await expect(page.getByRole('heading', { name: '库存预警' })).toBeVisible({ timeout: 15_000 })
    await expectContainedScrollableTable(page, 'admin-stock-warning-table-scroll', 1120)

    const lowStockFilter = page.getByTestId('admin-stock-warning-filter-low')
    await lowStockFilter.locator('.el-radio-button__inner').click()
    await expect(lowStockFilter.getByRole('radio')).toBeChecked()
    await expect(page.getByTestId('admin-stock-warning-table-scroll')).toContainText('硝苯地平缓释片')

    const refreshButton = page.getByRole('button', { name: '刷新库存预警' })
    await page.evaluate(() => localStorage.setItem('mock_stock_warning_fail_once', '1'))
    await refreshButton.click()
    await expect(page.getByRole('alert')).toContainText(/库存预警加载失败|加载失败|重试/)
    await page.getByRole('button', { name: /重试/ }).click()
    await expect(page.getByRole('alert')).toHaveCount(0)
    await expect(page.getByTestId('admin-stock-warning-table-scroll')).toContainText('硝苯地平缓释片')
  })
})
