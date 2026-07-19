import { expect, type Page } from '@playwright/test'
import { loginViaUI, test } from './helpers'

const onePixelPng = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAFgwJ/l6XW2QAAAABJRU5ErkJggg==',
  'base64'
)

async function gotoDoctorImaging(page: Page) {
  await loginViaUI(page, 'staff', 'doctor')
  await page.goto('/doctor/ai/imaging')
  await expect(page.locator('.main-tabs')).toBeVisible({ timeout: 15_000 })
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

test('医生影像任务列表在移动端切换为卡片并保留可触控操作', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await gotoDoctorImaging(page)

  await expect(page.locator('[data-testid="responsive-imaging-card"]').first()).toBeVisible()
  await expect(page.locator('.responsive-table__desktop')).toBeHidden()
  await expectTouchTargetsAtLeast(page, '.imaging-action:visible')
  await expectNoHorizontalOverflow(page)
})

test('医生影像上传错误可恢复且预览操作满足移动端触控尺寸', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await gotoDoctorImaging(page)

  await page.getByRole('tab').filter({ hasText: /新建|鏂板缓/ }).click()
  const fileInput = page.locator('.image-uploader input[type="file"]')

  await fileInput.setInputFiles({
    name: 'not-image.txt',
    mimeType: 'text/plain',
    buffer: Buffer.from('not an image')
  })
  await expect(page.locator('.inline-error[role="alert"]')).toBeVisible()

  await fileInput.setInputFiles({
    name: 'scan.png',
    mimeType: 'image/png',
    buffer: onePixelPng
  })
  await expect(page.locator('.preview-image')).toBeVisible()
  await expect(page.locator('.inline-error[role="alert"]')).toHaveCount(0)
  await expectTouchTargetsAtLeast(page, '.imaging-action:visible, .image-overlay .el-button:visible')
  await expectNoHorizontalOverflow(page)
})
