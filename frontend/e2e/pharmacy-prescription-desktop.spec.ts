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

test('pharmacy prescription dispensing contains desktop table overflow and detail dialog', async ({ page }) => {
  await page.setViewportSize({ width: 1024, height: 768 })
  await loginViaUI(page, 'staff', 'pharmacy')

  await page.goto('/pharmacy/prescription-review')
  await expect(page.getByRole('heading', { name: '处方发药' })).toBeVisible({ timeout: 15_000 })

  const desktopTable = page.locator('.responsive-table__desktop').first()
  await expect(desktopTable).toBeVisible()

  const tableMetrics = await desktopTable.evaluate(element => {
    const style = window.getComputedStyle(element)
    const rect = element.getBoundingClientRect()
    return {
      overflowX: style.overflowX,
      clientWidth: element.clientWidth,
      scrollWidth: element.scrollWidth,
      left: rect.left,
      right: rect.right,
      viewportWidth: window.innerWidth
    }
  })
  expect(['auto', 'scroll']).toContain(tableMetrics.overflowX)
  expect(tableMetrics.right).toBeLessThanOrEqual(tableMetrics.viewportWidth + 1)
  expect(tableMetrics.clientWidth).toBeLessThanOrEqual(tableMetrics.viewportWidth - tableMetrics.left + 1)

  await page.getByRole('button', { name: /查看 RX202607180006 处方详情/ }).click()
  const dialog = page.getByRole('dialog', { name: '处方详情' })
  await expect(dialog).toBeVisible()
  await expect.poll(async () => {
    const box = await dialog.boundingBox()
    return box ? Math.round(box.y) : -999
  }).toBeGreaterThanOrEqual(0)
  await expect.poll(async () => {
    const box = await dialog.boundingBox()
    return box ? Math.round(box.y + box.height) : 9999
  }).toBeLessThanOrEqual(768)
  const dialogBox = await dialog.boundingBox()
  expect(dialogBox).not.toBeNull()
  expect(dialogBox!.x).toBeGreaterThanOrEqual(0)
  expect(dialogBox!.x + dialogBox!.width).toBeLessThanOrEqual(1024)

  await expectNoHorizontalOverflow(page)
})

test('pharmacy prescription dispensing exposes alert and retry after desktop list failure', async ({ page }) => {
  await page.setViewportSize({ width: 1024, height: 768 })
  await loginViaUI(page, 'staff', 'pharmacy')
  await page.evaluate(() => localStorage.setItem('mock_pharmacy_prescription_list_fail_once', '1'))

  await page.goto('/pharmacy/prescription-review')

  const recoveryAlert = page.getByRole('alert')
  await expect(recoveryAlert).toContainText(/处方列表加载失败|重试/)
  await recoveryAlert.getByRole('button', { name: /重试/ }).click()
  await expect(recoveryAlert).toHaveCount(0)
  await expect(page.getByRole('button', { name: /查看 RX202607180006 处方详情/ })).toBeVisible()
  await expectNoHorizontalOverflow(page)
})
