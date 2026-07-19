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

test('医生工作台移动端快捷入口可键盘操作', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await loginViaUI(page, 'staff', 'doctor')

  await page.goto('/doctor/workbench')
  await expect(page.getByText('今日待就诊')).toBeVisible()
  await expect(page.getByRole('button', { name: '接诊管理' })).toBeVisible()
  await expectTouchTargetsAtLeast(page, '.doctor-workbench-action:visible, .quick-item:visible')
  await expectNoHorizontalOverflow(page)

  await page.getByRole('button', { name: '接诊管理' }).focus()
  await page.keyboard.press('Enter')
  await expect(page).toHaveURL(/\/doctor\/reception/)
})
