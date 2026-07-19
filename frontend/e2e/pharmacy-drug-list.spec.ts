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

test('pharmacy drug catalog keeps search and card actions usable on mobile', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await loginViaUI(page, 'staff', 'pharmacy')

  await page.goto('/pharmacy/drug')
  await expect(page.getByRole('heading', { name: '药品目录' })).toBeVisible({ timeout: 15_000 })

  await expect(page.locator('[data-testid="responsive-drug-card"]').first()).toBeVisible()
  await page.getByLabel('搜索药品名称或编号').fill('硝苯')
  await expect(page.locator('[data-testid="responsive-drug-card"]')).toHaveCount(1)
  await expect(page.locator('[data-testid="responsive-drug-card"]').first()).toContainText('硝苯地平缓释片')

  await expectTouchTargetsAtLeast(page, '.drug-action:visible, .drug-card__actions .el-button:visible, .header-actions .el-input__wrapper:visible')
  await expectNoHorizontalOverflow(page)
})

test('pharmacy drug catalog exposes alert and retry after first load failure', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await loginViaUI(page, 'staff', 'pharmacy')
  await page.evaluate(() => localStorage.setItem('mock_drug_list_fail_once', '1'))

  await page.goto('/pharmacy/drug')

  await expect(page.getByRole('alert')).toContainText(/药品目录加载失败|加载失败|重试/)
  await page.getByRole('button', { name: /重试/ }).click()

  await expect(page.getByRole('alert')).toHaveCount(0)
  await expect(page.locator('[data-testid="responsive-drug-card"]').first()).toBeVisible()
  await expectNoHorizontalOverflow(page)
})

test('pharmacy drug save failure keeps dialog input and exposes alert', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await loginViaUI(page, 'staff', 'pharmacy')
  await page.evaluate(() => localStorage.setItem('mock_drug_save_fail_once', '1'))

  await page.goto('/pharmacy/drug')
  await page.getByRole('button', { name: '新增药品' }).click()

  const dialog = page.getByRole('dialog', { name: '新增药品' })
  await expect(dialog).toBeVisible()
  await dialog.getByPlaceholder('请输入药品名称').fill('移动端测试药品')
  await dialog.getByPlaceholder('如：0.5g*24粒').fill('10mg*10片')
  await dialog.getByPlaceholder('请输入生产厂家').fill('测试药厂')
  await dialog.locator('.category-select .el-select__wrapper').click()
  await page.getByRole('option', { name: '其他' }).click()
  await dialog.getByRole('spinbutton').fill('12.50')
  await dialog.getByRole('button', { name: '确定' }).click()

  await expect(dialog.getByRole('alert')).toContainText(/药品保存失败|保存失败|重试/)
  await expect(dialog.getByPlaceholder('请输入药品名称')).toHaveValue('移动端测试药品')
  await expectNoHorizontalOverflow(page)
})
