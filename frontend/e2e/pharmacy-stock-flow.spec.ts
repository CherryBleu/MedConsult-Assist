import { expect } from '@playwright/test'
import { loginViaUI, test } from './helpers'

async function expectNoHorizontalOverflow(page) {
  const metrics = await page.evaluate(() => ({
    documentScrollWidth: document.documentElement.scrollWidth,
    bodyScrollWidth: document.body.scrollWidth,
    clientWidth: document.documentElement.clientWidth
  }))

  expect(metrics.documentScrollWidth).toBeLessThanOrEqual(metrics.clientWidth + 1)
  expect(metrics.bodyScrollWidth).toBeLessThanOrEqual(metrics.clientWidth + 1)
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

async function selectFirstDrug(page) {
  await page.locator('.drug-select').click()
  const firstOption = page.locator('.el-select-dropdown__item:visible').first()
  await expect(firstOption).toBeVisible()
  await firstOption.click()
}

test('pharmacy stock flow switches to cards and keeps filters touch-friendly on mobile', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await loginViaUI(page, 'staff', 'pharmacy')

  await page.goto('/pharmacy/stock-flow')
  await expect(page.getByRole('heading', { name: '库存流水' })).toBeVisible({ timeout: 15_000 })

  await selectFirstDrug(page)

  await expect(page.locator('[data-testid="responsive-stock-flow-card"]').first()).toBeVisible()
  await expect(page.locator('.responsive-table__desktop')).toBeHidden()

  await page.locator('.type-select').click()
  await page.getByRole('option', { name: '出库' }).click()
  await expect(page.locator('[data-testid="responsive-stock-flow-card"]')).toHaveCount(5)

  await expectTouchTargetsAtLeast(page, '.flow-action:visible, .header-actions .el-select__wrapper:visible')
  const queryButton = page.locator('.flow-action:visible').first()
  await queryButton.focus()
  await expect(queryButton).toBeFocused()
  await expectNoHorizontalOverflow(page)
})

test('pharmacy stock flow exposes alert and retry after first load failure', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await loginViaUI(page, 'staff', 'pharmacy')
  await page.evaluate(() => localStorage.setItem('mock_stock_flow_fail_once', '1'))

  await page.goto('/pharmacy/stock-flow')
  await selectFirstDrug(page)

  await expect(page.getByRole('alert')).toContainText(/库存流水加载失败|加载失败|重试/)
  await page.getByRole('button', { name: /重试/ }).click()

  await expect(page.getByRole('alert')).toHaveCount(0)
  await expect(page.locator('[data-testid="responsive-stock-flow-card"]').first()).toBeVisible()
  await expectNoHorizontalOverflow(page)
})
