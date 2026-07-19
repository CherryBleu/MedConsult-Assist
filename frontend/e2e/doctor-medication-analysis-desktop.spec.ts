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

async function selectMedicationInputs(page) {
  await page.locator('.medication-record-select .el-select__wrapper').click()
  await page.getByRole('option', { name: /MR20260708001/ }).click()
  await page.locator('.medication-drug-select .el-select__wrapper').click()
  await page.getByRole('option', { name: /阿莫西林胶囊/ }).click()
  await page.keyboard.press('Escape')
}

test('doctor medication analysis uses a desktop two-pane workflow with visible focus', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 })
  await loginViaUI(page, 'staff', 'doctor')

  await page.goto('/doctor/medication-analysis')
  await expect(page.getByRole('heading', { name: 'AI用药安全分析' })).toBeVisible({ timeout: 15_000 })
  await selectMedicationInputs(page)

  const analyzeButton = page.getByRole('button', { name: '开始分析' })
  await analyzeButton.focus()
  const focusStyle = await analyzeButton.evaluate(element => {
    const style = window.getComputedStyle(element)
    return {
      outlineStyle: style.outlineStyle,
      outlineWidth: style.outlineWidth,
      boxShadow: style.boxShadow
    }
  })
  const hasVisibleOutline =
    focusStyle.outlineStyle !== 'none' && Number.parseFloat(focusStyle.outlineWidth) > 0
  const hasVisibleBoxShadow = focusStyle.boxShadow !== 'none'
  expect(hasVisibleOutline || hasVisibleBoxShadow).toBeTruthy()

  await analyzeButton.click()
  const result = page.getByTestId('medication-analysis-result')
  await expect(result).toContainText('整体风险等级')

  const formBox = await page.locator('.select-section').boundingBox()
  const resultBox = await result.boundingBox()
  expect(formBox).not.toBeNull()
  expect(resultBox).not.toBeNull()
  expect(Math.abs(resultBox!.y - formBox!.y)).toBeLessThanOrEqual(72)
  expect(resultBox!.x).toBeGreaterThan(formBox!.x + formBox!.width - 1)
  await expectNoHorizontalOverflow(page)
})
