import { expect } from '@playwright/test'
import { loginViaUI, test } from './helpers'

async function expectNoHorizontalOverflow(page) {
  const metrics = await page.evaluate(() => ({
    viewport: window.innerWidth,
    documentWidth: document.documentElement.scrollWidth,
    bodyWidth: document.body.scrollWidth
  }))

  expect(metrics.documentWidth, JSON.stringify(metrics)).toBeLessThanOrEqual(metrics.viewport)
  expect(metrics.bodyWidth, JSON.stringify(metrics)).toBeLessThanOrEqual(metrics.viewport)
}

async function expectTouchTargetsAtLeast(page, selector: string, minSize = 44) {
  const boxes = await page.locator(selector).evaluateAll(elements =>
    elements.map(element => {
      const rect = element.getBoundingClientRect()
      return { width: rect.width, height: rect.height }
    })
  )

  expect(boxes.length).toBeGreaterThan(0)
  for (const box of boxes) {
    expect(box.width).toBeGreaterThanOrEqual(minSize)
    expect(box.height).toBeGreaterThanOrEqual(minSize)
  }
}

test('移动端药师库存管理切换为卡片并保持筛选可用', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await loginViaUI(page, 'staff', 'pharmacy')

  await page.goto('/pharmacy/stock')
  await expect(page.getByRole('heading', { name: '库存管理' })).toBeVisible({ timeout: 15_000 })

  await expect(page.locator('[data-testid="responsive-stock-card"]').first()).toBeVisible()
  await expect(page.locator('.responsive-table__desktop')).toBeHidden()
  await expectTouchTargetsAtLeast(page, '.stock-action:visible, .stock-card__actions .el-button:visible')

  await page.locator('.header-actions .el-select').click()
  await page.getByRole('option', { name: '库存不足' }).click()
  await expect(page.locator('[data-testid="responsive-stock-card"]')).toHaveCount(1)
  await expectNoHorizontalOverflow(page)
})

test('药师库存列表加载失败时提供可感知错误和重试入口', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await loginViaUI(page, 'staff', 'pharmacy')
  await expect(page).toHaveURL(/\/pharmacy\/workbench/, { timeout: 15_000 })
  await page.evaluate(() => localStorage.setItem('mock_stock_list_fail_once', '1'))

  await page.goto('/pharmacy/stock')

  await expect(page.getByRole('alert')).toContainText('库存列表加载失败')
  await page.getByRole('button', { name: '重试' }).click()

  await expect(page.getByRole('alert')).toHaveCount(0)
  await expect(page.locator('[data-testid="responsive-stock-card"]').first()).toBeVisible()
})

test('药师库存出库失败时对话框保留输入并提供错误恢复', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await loginViaUI(page, 'staff', 'pharmacy')
  await page.evaluate(() => localStorage.setItem('mock_stock_operate_fail_once', '1'))

  await page.goto('/pharmacy/stock')
  const firstCard = page.locator('[data-testid="responsive-stock-card"]').first()
  await expect(firstCard).toBeVisible()
  await firstCard.getByRole('button', { name: '出库' }).click()

  const dialog = page.getByRole('dialog', { name: '药品出库' })
  await expect(dialog).toBeVisible()
  await dialog.getByRole('spinbutton').fill('2')
  await dialog.getByPlaceholder('如：门诊发药、病区领药').fill('移动端失败恢复验证')
  await dialog.getByRole('button', { name: '确定' }).click()

  await expect(dialog.getByRole('alert')).toContainText('库存操作失败')
  await expect(dialog.getByRole('spinbutton')).toHaveValue('2')
  await expect(dialog.getByPlaceholder('如：门诊发药、病区领药')).toHaveValue('移动端失败恢复验证')
  await expectNoHorizontalOverflow(page)
})
