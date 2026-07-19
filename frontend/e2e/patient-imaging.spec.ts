import { expect } from '@playwright/test'
import { loginViaUI, test } from './helpers'

const onePixelPng = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAFgwJ/l6XW2QAAAABJRU5ErkJggg==',
  'base64'
)

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

test('患者影像历史在移动端切换为卡片', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await loginViaUI(page, 'patient', 'patient')

  await page.goto('/patient/ai/imaging')
  await page.getByRole('tab', { name: '历史记录' }).click()

  await expect(page.locator('[data-testid="responsive-patient-imaging-card"]').first()).toBeVisible()
  await expect(page.locator('.responsive-table__desktop')).toBeHidden()
  await expectTouchTargetsAtLeast(page, '.patient-imaging-action:visible')
  await expectNoHorizontalOverflow(page)
})

test('患者影像上传有可恢复错误和图片替代文本', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await loginViaUI(page, 'patient', 'patient')

  await page.goto('/patient/ai/imaging')
  const fileInput = page.locator('.image-uploader input[type="file"]')

  await fileInput.setInputFiles({
    name: 'not-image.txt',
    mimeType: 'text/plain',
    buffer: Buffer.from('not an image')
  })
  const inlineAlert = page.locator('.inline-error[role="alert"]')
  await expect(inlineAlert).toContainText('请上传图片文件')

  await fileInput.setInputFiles({
    name: 'scan.png',
    mimeType: 'image/png',
    buffer: onePixelPng
  })
  await expect(page.getByAltText(/待提交的医学影像预览/)).toBeVisible()
  await expect(inlineAlert).toHaveCount(0)
  await expectTouchTargetsAtLeast(page, '.patient-imaging-action:visible')
  await expectNoHorizontalOverflow(page)
})
