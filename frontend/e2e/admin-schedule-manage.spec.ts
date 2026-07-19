import { expect, type Page } from '@playwright/test'
import { loginViaUI, test } from './helpers'

async function gotoScheduleManage(page: Page) {
  await loginViaUI(page, 'staff', 'admin')
  await page.goto('/admin/schedule')
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

async function expectTouchTargetsAtLeast(page: Page, selector: string, minSize = 44) {
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

async function expectVisibleElementsWithinHorizontalViewport(page: Page, selector: string) {
  const boxes = await page.locator(selector).evaluateAll(elements =>
    elements
      .map(element => {
        const rect = element.getBoundingClientRect()
        const style = window.getComputedStyle(element)
        return {
          left: rect.left,
          right: rect.right,
          width: rect.width,
          height: rect.height,
          display: style.display,
          visibility: style.visibility,
          viewportWidth: document.documentElement.clientWidth
        }
      })
      .filter(box => box.display !== 'none' && box.visibility !== 'hidden' && box.width > 0 && box.height > 0)
  )

  expect(boxes.length).toBeGreaterThan(0)
  for (const box of boxes) {
    expect(box.left).toBeGreaterThanOrEqual(-1)
    expect(box.right).toBeLessThanOrEqual(box.viewportWidth + 1)
  }
}

async function expectWideTableScrollsInsideContainer(page: Page, requireOverflow: boolean) {
  const tableShell = page.locator('.responsive-table__desktop')
  await expect(tableShell).toBeVisible()

  const metrics = await tableShell.evaluate(element => {
    const rect = element.getBoundingClientRect()
    const style = window.getComputedStyle(element)
    return {
      clientWidth: element.clientWidth,
      scrollWidth: element.scrollWidth,
      overflowX: style.overflowX,
      left: rect.left,
      right: rect.right,
      viewportWidth: document.documentElement.clientWidth
    }
  })

  expect(['auto', 'scroll']).toContain(metrics.overflowX)
  expect(metrics.left).toBeGreaterThanOrEqual(-1)
  expect(metrics.right).toBeLessThanOrEqual(metrics.viewportWidth + 1)
  if (requireOverflow) {
    expect(metrics.scrollWidth).toBeGreaterThan(metrics.clientWidth)
  }
}

async function expectActiveDialogViewportSafe(page: Page) {
  const dialog = page.locator('.el-dialog:visible').last()
  await expect(dialog).toBeVisible()

  const readMetrics = () => dialog.evaluate(element => {
    const rect = element.getBoundingClientRect()
    const body = element.querySelector('.el-dialog__body')
    const footer = element.querySelector('.el-dialog__footer')
    const bodyStyle = body ? window.getComputedStyle(body) : null
    const footerRect = footer?.getBoundingClientRect()
    return {
      dialogTop: rect.top,
      dialogLeft: rect.left,
      dialogRight: rect.right,
      dialogBottom: rect.bottom,
      footerRight: footerRect?.right ?? null,
      footerBottom: footerRect?.bottom ?? null,
      bodyOverflowY: bodyStyle?.overflowY ?? '',
      viewportWidth: document.documentElement.clientWidth,
      viewportHeight: window.innerHeight
    }
  })

  await expect.poll(async () => {
    const metrics = await readMetrics()
    return (
      metrics.dialogTop >= 15 &&
      metrics.dialogLeft >= 15 &&
      metrics.dialogRight <= metrics.viewportWidth - 15 &&
      metrics.dialogBottom <= metrics.viewportHeight - 15 &&
      (metrics.footerRight ?? Number.POSITIVE_INFINITY) <= metrics.viewportWidth - 15 &&
      (metrics.footerBottom ?? Number.POSITIVE_INFINITY) <= metrics.viewportHeight - 15 &&
      ['auto', 'scroll'].includes(metrics.bodyOverflowY)
    )
  }).toBe(true)

  const metrics = await readMetrics()
  expect(metrics.dialogTop).toBeGreaterThanOrEqual(15)
  expect(metrics.dialogLeft).toBeGreaterThanOrEqual(15)
  expect(metrics.dialogRight).toBeLessThanOrEqual(metrics.viewportWidth - 15)
  expect(metrics.dialogBottom).toBeLessThanOrEqual(metrics.viewportHeight - 15)
  expect(metrics.footerRight).not.toBeNull()
  expect(metrics.footerBottom).not.toBeNull()
  expect(metrics.footerRight ?? 0).toBeLessThanOrEqual(metrics.viewportWidth - 15)
  expect(metrics.footerBottom ?? 0).toBeLessThanOrEqual(metrics.viewportHeight - 15)
  expect(['auto', 'scroll']).toContain(metrics.bodyOverflowY)
}

for (const width of [1024, 1280]) {
  test(`admin schedule desktop layout contains overflow at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 720 })
    await gotoScheduleManage(page)

    await expect(page.locator('.responsive-table__mobile')).toBeHidden()
    await expectVisibleElementsWithinHorizontalViewport(page, '.page-header')
    await expectVisibleElementsWithinHorizontalViewport(page, '.search-form')
    await expectVisibleElementsWithinHorizontalViewport(page, '.pagination')
    await expectWideTableScrollsInsideContainer(page, width === 1024)
    await expectTouchTargetsAtLeast(page, '.header-actions .admin-schedule-action:visible')
    await expectTouchTargetsAtLeast(page, '.search-form .admin-schedule-action:visible')
    await expectNoHorizontalOverflow(page)
  })
}

test('admin schedule add and batch dialogs keep footers inside a short desktop viewport', async ({ page }) => {
  await page.setViewportSize({ width: 1024, height: 560 })
  await gotoScheduleManage(page)

  await page.locator('.header-actions .admin-schedule-action').nth(1).click()
  await expectActiveDialogViewportSafe(page)
  await expectNoHorizontalOverflow(page)
  await page.locator('.el-dialog:visible .el-dialog__footer .admin-schedule-action').first().click()
  await expect(page.locator('.el-dialog:visible')).toHaveCount(0)

  await page.locator('.header-actions .admin-schedule-action').first().click()
  await expectActiveDialogViewportSafe(page)
  await expectNoHorizontalOverflow(page)
})

test('admin schedule list exposes alert and retry recovers on narrow desktop', async ({ page }) => {
  await page.setViewportSize({ width: 1024, height: 720 })
  await loginViaUI(page, 'staff', 'admin')
  await page.evaluate(() => localStorage.setItem('mock_admin_schedule_list_fail_once', '1'))
  await page.goto('/admin/schedule')

  const errorAlert = page.getByRole('alert')
  await expect(errorAlert).toBeVisible({ timeout: 15_000 })
  await expectVisibleElementsWithinHorizontalViewport(page, '.page-state__panel--error')
  await errorAlert.locator('.page-state__action').click()
  await expect(errorAlert).toHaveCount(0)
  await expect(page.locator('.responsive-table__desktop')).toBeVisible()
  await expectWideTableScrollsInsideContainer(page, true)
  await expectNoHorizontalOverflow(page)
})
