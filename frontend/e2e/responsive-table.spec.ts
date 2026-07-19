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

test.describe('responsive table', () => {
  test('admin user list switches to cards on mobile', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await loginViaUI(page, 'staff', 'admin')

    await page.goto('/admin/user')

    await expect(page.locator('[data-testid="responsive-user-card"]').first()).toBeVisible()
    await expect(page.locator('.responsive-table__desktop')).toBeHidden()
    await expectNoHorizontalOverflow(page)
  })

  test('admin patient list switches to cards on mobile', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await loginViaUI(page, 'staff', 'admin')

    await page.goto('/admin/patient')

    await expect(page.locator('[data-testid="responsive-patient-card"]').first()).toBeVisible()
    await expect(page.locator('.responsive-table__desktop')).toBeHidden()
    await expectNoHorizontalOverflow(page)
  })

  test('doctor reception list switches to cards on mobile', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await loginViaUI(page, 'staff', 'doctor')

    await page.goto('/doctor/reception')

    await expect(page.locator('[data-testid="responsive-reception-card"]').first()).toBeVisible()
    await expect(page.locator('.responsive-table__desktop')).toBeHidden()
    await expectNoHorizontalOverflow(page)
  })

  test('admin stock warning list switches to cards on mobile', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await loginViaUI(page, 'staff', 'admin')

    await page.goto('/admin/stock-warning')

    await expect(page.locator('[data-testid="responsive-stock-warning-card"]').first()).toBeVisible()
    await expect(page.locator('.responsive-table__desktop')).toBeHidden()
    await expectNoHorizontalOverflow(page)
  })

  test('pharmacy stock warning list keeps filters usable on mobile', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await loginViaUI(page, 'staff', 'pharmacy')

    await page.goto('/pharmacy/stock-warning')

    await expect(page.locator('[data-testid="responsive-stock-warning-card"]').first()).toBeVisible()
    const lowStockFilter = page.getByTestId('stock-warning-filter-low')
    await lowStockFilter.locator('.el-radio-button__inner').click()
    await expect(lowStockFilter.getByRole('radio')).toBeChecked()
    await expect(page.locator('[data-testid="responsive-stock-warning-card"]')).toHaveCount(2)
    await expect(page.locator('.responsive-table__desktop')).toBeHidden()
    await expectNoHorizontalOverflow(page)
  })

  test('audit log switches to cards on mobile', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await page.route('**/api/v1/audit-logs**', async (route) => {
      await route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          message: 'success',
          data: {
            total: 1,
            items: [{
              id: 1,
              auditNo: 'AUD202607170001',
              resourceType: 'appointment',
              resourceId: 'APT202607170001',
              action: 'VIEW',
              operatorName: '系统管理员',
              operatorRole: 'HOSPITAL_ADMIN',
              result: 'SUCCESS',
              createdAt: '2026-07-17 09:30:00'
            }]
          }
        })
      })
    })
    await loginViaUI(page, 'staff', 'admin')

    await page.goto('/admin/audit-log')

    await expect(page.locator('[data-testid="responsive-audit-card"]').first()).toBeVisible()
    await expect(page.locator('.responsive-table__desktop')).toBeHidden()
    await expectNoHorizontalOverflow(page)
  })

  test('admin AI call log switches to cards on mobile', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await loginViaUI(page, 'staff', 'admin')

    await page.goto('/admin/ai-call-log')

    const firstCard = page.locator('[data-testid="responsive-ai-call-log-card"]').first()
    await expect(firstCard).toBeVisible()
    await expect(firstCard).toContainText('缓存命中')
    await expect(firstCard).toContainText('trace-cache-001')
    await expect(firstCard).toContainText('REQ-cache-001')
    await expect(firstCard).toContainText('Token')
    await expect(firstCard).toContainText('￥0.000000')
    await expect(page.locator('.responsive-table__desktop')).toBeHidden()
    await expectTouchTargetsAtLeast(
      page,
      '.pagination-box .btn-prev, .pagination-box .btn-next, .pagination-box .number, .pagination-box .el-pagination__sizes .el-select, .pagination-box .el-pagination__jump .el-input'
    )
    await expectNoHorizontalOverflow(page)
  })

  test('admin AI feedback keeps processing dialog usable on mobile', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await loginViaUI(page, 'staff', 'admin')

    await page.goto('/admin/ai-feedback')

    const firstFeedbackCard = page.locator('[data-testid="responsive-ai-feedback-card"]').first()
    await expect(firstFeedbackCard).toBeVisible()
    await expect(page.locator('.responsive-table__desktop')).toBeHidden()
    await firstFeedbackCard.getByTestId('ai-feedback-process-button').click()
    await expect(page.getByRole('dialog')).toBeVisible()
    await expectNoHorizontalOverflow(page)
    await page.keyboard.press('Escape')
    await expect(page.getByRole('dialog')).toHaveCount(0)
  })

  test('prescription review switches to cards on mobile', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await page.route('**/api/v1/prescriptions**', async (route) => {
      if (route.request().method() !== 'GET') {
        await route.continue()
        return
      }
      await route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          message: 'success',
          data: {
            total: 1,
            items: [{
              id: 1,
              prescriptionId: 'RX202607170001',
              status: 'PENDING_REVIEW',
              totalFee: 88.5,
              paymentStatus: 'UNPAID',
              createdAt: '2026-07-17 10:15:00'
            }]
          }
        })
      })
    })
    await loginViaUI(page, 'staff', 'pharmacy')

    await page.goto('/pharmacy/prescription-review')

    await expect(page.locator('[data-testid="responsive-prescription-card"]').first()).toBeVisible()
    await expect(page.locator('.responsive-table__desktop')).toBeHidden()
    await expectNoHorizontalOverflow(page)
  })

  test('prescription review detail shows medicine cards on mobile', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await loginViaUI(page, 'staff', 'pharmacy')

    await page.goto('/pharmacy/prescription-review')

    const firstCard = page.locator('[data-testid="responsive-prescription-card"]').first()
    await expect(firstCard).toBeVisible()
    await firstCard.getByRole('button', { name: '查看 RX202607160002 处方详情' }).click()

    const detailDialog = page.getByRole('dialog', { name: '处方详情' })
    await expect(detailDialog).toBeVisible()
    await expect(detailDialog.getByTestId('prescription-detail-medicine-card')).toBeVisible()
    await expectNoHorizontalOverflow(page)
  })

  test('prescription review keeps pharmacist review dialog recoverable on mobile', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await loginViaUI(page, 'staff', 'pharmacy')
    await page.evaluate(() => localStorage.setItem('mock_prescription_review_fail_once', '1'))

    await page.goto('/pharmacy/prescription-review')

    const firstCard = page.locator('[data-testid="responsive-prescription-card"]').first()
    await expect(firstCard).toBeVisible()
    await expectTouchTargetsAtLeast(page, '.prescription-review-action:visible')
    await firstCard.getByRole('button', { name: '驳回 RX202607160002' }).click()

    const reviewDialog = page.getByRole('dialog', { name: '审方驳回' })
    await expect(reviewDialog).toBeVisible()
    await reviewDialog.getByPlaceholder('请填写驳回原因（必填）').fill('剂量需要医生复核')
    await reviewDialog.getByRole('button', { name: '确认' }).click()

    await expect(reviewDialog.getByRole('alert')).toContainText(/审方提交失败|请重试/)
    await expect(reviewDialog).toBeVisible()
    await expectNoHorizontalOverflow(page)
  })

  test('prescription review ignores stale list responses after filter changes', async ({ page }) => {
    await loginViaUI(page, 'staff', 'pharmacy')
    await page.evaluate(() => localStorage.setItem('mock_prescription_pending_review_delay_once', '1'))

    await page.goto('/pharmacy/prescription-review')
    await page.locator('.header-actions .el-select').click()
    await page.getByRole('option', { name: '已通过' }).click()

    const desktopTable = page.locator('.responsive-table__desktop')
    await expect(desktopTable.getByText('RX202607190001')).toBeVisible()
    await page.waitForTimeout(700)
    await expect(desktopTable.getByText('RX202607190001')).toBeVisible()
    await expect(desktopTable.getByText('RX202607160002')).toHaveCount(0)
  })
})
