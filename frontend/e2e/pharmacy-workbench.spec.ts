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

test('pharmacy workbench keeps warning actions and quick links usable on mobile', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await loginViaUI(page, 'staff', 'pharmacy')

  await page.goto('/pharmacy/workbench')
  await expect(page.getByText('待处理预警')).toBeVisible({ timeout: 15_000 })
  await expect(page.getByTestId('pharmacy-workbench-warning-item').first()).toContainText('硝苯地平缓释片')
  await expect(page.getByRole('button', { name: '处理硝苯地平缓释片库存预警' })).toBeVisible()
  await expectTouchTargetsAtLeast(page, '.workbench-action:visible, .warning-action:visible, .quick-item:visible')
  await expectNoHorizontalOverflow(page)

  await page.getByRole('button', { name: '库存流水' }).focus()
  await page.keyboard.press('Enter')
  await expect(page).toHaveURL(/\/pharmacy\/stock-flow/)
})

test('pharmacy workbench exposes alert and retry after warning load failure', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await loginViaUI(page, 'staff', 'pharmacy')
  await expect(page.getByTestId('pharmacy-workbench-warning-item').first()).toBeVisible({ timeout: 15_000 })
  await page.evaluate(() => localStorage.setItem('mock_stock_warning_fail_once', '1'))

  await page.reload()

  await expect(page.getByRole('alert')).toContainText(/库存预警加载失败|加载失败|重试/)
  await page.getByRole('button', { name: /重试/ }).click()
  await expect(page.getByRole('alert')).toHaveCount(0)
  await expect(page.getByTestId('pharmacy-workbench-warning-item').first()).toBeVisible()
  await expectNoHorizontalOverflow(page)
})
